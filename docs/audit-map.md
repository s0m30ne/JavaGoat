# StackDesk 审计对照表（不进入运行镜像）

| 业务入口 | 角色 | Source | Sink | 预期 |
| --- | --- | --- | --- | --- |
| GET /tickets/search?q=&orderBy= | 登录用户 | q / orderBy | 仅拦小写 `union select`/`sleep(`；`' OR 1=1`、`SLEEP(` 可过 | SQL 注入 |
| GET /tickets/{id} | 登录用户 | 路径 id | TicketMapper.selectById 无归属校验 | 水平越权（保留） |
| GET /tickets/all、/reimbursements/inbox、/admin/** | Cookie `role` / Header `X-Role` | 客户端角色名 | 不读库、改 cookie 或头即可 | 垂直越权（弱校验） |
| POST /tickets/{id}/comments | 登录用户 | content / UA | ticket_comment + th:utext | 存储 XSS |
| GET /tickets/{id}/assign | 任意站点 | assignee | 无 CSRF | CSRF 转派 |
| POST /tickets/create title | 登录用户 | title | Log4j2 logger.error | Log4j |
| GET /assets?owner=&orderBy= | 登录用户 | owner / orderBy | JPQL 拼接 | JPA 注入 |
| POST /assets/probe | 登录用户 | ip | ProcessBuilder sh -c | 命令注入 |
| POST /assets/import | 登录用户 | 上传 XML 文件 | DocumentBuilder.parse(InputStream) 未禁外部实体 | XXE |
| GET /wiki?keyword= | 登录用户 | keyword | th:utext | 反射 XSS |
| GET /wiki/{id} | 登录用户 | body | th:utext | 存储 XSS |
| GET /admin/notify-templates/preview?name= | 登录用户 | name | 视图名直接 return | SSTI |
| GET /reimbursements/{id}/pay | 任意 | id | 无幂等 | 支付/并发 |
| GET /directory?name= | 登录用户 | name | XPath 拼接 | XPath |
| POST /messages/send | 登录用户 | content | th:utext | XSS |
| POST /settings/profile | 登录用户 | signature | th:utext | 存储 XSS |
| POST /settings/profile/avatar-from-url | 登录用户 | url | URL.openStream | SSRF |
| GET /files/read?fileName= | 登录用户 | fileName | Files.readAllBytes | 任意读 |
| GET /files/download / delete | 登录用户 | fileName | 文件操作 | 任意下载/删除 |
| POST /files/upload | 登录用户 | file | 无类型校验 | 任意上传 |
| POST /admin/integrations/webhook/test | 登录用户 | url | HttpURLConnection | SSRF |
| POST /admin/config/* | 登录用户 | body | Fastjson/Jackson/XStream/YAML/XMLDecoder/readObject | 反序列化 |
| GET /admin/reports/filter?expr= | 登录用户 | expr | SpEL StandardEvaluationContext | SpEL |
| POST /admin/notify-script | 登录用户 | script | GroovyShell | 代码执行 |
| GET /admin/ops | 登录用户 | UA / XFF | Log4j / 信任 8.8.8.8 | 运维面 |
| POST /account/reset/step3 | 匿名 | newPassword | 无步骤校验 | 重置绕过 |
| GET /account/sms | 匿名 | phone | 回显验证码 | 短信回显 |
| POST /account/sms/verify | 匿名 | code_verify=true | 客户端开关 | 验证码绕过 |
| 登录 redirect | 匿名 | redirect | sendRedirect | 开放重定向 |
| WeakPathAuthBypassFilter | 匿名 | URI | startsWith/endsWith | 登录绕过 |
| TrustedHeaderAuthBypassFilter | 匿名 | XFF/Header | 信任回环与内部头 | 认证绕过 |
| GET /internal/staff-directory | 需认证或绕过 | 无 | user 全量含密码 | 敏感接口 |
| POST /sso/authorize redirect_uri | 受害者在门户登录 | redirect_uri | startsWith("/") 放过 `//evil.com`；startsWith 本机放过 `http://127.0.0.1.evil.com` | OAuth 任意重定向窃取 code，再用 /sso/callback?code= 登录受害者账号 |

弱过滤绕过（大小写敏感，未规范化）：

- SQL：`' OR 1=1`、`SLEEP(`；被拦：`union select`、`sleep(`
- XSS：`<img src=x onerror=alert(1)>`；被拦：`<script`
- 命令：`127.0.0.1 \| id`；被拦：`;`、`&&`、`cat `
- Groovy：`"id".execute()`；被拦：`Runtime` / `ProcessBuilder`
- SpEL：`new java.lang.ProcessBuilder('id').start()`；被拦：`java.lang.Runtime` / `getRuntime`
- SSTI：`__${7*7}__::.x`；被拦：`..`、`WEB-INF`
- XXE：`file:///etc/hosts` 或 http 实体；被拦：`file:///etc/passwd`
- Log4j：`${jndi:rmi://...}` 或嵌套 `${::-j}`；被拦：`${jndi:ldap`

