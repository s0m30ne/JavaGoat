# JavaGoat

基于 [JavaSecLab](https://github.com/whgojp/JavaSecLab) 改造的 Java 漏洞靶场。运行形态是企业内部 IT 服务台 **StackDesk（云栈服务台）**：漏洞埋在工单、资产、知识库、报销、单点登录等真实流程里，需要代码审计或黑盒测试才能发现，产品界面不再按「XSS / SQL 注入 / RCE」分菜单。

上游项目：[https://github.com/whgojp/JavaSecLab](https://github.com/whgojp/JavaSecLab)（作者 [whgojp](https://github.com/whgojp)）。本仓库在其 Sink 与组件版本基础上，做了业务包装、部署形态和若干新漏洞链路。

> 故意保留危险接口、低版本依赖和不安全配置。只在本地或隔离网络运行，不要直接暴露到公网。

## 和上游的主要差异

| 上游 JavaSecLab | 本仓库 JavaGoat |
| --- | --- |
| 按漏洞类型教学，vul / safe 对照页 | 业务系统外壳，无教学标注 |
| 内嵌 Tomcat `java -jar` | 推荐 Nginx → 外置 Tomcat 9 → MySQL |
| JDBC / Hibernate / MyBatis / JPA 四套 SQL 注入 | 业务库只保留 **MyBatis** 与 **JPA** |
| 含 Shiro-550、内存马教学模块 | **已移除** Shiro 与内存马 |
| 明文告诉你这里有洞 | 要自己挖；训练对照表在 `docs/audit-map.md`，不进镜像 |

## 业务系统

登录后看到的是 StackDesk，不是漏洞练习宫格。

| 模块 | 路径 | 做什么 |
| --- | --- | --- |
| 工单中心 | `/tickets` | 提单、搜索、评论、转派 |
| 知识库 | `/wiki` | 文章、富文本、搜索 |
| 资产管理 | `/assets` | 主机台账、探活、XML 导入 |
| 报销与额度 | `/reimbursements` | 申请、审批、支付 |
| 消息中心 | `/messages` | 群聊会话 |
| 通讯录 | `/directory` | 人员查询 |
| 附件 | `/files` | 上传 / 读 / 下载 / 删除 |
| 个人设置 | `/settings/profile` | 资料、签名、头像 URL |
| 系统管理 | `/admin/*` | 用户、集成、配置导入、报表、会话、运维 |
| 企业门户 SSO | `/sso/authorize` | 登录页「门户单点登录」 |

默认账号（明文）：

| 账号 | 密码 | 角色 Cookie |
| --- | --- | --- |
| `admin` | `admin` | `admin` |
| `agent` | `agent` | `agent` |
| `test` | `test` | `employee` |

## 漏洞覆盖

Sink 多数来自 JavaSecLab，入口改成业务接口。本仓库额外增加的类型标了「新增」。

### 注入与执行

- **SQL 注入**：工单高级搜索走 MyBatis `${}`；资产列表走 JPA JPQL 拼接
- **XSS**：工单评论 / 知识库正文 / 消息 / 签名 `th:utext`；知识库搜索反射；UEditor 上传
- **命令注入**：资产探活 `ping -c 1 ` + 用户输入
- **代码注入**：报表「通知脚本」GroovyShell
- **SpEL**：报表过滤表达式
- **SSTI**：系统管理 → 通知模板，视图名直接当 Thymeleaf 模板
- **XXE**：资产 XML 文件导入，`DocumentBuilder` 未禁外部实体
- **XPath**：通讯录姓名拼进表达式
- **Log4j2**：工单标题写入 `logger.error`；运维页记录 User-Agent

### 文件与请求

- **任意文件读 / 下载 / 删除**：`/files/read|download|delete?fileName=`，路径无沙箱
- **任意上传**：`/files/upload` 不校验类型
- **SSRF**：头像「从 URL 拉取」、Webhook 测试连接
- **CSRF**：工单转派支持 GET，全局未开 CSRF Token
- **开放重定向**：登录 `redirect=`

### 鉴权与逻辑（含新增）

- **水平越权**：工单详情 / 评论 / 转派、报销支付只认 ID，不认归属
- **垂直越权（弱校验）**：`/admin/**`、全部工单、报销审批、探活/导入读取 Cookie `role`、Header `X-Role` / `X-User-Role` 或参数 `role`，**不查库**。把 `employee` 改成 `admin` 即可进管理功能
- **登录绕过（新增）**：原始 URI `startsWith("/static")` 等或后缀 `.js`；`/static/..;/internal/staff-directory`、`/internal/staff-directory;.js`
- **Header 认证绕过（新增）**：`X-Forwarded-For` 首跳 `127.0.0.1`、`X-Internal-Request: true`、存在 `X-Original-URL` 等，直接写成 admin 身份
- **OAuth + 任意重定向账户劫持（新增）**：门户 SSO 的 `redirect_uri` 用 `startsWith("/")` 校验，`//evil.com` 可把授权码带走，再访问 `/sso/callback?code=` 登录受害者

### 组件与运维面

- 配置导入：Fastjson / Jackson / XStream / SnakeYAML / XMLDecoder / `ObjectInputStream`
- 运维页：Swagger `/v3/api-docs`、Actuator `/sys/actuator`、Druid `/druid/`
- 支付/并发：报销 `/reimbursements/{id}/pay` 无幂等、无锁
- 找回密码可跳步；短信验证码可回显或 `code_verify=true`

已删除：Shiro RememberMe 密钥泄漏、内存马注入。

训练对照（不进镜像）：[docs/audit-map.md](./docs/audit-map.md)

## 过滤机制

业务接口加了一层「看起来像防护」的检查（`WeakInputGuard`），**区分大小写、不规范化**，用于练习绕过，不是安全基线。

| 检查 | 会拦住 | 仍可过 |
| --- | --- | --- |
| SQL | `union select`、`sleep(` | `' OR 1=1`、`SLEEP(` |
| XSS | `<script`、`javascript:` | `<img src=x onerror=...>` |
| 命令 | `;`、`&&`、`cat ` | `127.0.0.1 \| id` |
| Groovy | `Runtime`、`ProcessBuilder` | `"id".execute()` |
| SpEL | `java.lang.Runtime`、`getRuntime` | `new java.lang.ProcessBuilder('id').start()` |
| SSTI 模板名 | `..`、`WEB-INF` | `__${7*7}__::.x` |
| XXE | 精确 `file:///etc/passwd` | `file:///etc/hosts` 或 http 实体 |
| Log4j | `${jndi:ldap` | `${jndi:rmi` 或嵌套表达式 |
| SSO 回调 | 非 `/` 且非本机前缀 | `//evil.com`、`http://127.0.0.1.evil.com` |
| 管理角色 | Cookie/Header 不是 admin/agent | 改 `role=admin` 或 `X-Role: admin` |

没有 HTML 消毒、没有 `toLowerCase()` 黑名单、没有按 Host 白名单校验 OAuth `redirect_uri`。

## 技术栈

- JDK 8、Spring Boot 2.4.1、Spring Security（Session）
- MyBatis-Plus、Spring Data JPA、Thymeleaf、Layui、MySQL 8
- 故意保留的低版本：Fastjson 1.2.37、Log4j 2.8.2、XStream 1.4.14、Groovy 2.5.6 等

## 部署架构

推荐形态（贴近传统 Java 生产）：

```text
浏览器
  │ :80
  ▼
Nginx 1.26          反代、WebSocket、X-Forwarded-*
  │ 容器内 :8080
  ▼
Tomcat 9            WAR 部署为 ROOT（不是 Spring Boot 内嵌 Tomcat）
  │ mysql:3306
  ▼
MySQL 8             库名仍为 JavaSecLab（兼容上游 SQL）
```

- 应用上下文必须是 `/`，否则登录和静态资源路径会错
- Tomcat 默认不对外映射 8080，只给 Nginx 访问
- WAR 的 `ServletInitializer` 只在镜像构建时注入，不改业务源码树
- 另保留上游的内嵌 Tomcat Compose，便于对照

## 启动方法

环境：JDK 8、Maven、Docker / Docker Compose、MySQL 8（本地 IDEA 时需要）。

### 方式一：Nginx + 外置 Tomcat（推荐）

```shell
docker compose -f docker-compose.nginx-tomcat.yml -p javagoat-stack up -d --build
```

状态：

```shell
docker compose -f docker-compose.nginx-tomcat.yml -p javagoat-stack ps
```

三个容器 `healthy` / `running` 后打开 <http://127.0.0.1/login>。

停止：

```shell
docker compose -f docker-compose.nginx-tomcat.yml -p javagoat-stack down
```

首次构建会在镜像里打 WAR，时间取决于 Maven 依赖。若 80 / 3306 被旧编排占用，先停掉再启动。

### 方式二：本地构建内嵌 Tomcat 镜像

```shell
mvn clean package -DskipTests
docker compose -p javagoat up -d
```

### 方式三：已发布镜像（上游镜像名，仅作对照）

```shell
docker compose -f docker-compose.image.yml up -d
```

### 方式四：IDEA / Maven 本地跑

1. 创建库并导入 [sql/JavaSecLab.sql](./sql/JavaSecLab.sql)（表结构会在启动时由程序补齐工单/资产等业务表）
2. `application.yml` 中 `spring.profiles.active: dev`
3. `application-dev.yml` 填写 MySQL 账号，库名 `JavaSecLab`
4. 启动主类 `top.whgojp.Application`

## 安全提示

- 隔离网络、一次性账号、测试库
- 不要把宿主机敏感目录挂进容器
- 上传目录、日志、导出配置都按不可信数据处理
- `docs/audit-map.md` 不要打进生产镜像

## 开源协议与致谢

本项目遵循 [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)，见 [LICENSE](./LICENSE)。

漏洞场景与大量 Sink 实现来自 [JavaSecLab](https://github.com/whgojp/JavaSecLab)。感谢原作者 **whgojp** 的开源工作。
