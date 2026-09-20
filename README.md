# JavaGoat

A Java vulnerability lab derived from [JavaSecLab](https://github.com/whgojp/JavaSecLab). At runtime it looks like an internal IT service desk named **StackDesk**. Vulnerabilities sit in tickets, assets, wiki, reimbursements, and SSO — they have to be found by audit or testing. The UI is not grouped as “XSS / SQLi / RCE”.

Upstream: [https://github.com/whgojp/JavaSecLab](https://github.com/whgojp/JavaSecLab) by [whgojp](https://github.com/whgojp). This repository keeps many of those sinks and vulnerable library versions, then adds a business shell, a production-like deploy path, and several new auth/OAuth issues.

> Intentionally unsafe. Run only on a local or isolated network. Do not put it on the public internet.

[中文文档](./README_ZH.md)

## What changed from JavaSecLab

| JavaSecLab | This repo (JavaGoat) |
| --- | --- |
| Teaching UI with vul/safe pages | Business UI, no lesson labels |
| Embedded Tomcat (`java -jar`) | Preferred path: Nginx → external Tomcat 9 → MySQL |
| JDBC / Hibernate / MyBatis / JPA SQLi | Business data access is **MyBatis** and **JPA** only |
| Shiro-550 and memory-shell modules | **Removed** |
| The menu tells you the bug class | You have to find it; trainer map is `docs/audit-map.md` (not in the image) |

## Business app

After login you get StackDesk, not a vulnerability gallery.

| Area | Path | Role |
| --- | --- | --- |
| Tickets | `/tickets` | Create, search, comment, assign |
| Wiki | `/wiki` | Articles, rich text, search |
| Assets | `/assets` | Inventory, host probe, XML import |
| Reimbursements | `/reimbursements` | Submit, approve, pay |
| Messages | `/messages` | Chat |
| Directory | `/directory` | People lookup |
| Files | `/files` | Upload / read / download / delete |
| Profile | `/settings/profile` | Bio, signature, avatar URL |
| Admin | `/admin/*` | Users, integrations, config import, reports, sessions, ops |
| Portal SSO | `/sso/authorize` | “Sign in with enterprise portal” on the login page |

Default accounts (plaintext):

| User | Password | Role cookie |
| --- | --- | --- |
| `admin` | `admin` | `admin` |
| `agent` | `agent` | `agent` |
| `test` | `test` | `employee` |

## Vulnerabilities

Most sinks come from JavaSecLab and are reached through business routes. Items marked **new** were added here.

**Injection and execution:** MyBatis `${}` on ticket search; JPA JPQL on asset lists; stored/reflected XSS (`th:utext`, UEditor); command injection on host probe; Groovy notify scripts; SpEL report filters; SSTI via notify template names; XXE on XML import; XPath on directory search; Log4j2 on ticket titles and User-Agent.

**Files and requests:** arbitrary read/download/delete via `fileName`; unrestricted upload; SSRF (avatar URL, webhook test); CSRF on ticket assign (GET); open redirect on login `redirect=`.

**Auth and logic (including new):**

- Horizontal IDOR: ticket detail/comment/assign and reimbursement pay key only on id
- Vertical auth is weak: `/admin/**`, all tickets, reimbursement inbox, probe/import read Cookie `role`, Header `X-Role` / `X-User-Role`, or `role=` — **not the database**. Change `employee` to `admin` to enter admin features
- **New** login bypass: raw URI `startsWith("/static")` / suffix `.js` (`/static/..;/internal/staff-directory`, `/internal/staff-directory;.js`)
- **New** header auth bypass: first `X-Forwarded-For` hop `127.0.0.1`, `X-Internal-Request: true`, presence of `X-Original-URL`, and similar
- **New** OAuth + open redirect account takeover: SSO `redirect_uri` checked with `startsWith("/")`, so `//evil.com` steals the code; then `/sso/callback?code=` logs in as the victim

**Components / ops:** Fastjson, Jackson, XStream, SnakeYAML, XMLDecoder, `ObjectInputStream` on config import; Swagger, Actuator, Druid; unpaid reimbursement replay.

Removed: Shiro RememberMe key leak, memory shells.

Trainer map (not shipped in the image): [docs/audit-map.md](./docs/audit-map.md)

## Filters (meant to be bypassed)

`WeakInputGuard` looks like input validation. Matches are **case-sensitive** and not normalized.

| Check | Blocked | Still works |
| --- | --- | --- |
| SQL | `union select`, `sleep(` | `' OR 1=1`, `SLEEP(` |
| XSS | `<script`, `javascript:` | `<img src=x onerror=...>` |
| Command | `;`, `&&`, `cat ` | `127.0.0.1 \| id` |
| Groovy | `Runtime`, `ProcessBuilder` | `"id".execute()` |
| SpEL | `java.lang.Runtime`, `getRuntime` | `new java.lang.ProcessBuilder('id').start()` |
| SSTI name | `..`, `WEB-INF` | `__${7*7}__::.x` |
| XXE | exact `file:///etc/passwd` | `file:///etc/hosts` or http |
| Log4j | `${jndi:ldap` | `${jndi:rmi` or nested lookup |
| SSO redirect | not `/` and not localhost prefix | `//evil.com`, `http://127.0.0.1.evil.com` |
| Admin role | cookie/header not admin/agent | `role=admin` or `X-Role: admin` |

There is no HTML sanitizer, no lowercased keyword list, and no host allowlist on OAuth `redirect_uri`.

## Stack

JDK 8, Spring Boot 2.4.1, Spring Security (session), MyBatis-Plus, Spring Data JPA, Thymeleaf, Layui, MySQL 8. Intentionally old: Fastjson 1.2.37, Log4j 2.8.2, XStream 1.4.14, Groovy 2.5.6, and others.

## Deployment architecture

Preferred layout (classic Java production):

```text
browser
  │ :80
  ▼
Nginx 1.26          reverse proxy, WebSocket, X-Forwarded-*
  │ :8080 in Docker
  ▼
Tomcat 9            WAR as ROOT (not Spring Boot’s embedded Tomcat)
  │ mysql:3306
  ▼
MySQL 8             database name still JavaSecLab (upstream SQL)
```

Context path must be `/`. Tomcat 8080 is not published by default. The WAR `ServletInitializer` is injected only at image build time. The original embedded-Tomcat compose files remain for comparison.

## How to start

Need JDK 8, Maven, Docker Compose; local IDEA also needs MySQL 8.

**Option 1 — Nginx + external Tomcat (recommended)**

```shell
docker compose -f docker-compose.nginx-tomcat.yml -p javagoat-stack up -d --build
docker compose -f docker-compose.nginx-tomcat.yml -p javagoat-stack ps
```

When all three services are `healthy` / `running`, open <http://127.0.0.1/login>. Stop with `-p javagoat-stack down`. First build compiles a WAR inside the image. Free ports 80 and 3306 first.

**Option 2 — local image, embedded Tomcat**

```shell
mvn clean package -DskipTests
docker compose -p javagoat up -d
```

**Option 3 — published upstream image (comparison only)**

```shell
docker compose -f docker-compose.image.yml up -d
```

**Option 4 — IDEA / Maven**

1. Import [sql/JavaSecLab.sql](./sql/JavaSecLab.sql). Extra business tables are created on startup.
2. `spring.profiles.active: dev` in `application.yml`
3. Set MySQL credentials in `application-dev.yml` (database `JavaSecLab`)
4. Run `top.whgojp.Application`

## Safety

Isolated network, throwaway accounts, test database. Do not mount host secrets. Treat uploads and logs as untrusted. Do not copy `docs/audit-map.md` into a runnable image.

## License and credit

[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0) — see [LICENSE](./LICENSE).

Vulnerability scenarios and most sinks come from [JavaSecLab](https://github.com/whgojp/JavaSecLab). Thanks to **whgojp** for the original work.
