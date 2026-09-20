# StackDesk 业务化盲审靶场规格

状态：待确认后实现  
日期：2026-09-20  
范围：把 JavaSecLab 从「明示漏洞靶场」改造成可盲审的企业内部 IT 服务台，保留现有 Sink，去掉教学 UI。

## 1. 背景与目标

JavaSecLab 当前是教学靶场：菜单按漏洞类型分组，接口名为 `vul` / `safe`，页面展示缺陷代码、Payload 和流量包。这对培训有效，对代码审计和黑盒挖洞无效——入口本身已经告诉审计员「这里有洞」。

本规格把同一套 Spring Boot 应用改造成 **StackDesk（云栈服务台）**：一套能登录、提单、传附件、管资产、写知识库、报账、接集成的内部 ITSM。漏洞留在真实业务链路里，需要审计或测试才能发现。

### 1.1 目标

- 运行中的应用看起来是业务系统，不出现「漏洞场景」「安全场景」「缺陷代码」「Payload」等靶场用语。
- 现有漏洞 Sink 全部保留且仍可利用（MyBatis / JPA SQL 注入、XSS、文件、SSRF、XXE、RCE、逻辑漏洞、反序列化、组件、表达式、运维暴露面、内存马等）。
- **新增**认证绕过 Sink（当前仓库没有）：用 `startsWith` / `endsWith` 做登录豁免，以及用 XFF / 特定 Header 把请求当成已登录。这部分必须新写 Filter 与内部接口，不能只改菜单名。
- 同一模块同时存在正常路径和有缺陷路径，比例大约正常 6、有缺陷 4，避免「每个按钮都有洞」。
- 训练组织另持审计对照表；对照表不进入运行镜像。
- 继续使用现有技术栈：JDK 8、Spring Boot 2.4.1、MyBatis-Plus、Spring Data JPA、Thymeleaf、Layui、MySQL、Nginx + 外置 Tomcat 部署。

### 1.2 非目标

- 不升级故意保留的危险依赖（Fastjson 1.2.37、Log4j 2.8.2、Shiro 1.2.4 等）。
- 不在运行中的应用里并排提供 `safe` 对照接口。
- 不把内存马、Groovy 代码执行做成普通员工功能。
- 本规格不包含逐步改文件的实现计划；实现计划在规格确认后再写。
- 不改变「仅隔离环境运行」的安全边界。

## 2. 产品定义

### 2.1 定位

StackDesk 是企业内部 IT 服务台，供员工提单、IT 处理人处理、管理员配置集成与资产。

对外品牌：登录页标题、浏览器 Title、侧栏 Logo 均为「StackDesk 云栈服务台」。仓库目录名可暂保持 `JavaSecLab`，运行时文案必须业务化。

### 2.2 角色

| 角色 | 默认账号（明文，保持靶场习惯） | 权限 |
| --- | --- | --- |
| 员工 | `test` / `test` | 自己的工单、知识库只读、自己的报销、个人资料 |
| IT 处理人 | `agent` / `agent` | 被指派的工单、资产探活、附件处理 |
| 管理员 | `admin` / `admin` | 用户、集成、导入导出、报表表达式、系统设置、运维入口 |

角色存在 `user.role` 字段：`employee` / `agent` / `admin`。垂直越权漏洞点是：部分管理接口只靠「知道 URL」，不校验 `role`。水平越权漏洞点是：工单/报销详情只靠 `id` 查询，不校验归属。

### 2.3 成功标准

- 未持有审计对照表的人，打开系统后能当 ITSM 使用，无法从菜单判断漏洞类型。
- 持有对照表或进行代码审计时，能从业务接口追到原 Sink。
- 原教学文档中的利用手法在对应业务入口上仍然成立（Payload 可变，Sink 行为不变）。
- 旧路径 `/sqli/*`、`/xss/*`、`/command/vul1` 等不再出现在菜单，也不做 301 别名，避免路径名泄题。

## 3. 信息架构

登录后为 Layui 后台壳，左侧业务菜单，首页为待办摘要，不是漏洞宫格。

```text
StackDesk
├── 工作台            /home
├── 工单中心
│   ├── 我的工单      /tickets
│   ├── 全部工单      /tickets/all          （处理人/管理员）
│   └── 新建工单      /tickets/new
├── 知识库
│   ├── 文章          /wiki
│   └── 模板          /wiki/templates       （管理员可见编辑，全员可见渲染）
├── 资产管理
│   ├── 主机台账      /assets
│   ├── 探活          /assets/probe         （处理人/管理员）
│   └── 导入          /assets/import        （管理员）
├── 报销与额度
│   ├── 我的申请      /reimbursements
│   └── 审批          /reimbursements/inbox （处理人/管理员）
├── 消息中心          /messages
├── 通讯录            /directory
├── 个人设置
│   ├── 资料与头像    /settings/profile
│   └── 修改密码      /settings/password
└── 系统管理（管理员）
    ├── 用户          /admin/users
    ├── 集成          /admin/integrations
    ├── 配置导入      /admin/config
    ├── 报表          /admin/reports
    ├── 会话与登录    /admin/sessions
    └── 运维          /admin/ops
```

登录页 `/login`。登录成功后的 `redirect` 参数进入首页或指定页（URL 跳转漏洞点）。

## 4. 领域模型

替换演示表 `sqli`、`xss` 的业务含义，保留可注入/可存储 HTML 的字段形态。`objects` 表改名为业务含义但仍存 blob。

### 4.1 表

**user**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | int PK AI | 新增数字主键，供水平越权用 id 遍历 |
| username | varchar(64) unique | 登录名 |
| password | varchar(255) | 明文，保持现有习惯 |
| role | varchar(32) | employee / agent / admin |
| display_name | varchar(64) | 展示名，可含 XSS |
| signature | varchar(512) | 个人签名，存储 XSS |
| avatar_url | varchar(512) | 头像地址 |
| email | varchar(128) | |
| department | varchar(64) | |
| created_at | datetime | |

种子：`admin/admin`（admin）、`agent/agent`（agent）、`test/test`（employee），以及现有 `123/123` 改为 employee。

**ticket**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | int PK AI | |
| title | varchar(255) | 工单标题，写入日志（Log4j） |
| content | text | 富文本描述 |
| status | varchar(32) | open / processing / done |
| priority | varchar(16) | |
| category | varchar(64) | 可用于动态排序 |
| reporter_id | int | |
| assignee_id | int | |
| created_at / updated_at | datetime | |

**ticket_comment**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | int PK AI | |
| ticket_id | int | |
| user_id | int | |
| content | text | 存储 XSS 主入口 |
| user_agent | varchar(255) | 原 xss.ua，继续入库并在详情展示 |
| created_at | datetime | |

**attachment**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | int PK AI | |
| biz_type | varchar(32) | ticket / wiki / reimbursement |
| biz_id | int | |
| orig_name | varchar(255) | 用户文件名 |
| store_path | varchar(512) | 磁盘路径 |
| created_by | int | |

**wiki_article**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | int PK AI | |
| title | varchar(255) | |
| body | text | 富文本 |
| template_name | varchar(128) | SSTI：用户可控模板名 |
| created_by | int | |

**asset_host**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | int PK AI | |
| hostname | varchar(128) | |
| ip | varchar(64) | 探活目标 |
| owner_id | int | |
| extra_xml | text | 导入时的原始 XML 片段 |

**reimbursement**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | int PK AI | |
| applicant_id | int | |
| amount | decimal(12,2) | 客户端可改 |
| status | varchar(32) | draft / submitted / paid |
| remark | varchar(512) | |
| paid | tinyint | 并发漏洞：无锁更新 |

**directory_xml** 或文件型通讯录：启动时放一份 `classpath:/data/directory.xml`，供 XPath 查询。

**import_object**（由 `objects` 重命名或保留表名、改注释）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | int PK | |
| payload | blob | 任务/配置反序列化 |

MyBatis 操作工单搜索、评论、用户。JPA 操作资产列表与动态排序。不要再引入 JDBC/Hibernate 注入模块。

### 4.2 包结构

按业务分包，不再按漏洞类型分包展示给审计员看「这是 sqli 包」。实现上允许把原 Controller 迁入业务包并改名：

```text
top.whgojp
├── Application.java
├── common/
├── security/
└── modules/
    ├── iam/            登录、用户、资料、JWT、验证码、认证网关绕过（新增）
    ├── ticket/         工单、评论、搜索
    ├── file/           附件（可保留现 file 包，改路由）
    ├── wiki/           知识库、模板
    ├── asset/          资产、探活、导入
    ├── finance/        报销额度
    ├── directory/      通讯录
    ├── message/        站内信、WebSocket
    ├── integration/    Webhook、CORS 小部件、配置导入
    ├── report/         报表、SpEL、导出
    └── admin/          用户管理、运维面入口、会话、插件
```

原 `modules.sqli`、`modules.xss` 等包在迁移完成后删除或变为空，避免包名直接暴露漏洞类型。

## 5. 功能规格与漏洞嵌入

下列「有缺陷」路径必须可利用；「正常」路径必须是同一模块里看起来合理的功能，不能标注「安全场景」。

### 5.1 账号与登录（iam）

| 业务功能 | 方法/路由 | Sink 来源 | 缺陷要点 |
| --- | --- | --- | --- |
| 登录 | `POST /loginProcess` | 现登录 | 用户名错误与密码错误回显不同（账号枚举） |
| 图形验证码 | 登录页 | GraphicController | 可复用、弱验证码 |
| 短信验证码 | 找回密码 | SMSController | 回显或 `code_verify` 绕过 |
| 找回密码 | `/account/reset` | BypassController | 可跳过步骤 |
| 登录签名 | `POST /api/auth/sign-in` | ReverseController | 前端 sign/RSA 可绕过 |
| JWT | `Authorization` 访问 `/api/me` | CredentialController | 可改 role 声明 |
| Remember Me | 登录勾选 | ShiroController | 暴露/使用固定 AES Key |
| 登录后跳转 | `/login?redirect=` | UrlRedirectController | 开放重定向 |
| 管理后台 IP 限制 | `/admin/**` | XffForgeryController | 信任 X-Forwarded-For（授权后的 IP 策略，不是未登录绕过） |
| 静态资源网关 | 见 5.1.1 | **新增** WeakPathAuthBypassFilter | `startsWith` / `endsWith` 判断公开路径，未规范化即可绕过登录 |
| 内网信任接入 | 见 5.1.1 | **新增** TrustedHeaderAuthBypassFilter | XFF 首跳、X-Real-IP、X-Internal-Request、X-Original-URL 等直接视为已认证 |
| 改密 | `/settings/password` | SysController | 正常功能，保持 |

登录失败文案继续区分「用户不存在」和「密码错误」，作为业务上的「友好提示」，不在 UI 解释这是安全问题。

现有 `XffForgeryController` 只影响「已登录后按 IP 展示敏感信息」，**不能**代替未登录访问。5.1.1 的 Filter 在 Spring Security 鉴权之前写入 Authentication，效果是整站接口都可在无 Session 时访问。

### 5.1.1 认证绕过（新增代码，无旧模块可迁）

业务伪装：反向代理后面的「静态资源短路」和「内网网关已鉴权」。代码注释与接口名都用网关/CDN 语气，产品里不出现「绕过登录」。

实现位置：

- `top.whgojp.security.filter.WeakPathAuthBypassFilter`
- `top.whgojp.security.filter.TrustedHeaderAuthBypassFilter`
- `top.whgojp.modules.iam.controller.InternalGatewayController`（敏感数据接口，本身要求已登录）
- 在 `SecurityConfigurer` 里 `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`

Filter 命中后：若当前没有 Authentication，则向 `SecurityContext` 写入用户 `admin` 的 `UsernamePasswordAuthenticationToken`（视为网关已代签）。之后 `anyRequest().authenticated()` 放行。已登录用户不覆盖其身份。

不要把 `10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16` 整段当成内网，否则 Nginx 反代后所有浏览器请求都会被当成已登录。

#### A. 路径前缀 / 后缀误判

公开路径判定**只**使用原始 `request.getRequestURI()`（去掉 query），**禁止** `URI.normalize()`、`FilenameUtils.normalize`、`UrlPathHelper.getPathWithinApplication`。这是漏洞根因：容器/Spring 随后会按规范化路径路由到业务 Controller，Filter 仍以为这是静态资源。

```text
isPublic(uri):
  uri.startsWith("/static")
  uri.startsWith("/public")
  uri.startsWith("/assets/files")
  uri.startsWith("/help/static")
  uri.startsWith("/login/")          // 注意是 /login/ 不是 /login，避免误伤 /loginProcess
  uri.endsWith(".js") || .css || .png || .ico || .woff || .map
```

精确 `/login`、`/loginProcess`、`/logout`、验证码接口 **不得** 注入 admin 身份，以免打乱表单登录。

受保护的业务接口（无 Session 时 302 到登录页；绕过成功则 200 + 数据）：

| 方法 | 路由 | 业务含义 | 返回 |
| --- | --- | --- | --- |
| GET | `/internal/staff-directory` | 内网员工名录（人事同步） | 全部用户，含密码字段 |
| GET | `/internal/ticket-export` | 服务台导出 | 工单/评论全量 |
| GET | `/internal/config-dump` | 网关调试配置 | 非密钥的内部开关 + 当前认证主体 |

可复现的绕过（未登录）：

| 手法 | 示例 | 原理 |
| --- | --- | --- |
| 前缀 + `../` | `/static/../internal/staff-directory` | URI 仍以 `/static` 开头；Tomcat 路由到 `/internal/staff-directory` |
| 前缀 + `..;/` | `/static/..;/internal/staff-directory` | `..;` 被当成路径参数，许多容器不按 `..` 折叠，startsWith 仍成立 |
| login 前缀 | `/login/..;/internal/staff-directory` | `startsWith("/login/")` |
| 静态目录穿越 | `/public/..;/internal/config-dump` | 同前缀误判 |
| 后缀伪装 | `/internal/staff-directory.js` | `endsWith(".js")` |
| 矩阵参数后缀 | `/internal/staff-directory;.js` | URI 以 `.js` 结尾，Servlet path 仍是 `/internal/staff-directory` |
| 后缀 css | `/internal/ticket-export.css` | `endsWith(".css")` |

Nginx 使用 `proxy_pass http://tomcat:8080`（无额外 rewrite），把原始 URI 原样转给 Tomcat，保证 `..;` 不被 Nginx 先折叠。

#### B. Header / XFF 信任接入

`TrustedHeaderAuthBypassFilter` 在路径 Filter 之后执行。任一条件为真即写入 `admin` 身份。

信任 IP 集合（只匹配 **XFF 第一个逗号前** 的 trim 值，或单头的全部 trim 值）：

- `127.0.0.1`
- `::1`
- `localhost`
- `10.0.0.1`（伪造的「网关健康检查地址」，不是整段 10.0.0.0/8）
- `startsWith("127.")`（覆盖 127.0.0.0/8 回环）

| 条件 | 请求头 | 业务说法 | 未登录复现 |
| --- | --- | --- | --- |
| XFF 首跳信任 | `X-Forwarded-For` | 网关已标明来自本机探活 | `X-Forwarded-For: 127.0.0.1`；经 Nginx 时用 `127.0.0.1, 客户端` 仍取首跳 |
| X-Real-IP | `X-Real-IP` | 反代真实客户端 | `X-Real-IP: 127.0.0.1`（若 Nginx 覆盖此头，直连 Tomcat `:8080` 测试） |
| True-Client-IP | `True-Client-IP` | CDN 回源 | `True-Client-IP: 127.0.0.1` |
| X-Client-IP | `X-Client-IP` | 旧负载均衡 | `X-Client-IP: 127.0.0.1` |
| 自定义授权 IP | `X-Custom-IP-Authorization` | 历史 WAF 头 | `X-Custom-IP-Authorization: 127.0.0.1` |
| 内网标记 | `X-Internal-Request` | 服务间调用 | `X-Internal-Request: true`（`yes`/`1` 同样命中） |
| 网关来源 | `X-Gateway-Source` | 内网网关 | `X-Gateway-Source: intranet` 或 `internal` |
| 原始 URL | `X-Original-URL` 或 `X-Rewrite-URL` | 由网关改写过，视为已鉴权 | 头存在即可，值任意，如 `X-Original-URL: /` |

注意：现有 `X-Forwarded-For` 伪造模块（5.1 管理 IP 白名单）是 **已登录** 后的授权问题；本 Filter 是 **未登录** 的认证问题。两个 Sink 都要保留，不要合并成一个 if。

#### C. 正常路径（同一模块里必须有）

| 路由 | 行为 |
| --- | --- |
| `GET /public/status` | 匿名健康检查，只返回 `ok` 与版本号，不注入 admin |
| `GET /static/**`、`/js/**`、`/css/**` | 真正的静态资源，Spring Security `permitAll`，与 Filter 短路独立 |

`/public/status` 不要走 WeakPath Filter 的 admin 注入（精确匹配放行即可）。

#### D. 与 Spring Security 的关系

- `/internal/**` **不要** 加入 `permitAll`。未绕过时必须跳登录页。
- Filter 只解决「有没有登录」；垂直越权仍由后续业务接口自己不校验 role 来体现。
- 不在响应里返回 `X-Auth-Bypass: path` 这类提示头。

### 5.2 工单（ticket）

| 业务功能 | 路由 | Sink | 缺陷要点 |
| --- | --- | --- | --- |
| 工单搜索 | `GET /tickets?q=&orderBy=` | MyBatisController 的 `${}` ORDER BY / LIKE / IN | 搜索走 MyBatis |
| 工单详情 | `GET /tickets/{id}` | HorizontalController | 不校验 reporter/assignee |
| 新建评论 | `POST /tickets/{id}/comments` | StoreController | content、UA 原样入库原样 HTML 渲染 |
| 工单标题 | 创建/更新 | Log4j2Controller | `logger.error(title)` 或 UA |
| 转派 | `POST /tickets/{id}/assign` | CsrfController | 无 CSRF Token 的 GET/POST 均可转派 |
| 全部工单 | `/tickets/all` | VerticalController | 员工猜 URL 可打开 |

工单列表另提供「我的工单」过滤，这是正常路径；「高级搜索」才走动态 SQL。

### 5.3 资产（asset，JPA）

| 业务功能 | 路由 | Sink | 缺陷要点 |
| --- | --- | --- | --- |
| 资产列表 | `GET /assets?owner=&orderBy=` | JPAController vul1/vul2 | JPQL 拼接、动态 ORDER BY |
| 探活 | `POST /assets/{id}/probe` | CommandController vul1/vul2/vul3 | 对 `ip` 做 `ping`/`sh -c`，处理人可用 |
| 通知脚本 | `POST /admin/notify-script` | CodeController Groovy | 仅管理员菜单，预览通知脚本 |
| XML 导入 | `POST /assets/import` | XXEController | 解析库存 XML |
| 通讯录查询 | `GET /directory?name=` | XpathController | XPath 拼接 |

探活页面文案为「检测主机是否在线」，输入框为 IP/主机名，不出现「命令执行」。

### 5.4 知识库与模板（wiki）

| 业务功能 | 路由 | Sink | 缺陷要点 |
| --- | --- | --- | --- |
| 文章渲染 | `/wiki/{id}` | XSS other / th:utext | 正文不安全渲染 |
| 搜索高亮 | `/wiki?keyword=` | ReflectController | 反射 XSS |
| 模板预览 | `/wiki/templates/preview?name=` | SSTIController | 模板名进入视图名 |
| 过滤表达式 | `/admin/reports/filter?expr=` | SPELController | SpEL |
| 富文本上传 | UEditor 接口改到 `/wiki/editor/` | UEditorController | 组件 XSS / 上传 |

DOM XSS、hash 跳转、localStorage 放到知识库前端「阅读进度 / 目录锚点」。  
postMessage 放到「把知识库卡片嵌入门户」。  
WebSocket 放到消息中心实时通知。

### 5.5 附件（file）

现有 Upload/Read/Download/Delete 四条链路全部保留，路由改为：

- `POST /files` 上传（工单/知识库共用）
- `GET /files/{id}/content` 预览/读取
- `GET /files/{id}/download` 下载
- `DELETE /files/{id}` 删除

缺陷路径：不校验后缀、路径穿越、任意删除。  
正常路径：图片附件走白名单，作为「头像上传」的一种方式，与工单附件入口分开，避免全站不可用。

静态映射 `/file/**` 保留，以便上传后访问。

### 5.6 报销（finance）

由 PayController、ConcurrentController、Csrf 转账合并：

- `POST /reimbursements` 提交金额（客户端 amount）
- `POST /reimbursements/{id}/pay` 支付（无幂等、无锁）
- 流程可跳过审批直接 pay（原支付流程绕过）

CSRF：报销支付支持跨站 POST。  
正常路径：列表页展示「已支付不可重复」的提示，但后端不强制，审计需抓包验证。

### 5.7 集成与跨源（integration）

| 业务功能 | 路由 | Sink |
| --- | --- | --- |
| 测试 Webhook | `POST /admin/integrations/webhook/test` | SSRF vul |
| 从 URL 拉取头像 | `POST /settings/profile/avatar-from-url` | SSRF |
| 链接预览 | `GET /tickets/link-preview?url=` | SSRF redirect |
| 门户小部件 | `/widgets/summary` CORS | corsVul |
| JSONP 看板 | `/widgets/summary.jsonp?callback=` | jsonpVul |

内网元数据页改为「云厂商实例信息（内部）」`/internal/meta`，匿名可访问，模拟误暴露。

### 5.8 配置导入与反序列化（admin/config）

管理员「系统配置」支持多种格式，对应组件漏洞，文案为「兼容历史配置包」：

| 导入格式 | 路由 | Sink |
| --- | --- | --- |
| JSON | `POST /admin/config/json` | Fastjson / Jackson |
| XML | `POST /admin/config/xml` | XStream / XMLDecoder |
| YAML | `POST /admin/config/yaml` | SnakeYAML |
| 二进制任务 | `POST /admin/config/job` | readObject |
| 数据源测试 | `POST /admin/ops/datasource-test` | SpringBoot JDBC URL + objects blob |

不要在页面写 CVE 编号。可写「支持旧版客户端导出的配置」。

### 5.9 运维面（admin/ops）

- Swagger 仍开启，标题改为「StackDesk OpenAPI」，路径保持可发现。
- Actuator 仍在 `/sys/actuator`，菜单「运行指标」链过去，或仅文档泄漏路径。
- Druid `/druid/` 作为「SQL 监控」。
- 备份文件、目录列表、前端硬编码密钥：放到「帮助中心 / 静态资源」，对应 infoleak。
- 测试 Ping 页改为处理人「网络诊断」`/assets/diagnose`，与探活可共用命令注入。
- ZIP 导入知识库附件走 DosController。
- 报表导出图片尺寸走图片 DoS。

### 5.10 内存马与危险插件（admin/plugins）

菜单名称：「请求过滤器 / 热更新（实验）」，仅 admin。

路由：`/admin/plugins/filter|servlet|listener|interceptor` 对应现 mshell inject/detect。

不出现在工作台快捷入口。帮助文案写成「动态注册处理链，用于临时排障」，不写「内存马」。

### 5.11 其它

- 摄像头/劫持：不进入正式菜单。若保留，放在个人设置「设备检测」且默认关闭；规格默认 **不做首页入口**，避免不像 ITSM。
- JSONP 仅门户小部件使用。
- HttpOnly / CSP 不作为产品卖点展示；缺陷接口不主动加这些头。

## 6. 旧入口到新入口对照

实现时以本表迁移 Sink，迁移后删除旧路由，避免 `/sqli` 这种路径名。

| 旧路由前缀 | 新路由前缀 | 业务名 |
| --- | --- | --- |
| `/sqli/mybatis` | `/tickets` 搜索与排序 | 工单搜索 |
| `/sqli/jpa` | `/assets` | 资产列表 |
| `/xss/reflect` | `/wiki` 搜索 | 知识库搜索 |
| `/xss/store` | `/tickets/{id}/comments` | 工单评论 |
| `/xss/dom` | `/wiki` 前端锚点/进度 | 阅读页脚本 |
| `/xss/other` | 知识库正文、UEditor、WS、postMessage | 知识库/消息 |
| `/csrf` | `/tickets/{id}/assign`、报销支付 | 转派/支付 |
| `/file/*` | `/files` | 附件 |
| `/ssrf` | `/admin/integrations`、头像 URL | 集成 |
| `/xxe` | `/assets/import` | 资产导入 |
| `/crossorigin` | `/widgets` | 门户部件 |
| `/command` | `/assets/{id}/probe` | 探活 |
| `/code` | `/admin/notify-script` | 通知脚本 |
| `/logic/idor` | `/tickets/{id}`、`/admin/reports` | 越权 |
| `/logic/captcha` | 登录/找回 | 验证码 |
| `/logic/pay` `/logic/concurrent` | `/reimbursements` | 报销 |
| `/other/URLRedirect` | 登录 redirect | 跳转 |
| `/other/xff` | 管理 IP 策略 | XFF |
| `/other/dos` | 报表导出/ZIP | 导出 |
| `/other/xpath` | `/directory` | 通讯录 |
| `/infoLeak/*` | 静态帮助、诊断、备份下载 | 运维泄漏 |
| `/loginconfront/*` | `/login`、`/account`、`/api/auth` | 账号 |
| `/springboot` | `/admin/ops` | 运维 |
| `/spel` | `/admin/reports/filter` | 报表 |
| `/ssti` | `/wiki/templates` | 模板 |
| `/readObject` `/snakeYaml` `/xmlDecoder` | `/admin/config/*` | 配置导入 |
| `/fastjson` `/jackson` `/xstream` `/log4j2` `/shiro` | 配置导入/登录/日志 | 组件 |
| `/mshell/*` | `/admin/plugins/*` | 实验插件 |
| （新增，无旧路由） | `/internal/*` + 路径/Header Filter | 静态网关与内网信任接入 |

旧 Thymeleaf 教学页（`templates/vul/**`）删除或不再挂路由。`init.json` 改为第 3 节菜单。首页宫格改为待办：我的工单数、待审批报销、未读消息。

## 7. UI 与文案约束

- 禁止出现：漏洞、靶场、Payload、Sink、POC、安全场景、缺陷代码、Run（作为攻击按钮）。
- 主按钮用业务动词：搜索、提交、导入、探活、预览模板、测试连接。
- 不内嵌 CodeMirror 展示 Java 源码。
- 不提供 Payload 下拉；输入框给业务示例（如工单关键字、主机 IP）。
- 错误信息可含 SQLException / 模板异常原文（这是漏洞表现，不是教学注释）。
- 双语：业务文案继续走 i18n；键名改为 `ticket.search` 这类，删除 `xss.reflect.vul` 等键。

## 8. 平台安全 vs 靶场安全

以下属于 **产品外壳**，可以保持现状以便漏洞能打到：

- Spring Security Session 登录、全局 CSRF disable（报销/转派 CSRF 才成立）
- CORS 全局 `*` 与小部件接口的反射 Origin 并存时，小部件接口单独设置头
- Actuator / Swagger / Druid 开启
- 明文密码、NoOpPasswordEncoder
- Nginx 反代保留 X-Forwarded-* ，使 XFF 场景成立

以下 **不要** 再做教学向加强：

- 不要加「安全编码说明」弹层
- 不要在响应头加 `X-Vuln-Type`
- 不要把审计对照表放到 `/static`

## 9. 审计对照表（运行外）

路径：`docs/audit-map.md`（实现阶段编写，不进 Docker 镜像）。

每条记录固定字段：

- 业务入口（方法 + URL）
- 角色
- Source
- Sink（类名.方法名 + 代码行为）
- 预期利用结果
- 原 JavaSecLab 模块名（便于迁移核对）

该文件仅训练组织使用。README 对外只描述「StackDesk 内部服务台演示环境」，可保留「隔离网络运行」警告，不列举 CVE。

## 10. 部署

保持三种方式：

1. IDEA + `dev` profile  
2. 原 `docker-compose.yml` 内嵌 Tomcat（可改镜像名文案）  
3. `docker-compose.nginx-tomcat.yml`：Nginx → Tomcat 9 ROOT → MySQL  

运行端口仍由 Nginx `:80` 对外。应用名、登录页、日志横幅改为 StackDesk。  
`SPRING_PROFILES_ACTIVE=docker` 不变。

## 11. 数据与兼容

- 提供新的 `sql/stackdesk.sql`（或升级现有 `JavaSecLab.sql`），包含第 4 节表和种子数据。
- 开发环境允许 Hibernate/JPA `ddl-auto: update` 补列，以 SQL 脚本为准。
- 不再需要 `sqli`、`xss` 表名；若迁移成本高，可先改实体映射到新表，不保留旧表。

## 12. 关键决策

1. **产品选 ITSM 而不是电商**：工单/资产/知识库能自然容纳文件、SSRF、XXE、RCE、模板、导入；支付用报销额度覆盖。
2. **盲审优先，运行时不提供 safe 对照**：修复样本不进入运行应用。
3. **按业务包重组，删除漏洞类型包名**：避免白盒第一眼看到 `modules.sqli`。
4. **Sink 行为冻结，只改调用方与路由**：利用手法不变，降低「改没了」的风险。
5. **约 60/40 的正常与有缺陷功能**：同一资源分「我的列表（正常过滤）」和「高级搜索/导入/探活（有缺陷）」。
6. **高危能力放管理员/处理人**：Groovy、内存马、反序列化导入不给普通员工菜单。
7. **审计对照表不部署**：防止靶场自己把答案托管出去。
8. **不保留旧 `/vul` 别名**：否则路径名会直接泄题。
9. **认证绕过是新 Sink，不是改 XFF 信息泄漏**：路径用原始 URI 的 `startsWith`/`endsWith`；Header 只信任回环与少量固定值，避免 Nginx 后面全站免登录。

## 13. 分期（规格级，非施工勾选）

| 阶段 | 交付 | 依赖 |
| --- | --- | --- |
| P0 | 品牌/菜单/角色/新表结构/登录与工作台，去掉靶场首页宫格 | 无 |
| P1 | 工单 + 评论 + MyBatis 搜索 + 水平/垂直越权 + CSRF 转派 | P0 |
| P2 | 附件四操作接到工单/知识库 | P1 |
| P3 | 资产 JPA 列表、探活 RCE、XML 导入 XXE、通讯录 XPath | P0 |
| P4 | 知识库、模板 SSTI、报表 SpEL、XSS 其它场景 | P1 |
| P5 | 报销额度、并发、支付逻辑 | P0 |
| P6 | 集成 SSRF/CORS/JSONP、配置导入反序列化与组件、Log4j/Shiro | P0 |
| P7 | 运维面、信息泄漏、登录对抗收口、内存马插件、删除旧 vul 包与教学页 | 前序 |

每阶段结束时：业务功能可演示，且对照表中该阶段条目可复现。

## 14. PR 规划

| PR | 标题 | 影响 | 依赖 |
| --- | --- | --- | --- |
| PR1 | 业务外壳：StackDesk 菜单、角色、工作台、SQL 种子；认证绕过 Filter 与 `/internal/*` | `init.json`、home、user 表、i18n、登录文案、WeakPath/TrustedHeader Filter、InternalGatewayController | 无 |
| PR2 | 工单域：搜索/评论/越权/转派 | 新 ticket 包，迁移 MyBatis/XSS store/IDOR/CSRF Sink | PR1 |
| PR3 | 附件域接到工单 | file 路由重写 | PR2 |
| PR4 | 资产域：JPA/探活/XXE/XPath | asset/directory 包 | PR1 |
| PR5 | 知识库与表达式 | wiki/report，XSS/SSTI/SpEL | PR2 |
| PR6 | 报销域 | finance 包 | PR1 |
| PR7 | 集成与配置导入 | integration/admin/config，组件与反序列化 | PR1 |
| PR8 | 运维、泄漏、登录对抗、插件；删除 `templates/vul` 与旧模块 | 全站收口 | PR2–PR7 |

每个 PR 必须更新 `docs/audit-map.md` 对应行，且不把该文件拷进镜像。

## 15. 风险

- 业务包装后若错误「修掉」拼接或编码，漏洞会消失。迁移时以 Sink 单测或对照表手工复现为准。
- 包重命名会使外部已写的 POC 失效，这是预期（盲审不要旧路径）。
- 管理员功能过多会像「漏洞控制台」；必须用 ITSM 文案稀释，并把高危入口放二级菜单。
- 全局 CSRF disable 与「真 ITSM」不符，但为保留 CSRF 漏洞必须维持；规格接受这一点。

## 16. 确认项

实现前只需确认本规格。若无修改意见，下一步再写分任务实现计划（具体改哪些文件、如何验收），然后才改代码。
