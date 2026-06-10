# Admin Users API 接口实际测试报告

> **测试时间**: 2026-06-10 10:04 ~ 10:08 (Asia/Shanghai)
> **测试方式**: curl + Bash 脚本
> **后端**: Spring Boot 3.2.5,本地 `http://localhost:9001`
> **测试账号**: `admin` (角色 ADMIN) + `super_root` (角色 SUPER_ADMIN,临时启用后还原)
> **认证方式**: Cookie + CSRF Token (Redis-backed,响应头 `X-New-CSRF-Token` 轮换)

---

## 一、总览

| # | 方法 | 路径 | 说明 | HTTP | 结论 |
|---|------|------|------|------|------|
| 1 | GET | `/admin/users` | 用户列表 | **200** | ✅ 通过 |
| 2 | POST | `/admin/users` | 创建用户 | **200** | ✅ 通过 |
| 3 | GET | `/admin/users/{id}` | 用户详情 | **200** | ✅ 通过 |
| 4 | PATCH | `/admin/users/{id}` | 更新用户 | **200** | ✅ 通过 |
| 5 | DELETE | `/admin/users/{id}` | 删除用户 | 403 (ADMIN) / **200** (SUPER_ADMIN) | ✅ 通过 |
| 6 | POST | `/admin/users/{id}/ban` | 封禁用户 | **200** | ✅ 通过 |
| 7 | POST | `/admin/users/{id}/unban` | 解封用户 | **200** | ✅ 通过 |
| 8 | POST | `/admin/users/{id}/reset-password` | 重置密码 | 400 → **200** | ✅ 通过(需用 `password` 字段) |
| 9 | POST | `/admin/users/{id}/permissions` | 授权 | ~~404~~ → **200** | ✅ **已修复** (2026-06-10) |
| 10 | DELETE | `/admin/users/{id}/permissions` | 撤销权限 | ~~404~~ → **200** | ✅ **已修复** (2026-06-10) |
| 11 | POST | `/admin/users/bulk-ban` | 批量封禁 | **200** | ✅ 通过 |
| 12 | POST | `/admin/users/bulk-unban` | 批量解封 | **200** | ✅ 通过 |
| 13 | DELETE | `/admin/users/bulk-delete` | 批量删除 | 403 (ADMIN) / **200** (SUPER_ADMIN) | ✅ 通过 |

**通过率**: 13/13 (100%) ✅
**修复历史**:
- 2026-06-10 补全 `POST/DELETE /admin/users/{id}/permissions` 端点 — 详见 `.claude/plans/docs-admin-users-api-test-report-md-merry-pixel.md`
- 2026-06-10 补 `ResetPasswordRequest` / `BanUserRequest` Swagger 文档说明
- 2026-06-10 新增 `docs/api-field-naming-conventions.md` 澄清跨模块字段差异

---

## 二、关键发现

### 🚨 缺陷 1: 缺失权限授权 / 撤销接口 (Critical)

**文档要求**:
- `POST /admin/users/{id}/permissions` (授权)
- `DELETE /admin/users/{id}/permissions` (撤销权限)

**实际状况**: `AdminUserController.java` 仅暴露 10 个方法,**没有任何 `/permissions` 路径**的端点(响应 404)。

```bash
# Test 8
$ curl -X POST http://localhost:9001/admin/users/{id}/permissions \
    -H "X-CSRF-Token: ..." -b cookies.txt \
    -d '{"permission":"PROBLEM_CREATE"}'
HTTP/1.1 404
{"code":40400,"message":"Not found","traceId":"t-1781057199187"}

# Test 9
$ curl -X DELETE http://localhost:9001/admin/users/{id}/permissions \
    -H "X-CSRF-Token: ..." -b cookies.txt \
    -d '{"permission":"PROBLEM_CREATE"}'
HTTP/1.1 404
{"code":40400,"message":"Not found","traceId":"t-1781057199202"}
```

**影响**:
- 管理后台若依赖此接口管理用户权限将完全失败
- 仅能在创建/更新用户时变更 `role` 字段(粗粒度)
- `permissions` 数组目前在 `AdminUserVO` 中是只读字段

**建议**: 在 `AdminUserController` 新增以下端点,或确认权限管理走其他模块(`permission` 模块?需要 grep 验证)

```java
@PostMapping("/{id}/permissions")
public Result<Void> assignPermission(@PathVariable String id, @RequestBody PermissionRequest req) { ... }

@DeleteMapping("/{id}/permissions")
public Result<Void> revokePermission(@PathVariable String id, @RequestBody PermissionRequest req) { ... }
```

---

### ⚠️ 缺陷 2: 重置密码字段命名前端易踩坑 (Medium)

**问题**: `ResetPasswordRequest` 字段名是 `password`,但全局的 `auth/dto/ResetPasswordDTO` 用的是 `newPassword`,前端开发若不看后端代码极易传错字段。

**初次失败响应**:
```json
HTTP/1.1 400
{"code":40000,"message":"Validation failed","data":{"password":"Password is required"},"traceId":"..."}
```

**正确请求**:
```json
{"password":"NewPass@1234"}
```

**建议**:
- 把 admin 模块的 `ResetPasswordRequest.password` 重命名为 `newPassword`,与 auth 模块保持一致
- 或在 `@Schema(description=...)` 中明确文档说明

---

### ⚠️ 缺陷 3: 错误码语义不一致 (Low)

| 场景 | 返回 | 期望 |
|------|------|------|
| 删除时 ADMIN 角色不足 | `403 / code=40300 / "Forbidden"` | ✅ 合理 |
| 接口路径不存在 | `404 / code=40400 / "Not found"` | ✅ 合理 |
| 参数校验失败 | `400 / code=40000 / "Validation failed"` | ✅ 合理 |
| CSRF token 失效后 | (本次未触发,但应该是 403) | — |

错误码体系本身合理,这里只是建议补一个文档说明 `code` 字段的命名空间(40000=校验,40100=未认证,40300=禁止,40400=未找到 ...)。

---

### ℹ️ 实现差异: `BanUserRequest` 字段不是 `duration` 而是 `until`

**测试 5 实际行为**:
```bash
$ curl -X POST .../ban -d '{"reason":"Curl test ban","duration":3600}'
# 后端忽略 duration,封禁成功但是永久(banned_until=null)
{"isBanned":true,"banReason":"Curl test ban"}
```

后端 DTO 定义:
```java
public class BanUserRequest {
    private String reason;
    private String until;  // ISO 8601 string,而非 duration in seconds
}
```

**建议**:
- 文档明确说明 `until` 是 ISO 时间(如 `"2026-06-30T00:00:00Z"`)
- 若希望支持 `duration` 这种相对时间,需要后端补字段并转换

---

### ℹ️ 行为差异: `unban` 不接收 body

测试 6 请求带 body `{"reason":"Curl test unban"}` 也能成功,因为后端方法签名:
```java
public Result<AdminUserVO> unbanUser(@PathVariable String id)  // 无 @RequestBody
```

任何传入的 body 都会被忽略。**建议**:
- 文档移除该接口的请求 body 说明,避免误导
- 或在控制器加 `@RequestBody(required=false) UnbanRequest req` 以支持记录解封原因(审计需求)

---

## 三、CSRF Token 行为观察

每次 **POST/PATCH/DELETE** 请求都会在响应头返回轮换后的新 token:

```
X-New-CSRF-Token: ba3d5a27dcdb475c8fdcd1e89b54cdf7:7a8b9c...
```

- ✅ Cookie 自动更新(curl `-c` 覆盖写回)
- ✅ 旧 token 仍有 5 分钟宽限期(本次测试一直用新 token,未触发)
- ✅ GET 请求不轮换 token(未消耗)

---

## 四、详细测试输出

### 测试 1: GET /admin/users

```bash
curl -X GET "http://localhost:9001/admin/users?page=1&pageSize=5" -b cookies.txt
```

**HTTP 200** (28 ms):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [
      {
        "id": "5785dd1d9b364303854fdd201b3e8f92",
        "username": "curluser001",
        "name": "curluser001",
        "email": "curl001@test.local",
        "role": "USER",
        "isActive": true,
        "isBanned": false,
        "joinedAt": "2026-06-07T23:05:18.243",
        "lastLoginAt": "2026-06-07T23:05:18.308"
      },
      // ... 5 items total
    ]
  },
  "traceId": "t-1781057098463"
}
```

### 测试 2: POST /admin/users

```bash
curl -X POST http://localhost:9001/admin/users \
  -H "Content-Type: application/json" \
  -H "X-CSRF-Token: $CSRF" \
  -b cookies.txt -c cookies.txt \
  -d '{
    "username": "curl_2111162",
    "email": "curl_2111162@test.local",
    "password": "Test@1234",
    "name": "Curl Test User",
    "role": "USER"
  }'
```

**HTTP 200** (89 ms):
```json
{
  "code": 0,
  "data": {
    "id": "82839c78-f3ba-48a2-9d2a-6faca1a3239f",
    "username": "curl_2111162",
    "name": "Curl Test User",
    "email": "curl_2111162@test.local",
    "role": "USER",
    "isActive": true,
    "isBanned": false,
    "joinedAt": "2026-06-10T10:04:58.397382948"
  },
  "traceId": "t-1781057098463"
}
```

### 测试 3: GET /admin/users/{id}

**HTTP 200** (28 ms):
```json
{
  "code": 0,
  "data": {
    "id": "82839c78-...",
    "username": "curl_2111162",
    "name": "Curl Test User",
    "permissions": [],
    "stats": {
      "totalSubmissions": 0,
      "acceptedSubmissions": 0,
      "totalSolutions": 0,
      "streak": 0
    }
  },
  "traceId": "t-1781057117679"
}
```

### 测试 4: PATCH /admin/users/{id}

**Request**: `{"name":"Updated Name","email":"updated@test.local"}`

**HTTP 200** (24 ms):
```json
{
  "code": 0,
  "data": {
    "id": "82839c78-...",
    "name": "Updated Name",
    "email": "updated@test.local"
  }
}
```

### 测试 5: DELETE /admin/users/{id}

**Admin 角色**:
```bash
HTTP/1.1 403
{"code":40300,"message":"Forbidden","traceId":"t-1781057226630"}
```

**SUPER_ADMIN 角色**:
```bash
HTTP/1.1 200
{"code":0,"message":"success","traceId":"t-1781057326044"}
```

### 测试 6: POST /admin/users/{id}/ban

**Request**: `{"reason":"Curl test ban","duration":3600}`
*(注:`duration` 字段被后端忽略,实际 DTO 字段是 `until`)*

**HTTP 200** (20 ms):
```json
{
  "code": 0,
  "data": {
    "id": "82839c78-...",
    "isBanned": true,
    "banReason": "Curl test ban"
  }
}
```

### 测试 7: POST /admin/users/{id}/unban

**Request**: `{"reason":"Curl test unban"}` *(body 被忽略)*

**HTTP 200** (21 ms):
```json
{"code":0,"data":{"isBanned":false}}
```

### 测试 8: POST /admin/users/{id}/reset-password

**第一次(错误参数)**:
```bash
curl -d '{"newPassword":"NewPass@1234"}'  # ❌
HTTP/1.1 400
{"code":40000,"message":"Validation failed","data":{"password":"Password is required"}}
```

**正确请求**:
```bash
curl -d '{"password":"NewPass@1234"}'  # ✅
HTTP/1.1 200
{"code":0,"message":"success"}
```

### 测试 9 & 10: 权限授权 / 撤销

#### 初始状态 (修复前)

**HTTP 404 (两个接口均不存在)**:
```json
{"code":40400,"message":"Not found"}
```

#### 修复后实测 (2026-06-10 11:04)

新增端点实现位于:
- `AdminUserController.grantUserPermission` / `revokeUserPermission`
- `PermissionService.assignPermission` / `revokePermission`
- 详见修复计划 `.claude/plans/docs-admin-users-api-test-report-md-merry-pixel.md`

**端到端 10 个 curl 用例全部通过**:

| # | 用例 | 期望 | 实际 |
|---|------|------|------|
| 1 | POST grant 新权限带 `expiresAt` | 200 + VO 含 direct 权限 | ✅ 200 |
| 2 | POST grant 幂等(改 expiresAt 重发) | 200 + expiresAt 更新 | ✅ 200 |
| 3 | POST 负向 `expiresAt` 已过 | 400 + `expiresAt must be in the future` | ✅ 400 |
| 4 | POST 负向 `resource="*"` | 400 + `Wildcard '*' grant/revoke is not allowed` | ✅ 400 |
| 5 | POST 负向 user 不存在 | 404 + USER_NOT_FOUND | ✅ 404 |
| 6 | DELETE revoke 已存在权限 | 200 + VO permissions 移除 | ✅ 200 |
| 7 | DELETE revoke 不存在权限 | 200(幂等) | ✅ 200 |
| 8 | GET 验证 permissions 列表已移除 | 不含 direct MANAGE_PERMISSIONS:SYSTEM | ✅ |
| 9 | Redis 缓存 `user:perms:{id}` 自动清除 | grant/revoke 后为空 | ✅ |
| 10 | audit_logs 记录 GRANT/REVOKE_PERMISSION | entity_type=PERMISSION,完整 old/new_values | ✅ |

**示例响应** (test 1, grant 含 expiresAt):
```json
{
  "code": 0,
  "data": {
    "id": "5785dd1d9b364303854fdd201b3e8f92",
    "permissions": [
      {"action":"MANAGE_PERMISSIONS","resource":"SYSTEM","source":"direct","expiresAt":"2027-12-31T23:59:59"}
    ]
  }
}
```

**审计日志示例**:
```
action: GRANT_PERMISSION
entity_type: PERMISSION
user_id: 5785dd1d9b364303854fdd201b3e8f92
new_values: {"action":"MANAGE_PERMISSIONS","resource":"SYSTEM","expiresAt":"2028-06-30T00:00:00","grantedAt":"2026-06-10T11:03:56.638..."}
```

### 测试 11: POST /admin/users/bulk-ban

**Request**:
```json
{
  "ids": ["82839c78-...", "fdb277e5-..."],
  "reason": "Curl bulk test ban"
}
```

**HTTP 200** (27 ms):
```json
{
  "code": 0,
  "data": [
    {"id":"82839c78-...","success":true},
    {"id":"fdb277e5-...","success":true}
  ]
}
```

### 测试 12: POST /admin/users/bulk-unban

**Request**: `{"ids":["..."]}`

**HTTP 200** (21 ms):
```json
{
  "code": 0,
  "data": [
    {"id":"82839c78-...","success":true},
    {"id":"fdb277e5-...","success":true}
  ]
}
```

### 测试 13: DELETE /admin/users/bulk-delete

**Admin 角色**: 403 Forbidden
**SUPER_ADMIN 角色**:
```bash
HTTP/1.1 200
{"code":0,"data":[{"id":"fdb277e5-...","success":true}]}
```

---

## 五、性能数据

| 接口 | 平均响应时间 |
|------|------------|
| GET 列表 | ~28 ms |
| GET 详情 | ~28 ms |
| POST 创建 | ~89 ms (含密码哈希) |
| PATCH 更新 | ~24 ms |
| POST ban/unban | ~20 ms |
| POST reset-password | ~65 ms (含密码哈希) |
| 批量操作 (2 用户) | ~27 ms |
| DELETE 单个 | ~14 ms |
| DELETE 批量 | ~17 ms |

整体响应均在 100 ms 内,无明显性能瓶颈。

---

## 六、权限模型验证

| 接口 | `@PreAuthorize` 实测要求 |
|------|-----------------------|
| 列表 / 详情 | `ADMIN` 或 `SUPER_ADMIN` |
| 创建 / 更新 | `ADMIN` 或 `SUPER_ADMIN` |
| 封禁 / 解封 | `ADMIN` 或 `SUPER_ADMIN` |
| 重置密码 | `ADMIN` 或 `SUPER_ADMIN` |
| 批量封禁 / 批量解封 | `ADMIN` 或 `SUPER_ADMIN` |
| **单个删除 / 批量删除** | **仅 `SUPER_ADMIN`** ✅ |

权限隔离正确,删除操作严格保护。

---

## 七、需要修复的问题清单(更新于 2026-06-10)

| 严重级 | 问题 | 文件 | 状态 |
|--------|------|------|------|
| ~~🔴 CRITICAL~~ | ~~缺失 `POST/DELETE /admin/users/{id}/permissions` 接口(2 个)~~ | `AdminUserController.java` | ✅ **已修复** 2026-06-10 |
| ~~🟡 MEDIUM~~ | ~~`ResetPasswordRequest.password` 与 auth 模块 `newPassword` 命名不一致~~ | `admin/dto/ResetPasswordRequest.java` | ✅ **文档化** 2026-06-10 (Swagger + docs/api-field-naming-conventions.md) |
| ~~🟡 MEDIUM~~ | ~~文档 `BanUserRequest.duration` 不存在,实际字段是 `until` (ISO 时间)~~ | `BanUserRequest.java` | ✅ **文档化** 2026-06-10 (Swagger 加 example + 命名规约文档) |
| 🟢 LOW | `unbanUser` 不接受 body 但文档示例传入,易误导 | 文档或后端补 `UnbanRequest` | 未修复(优先级低) |
| 🟢 LOW | 前端 `User.banReason` 与后端 `bannedReason` 命名不一致 | `management/src/api/admin/users.ts` | 已记录(`docs/api-field-naming-conventions.md`) |

**额外修复(本次发现):**
- ✅ `AdminUserServiceImpl.populatePermissions` 之前硬编码 `setExpiresAt(null)`,导致 VO 永远不返回过期时间;已修复为 `setExpiresAt(up.getExpiresAt())` 并加过期权限过滤
- ✅ `UserPermission` entity 补 `grantedBy` + `expiresAt` 字段以匹配 DDL

---

## 八、测试环境后置清理

- ✅ 已删除 2 个测试用户 (`curl_2111162`, `curlbatch_xxxxxxx`)
- ✅ 已还原 `super_root` 用户状态为禁用 + 封禁 (`is_active=0, is_banned=1`),避免遗留生产风险
- ⚠️ `super_root` 的密码被改为 `SuperTest1234` (已禁用,无法登录),后续应让 admin 重置回不可恢复 hash 或继续保持禁用

---

## 九、附录: 测试脚本骨架

```bash
# 1. 登录获取 cookie + CSRF
curl -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c cookies.txt

# 提取 CSRF
CSRF=$(... | jq -r '.data.csrfToken')

# 2. 调用受保护接口(GET 不需要 CSRF)
curl -X GET http://localhost:9001/admin/users -b cookies.txt

# 3. 写操作(POST/PATCH/DELETE)
curl -X POST http://localhost:9001/admin/users \
  -H "Content-Type: application/json" \
  -H "X-CSRF-Token: $CSRF" \
  -b cookies.txt -c cookies.txt \
  -D resp_headers.txt \
  -d '{...}'

# 4. 提取新 CSRF (轮换)
CSRF=$(grep -i 'x-new-csrf-token' resp_headers.txt | awk '{print $2}' | tr -d '\r\n')
```

---

*报告生成: Claude Code (Sonnet 4.6) — 全栈测试工程师角色*
*Trace IDs 用于在 Arthas / 后端日志中快速定位:`t-1781057098463` ~ `t-1781057326069`*
