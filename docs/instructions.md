# 常规漏洞

## 跨站脚本
当前覆盖反射型、存储型、DOM型、模板引擎不安全渲染、文件上传导致的存储型XSS、第三方组件XSS、WebSocket XSS、postMessage XSS、CSP、HttpOnly、输出编码等常见场景。

XSS的本质是不可信数据进入浏览器页面执行上下文后，被当作HTML、脚本、URL或可执行DOM操作解析。修复优先按输出上下文处理数据，普通文本使用HTML实体编码或安全DOM API，URL/属性/JavaScript/CSS等位置使用对应编码与白名单校验；CSP、HttpOnly、输入过滤是辅助防护，不能替代根因修复。

已覆盖类型

| 分类 | 已有场景 | 结论 |
| --- | --- | --- |
| 反射型XSS | GET/POST参数、String直出、Content-Type差异 | 覆盖请求即触发、响应类型影响浏览器解析的基础成因 |
| 反射型安全写法 | 前后端白名单、CSP、HTML正文输出编码、HttpOnly | 覆盖常见防护，并明确白名单/CSP/HttpOnly不是根因修复 |
| 存储型XSS | 表单内容、User-Agent持久化、表格不安全渲染 | 覆盖先存储后触发，也包含Header进入持久化链路 |
| 存储型安全写法 | 表格渲染阶段输出编码 | 说明数据库可保存原始值，进入页面前必须按上下文编码或净化 |
| DOM型XSS | innerHTML、localStorage、hash跳转、location、eval、document.write | 覆盖常见Source到Sink的客户端链路 |
| DOM型安全写法 | textContent、URL协议白名单、命令映射、createTextNode | 覆盖DOM侧推荐修复方式 |
| 其他场景 | Thymeleaf `th:utext`、文件上传、jQuery/Swagger/UEditor、WebSocket、postMessage | 覆盖模板、文件、供应链与HTML5通信场景 |

### 反射型XSS测试

页面：`/xss/reflect/vul`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| GET型JSON返回 | `GET /xss/reflect/vul1?payload=<img src=x onerror=alert(1)>` | HTML事件载荷 | 接口返回payload；页面结果区使用不安全HTML渲染时可触发 |
| POST型JSON返回 | `POST /xss/reflect/vul1` | `payload=<img src=x onerror=alert(1)>` | 接口返回payload；验证POST入口同样可控 |
| String直出 | `GET /xss/reflect/vul2?payload=<script>alert(1)</script>` | 脚本标签 | 响应体直接包含payload，用于观察浏览器解析行为 |
| text/plain | `GET /xss/reflect/vul3?type=plain&payload=<script>alert(1)</script>` | 脚本标签 | `Content-Type`为`text/plain;charset=utf-8`，浏览器按文本展示 |
| text/html | `GET /xss/reflect/vul3?type=html&payload=<script>alert(1)</script>` | 脚本标签 | `Content-Type`为`text/html;charset=utf-8`，浏览器按HTML解析 |
| 流量包/示例载荷 | 页面下拉与按钮 | 标签探测、流量劫持、Cookie读取、页面篡改 | 示例可填充，按钮可提交 |

### 反射型安全场景测试

页面：`/xss/reflect/safe`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 前端白名单 | `GET /xss/reflect/safe1?type=frontEnd&payload=<script>alert(1)</script>` | 非白名单字符 | 页面按钮触发时前端拦截；直接请求仍会返回，说明前端不是安全边界 |
| 后端白名单 | `GET /xss/reflect/safe1?type=backEnd&payload=<script>alert(1)</script>` | 非白名单字符 | 返回“输入内容包含非法字符，请检查输入” |
| CSP Header | `GET /xss/reflect/safe2?payload=<script>alert(1)</script>` | 脚本标签 | 响应包含`Content-Security-Policy`，用于演示防御层 |
| HTML正文手动编码 | `GET /xss/reflect/safe3?type=manual&payload=<img src=x onerror=alert(1)>` | HTML事件载荷 | 返回实体编码后的内容，不作为标签执行 |
| Spring HTML编码 | `GET /xss/reflect/safe3?type=spring&payload=<img src=x onerror=alert(1)>` | HTML事件载荷 | 返回Spring编码后的内容 |
| HttpOnly | `GET /xss/reflect/safe4?payload=<script>alert(document.cookie)</script>` | Cookie读取载荷 | 返回设置结果，响应`Set-Cookie`应带`HttpOnly` |

### 存储型XSS测试

页面：`/xss/store`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 原生写入 | `POST /xss/store/vul` | `payload=<img src=x onerror=alert(1)>` | 写入成功，漏洞表格不安全渲染时可触发 |
| User-Agent持久化 | `POST /xss/store/vul` | Header `User-Agent: <img src=x onerror=alert(1)>` | UA字段被持久化，漏洞表格不安全渲染时可触发 |
| 列表查询 | `GET /xss/store/getXssList?page=1&limit=10` | 无 | 返回分页数据，包含刚写入记录 |
| 安全表格 | 页面安全场景表格 | 已存储恶意内容 | Content与User-Agent经HTML实体编码展示，不执行脚本 |
| 删除记录 | `POST /xss/store/deleteOne?id=<记录ID>` | 已存在ID | 返回删除成功，页面表格记录消失 |

### DOM型XSS测试

页面：`/xss/dom`

| 场景 | 入口 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| innerHTML | 多种代码场景/innerHTML | `123<img src=x onerror=alert(1)>123` | 结果区使用`innerHTML`写入，事件载荷可执行 |
| LocalStorage | 多种代码场景/LocalStorage | `123<img src=x onerror=alert(1)>123` | 先写入`localStorage`，再读取并不安全写入DOM |
| hash跳转 | `/xss/dom/href#javascript:alert(1)` | `javascript:`伪协议 | 页面读取`location.hash`并赋值给`location.href` |
| location | 多种代码场景/location对象 | `javascript:alert(1)` | 直接赋值给`window.location`，用于演示危险URL Sink |
| eval | 多种代码场景/eval执行 | `alert(1)` | 用户输入被`eval`执行 |
| document.write | 多种代码场景/document对象 | `<img src=x onerror=alert(1)>` | `document.write`写入HTML并可能触发 |
| 文本安全输出 | 安全场景/文本输出 | `<img src=x onerror=alert(1)>` | 使用`textContent`，作为文本展示 |
| URL安全校验 | 安全场景/URL跳转 | `javascript:alert(1)` | 拦截危险协议 |
| 替代eval | 安全场景/替代eval | `alert(1)` | 命令白名单无匹配，拒绝执行 |
| DOM API | 安全场景/DOM API | `<img src=x onerror=alert(1)>` | 使用文本节点展示，不执行脚本 |

### 其他XSS场景测试

页面：`/xss/other`

| 场景 | 请求/入口 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| Thymeleaf `th:utext` | `GET /xss/other/vul2OtherTemplate?type=html&payload=<img src=x onerror=alert(1)>` | HTML事件载荷 | `th:utext`按HTML渲染，演示模板不安全输出 |
| Thymeleaf `th:text` | `GET /xss/other/vul2OtherTemplate?type=text&payload=<img src=x onerror=alert(1)>` | HTML事件载荷 | `th:text`实体转义，作为文本展示 |
| HTML文件上传 | `POST /xss/other/vul1Upload?type=html` | `xss.html` | 返回可访问文件路径，访问后按浏览器解析策略触发/展示 |
| SVG文件上传 | `POST /xss/other/vul1Upload?type=svg` | `xss.svg` | 返回可访问文件路径，用于验证可解析文件风险 |
| XML文件上传 | `POST /xss/other/vul1Upload?type=xml` | `xss.xml` | 后端解析成功后落盘，返回访问路径 |
| PDF文件上传 | `POST /xss/other/vul1Upload?type=pdf` | `xss.pdf` | 返回访问路径；PDF脚本执行能力取决于阅读器实现 |
| jQuery组件XSS | `/xss/other/jquery-xss` | 页面内示例 | 页面可打开，用于演示旧版jQuery风险 |
| Swagger UI组件XSS | `/swagger-ui/index.html?configUrl=...` | 恶意配置URL示例 | 页面可打开，用于供应链组件风险演示 |
| UEditor | `/ueditor`、`/ueditor/config` | 编辑器上传/配置 | 页面与配置接口可访问，上传接口返回UEditor格式结果 |
| WebSocket XSS | 页面HTML5特性/WebSocket XSS | `<img src=x onerror=alert(1)>` | 服务端广播消息，前端用`innerHTML`写入消息区 |
| postMessage XSS | 页面HTML5特性/PostMessage XSS | `<img src=x onerror=alert(1)>` | 接收窗口未校验origin且用`innerHTML`写入消息 |

## CSRF

当前覆盖基于登录态 Cookie 的状态变更请求、CSRF Token 校验、Origin/Referer 辅助校验三类核心场景。模块重点演示“用户已登录 + 浏览器自动携带凭证 + 服务端缺少请求来源或意图校验”这一条攻击链。

CSRF的本质是攻击者诱导已登录用户访问恶意页面或触发恶意请求，浏览器自动携带目标站点 Cookie/Session 等凭证，服务端误以为请求来自用户本人，从而执行转账、改密、绑定账号等敏感操作。修复优先使用框架内置 CSRF 防护或不可预测的 CSRF Token；Origin/Referer、SameSite Cookie、二次确认、操作审计是重要补充，但不应替代 Token 和服务端鉴权。

已覆盖类型

| 分类 | 已有场景 | 结论 |
| --- | --- | --- |
| 原生漏洞 | `GET /csrf/vul` 只依赖登录态执行转账 | 覆盖最基础的跨站请求伪造风险，GET 状态变更会放大问题 |
| Token防护 | `GET /csrf/safe1` 校验 Session 中的随机 Token | 覆盖 CSRF 的主流修复方式 |
| 来源校验 | `GET /csrf/safe2` 校验 Origin/Referer 的协议、域名、端口 | 适合作为 Token 之外的辅助防线 |
| 安全编码提示 | 页面说明 SameSite、二次确认、短有效期、审计 | 覆盖业务侧加固建议 |

### CSRF漏洞场景测试

页面：`/csrf`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /csrf` | 已登录会话 | 页面正常打开，展示原生漏洞、Token防护、Origin/Referer辅助校验和代码片段 |
| 原生转账 | `GET /csrf/vul?receiver=zhangsan&amount=100` | 已登录会话 | 返回当前登录用户、收款人和金额，说明仅凭 Cookie/Session 即可触发状态变更 |
| 未登录访问 | `GET /csrf/vul?receiver=zhangsan&amount=100` | 无登录会话 | 跳转登录页或被认证流程拦截 |
| GET状态变更 | 页面“漏洞场景：原生漏洞场景”表单 | `receiver=zhangsan&amount=100` | 点击后以 GET 打开转账结果，便于观察 CSRF 风险 |

### CSRF安全场景测试

页面：`/csrf`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 获取Token | `GET /csrf/getCsrfToken` | 已登录会话 | 返回随机 `csrfToken`，并写入 Session |
| Token缺失 | `GET /csrf/safe1?receiver=zhangsan&amount=100` | 不带 `csrfToken` | 返回 `success=false` 和 “Token失效！” |
| Token错误 | `GET /csrf/safe1?receiver=zhangsan&amount=100&csrfToken=bad` | 错误Token | 返回 `success=false` 和 “Token失效！” |
| Token正确 | `GET /csrf/safe1?receiver=zhangsan&amount=100&csrfToken=<session token>` | Session中生成的Token | 返回当前用户、收款人、金额和Token |
| Origin/Referer缺失 | `GET /csrf/safe2?receiver=zhangsan&amount=100` | 不带来源头 | 返回 `success=false` 和 “Origin/Referer无效！” |
| Origin恶意来源 | `GET /csrf/safe2?receiver=zhangsan&amount=100` | Header `Origin: http://evil.example` | 返回 `success=false` |
| Origin同源 | `GET /csrf/safe2?receiver=zhangsan&amount=100` | Header `Origin: http://127.0.0.1` | 返回当前用户、收款人和金额 |
| Referer同源 | `GET /csrf/safe2?receiver=zhangsan&amount=100` | Header `Referer: http://127.0.0.1/csrf` | 返回当前用户、收款人和金额 |

## SQL注入

当前按业务常用栈覆盖 MyBatis 动态SQL 与 JPA JPQL/动态排序。修复优先使用参数化查询；列名、表名、排序方向等 SQL 结构必须使用枚举或白名单映射。

已覆盖类型

| 分类 | 已有场景 | 结论 |
| --- | --- | --- |
| MyBatis | 内置方法、自定义`#{}`、`${}` ORDER BY/LIKE/IN、foreach | 覆盖MyBatis典型误区 |
| JPA | JPQL、动态排序、命名参数、Criteria白名单 | 覆盖JPA常见风险点 |

### MyBatis场景测试

页面：`/sqli/mybatis`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 内置方法 | `POST /sqli/mybatis/safe1?type=select&id=2` | 合法ID | 正常查询 |
| 内置方法缺少ID | `POST /sqli/mybatis/safe1?type=delete` | 缺少ID | 返回`id不能为空!` |
| 自定义`#{}` | `POST /sqli/mybatis/safe2?type=select&id=999999` | 不存在ID | 返回`用户ID不存在!`，不出现空指针 |
| ORDER BY `${}` | `POST /sqli/mybatis/special1-OrderBy?type=raw&field=username and updatexml(1,concat(0x7e,(SELECT user()),0x7e),1)%23` | 动态字段注入 | 漏洞路径被触发 |
| ORDER BY `#{}`误区 | `POST /sqli/mybatis/special1-OrderBy?type=prepareStatement&field=username` | 合法字段 | 不能作为真正动态字段排序 |
| ORDER BY白名单 | `POST /sqli/mybatis/special1-OrderBy?type=writeList&field=username` | 合法字段 | 正常返回 |
| LIKE `${}` | `POST /sqli/mybatis/special2-Like?type=raw&keyword=1' OR '1'='1` | LIKE注入 | 漏洞路径被触发 |
| LIKE `#{}` | `POST /sqli/mybatis/special2-Like?type=prepareStatement&keyword=admin' OR '1'='1` | LIKE注入 | 作为普通关键词处理 |
| IN `${}` | `POST /sqli/mybatis/special3-In?type=raw&scope=1) OR 1=1 -- ` | IN注入 | 漏洞路径被触发 |
| IN `#{}`误区 | `POST /sqli/mybatis/special3-In?type=prepareStatement&scope=1,2` | 多ID | 作为单个参数处理，不能展开多个占位 |
| IN foreach | `POST /sqli/mybatis/special3-In?type=Foreach&scope=1,2,abc` | 混入非法值 | 忽略非法值，使用合法整数查询 |
| IN foreach空值 | `POST /sqli/mybatis/special3-In?type=Foreach` | 缺少scope | 返回`scope中没有合法整数ID!` |

### JPA场景测试

页面：`/sqli/jpa`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| JPA JPQL | `GET /sqli/jpa/vul1?username=admin' OR '1'='1` | 注入payload | 返回多条记录或表现出注入效果 |
| JPA动态排序 | `GET /sqli/jpa/vul2?orderBy=username desc` | 动态排序字段 | 正常排序；异常payload应暴露风险 |
| JPA参数化 | `GET /sqli/jpa/safe?username=admin' OR '1'='1` | 注入payload | 当作普通用户名，返回未找到记录 |
| JPA排序白名单 | `GET /sqli/jpa/safe-order?orderBy=username` | 合法字段 | 正常排序 |
| JPA排序白名单拦截 | `GET /sqli/jpa/safe-order?orderBy=username desc` | 非白名单字段 | 返回排序字段不合法 |

## 任意文件操作

当前覆盖任意文件上传、任意文件读取、任意文件下载、任意文件删除四类常见风险，能串联“上传恶意文件 -> 通过静态映射访问 -> 读取/下载敏感文件 -> 删除业务文件”的典型文件安全链路。

任意文件类漏洞的本质是用户可控的文件名、路径、内容或文件元数据进入文件系统操作后，应用没有正确限制目录边界、文件类型、访问方式和业务权限。修复时不要只依赖字符串替换、黑名单或前端限制，应使用后端白名单、服务端生成文件名、路径标准化、真实路径校验、目录隔离、权限校验和审计日志。

已覆盖类型

| 分类     | 已有场景                                        | 结论                                                         |
| -------- | ----------------------------------------------- | ------------------------------------------------------------ |
| 文件上传 | 任意类型上传、图片后缀白名单、图片内容校验      | 覆盖上传入口、后缀校验误区和上传后访问方式的影响             |
| 文件读取 | 绝对路径/目录穿越读取、上传目录限制             | 覆盖敏感文件读取和安全目录边界校验                           |
| 文件下载 | 绝对路径/目录穿越下载、文件名校验、上传目录限制 | 覆盖附件下载类接口的常见风险                                 |
| 文件删除 | 任意路径删除、上传目录限制                      | 覆盖破坏性文件操作风险，并强调只使用临时文件测试             |
| 静态映射 | `/file/**` 映射到上传目录                       | 说明上传文件可被访问，需关注脚本解析、内容类型和独立域名隔离 |

### 文件上传测试

页面：`/file/upload`

| 场景                  | 请求/入口                   | 测试输入                             | 预期结果                                           |
| --------------------- | --------------------------- | ------------------------------------ | -------------------------------------------------- |
| 任意文件上传          | `POST /file/upload/vul`     | `test.jsp` 或任意扩展名文件          | 返回“上传文件成功”及 `/file/<文件名>` 访问路径     |
| 上传后访问            | `GET /file/<文件名>`        | 上一步返回文件名                     | 文件可被静态映射访问；Spring Boot 默认不会解析 JSP |
| 安全上传-图片         | `POST /file/upload/safe`    | 真实 `png/jpg/gif/jpeg/bmp/ico` 图片 | 后缀白名单和图片内容校验均通过，上传成功           |
| 安全上传-脚本拦截     | `POST /file/upload/safe`    | `jsp/php/html`                       | 返回“只能上传图片哦！”                             |
| 安全上传-伪造后缀拦截 | `POST /file/upload/safe`    | 内容不是图片的 `test.png`            | 返回“文件内容与图片类型不匹配！”                   |
| 流量包/示例Payload    | 页面右上角 Payload/流量分析 | `test.jsp`、`upload.pcapng`          | 可下载对应测试文件                                 |

### 文件读取测试

页面：`/file/read`

| 场景                  | 请求                                                | 测试输入           | 预期结果                                   |
| --------------------- | --------------------------------------------------- | ------------------ | ------------------------------------------ |
| 绝对路径读取          | `GET /file/read/vul?fileName=/etc/hosts`            | `/etc/hosts`       | 返回文件内容                               |
| 目录穿越读取          | `GET /file/read/vul?fileName=../../../../etc/hosts` | `../` payload      | 如果路径解析到真实文件，返回文件内容       |
| 安全读取-越权拦截     | `GET /file/read/safe?fileName=/etc/hosts`           | 绝对路径           | 返回“访问被拒绝：文件路径不合法”或不可访问 |
| 安全读取-目录内文件   | `GET /file/read/safe?fileName=<上传目录内文件名>`   | 上传目录内普通文件 | 返回文件内容                               |
| 安全读取-符号链接绕过 | `GET /file/read/safe?fileName=<指向外部的软链接>`   | 上传目录内软链接   | 返回“文件真实路径不合法”                   |

### 文件下载测试

页面：`/file/download`

| 场景                  | 请求                                                    | 测试输入           | 预期结果                         |
| --------------------- | ------------------------------------------------------- | ------------------ | -------------------------------- |
| 绝对路径下载          | `GET /file/download/vul?fileName=/etc/passwd`           | `/etc/passwd`      | 以附件形式返回文件，存在则可下载 |
| 目录穿越下载          | `GET /file/download/vul?fileName=../../../../etc/hosts` | `../` payload      | 如果路径解析到真实文件，返回附件 |
| 安全下载-非法文件名   | `GET /file/download/safe?fileName=/etc/hosts`           | 绝对路径           | 返回 400 或 404，不允许下载      |
| 安全下载-目录内文件   | `GET /file/download/safe?fileName=<上传目录内文件名>`   | 上传目录内普通文件 | 正常下载                         |
| 安全下载-符号链接绕过 | `GET /file/download/safe?fileName=<指向外部的软链接>`   | 上传目录内软链接   | 返回 403 “文件真实路径不合法”    |

### 文件删除测试

页面：`/file/delete`

| 场景                  | 请求                                                         | 测试输入           | 预期结果                         |
| --------------------- | ------------------------------------------------------------ | ------------------ | -------------------------------- |
| 任意路径删除          | `GET /file/delete/vul?filePath=./src/main/resources/static/upload/demo.txt` | 临时测试文件       | 文件存在时返回删除成功           |
| 目录穿越删除          | `GET /file/delete/vul?filePath=../../tmp/demo.txt`           | 仅限临时文件       | 如果路径存在且有权限，会尝试删除 |
| 安全删除-越权拦截     | `GET /file/delete/safe?fileName=../test`                     | `../` payload      | 返回“访问被拒绝：文件路径不合法” |
| 安全删除-目录内文件   | `GET /file/delete/safe?fileName=<上传目录内文件名>`          | 上传目录内普通文件 | 文件存在时删除成功               |
| 安全删除-符号链接绕过 | `GET /file/delete/safe?fileName=<指向外部的软链接>`          | 上传目录内软链接   | 返回“文件真实路径不合法”         |



## SSRF

当前覆盖任意协议请求、本地文件读取、内网HTTP访问、跳转链访问内网，以及协议、域名白名单、解析后IP校验和禁止自动跳转等常见修复点。

SSRF的本质是服务端把用户可控的URL、主机名或资源地址用于发起网络请求，且未限制协议、目标主机、解析后的IP和跳转链路。攻击者可借服务端网络身份访问内网服务、云元数据、管理端口、本地文件或第三方资源。修复优先使用业务枚举或服务端映射，不直接接受完整URL；必须限制协议、校验白名单域名、解析所有目标IP并拦截内网地址，且对30x跳转链路逐跳复检。

已覆盖类型

| 分类         | 已有场景                                            | 结论                                              |
| ------------ | --------------------------------------------------- | ------------------------------------------------- |
| 任意协议请求 | `URLConnection`直接请求用户输入URL                  | 覆盖SSRF最基础成因，可演示`file://`和`http://`    |
| 本地文件读取 | `file:///etc/hosts`、`file:///etc/passwd`           | 覆盖服务端本地文件被读取的影响                    |
| 内网HTTP访问 | `http://127.0.0.1/ssrf/internal/metadata`           | 覆盖内网服务/云元数据类风险，使用靶场内置模拟接口 |
| 跳转链风险   | `/ssrf/redirect?target=...`                         | 覆盖只校验第一跳但自动跟随跳转的常见绕过点        |
| 安全写法     | http(s)协议、域名白名单、解析后IP校验、禁用自动跳转 | 覆盖推荐修复主线                                  |

### SSRF漏洞场景测试

页面：`/ssrf`

| 场景           | 请求                                                         | 测试输入              | 预期结果                                             |
| -------------- | ------------------------------------------------------------ | --------------------- | ---------------------------------------------------- |
| 页面访问       | `GET /ssrf`                                                  | 无                    | 页面正常打开，展示漏洞场景、安全场景、tips和代码片段 |
| 本地文件读取   | `GET /ssrf/vul?url=file:///etc/hosts`                        | `file:///etc/hosts`   | 返回本机hosts文件内容                                |
| 内网HTTP访问   | `GET /ssrf/vul?url=http://127.0.0.1/ssrf/internal/metadata`  | 本机内网模拟元数据URL | 返回`instance-id`、`role`、`token`等模拟元数据       |
| 跳转链访问内网 | `GET /ssrf/vul?url=http://127.0.0.1/ssrf/redirect?target=http://127.0.0.1/ssrf/internal/metadata` | 第一跳为跳转接口      | 漏洞请求跟随跳转后返回模拟元数据                     |
| 协议探测       | `GET /ssrf/vul?url=dict://127.0.0.1:6379/info`               | 非HTTP协议            | 返回连接异常或协议处理结果，用于观察任意协议风险     |
| 流量包下载     | 页面流量分析按钮                                             | `ssrf.pcapng`         | 可下载对应流量包                                     |

### SSRF安全场景测试

页面：`/ssrf`

| 场景             | 请求                                                         | 测试输入        | 预期结果                                                     |
| ---------------- | ------------------------------------------------------------ | --------------- | ------------------------------------------------------------ |
| 非HTTP协议拦截   | `GET /ssrf/safe?url=file:///etc/hosts`                       | `file://`       | 返回“检测到不是http(s)协议！”                                |
| 内网地址拦截     | `GET /ssrf/safe?url=http://127.0.0.1/ssrf/internal/metadata` | 回环地址        | 返回“非白名单域名！”                                         |
| 用户信息混淆拦截 | `GET /ssrf/safe?url=http://baidu.com@127.0.0.1/ssrf/internal/metadata` | `userinfo@host` | 返回“非白名单域名！”                                         |
| 白名单域名       | `GET /ssrf/safe?url=http://baidu.com`                        | 白名单域名      | 通过协议和白名单校验，返回远端响应或网络访问结果             |
| 跳转链拦截       | `GET /ssrf/safe?url=http://127.0.0.1/ssrf/redirect?target=http://127.0.0.1/ssrf/internal/metadata` | 跳转到内网      | 第一跳目标不在白名单，直接返回“非白名单域名！”；安全代码同时禁用自动跳转 |
| 超时控制         | 访问慢速或不可达HTTP地址                                     | 慢速目标        | 连接/读取超时后返回异常信息，不长期阻塞请求线程              |


## XXE

当前覆盖XMLReader、SAXParser、DocumentBuilder三类常见Java XML解析入口，可演示外部实体读取本地文件、通过外部实体访问内网地址，以及禁用DOCTYPE/外部实体/外部DTD/外部Schema的修复方式。

XXE的本质是应用解析不可信XML时允许DTD或外部实体，攻击者可通过SYSTEM/PUBLIC外部实体让解析器读取本地文件、访问内网地址、触发SSRF，或利用实体膨胀造成拒绝服务。修复不应依赖某个解析器版本的默认行为，应在每个XML解析入口显式禁用DOCTYPE、外部通用实体、外部参数实体和外部DTD加载；DOM类解析器还应限制`ACCESS_EXTERNAL_DTD`和`ACCESS_EXTERNAL_SCHEMA`。

已覆盖类型

| 分类 | 已有场景 | 结论 |
| --- | --- | --- |
| SAX/XMLReader | `XMLReaderFactory.createXMLReader()` | 覆盖底层SAX解析入口的外部实体展开风险 |
| SAXParser | `SAXParserFactory.newInstance()` | 覆盖常见SAXParser封装场景，强调不要依赖默认安全行为 |
| DOM/DocumentBuilder | `DocumentBuilderFactory.newInstance()` | 覆盖业务中常见DOM解析、配置导入、XML文档读取场景 |
| 安全写法 | 禁用DOCTYPE、外部实体、外部DTD、外部Schema，配置空EntityResolver | 覆盖推荐修复主线 |
| 辅助检测 | 关键词黑名单检测`ENTITY`、`DOCTYPE` | 保留为辅助检测，不作为根因修复 |

### XXE漏洞场景测试

页面：`/xxe/vul`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /xxe/vul` | 无 | 页面正常打开，展示XMLReader、SAXParser、DocumentBuilder漏洞场景和代码片段 |
| XMLReader读取本地文件 | `GET /xxe/vul1?payload=<xml>` | `<!ENTITY xxe SYSTEM "file:///etc/hosts">` | 返回hosts文件内容，证明外部实体被展开 |
| XMLReader访问内网 | `GET /xxe/vul1?payload=<xml>` | `<!ENTITY xxe SYSTEM "http://127.0.0.1/ssrf/internal/metadata">` | 返回模拟元数据，证明可触发SSRF链路 |
| SAXParser读取本地文件 | `GET /xxe/vul2?payload=<xml>` | `<!ENTITY xxe SYSTEM "file:///etc/hosts">` | 返回hosts文件内容或解析器外部实体展开结果 |
| DocumentBuilder读取本地文件 | `GET /xxe/vul3?payload=<xml>` | `<!ENTITY xxe SYSTEM "file:///etc/hosts">` | 返回hosts文件内容，证明DOM解析器同样受影响 |
| DocumentBuilder访问内网 | `GET /xxe/vul3?payload=<xml>` | `<!ENTITY xxe SYSTEM "http://127.0.0.1/ssrf/internal/metadata">` | 返回模拟元数据 |
| 审计SINK点 | 页面tips | XMLReader、SAXParser、DocumentBuilder、XMLStreamReader等 | 页面列出常见XML解析入口，便于代码审计 |

### XXE安全场景测试

页面：`/xxe/safe`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /xxe/safe` | 无 | 页面正常打开，展示安全配置和辅助检测场景 |
| XMLReader安全配置 | `GET /xxe/safe1?payload=<xml>` | 带DOCTYPE和外部实体的payload | 返回DOCTYPE被禁止或外部实体无法展开的错误信息，不泄露文件内容 |
| XMLReader正常XML | `GET /xxe/safe1?payload=<root>hello</root>` | 不含DTD的普通XML | 返回`hello` |
| DocumentBuilder安全配置 | `GET /xxe/safe3?payload=<xml>` | 带DOCTYPE和外部实体的payload | 返回DOCTYPE被禁止或外部实体无法展开的错误信息，不泄露文件内容 |
| DocumentBuilder正常XML | `GET /xxe/safe3?payload=<root>hello</root>` | 不含DTD的普通XML | 返回`hello` |
| 黑名单辅助拦截 | `GET /xxe/safe2?payload=<xml>` | 带`DOCTYPE`或`ENTITY`关键字 | 返回`[+]检测到恶意XML！` |
| 黑名单正常XML | `GET /xxe/safe2?payload=<root>hello</root>` | 普通XML | 返回`[-]XML内容安全` |

## 跨源安全

当前覆盖 CORS 配置错误、CORS 白名单修复、JSONP 敏感数据泄露、JSONP callback 校验与公开数据返回四类场景。模块重点演示同源策略约束的是浏览器脚本“读取跨源响应”的能力，服务端一旦错误放宽 CORS 或继续用 JSONP 承载敏感数据，就可能把登录态接口的数据暴露给攻击者站点。

跨源安全问题的本质是跨站点读取边界配置不当。CORS 不是认证或鉴权机制，`Access-Control-Allow-Origin` 只是在告诉浏览器哪些来源可以读取响应；允许凭证时必须精确返回可信 Origin，不能反射任意 Origin 或使用通配策略。JSONP 依赖 `script` 标签跨源加载执行，不适合返回用户身份、权限、订单、Token 等敏感数据；必须保留时只能服务公开只读数据，并严格校验 callback。

已覆盖类型

| 分类          | 已有场景                                                     | 结论                                              |
| ------------- | ------------------------------------------------------------ | ------------------------------------------------- |
| CORS漏洞      | `GET /crossorigin/corsVul` 反射请求 `Origin`，并允许凭证     | 覆盖 CORS 敏感数据跨源读取的典型错误配置          |
| CORS安全写法  | `GET /crossorigin/corsSafe` 精确匹配可信 Origin、限制方法和请求头 | 覆盖白名单、凭证、`Vary: Origin` 等关键修复点     |
| JSONP漏洞     | `GET /crossorigin/jsonpVul?callback=stealData` 返回敏感数据  | 覆盖 JSONP 被任意站点通过 `script` 标签读取的问题 |
| JSONP安全写法 | `GET /crossorigin/jsonpSafe?callback=stealData` 校验 callback，只返回公开数据 | 覆盖 JSONP 保留场景下的最低安全要求               |

### CORS漏洞场景测试

页面：`/crossorigin/cors`

| 场景             | 请求                           | 测试输入                             | 预期结果                                                     |
| ---------------- | ------------------------------ | ------------------------------------ | ------------------------------------------------------------ |
| 页面访问         | `GET /crossorigin/cors`        | 已登录会话                           | 页面正常打开，展示 CORS 漏洞、白名单修复和代码片段           |
| 无Origin直接访问 | `GET /crossorigin/corsVul`     | 不带 `Origin`                        | 返回敏感演示数据，响应带默认 `Access-Control-Allow-Origin: http://example.com` |
| 反射任意Origin   | `GET /crossorigin/corsVul`     | Header `Origin: http://evil.example` | 返回敏感演示数据，响应 `Access-Control-Allow-Origin` 反射为恶意来源 |
| 允许凭证         | `GET /crossorigin/corsVul`     | Header `Origin: http://evil.example` | 响应包含 `Access-Control-Allow-Credentials: true`，说明跨源脚本可在带凭证时读取响应 |
| 预检请求         | `OPTIONS /crossorigin/corsVul` | Header `Origin: http://evil.example` | 返回允许方法和请求头，用于演示过宽预检策略                   |

### CORS安全场景测试

页面：`/crossorigin/cors`

| 场景           | 请求                            | 测试输入                               | 预期结果                                                     |
| -------------- | ------------------------------- | -------------------------------------- | ------------------------------------------------------------ |
| 同源访问       | `GET /crossorigin/corsSafe`     | 不带 `Origin`                          | 返回“同源请求不需要CORS响应头”，不暴露跨源读取策略           |
| 非白名单Origin | `GET /crossorigin/corsSafe`     | Header `Origin: http://evil.example`   | 返回 403 或被 CORS 过滤器拒绝，不返回可信 `Access-Control-Allow-Origin` |
| 白名单Origin   | `GET /crossorigin/corsSafe`     | Header `Origin: http://127.0.0.1:8080` | 返回成功，响应 `Access-Control-Allow-Origin: http://127.0.0.1:8080` |
| 白名单预检     | `OPTIONS /crossorigin/corsSafe` | Header `Origin: http://127.0.0.1:8080` | 返回允许 `GET, OPTIONS` 和必要请求头                         |

### JSONP漏洞场景测试

页面：`/crossorigin/jsonp`

| 场景           | 请求                                           | 测试输入             | 预期结果                                                     |
| -------------- | ---------------------------------------------- | -------------------- | ------------------------------------------------------------ |
| 页面访问       | `GET /crossorigin/jsonp`                       | 已登录会话           | 页面正常打开，展示 JSONP 劫持、安全写法和代码片段            |
| JSONP敏感数据  | `GET /crossorigin/jsonpVul?callback=stealData` | `callback=stealData` | 返回 `stealData({"username":"admin","password":"Admin123"});` |
| callback未校验 | `GET /crossorigin/jsonpVul?callback=alert`     | `callback=alert`     | 返回可执行脚本格式，说明任意回调名可控                       |

### JSONP安全场景测试

页面：`/crossorigin/jsonp`

| 场景             | 请求                                                | 测试输入           | 预期结果                                                     |
| ---------------- | --------------------------------------------------- | ------------------ | ------------------------------------------------------------ |
| 合法callback     | `GET /crossorigin/jsonpSafe?callback=stealData`     | 合法函数名         | 返回 `stealData({"message":"public data only"});`，不包含敏感账号密码 |
| 命名空间callback | `GET /crossorigin/jsonpSafe?callback=app.stealData` | 合法命名空间函数名 | 返回 `app.stealData({"message":"public data only"});`        |
| 非法callback     | `GET /crossorigin/jsonpSafe?callback=alert(1)`      | 含括号的非法函数名 | 返回 400 和 `Invalid callback`                               |
| 响应头           | `GET /crossorigin/jsonpSafe?callback=stealData`     | 合法函数名         | 响应 `Content-Type` 为 JavaScript，并带 `X-Content-Type-Options: nosniff` |

## RCE

当前覆盖命令注入和代码注入两条主线：命令注入包含 `ProcessBuilder`、`Runtime.getRuntime().exec()`、反射调用 `ProcessImpl` 三类入口；代码注入包含 `GroovyShell.evaluate` 和受控动作分发修复方式。

RCE的本质是不可信输入进入服务端“可执行上下文”。命令注入通常发生在用户输入进入系统命令、shell 语法或命令参数；代码注入通常发生在用户输入进入脚本引擎、表达式引擎、模板引擎、动态编译或插件执行逻辑。修复优先移除动态执行能力，改用业务 API 或服务端固定动作映射；必须调用系统命令时，不拼接字符串，不进入 `sh -c` 或 `cmd.exe /c`，只允许固定命令和固定参数，并加超时、最小权限、输出限制和审计。

已覆盖类型

| 分类 | 已有场景 | 结论 |
| --- | --- | --- |
| ProcessBuilder命令注入 | `GET /command/vul1` 使用 `sh -c` 执行用户输入 | 覆盖 shell 元字符拼接、管道、重定向等高危命令注入成因 |
| Runtime命令执行 | `GET /command/vul2` 直接执行用户输入 | 覆盖 Java 常见命令执行 Sink，说明即使不经过 shell 也危险 |
| ProcessImpl反射 | `GET /command/vul3` 反射调用 JDK 内部进程启动入口 | 覆盖只审计 `Runtime.exec` 不足的问题；新版 JDK 可能因模块限制拦截 |
| 命令执行安全写法 | `GET /command/safe` 使用动作白名单映射固定命令 | 覆盖服务端固定动作、固定参数、超时和输出读取顺序 |
| Groovy代码注入 | `GET /code/vulGroovy` 执行 `GroovyShell.evaluate(payload)` | 覆盖脚本引擎代码执行风险 |
| 代码执行安全写法 | `GET /code/safeGroovy` 使用受控动作分发 | 覆盖把“任意脚本”改造成“有限业务动作”的修复思路 |

### 命令注入场景测试

页面：`/command`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /command` | 已登录会话 | 页面正常打开，展示 ProcessBuilder、Runtime、ProcessImpl、白名单安全场景和代码片段 |
| ProcessBuilder基础命令 | `GET /command/vul1?payload=whoami` | `whoami` | 返回当前运行用户 |
| ProcessBuilder shell拼接 | `GET /command/vul1?payload=echo rce; whoami` | shell 元字符 `;` | 返回 `echo` 输出和当前用户，说明 `sh -c` 解释了拼接命令 |
| Runtime基础命令 | `GET /command/vul2?payload=whoami` | `whoami` | 返回当前运行用户 |
| Runtime非shell语义 | `GET /command/vul2?payload=echo rce` | 程序与参数 | 返回 `rce`，但 `;`、`&&` 等不会像 shell 一样被解释 |
| ProcessImpl反射 | `GET /command/vul3?payload=whoami` | `whoami` | 低版本或开放模块时返回当前用户；新版 JDK 可能返回模块访问限制错误 |
| 流量包下载 | 页面“流量分析”链接 | `command_injection.pcapng` | 可下载命令注入流量包 |

### 命令执行安全场景测试

页面：`/command`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 白名单动作-list | `GET /command/safe?payload=list` | `list` | 执行服务端固定 `ls` 动作并返回目录输出 |
| 白名单动作-date | `GET /command/safe?payload=date` | `date` | 执行服务端固定 `date` 动作并返回当前时间 |
| 非法命令拦截 | `GET /command/safe?payload=whoami;id` | 拼接命令 | 返回“不允许执行该动作！” |
| 任意命令拦截 | `GET /command/safe?payload=whoami` | 未配置动作 | 返回“不允许执行该动作！” |
| 超时保护 | 安全代码审计 | 长时间命令不在白名单内 | 用户无法触发任意长时间命令；固定命令执行也设置等待超时 |

### Groovy代码注入场景测试

页面：`/code`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /code` | 已登录会话 | 页面正常打开，展示 Groovy 代码注入、安全动作分发和代码片段 |
| 表达式执行 | `GET /code/vulGroovy?payload=1%2B2%2B3` | `1+2+3` | 返回 `6`，证明输入被当作 Groovy 代码执行 |
| 命令执行 | `GET /code/vulGroovy?payload='whoami'.execute()` | Groovy `execute()` | 返回当前运行用户或进程输出 |
| 非预期Java能力 | `GET /code/vulGroovy?payload=System.getProperty('user.dir')` | Java API 调用 | 返回服务端工作目录，说明代码执行不只等于命令执行 |
| 流量包下载 | 页面“流量分析”链接 | `code_injection.pcapng` | 可下载代码注入流量包 |

### Groovy安全场景测试

页面：`/code`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 受控动作-hello | `GET /code/safeGroovy?payload=hello` | `hello` | 返回 `Hello JavaSecLab` |
| 受控动作-time | `GET /code/safeGroovy?payload=time` | `time` | 返回服务端当前时间 |
| 受控动作-sum | `GET /code/safeGroovy?payload=sum` | `sum` | 返回 `6` |
| 非法脚本拦截 | `GET /code/safeGroovy?payload='whoami'.execute()` | Groovy 命令执行脚本 | 返回“非法的动作输入！” |

## 逻辑漏洞

当前覆盖越权访问、验证码安全、支付业务逻辑和并发安全四条主线：越权包含水平越权和垂直越权；验证码包含图形验证码复用、万能验证码、弱图形验证码、短信验证码回显和参数绕过；支付包含金额篡改、订单重放、流程绕过、整数溢出和浮点数精度问题；并发安全包含竞态条件和幂等校验。

逻辑漏洞的本质不是单个危险 API，而是业务状态、权限边界、校验顺序或信任来源设计错误。修复时不能只依赖前端限制、隐藏菜单、不可见参数或客户端价格，应在服务端围绕“当前用户是谁、允许做什么、资源属于谁、订单处于什么状态、关键参数是否可信”建立统一校验，并配合幂等、事务、锁、审计和风控。

已覆盖类型

| 分类 | 已有场景 | 结论 |
| --- | --- | --- |
| 水平越权 | `GET /logic/idor/horizontal/getUserInfo` 根据请求参数查用户 | 覆盖同权限用户之间通过对象标识访问他人资源 |
| 垂直越权 | `GET /logic/idor/vertical/vul` 低权限用户直接访问管理员页面 | 覆盖管理员功能缺少服务端角色校验 |
| 图形验证码 | 复用验证码、万能验证码、弱验证码识别、安全验证码 | 覆盖验证码生命周期、固定后门和识别难度问题 |
| 短信验证码 | 验证码回显、`code_verify=true` 参数绕过 | 覆盖响应泄露和信任客户端校验结果 |
| 支付逻辑 | 金额篡改、订单重放、流程绕过、整数溢出、浮点精度 | 覆盖交易链路常见高风险业务逻辑问题 |
| 并发安全 | 竞态条件重复支付、同步锁和幂等校验 | 覆盖共享资源并发读写导致的重复扣款和状态竞争 |

### 越权漏洞测试

页面：`/logic/idor/horizontal`、`/logic/idor/vertical`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 水平越权页面 | `GET /logic/idor/horizontal` | 已登录会话 | 页面正常打开，展示水平越权漏洞和 Session 校验安全场景 |
| 水平越权漏洞 | `GET /logic/idor/horizontal/getUserInfo?username=123` | 任意存在用户 | 返回指定用户信息，说明只信任请求参数 |
| 水平越权安全拦截 | `GET /logic/idor/horizontal/safe?username=admin` | 与当前登录用户不同 | 返回“您没有权限查看该用户的资料” |
| 水平越权安全放行 | `GET /logic/idor/horizontal/safe?username=<当前登录用户>` | 当前登录用户 | 返回当前用户信息 |
| 垂直越权页面 | `GET /logic/idor/vertical` | 已登录会话 | 页面正常打开，展示垂直越权场景 |
| 垂直越权漏洞 | `GET /logic/idor/vertical/vul` | 普通登录用户 | 可访问管理员页面，说明缺少服务端角色校验 |
| 垂直越权安全校验 | `GET /logic/idor/vertical/safe` | 普通登录用户 | 返回无管理员权限；管理员用户返回校验通过 |

### 图形验证码测试

页面：`/logic/captcha/graphic`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /logic/captcha/graphic` | 已登录会话 | 页面正常打开，展示验证码失效、万能验证码、可识别和安全场景 |
| 获取漏洞验证码 | `GET /logic/captcha/graphic/img` | 同一会话 | 返回图片验证码，并在 Session 中保存 4 位验证码 |
| 验证码复用 | `POST /logic/captcha/graphic/vul1` | 正确验证码重复提交 | 5分钟内验证码不会在成功后清除，可被重复使用 |
| 万能验证码 | `POST /logic/captcha/graphic/vul2` | `username=admin&password=admin123&captcha=6666` | 无需真实图片验证码即可通过 |
| 弱验证码识别 | `POST /logic/captcha/graphic/vul3` | OCR或人工识别出的4位验证码 | 正确验证码可通过，说明弱验证码容易被识别或爆破 |
| 获取安全验证码 | `GET /logic/captcha/graphic/safeImg` | 同一会话 | 返回 6 位验证码，并设置较短有效期 |
| 安全验证码错误 | `POST /logic/captcha/graphic/safe` | 错误验证码 | 返回“验证码错误，请重新输入！”，并清除验证码 |

### 短信验证码测试

页面：`/logic/captcha/sms`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /logic/captcha/sms` | 已登录会话 | 页面正常打开，展示验证码回显和验证码绕过场景 |
| 手机号格式校验 | `GET /logic/captcha/sms/code?phone=abc` | 非法手机号 | 返回“手机号格式不正确！” |
| 验证码回显 | `GET /logic/captcha/sms/code?phone=18888888888` | 合法手机号 | 响应中直接包含短信验证码 |
| 回显验证码验证 | `POST /logic/captcha/sms/vul1` | 使用响应中的验证码 | 返回验证通过 |
| 验证码绕过准备 | `GET /logic/captcha/sms/code2?phone=18888888888` | 合法手机号 | 响应不回显验证码，但 Session 中保存验证码 |
| 参数绕过 | `POST /logic/captcha/sms/vul2?phone=18888888888&code=000000&code_verify=true` | 任意错误验证码 | 返回验证通过，说明信任客户端控制参数 |

### 支付逻辑测试

页面：`/logic/pay`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /logic/pay` | 已登录会话 | 页面正常打开，展示6类支付逻辑漏洞和余额重置按钮 |
| 重置余额 | `POST /logic/pay/resetBalance` | 无 | 返回余额已重置为1000元 |
| 金额参数篡改 | `POST /logic/pay/vul1` | `count=1&price=0.01` | 使用客户端价格支付成功，说明未校验服务端真实价格 |
| 订单重放 | `POST /logic/pay/vul2` | 同一 `orderId` 重复支付 | 每次请求都会扣款，说明缺少幂等和支付状态校验 |
| 并发竞态 | 并发 `POST /logic/pay/vul3` | 相同 `orderId` 和金额并发请求 | 可能出现重复扣款或余额计算不一致 |
| 创建订单 | `POST /logic/pay/vul4/create` | `orderId=bypass123&amount=200` | 返回订单创建成功，状态未支付 |
| 流程绕过 | `POST /logic/pay/vul4/notify` | `orderId=bypass123&success=true` | 未真实支付即可把订单改为已支付 |
| 整数溢出 | `POST /logic/pay/vul5` | `count=2147483647&price=10` | `int` 乘法溢出，可能产生负金额并导致余额异常 |
| 浮点精度 | `POST /logic/pay/vul6` | `count=0.1&price=0.2` | 返回实际扣款金额中可见二进制浮点误差 |

### 并发安全测试

页面：`/logic/concurrent`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /logic/concurrent` | 已登录会话 | 页面正常打开，展示竞态条件漏洞和同步锁/幂等安全场景 |
| 重置测试数据 | `POST /logic/concurrent/reset` | 无 | 返回余额重置为1000元，订单状态清空 |
| 竞态条件重复支付 | 并发 `POST /logic/concurrent/vul` | `orderId=race123&amount=100` | 多个相同订单请求可能同时成功，出现重复扣款或余额结果不一致 |
| 安全幂等校验 | 并发 `POST /logic/concurrent/safe` | `orderId=safeRace123&amount=100` | 首个请求扣款成功，后续相同订单返回“订单已支付，拒绝重复扣款” |

## 其他漏洞

当前覆盖 URL 重定向、XFF 伪造、DoS 资源消耗和 XPath 注入四类容易散落在业务边缘的漏洞。该模块适合作为综合靶场的补充模块：不再围绕单一技术栈展开，而是展示真实项目中常被低估的输入信任、跳转控制、代理头信任、资源上限和表达式拼接问题。

这几类漏洞的修复核心分别是：URL 跳转必须使用服务端映射或严格白名单，不允许任意外部 URL 直接进入 `Location`；XFF 只能在请求来自可信代理时解析，不能直接信任客户端头；DoS 类功能要设置尺寸、大小、数量、深度、超时和并发上限；XPath 查询应使用变量绑定或业务层精确匹配，不能拼接表达式。

已覆盖类型

| 分类 | 已有场景 | 结论 |
| --- | --- | --- |
| URL 重定向 | Spring MVC、ModelAndView、Servlet、ResponseEntity、响应头重定向 | 覆盖 Java Web 常见跳转 Sink 和白名单修复思路 |
| XFF 伪造 | 直接信任 `X-Forwarded-For` 作为客户端 IP | 覆盖基于伪造请求头绕过 IP 控制和日志污染 |
| DoS 资源消耗 | 图片宽高参数可控、ZIP 递归解压 | 覆盖高成本图片生成和压缩包资源放大 |
| XPath 注入 | 用户名密码拼接进 XPath 表达式 | 覆盖 XML 查询场景中的认证绕过 |

### URL 重定向测试

页面：`/other/URLRedirect/vul`、`/other/URLRedirect/safe`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 漏洞页面 | `GET /other/URLRedirect/vul` | 已登录会话 | 页面正常打开，展示 6 种重定向写法 |
| Spring redirect | `GET /other/URLRedirect/vul1?url=http://example.com` | 外部 URL | 返回 302，`Location` 指向外部 URL |
| ModelAndView redirect | `GET /other/URLRedirect/vul2?url=http://example.com` | 外部 URL | 返回 302，`Location` 指向外部 URL |
| Servlet setHeader | `GET /other/URLRedirect/vul3?url=http://example.com` | 外部 URL | 返回 301，`Location` 指向外部 URL |
| Servlet sendRedirect | `GET /other/URLRedirect/vul4?url=http://example.com` | 外部 URL | 返回 302，`Location` 指向外部 URL |
| ResponseEntity redirect | `GET /other/URLRedirect/vul5?url=http://example.com` | 外部 URL | 返回 302，`Location` 指向外部 URL |
| ResponseStatus redirect | `GET /other/URLRedirect/vul6?url=http://example.com` | 外部 URL | 返回 302，`Location` 指向外部 URL |
| 安全页面 | `GET /other/URLRedirect/safe` | 已登录会话 | 页面正常打开，展示内部转发和白名单校验 |
| 白名单拦截 | `GET /other/URLRedirect/safe2?url=http://example.com` | 非白名单 URL | 返回 403 和 `Forbidden: url not in WhiteUrlList!` |
| 白名单放行 | `GET /other/URLRedirect/safe2?url=https://blog.csdn.net/weixin_53009585` | 白名单域名 | 返回 302，允许跳转 |

### XFF 伪造测试

页面：`/other/xff`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /other/xff` | 已登录会话 | 页面正常打开，展示 XFF 漏洞和可信代理安全场景 |
| 原始 IP 访问 | `GET /other/xff/vul1` | 无 XFF 头 | 返回页面展示真实连接来源，不泄露仅限 8.8.8.8 的敏感信息 |
| XFF 伪造漏洞 | `GET /other/xff/vul2?xff=true` | Header `X-Forwarded-For: 8.8.8.8` | 返回敏感信息，说明直接信任客户端头 |
| 不启用 XFF | `GET /other/xff/vul2?xff=false` | Header `X-Forwarded-For: 8.8.8.8` | 使用真实连接来源，不返回敏感信息 |
| 安全拦截 | `GET /other/xff/safe?xff=true` | 本地直连并伪造 XFF | 返回“非可信代理来源，忽略XFF头”，不泄露敏感信息 |

### DoS 测试

页面：`/other/dos`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /other/dos` | 已登录会话 | 页面正常打开，展示图片资源消耗、图片尺寸限制和 ZIP 解压场景 |
| 图片参数可控 | `GET /other/dos/vul?width=1200&height=1200` | 较大宽高 | 返回图片，说明服务端按用户输入分配资源 |
| 图片尺寸拦截 | `GET /other/dos/safe?width=1200&height=1200` | 超过上限宽高 | 返回 400 和“图片尺寸超出限制” |
| 图片正常生成 | `GET /other/dos/safe?width=300&height=120` | 合理宽高 | 返回 JPEG 图片 |
| ZIP 上传空文件 | `POST /other/dos/vul2` | 不传文件 | 返回“请先选择ZIP文件” |
| ZIP 解压资源消耗 | `POST /other/dos/vul2` | ZIP 文件 | 服务端尝试解压，递归 ZIP 或大量文件可造成资源压力 |

### XPath 注入测试

页面：`/other/xpath`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /other/xpath` | 已登录会话 | 页面正常打开，展示 XPath 注入和变量绑定安全场景 |
| 正常认证 | `GET /other/xpath/vul?username=admin&password=password` | 正确账号密码 | 返回认证通过 |
| 万能条件绕过 | `GET /other/xpath/vul?username=admin&password=' or '1'='1` | XPath 注入 Payload | 返回认证通过，说明表达式被拼接篡改 |
| 安全拦截 | `POST /other/xpath/safe` | `username=admin&password=' or '1'='1` | 返回认证失败 |
| 安全正常认证 | `POST /other/xpath/safe` | `username=admin&password=password` | 返回认证通过 |

## 敏感信息泄漏

当前覆盖 JS 前端泄漏、目录遍历、测试页面遗留和备份文件泄漏四类场景。该模块的重点不是单个危险 API，而是“本不该暴露给用户的内容被放在了可访问位置”：前端代码、构建产物、目录列表、测试工具、源码包、日志和临时文件都可能成为攻击入口。

敏感信息泄漏的修复核心是最小暴露面和发布前检查：密钥、认证逻辑、内部接口和敏感配置不能进入前端；静态目录不放备份、日志和测试文件；目录列表和测试页面默认关闭；必须保留的诊断入口应做强鉴权、白名单、审计、超时和输入限制；已泄漏的密钥、Token、Cookie 和密码应立即轮换。

已覆盖类型

| 分类 | 已有场景 | 结论 |
| --- | --- | --- |
| JS 泄漏 | 前端硬编码账号密码、Webpack 打包泄漏云密钥 | 覆盖前端源码和构建产物中的常见敏感信息泄漏 |
| 目录遍历 | 目录列表可控、黑名单过滤、根目录限制 | 覆盖目录浏览功能导致的文件名、路径和资源发现风险 |
| 测试页面 | 遗留 Ping 页面、命令拼接、安全 Ping 对照 | 覆盖测试入口暴露和输入处理不当导致的高风险链路 |
| 备份文件 | Web 源码压缩包、日志文件 | 覆盖源码、配置、SQL、Session 和调试日志泄漏 |

### JS 泄漏测试

页面：`/infoLeak/js`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /infoLeak/js` | 已登录会话 | 页面正常打开，展示前端硬编码和 Webpack 泄漏场景 |
| 前端硬编码页面 | `GET /infoLeak/js/hard-coding` | 无 | 返回登录页面，页面源码中可见硬编码账号密码 |
| 前端硬编码登录 | 浏览器提交 `/infoLeak/js/hard-coding` | `superadmin` / `Admin@1024.com` | 跳转到 `/infoLeak/js/loginSuccess` |
| Webpack 泄漏 JS | `GET /other/infoleak/chunk-0226s3f2.57e3ed6f.js` | 无 | 返回 JS 文件，内容包含 `SecretId`、`SecretKey`、Bucket 等敏感配置 |

### 目录遍历测试

页面：`/infoLeak/dirTraversal`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /infoLeak/dirTraversal` | 已登录会话 | 页面正常打开，展示目录遍历漏洞和两种安全写法 |
| 目录列表漏洞 | `GET /infoLeak/dirTraversal/vul?dir=/` | 根目录参数 | 返回静态目录列表 |
| 目录穿越尝试 | `GET /infoLeak/dirTraversal/vul?dir=../` | `../` | 可能列出静态目录之外的上级目录内容 |
| 黑名单拦截 | `GET /infoLeak/dirTraversal/safe1?dir=../` | `../` | 返回“非法字符！” |
| 根目录限制 | `GET /infoLeak/dirTraversal/safe2?dir=../` | `../` | 返回 `Directory not found or access denied.` |
| 安全目录访问 | `GET /infoLeak/dirTraversal/safe2?dir=/` | 根目录参数 | 只返回允许根目录内的文件列表 |

### 测试页面测试

页面：`/infoLeak/ceShiPage`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /infoLeak/ceShiPage` | 已登录会话 | 页面正常打开，展示遗留 Ping 测试入口 |
| Ping 页面 | `GET /infoLeak/ceShiPage/pingPage` | 无 | 页面正常打开，展示漏洞 Ping 和安全 Ping 表单 |
| 命令拼接漏洞 | `GET /infoLeak/ceShiPage/ping?ip=127.0.0.1%20%26%20whoami` | `127.0.0.1 & whoami` | 返回 ping 输出并可能追加当前进程用户，说明 shell 元字符生效 |
| 安全 Ping 拦截 | `GET /infoLeak/ceShiPage/safePing?ip=127.0.0.1%20%26%20whoami` | 含 `&` 的输入 | 返回“非法目标地址” |
| 安全 Ping 正常 | `GET /infoLeak/ceShiPage/safePing?ip=127.0.0.1` | 合法地址 | 返回 ping 输出或系统 ping 执行结果 |

### 备份文件测试

页面：`/infoLeak/backUp`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /infoLeak/backUp` | 已登录会话 | 页面正常打开，展示源码备份和日志泄漏场景 |
| 源码备份下载 | `GET /other/infoleak/www.zip` | 无 | 返回可下载压缩包，说明源码备份文件暴露在 Web 目录 |
| 日志文件访问 | `GET /other/infoleak/JavaSecLab_logs.txt` | 无 | 返回日志内容，包含 SQL、账号、SessionId、验证码等敏感信息 |

## 登录对抗

当前覆盖账号安全、登录绕过、JS逆向和凭证安全四条主线：账号安全包含用户名枚举和弱口令；登录绕过包含修改响应包绕过和密码重置步骤绕过；JS逆向包含客户端签名复现和前端RSA加密绕过；凭证安全包含JWT声明伪造。

登录对抗类问题的本质是认证流程把关键信任放在了错误的位置：错误提示暴露账号状态、口令强度不足、客户端响应或步骤状态被服务端信任、前端算法和密钥可被逆向、令牌声明被过度信任。修复时应统一认证失败提示，实施强密码和限速策略，所有认证状态、流程状态、权限判断都在服务端完成，并配合MFA、风控、审计、短有效期令牌和密钥轮换。

已覆盖类型

| 分类 | 已有场景 | 结论 |
| --- | --- | --- |
| 账号安全 | 用户名枚举、弱口令 | 覆盖认证入口最常见的信息泄露和低强度口令风险 |
| 登录绕过 | 修改响应包绕过、密码重置步骤绕过 | 覆盖客户端状态可信和多步骤流程缺少服务端前置校验的问题 |
| JS逆向 | sign请求签名绕过、RSA前端加密绕过 | 覆盖前端算法、固定密钥、公钥加密不能作为安全边界的典型误区 |
| 凭证安全 | JWT声明伪造 | 覆盖静态密钥和过度信任令牌role声明导致的权限冒用风险 |

### 账号安全测试

页面：`/loginconfront/account`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /loginconfront/account` | 已登录会话 | 页面正常打开，展示用户名枚举和弱口令场景 |
| 用户名不存在 | `POST /loginconfront/account/vul1` | `username=qwer&password=x` | 返回“用户不存在！”，可据此判断账号不存在 |
| 用户名存在但密码错误 | `POST /loginconfront/account/vul1` | `username=admin&password=wrong` | 返回“密码错误，请重试！”，可据此判断账号存在 |
| 用户名枚举登录成功 | `POST /loginconfront/account/vul1` | `username=admin&password=admin123` | 返回登录成功 |
| 弱口令命中 | `POST /loginconfront/account/vul2` | `username=admin&password=admin` | 返回登录成功，说明默认/弱口令可直接突破认证 |
| 弱口令失败提示 | `POST /loginconfront/account/vul2` | `username=admin&password=wrong` | 返回统一的“账号或密码错误！” |

### 登录绕过测试

页面：`/loginconfront/bypass`、`/loginconfront/bypass/reset`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /loginconfront/bypass` | 已登录会话 | 页面正常打开，展示修改响应包绕过和密码重置步骤绕过场景 |
| 第一阶段校验失败 | `POST /loginconfront/bypass/vul1step1` | `username=admin&password=wrong` | 返回“账号校验失败，请重试！” |
| 第一阶段校验成功 | `POST /loginconfront/bypass/vul1step1` | `username=admin&password=admin123` | 返回“账号校验通过，请稍等！” |
| 修改响应包绕过点 | `POST /loginconfront/bypass/vul1step2` | `code=0` | 返回“登录成功，欢迎！”，说明第二步只信任客户端传来的成功状态 |
| 密码重置页面 | `GET /loginconfront/bypass/reset` | 已登录会话 | 页面正常打开，展示三步密码重置流程 |
| 用户名步骤 | `POST /loginconfront/bypass/step1` | `username=admin` | 返回“用户名验证成功！” |
| 旧密码错误 | `POST /loginconfront/bypass/step2` | `oldPassword=bad` | 返回“旧密码错误！” |
| 正常旧密码校验 | `POST /loginconfront/bypass/step2` | `oldPassword=!@#qwf@3123` | 返回“密码验证成功！” |
| 跳过前置步骤重置 | `POST /loginconfront/bypass/step3` | `newPassword=newpass123` | 即使未完成旧密码校验也返回“密码重置成功！”，说明后端缺少步骤状态强校验 |

### JS逆向测试

页面：`/loginconfront/reverse`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /loginconfront/reverse` | 已登录会话 | 页面正常打开，展示sign请求签名绕过和RSA前端加密绕过场景 |
| sign缺失/错误 | `POST /loginconfront/reverse/vul1` | 错误 `sign` | 返回“签名验证失败” |
| sign复现成功 | `POST /loginconfront/reverse/vul1` | 使用前端固定密钥和参数拼接规则生成MD5签名 | 返回“登录成功！用户名：admin,密码：admin123” |
| sign与参数不匹配 | `POST /loginconfront/reverse/vul1` | 修改密码但复用旧签名 | 返回“签名验证失败” |
| RSA密文错误 | `POST /loginconfront/reverse/vul2` | 非法密文或错误字段 | 返回“解密失败！” |
| RSA前端加密复现 | `POST /loginconfront/reverse/vul2` | 使用页面公钥加密 `admin/admin123` | 返回登录成功，说明公钥加密不能证明请求来自可信前端 |

### 凭证安全测试

页面：`/loginconfront/credential`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /loginconfront/credential` | 已登录会话 | 页面正常打开，展示JWT声明伪造场景 |
| 生成JWT | `GET /loginconfront/credential/generate-jwt?username=admin&role=admin` | 任意用户名和角色声明 | 返回签名后的JWT |
| 缺少JWT | `GET /loginconfront/credential/vul1` | 不带 `Auth_Token` Header | 返回“缺少Auth_Token请求头” |
| 非法JWT | `GET /loginconfront/credential/vul1` | Header `Auth_Token: bad.jwt.token` | 返回JWT解析失败 |
| JWT解析成功 | `GET /loginconfront/credential/vul1` | Header携带生成的JWT | 返回 `user：admin,role：admin`，说明服务端信任令牌中的权限声明 |

# Java 专题

## SpringBoot 框架相关漏洞

当前覆盖 Swagger/OpenAPI 文档暴露、Spring Boot Actuator 敏感端点暴露、Druid 监控台暴露、MySQL JDBC 反序列化四类 Spring Boot 生态常见风险。模块重点不是 Spring Boot 框架自身漏洞，而是框架生态中“开发/运维辅助能力被带到生产环境”后的暴露面。

Spring Boot 相关漏洞的本质通常是配置边界和运行时暴露面管理不当：接口文档、管理端点、监控台、数据源连接和驱动参数本应用于开发、运维或内部系统，却被公网或普通用户访问。修复时应遵循生产环境最小暴露原则，关闭不必要组件，对管理入口加鉴权和内网限制，敏感端点脱敏，禁止用户控制 JDBC URL，并避免 Java 原生反序列化不可信数据。

已覆盖类型

| 分类 | 已有场景 | 结论 |
| --- | --- | --- |
| Swagger/OpenAPI | `/v3/api-docs`、Swagger UI | 覆盖接口文档未鉴权导致接口、参数和模型信息泄漏 |
| Actuator | `/sys/actuator`、`/sys/actuator/health` | 覆盖管理端点暴露和健康详情泄漏 |
| Druid 监控台 | `/druid/index.html` | 覆盖连接池监控台暴露导致 SQL、URI、Session、数据源信息泄漏 |
| MySQL JDBC 反序列化 | `/springboot/vul`、`/springboot/insert`、`/springboot/jdbc` | 覆盖不可信 JDBC URL 和从数据库读取字节流后原生反序列化的风险链路 |

### SpringBoot 页面与资源测试

页面：`/springboot`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /springboot` | 已登录会话 | 页面正常打开，展示 Swagger、Actuator、Druid、MySQL JDBC 四类场景 |
| Swagger 流量包 | `GET /other/datapackage/springboot/swagger_ui.pcapng` | 无 | 返回可下载流量包 |
| Actuator 流量包 | `GET /other/datapackage/springboot/actuator.pcapng` | 无 | 返回可下载流量包 |
| Druid 流量包 | `GET /other/datapackage/springboot/druid.pcapng` | 无 | 返回可下载流量包 |
| MySQL JDBC 流量包 | `GET /other/datapackage/springboot/mysql_jdbc.pcapng` | 无 | 返回可下载流量包 |

### Swagger/OpenAPI 暴露测试

页面：`/springboot`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| OpenAPI JSON | `GET /v3/api-docs` | 已登录会话 | 返回 OpenAPI 文档，内容包含 `openapi`、`paths` 等接口描述 |
| Swagger UI | `GET /swagger-ui/index.html` | 已登录会话 | Swagger UI 页面可访问 |
| 风险确认 | 查看 `/v3/api-docs` 内容 | 无 | 可看到后端接口路径、参数、模型信息，说明接口文档未做生产隔离 |

### Actuator 端点暴露测试

页面：`/springboot`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| Actuator 根端点 | `GET /sys/actuator` | 已登录会话 | 返回 `_links`，列出可访问管理端点 |
| 健康详情 | `GET /sys/actuator/health` | 已登录会话 | 返回 `status` 和组件详情 |
| 映射端点 | `GET /sys/actuator/mappings` | 已登录会话 | 返回应用请求映射信息，说明路由信息可枚举 |

### Druid 监控台暴露测试

页面：`/springboot`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| Druid 首页 | `GET /druid/index.html` | 无需登录或已登录会话 | 返回 Druid 监控页面 |
| Druid 数据源信息 | `GET /druid/datasource.json` | 无需登录或已登录会话 | 返回数据源监控 JSON |
| Druid URI 统计 | `GET /druid/weburi.json` | 无需登录或已登录会话 | 返回 Web URI 访问统计 |

### MySQL JDBC 反序列化测试

页面：`/springboot`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| JDBC URL 缺失 | `GET /springboot/vul` | 不传 `url` | 返回“JDBC URL不能为空” |
| Fake MySQL 连接尝试 | `GET /springboot/vul?url=jdbc:mysql://127.0.0.1:1/test&username=root&password=x` | 不可用 MySQL 地址 | 返回 JDBC 连接失败，说明服务端会使用用户传入的 JDBC URL 发起连接 |
| 插入测试对象 | `GET /springboot/insert?command=true` | 安全测试命令 `true` | 返回“恶意对象插入成功！” |
| 触发本地反序列化链路 | `GET /springboot/jdbc` | 依赖上一步写入对象 | 返回“触发MYSQL-JDBC反序列化漏洞！” |

## SPEL 表达式注入

当前覆盖原生 SpEL 表达式执行和 `SimpleEvaluationContext` 安全上下文限制两类场景。模块重点演示不可信输入直接进入 `SpelExpressionParser.parseExpression()` 并通过 `Expression.getValue()` 执行时，表达式从动态计算能力升级为类型引用、静态方法调用和命令执行能力的过程。

SpEL 注入的本质是不可信输入进入表达式“可执行上下文”后改变了原本的业务计算语义。`StandardEvaluationContext` 能力较完整，适合可信内部表达式，不适合直接执行用户输入；修复时应避免解析不可信表达式，确需表达式能力时使用 `SimpleEvaluationContext`、固定模板、白名单表达式、参数绑定和最小权限上下文，并禁止 Java 类型引用、构造函数、Bean 引用和任意方法调用。

已覆盖类型

| 分类 | 已有场景 | 结论 |
| --- | --- | --- |
| 表达式探测 | `100-1` | 覆盖基础表达式执行能力，可用于识别表达式是否被解析 |
| 类型引用 | `T(java.lang.Math).abs(-1)` | 覆盖 `T()` 类型引用和静态方法调用能力 |
| 命令执行 | `T(java.lang.Runtime).getRuntime().exec('true')` | 覆盖高危方法调用到系统命令执行链路 |
| 安全上下文 | `/spel/safe` 使用 `SimpleEvaluationContext` | 覆盖安全上下文对类型引用、构造函数、Bean 引用等能力的限制 |

### SPEL 漏洞场景测试

页面：`/spel`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /spel` | 已登录会话 | 页面正常打开，展示原生漏洞场景和 `SimpleEvaluationContext` 安全场景 |
| 算术表达式探测 | `GET /spel/vul?ex=100-1` | `100-1` | 返回 `99`，说明输入被作为 SpEL 表达式解析执行 |
| 类型引用探测 | `GET /spel/vul?ex=T(java.lang.Math).abs(-1)` | Java 类型引用 | 返回 `1`，说明 `StandardEvaluationContext` 允许类型引用和静态方法调用 |
| 命令执行链路 | `GET /spel/vul?ex=T(java.lang.Runtime).getRuntime().exec('true')` | 安全测试命令 `true` | 返回 `Process` 相关对象字符串或执行结果对象，说明可触达命令执行 Sink |
| 非法表达式 | `GET /spel/vul?ex=T(java.lang.Runtime).getRuntime().exec(` | 语法错误表达式 | 返回 “SPEL表达式执行失败” |
| 流量包下载 | `GET /other/datapackage/spel/spel.pcapng` | 无 | 返回可下载流量包 |

### SPEL 安全场景测试

页面：`/spel`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 安全算术表达式 | `GET /spel/safe?ex=100-1` | `100-1` | 返回 `99`，说明低风险表达式仍可计算 |
| 阻断类型引用 | `GET /spel/safe?ex=T(java.lang.Math).abs(-1)` | Java 类型引用 | 返回“表达式被安全上下文限制”，说明类型引用被禁止 |
| 阻断命令执行 | `GET /spel/safe?ex=T(java.lang.Runtime).getRuntime().exec('true')` | 命令执行表达式 | 返回“表达式被安全上下文限制”，不执行系统命令 |
| 安全场景错误处理 | `GET /spel/safe?ex=T(java.lang.Runtime).getRuntime().exec(` | 语法错误表达式 | 返回“表达式被安全上下文限制”或解析错误信息 |

## SSTI 模板注入

当前覆盖 Thymeleaf 视图名注入两类典型触发方式：Controller 返回值可控、URL 路径参数拼接进视图名。模块重点演示不可信输入进入服务端模板解析上下文后，`__${...}__` 预处理表达式会先被 Thymeleaf 计算，再进入模板名或 fragment 解析流程。

SSTI 的本质是不可信输入进入模板“可执行上下文”。在 Java Web 中风险入口不只包括页面内容，还包括视图名、fragment 表达式、邮件模板、报表模板、动态模板内容和多模板引擎配置。修复时应避免用户控制模板名、模板路径、fragment 或模板内容；动态选择模板时使用固定枚举/白名单映射；返回普通字符串时使用 `@ResponseBody`、`ResponseEntity` 或显式写入 `HttpServletResponse`，避免进入视图解析；变量输出优先使用自动转义能力，谨慎使用 `th:utext`。

已覆盖类型

| 分类 | 已有场景 | 结论 |
| --- | --- | --- |
| 返回视图名可控 | `/ssti/vul1?para=...` | 覆盖 Controller 直接把用户输入拼接进视图名，Thymeleaf 预处理表达式被执行 |
| URL 路径拼接视图名 | `/ssti/vul2/{path}` | 覆盖路径变量被拼接进视图名后触发 Thymeleaf 预处理表达式 |
| 白名单模板选择 | `/ssti/safe1?para=...` | 覆盖只允许固定模板名，拒绝表达式进入模板路径 |
| 跳过视图解析 | `/ssti/safe2/{path}` | 覆盖显式写入响应体后不再触发 Thymeleaf 视图解析 |
| 内容输出边界 | `/ssti/vul3?para=...` | 当前仅通过 `th:utext` 输出字符串，不会把输入当模板表达式执行，应归类为 HTML 输出/XSS 边界而非 SSTI 主场景 |

### SSTI 漏洞场景测试

页面：`/ssti`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /ssti` | 已登录会话 | 页面正常打开，展示 return 可控、URL 可控和安全对照场景 |
| return 可控算术探测 | `GET /ssti/vul1?para=__${7*7}__::.x` | Thymeleaf 预处理表达式 | 返回 500，错误信息中的模板名包含 `vul/ssti/49`，说明表达式已执行 |
| return 可控命令链路 | `GET /ssti/vul1?para=__${new java.util.Scanner(T(java.lang.Runtime).getRuntime().exec('id').getInputStream()).next()}__::.x` | 安全测试命令 `id` | 返回 500，错误信息中的模板名被替换为命令输出片段，说明可触达命令执行 Sink |
| URL 可控算术探测 | `GET /ssti/vul2/__${7*7}__::.x` | Thymeleaf 预处理表达式 | 返回 500，错误信息中的模板名包含 `vul/ssti/49`，说明表达式已执行 |
| 内容输出边界 | `GET /ssti/vul3?para=__${7*7}__::.x` | 模板表达式字符串 | 页面原样输出 `__${7*7}__::.x`，不执行表达式 |
| return 流量包下载 | `GET /other/datapackage/ssti/ssti_return.pcapng` | 无 | 返回可下载流量包 |
| URL 流量包下载 | `GET /other/datapackage/ssti/ssti_url.pcapng` | 无 | 返回可下载流量包 |

### SSTI 安全场景测试

页面：`/ssti`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 白名单允许模板 | `GET /ssti/safe1?para=ssti` | 白名单模板名 | 返回 SSTI 模块页面 |
| 白名单拒绝表达式 | `GET /ssti/safe1?para=__${7*7}__::.x` | Thymeleaf 预处理表达式 | 返回 401 页面，表达式不进入视图名 |
| 跳过视图解析 | `GET /ssti/safe2/__${7*7}__::.x` | Thymeleaf 预处理表达式 | 返回纯文本“已跳过视图解析...”，不触发 Thymeleaf 解析 |

## 反序列化

当前覆盖 Java 原生 `ObjectInputStream.readObject()`、SnakeYAML `Yaml.load()`、XMLDecoder `readObject()` 三类 Java 反序列化入口。模块重点演示“不可信数据恢复为对象”时，攻击者如何通过对象图、类型标签、构造器、setter、`readObject` 或组件 gadget 链，把普通数据解析入口升级为代码执行、类加载、文件操作或拒绝服务风险。

反序列化漏洞的本质是不可信输入进入对象构造和方法调用上下文。修复时优先避免使用 Java 原生序列化协议接收外部输入；确需解析时应采用 JSON/XML/YAML 普通数据绑定到固定 DTO，禁用任意类型解析，使用类型白名单、JEP 290 `ObjectInputFilter`、`SafeConstructor`、依赖升级、外部实体禁用、大小限制和隔离执行。单纯黑名单或关闭某个 gadget 开关只能降低特定利用链风险，不能视为完整修复。

已覆盖类型

| 分类 | 已有场景 | 结论 |
| --- | --- | --- |
| JDK 原生反序列化 | `/readObject/vul`、`/readObject/safe1`、`/readObject/safe2` | 覆盖 ObjectInputStream 直接读取不可信字节流、gadget 开关和类型白名单对照 |
| SnakeYAML | `/snakeYaml/vul`、`/snakeYaml/safe` | 覆盖默认 Constructor 按 `!!类名` 实例化对象，以及 SafeConstructor 只解析基础类型 |
| XMLDecoder | `/xmlDecoder/vul`、`/xmlDecoder/safe` | 覆盖 XMLDecoder 对象图执行方法调用，以及使用普通 XML 解析器替代危险对象反序列化 |

### ReadObject 测试

页面：`/readObject`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /readObject` | 已登录会话 | 页面正常打开，展示原生反序列化、gadget 开关和白名单安全场景 |
| 空 Payload | `POST /readObject/vul` | 不传 `payload` | 返回“Payload不能为空”或 Payload 错误提示，页面不出现 500 |
| 良性对象反序列化 | `POST /readObject/vul` | `payload=rO0ABXQACkphdmFTZWNMYWI=` | 返回 `JavaSecLab`，说明服务端执行了 `ObjectInputStream.readObject()` |
| Commons Collections 开关 | `POST /readObject/safe1` | 使用页面示例 payload | 返回执行失败或禁用提示，说明特定 gadget 链被限制；该方式不是完整防护 |
| 类型白名单 | `POST /readObject/safe2` | 使用页面示例 gadget payload | 返回反序列化失败，说明非白名单类被拒绝；`String` 等协议原生类型可能正常通过，应结合 JEP 290 进一步限制 |

### SnakeYAML 测试

页面：`/snakeYaml`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /snakeYaml` | 已登录会话 | 页面正常打开，展示默认 Constructor 和 SafeConstructor 对照 |
| 默认类型实例化 | `POST /snakeYaml/vul` | `payload=!!top.whgojp.modules.sqli.entity.Sqli {id: 1, username: test, password: pass}` | 返回解析结果中包含 `Sqli` 对象信息，说明 `!!类名` 被实例化 |
| 普通 YAML 安全解析 | `POST /snakeYaml/safe` | `payload=name: JavaSecLab` | 返回普通 Map 解析结果 |
| 阻断 Java 类型标签 | `POST /snakeYaml/safe` | `payload=!!top.whgojp.modules.sqli.entity.Sqli {id: 1, username: test, password: pass}` | 返回反序列化失败，说明 SafeConstructor 不允许任意 Java 类型构造 |

### XMLDecoder 测试

页面：`/xmlDecoder`

| 场景 | 请求 | 测试输入 | 预期结果 |
| --- | --- | --- | --- |
| 页面访问 | `GET /xmlDecoder` | 已登录会话 | 页面正常打开，展示 XMLDecoder 对象图执行和普通 XML 解析器对照 |
| 空 Payload | `POST /xmlDecoder/vul` | 不传 `payload` | 返回“Payload不能为空” |
| XMLDecoder 命令执行链路 | `POST /xmlDecoder/vul` | `payload=true` | 返回“命令执行成功”，说明 XMLDecoder 构造并启动了 ProcessBuilder |
| 普通 XML 解析器对照 | `POST /xmlDecoder/safe` | `payload=true` | 返回“命令解析成功:true”，只解析文本参数，不调用 ProcessBuilder.start() |
| 安全场景空 Payload | `POST /xmlDecoder/safe` | 不传 `payload` | 返回“Payload不能为空”，页面不出现 500 |
