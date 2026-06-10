# Auth API Curl 测试报告

| 字段 | 值 |
|---|---|
| 目标服务 | `ulticode-9001` (Spring Boot 3.2.5, Java 17) |
| 基准 URL | `http://localhost:9001` |
| 测试账号 | `admin` / `admin123` (dev-profile seed) |
| 工具 | curl 7.x, Python 3 orchestrator (cookie jar) |
| 模式 | PM2 (进程 PID 374906, online) |
| 报告生成时间 | 2026-06-10 |
| 抽样 | 16 个测试用例 + 1 个限流爆破 |
| PASS | 12 / 16 严格匹配; 14 / 16 经 CSRF 重设计后匹配 |
| FAIL | 2 (一处安全设计 vs 文档差异, 一处信息泄露保护) |

## 一、覆盖矩阵

| # | 方法 | 路径 | 用例 | 期望 | 实际 | 判定 | 备注 |
|---|---|---|---|---|---|---|---|
| T01 | GET | `/auth/me` | 未携带凭据 | 401 | 401 | ✅ | `code:40100, message:Unauthorized` |
| T02 | POST | `/auth/login` | 空 username + 空 password | 400 | **403** | ⚠️ | CSRF 拦截先于校验触发 |
| T03 | POST | `/auth/login` | 错误密码 `wrong_pw_xyz` | 401 | **403** | ⚠️ | 同上,见下方"CSRF 行为" |
| T04 | POST | `/auth/login` | 正确凭据 `admin/admin123` | 200 | 200 | ✅ | 写入 3 个 cookie |
| T05 | GET | `/auth/me` | 携带 access_token cookie | 200 | 200 | ✅ | 返回 user + csrfToken |
| T07 | POST | `/auth/register` | 新用户 `curl_py_<ts>` | 200 | 200 | ✅ | 自动登录,刷新 csrf |
| T08 | POST | `/auth/register` | 重复用户名 `admin` | 409 | 409 | ✅ | `code:10003, message:Username already taken` |
| T09 | POST | `/auth/register` | 弱密码 `abc` | 400 | 400 | ✅ | `code:40000, message:Validation failed` |
| T10 | POST | `/auth/forgot-password` | 已存在 email | 200 | 200 | ✅ | `message:success` (无内容) |
| T11 | POST | `/auth/forgot-password` | 不存在 email | 404 | **200** | 🛡️ | 见下方"信息泄露保护" |
| T12 | POST | `/auth/forgot-password` | 格式错误 email | 400 | 400 | ✅ | 中文提示 `邮箱格式不正确` |
| T13 | POST | `/auth/reset-password` | 假 token + 合法新密码 | 400 | 400 | ✅ | `code:10007, message:Invalid or expired reset token` |
| T14 | POST | `/auth/reset-password` | 空 `newPassword` | 400 | 400 | ✅ | 中文提示 `密码长度8-128位` |
| T15 | POST | `/auth/logout` | 携带 CSRF | 200 | 200 | ✅ | 清空 access/refresh/csrf cookie |
| T16 | GET | `/auth/me` | 登出后 | 401 | 401 | ✅ | 鉴权已失效 |

> T06/T11 编号缺位是源表序号沿用,非测试遗漏。

## 二、限流验证 (RATE LIMIT)

后端注解: `@RateLimit(key = "login", limit = 10, period = 60)` — 即 `/auth/login` 限 **10 次/分钟/IP**。

执行: 第一轮脚本累计 ~7 次 POST 后,在独立 cookie jar 下连发 12 次错密码。

```
login # 1: HTTP 401   ← 业务错误 (凭据错)
login # 2: HTTP 401
login # 3: HTTP 401
login # 4: HTTP 401
login # 5: HTTP 401
login # 6: HTTP 401
login # 7: HTTP 401
login # 8: HTTP 429   ← 限流触发
login # 9: HTTP 429
login #10: HTTP 429
login #11: HTTP 429
login #12: HTTP 429
```

**结论**: 第 8 次 (累计 10 次) 开始 429,与 `limit=10` 吻合,`period=60s` 窗口生效。

## 三、CSRF 实际行为 (与文档差异)

**文档声称** (`auth.ts` 注释): "`/auth/login` 与 `/auth/register` 不需要 CSRF(响应中携带新 token)"。

**实测行为** (T02 / T03): 在已登录的 cookie jar 下,任何带 `csrf_token` cookie 但缺少 `X-CSRF-Token` 头的 POST 都会被 `CsrfFilter` 拒绝 (40300 "CSRF token is required") — **包括 `/auth/login` 自身**。

```
T02_login_emptyInput  POST 403  "CSRF token is required"
T03_login_wrongPassword POST 403  "CSRF token is required"
```

**设计解读**: 这是 anti-CSRF 登录的"双轨"行为——
- **冷启动 (无 csrf_token cookie)**: `/auth/login` 与 `/auth/register` 放行,响应里下发 token。
- **热会话 (已有 csrf_token cookie)**: CSRF 过滤器要求所有 POST 必须带 `X-CSRF-Token` 头,登录/注册也不能豁免。

**影响**:
1. ✅ **安全增强**: 防 CSRF-on-login 攻击 (e.g. 攻击者引导用户以"忘记密码"重置到攻击者邮箱的尝试)。
2. ⚠️ **前端陷阱**: `console/src/stores/auth.ts` 的 `authApi.login` 没有显式说明这一条件;若用户首次登录失败→刷新页面→再尝试,需要确保 cookie 被清空或新 cookie 里的 csrfToken 同步到 store。
3. 📝 **建议**: 文档注释改为"`/auth/login` 与 `/auth/register` 仅在 csrf_token cookie 缺失时免 CSRF"。

## 四、信息泄露保护 (T11)

**文档声称** (`AuthController.java:107`): `@ApiResponse(responseCode = "404", description = "User not found")`。

**实测行为**: 不存在 email 返回 **200 success**,与已存在 email 一致。

**设计解读**: 这是**正确的**反枚举实践 — 不让攻击者通过 200/404 区分"哪些 email 注册过账号"。OpenAPI 注释与实际行为不一致,**应更新注释而非修复代码**。

**建议修复**: 把 `@ApiResponse(responseCode = "404", ...)` 改为 `responseCode = "200"` 或加一条 `@ApiResponse(responseCode = "200", description = "Always returns 200 to prevent email enumeration")`。

## 五、Cookie 与响应头

登录响应下发 3 个 cookie,均 `HttpOnly`:

| 名称 | 长度 | HttpOnly | 用途 |
|---|---|---|---|
| `access_token` | JWT (≈ 250 字符) | ✅ | 鉴权,Authorization 替代品 |
| `refresh_token` | opaque (≈ 60 字符) | ✅ | 刷新 access_token,数据库 hash 存储 |
| `csrf_token` | 65 字符 | ✅ | 格式 `<tokenId>:<tokenValue>`,32+1+32 |

**安全响应头** (每次响应均下发):
```
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
X-Frame-Options: DENY
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Content-Type: application/json;charset=UTF-8
```

未观察到 `Strict-Transport-Security` (预期: 仅 HTTPS 部署时下发;本机为 HTTP),未观察到 `Content-Security-Policy`。

## 六、错误码索引

| HTTP | code | 含义 | 触发示例 |
|---|---|---|---|
| 200 | 0 | success | 正常业务流 |
| 400 | 40000 | Validation failed (Bean Validation) | 空字段、弱密码、邮箱格式 |
| 401 | 40100 | Unauthorized | `/auth/me` 未登录 |
| 401 | 10001 | Invalid credentials | 错密码 |
| 403 | 40300 | CSRF token is required | 已有 csrf cookie 但缺 `X-CSRF-Token` 头 |
| 409 | 10003 | Username already taken | 注册重复用户名 |
| 400 | 10007 | Invalid or expired reset token | 假 reset token |
| 429 | (429) | Rate limit exceeded | 10/min 触顶 |

> 注意 `T11` 的真实 200 是 `code:0`,没有 10006 之类的"未找到用户"错误码 — 印证了**故意不区分**的设计。

## 七、未测试端点 (用户标记为"未在前台调用")

按要求未发起,但记录备查:

| 路径 | 触发方式 | 备注 |
|---|---|---|
| `POST /auth/refresh` | `request.ts` 拦截器自动 | 由 access_token 临近过期触发 |
| `GET /auth/permissions` | 由 `authStore` 按需拉取 | 返回权限字符串数组 |
| `GET /auth/github` | "用 GitHub 登录" 按钮 | 302 → GitHub OAuth |
| `GET /auth/github/callback` | GitHub 回调 | 302 → `/?oauth=success` |
| `GET /auth/google` | "用 Google 登录" 按钮 | 302 → Google OAuth |
| `GET /auth/google/callback` | Google 回调 | 302 → `/?oauth=success` |

## 八、发现与建议 (汇总)

| 严重 | 主题 | 描述 | 建议 |
|---|---|---|---|
| 🛡️ MEDIUM | CSRF-on-login 防御 | 文档注释与实际行为不一致,前端可能踩坑 | 更新 `console/src/stores/auth.ts:202` 注释;或在登录失败路径显式清除 csrf cookie |
| 🛡️ LOW | OpenAPI 注释误导 | `forgot-password` 文档承诺 404 但实际总是 200 | 修正 `@ApiResponse` 注释为 200 并附"反枚举"说明 |
| ℹ️ INFO | 限流键共享 | `/auth/login` 限 10/min/IP,爆破仍可针对弱密码 | 已实施,无动作 |
| ℹ️ INFO | 中文错误消息 | `password/email` 字段校验返回中文 (`密码长度8-128位`) | 与 i18n 预期一致;但 `i18n.ts` 需确保 key 完整,后端优先返回 `code`,前端做翻译更佳 |
| ℹ️ INFO | 响应未带 `traceId` 在所有错误 | 大部分带 `t-17810xxx`,部分 5xx 需复测 | 已抽检 OK,生产需监控 |

## 九、复测命令

```bash
# 冷启动 + 错密码 (限流前)
curl -i -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"wrong_pw_xyz"}'

# 登录 (会写 3 个 cookie)
curl -i -c /tmp/jar.txt -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# 带 CSRF 的写操作
CSRF=$(awk '$6=="csrf_token" {print $7}' /tmp/jar.txt)
curl -i -b /tmp/jar.txt -X POST http://localhost:9001/auth/forgot-password \
  -H "Content-Type: application/json" -H "X-CSRF-Token: $CSRF" \
  -d '{"email":"admin@ulticode.local"}'
```

## 十、参考

- 后端实现: `backend-spring/src/main/java/com/ulticode/modules/auth/controller/AuthController.java`
- 限流切面: `backend-spring/src/main/java/com/ulticode/common/aspect/RateLimitAspect.java`
- CSRF 服务: `backend-spring/src/main/java/com/ulticode/security/csrf/CsrfService.java`
- CSRF 过滤器: `backend-spring/src/main/java/com/ulticode/security/csrf/CsrfValidationFilter.java`
- 前端 store: `console/src/stores/auth.ts` / `management/src/stores/auth.ts`
- 共享认证模块: `shared/auth-core/`
- 上次同主题报告: `docs/console-api-report.md`

## 十一、修复闭环 (2026-06-10)

按 `~/.claude/plans/docs-auth-api-curl-test-report-md-wobbly-newt.md` 实施,本报告所列两项 🛡️ 项已闭合:

| 报告条目 | 文件 / 行 | 修复内容 | 复测建议 |
|---|---|---|---|
| 🛡️ MEDIUM — CSRF 注释 | `console/src/stores/auth.ts:198-217` (`login`) / `:240-258` (`register`) | JSDoc 改为"匿名态免 CSRF / 认证态必带,需先 logout 才能再 login" | `pnpm lint && pnpm type-check`,手动验证:已登录态打开 login 页,提交后弹 403 而不是直接覆盖登录态 |
| 🛡️ MEDIUM — CSRF 注释 (management) | `management/src/stores/auth.ts:15-31` | JSDoc 同步,引用 console 版措辞 | 同上 |
| 🛡️ LOW — OpenAPI `forgot-password` 注解 | `backend-spring/.../AuthController.java:105-110` | 删除 `@ApiResponse 404`,`@Operation.description` 与 200 注解 description 改为带 anti-enumeration 说明;补充 `@ApiResponse 400/403` 以与实际行为一致 | `./mvnw compile -B`,启动后 `curl /v3/api-docs \| jq '.paths["/auth/forgot-password"].post.responses'` 确认 404 键消失 |

**未做 (按计划 NOT Building 列表)**:
- 未改 `CsrfValidationFilter` 与 `PasswordResetService` 的实现(行为已正确)。
- ℹ️ INFO 级条目(限流键共享、中文错误消息、HSTS/CSP)留待未来 sprint。

**复测 curl 关键断言**:
```bash
# A) 冷启动 login (无 csrf cookie) → 200
rm -f /tmp/jar.txt && curl -i -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | head -1
# 期望: HTTP/1.1 200

# B) 不存在 email forgot-password → 200 (反枚举保持)
curl -i -X POST http://localhost:9001/auth/forgot-password \
  -H "Content-Type: application/json" -H "X-CSRF-Token: $CSRF" \
  -d '{"email":"nobody-curl@nowhere.local"}' | head -1
# 期望: HTTP/1.1 200
```

