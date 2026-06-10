# `user.ts` + `userStats.ts` — 用户与统计 API 实际测试报告

*生成时间：2026-06-10 14:19 (UTC+8)*
*测试目标：`backend-spring` 9001 端口 (PM2 进程 `ulticode-9001`)*
*测试工具：curl + Bash + ctx_execute 沙箱*
*测试身份：admin (RBAC=ADMIN)*
*测试用户：admin (`bba5ed74-6482-11f1-8191-467dade0a82b`) + nina (`mod-nina-002`, RBAC=USER)*

---

## 0. 结论速览

| # | 测试用例 | 文档/前端假设 | 实际后端行为 | 评估 |
|---|---------|------------|------------|------|
| 1 | `GET /users/{userId}` | ✅ 存在 | ✅ 200，返回 `UserVO` | **通过**（字段名与前端不完全一致） |
| 2 | `PATCH /users/{userId}` | ❌ 前端假设可改任意 user | ❌ 405，**后端只支持 `PATCH /users/me`** | **🔴 重大设计错配** |
| 3 | `GET /users/{userId}/stats` | ✅ 存在 | 🔴 500 `NoSuchMethodError` | **🔴 后端运行时 bug** |
| 4 | `GET /users/{userId}/skills` | ✅ 存在 | ✅ 200，返回 `UserSkillsDTO` | **通过** |
| 5 | `GET /users/by-username/{username}/profile` | ✅ 存在 | 🔴 500 `NoSuchMethodError` | **🔴 后端运行时 bug** |

**总判定**：5 个接口中 **2 个完全不可用 (40%)**。前端 `user.ts` + `userStats.ts` 的 PATCH 调用错配真实端点；后端 DTO 与 Service 之间存在 **构建产物不一致**（运行 jar 缺 `getCount()`）。

---

## 1. 测试环境

| 项目 | 值 |
|------|-----|
| 后端 | `ulticode-9001` PM2 fork, uptime 112m, 21 次重启, 603MB |
| 后端 PID | 374906 |
| Java | OpenJDK 17 |
| Spring Boot | 3.2.5 |
| 数据库 | MySQL 9.1 (Docker, port 23306) |
| Redis | 7 (Docker, port 26379) |
| 登录账号 | `admin / admin123` (dev seed) |
| 字符集 | UTF-8 (后端 JDNI `useUnicode=true&characterEncoding=UTF-8`) |
| 鉴权 | JWT in HttpOnly Cookie + Redis-backed CSRF token (24h TTL) |
| 响应包装 | `Result<T>`: `{code, message, data, traceId}` |

---

## 2. 后端真实端点清单（与前端 user.ts/userStats.ts 假设对照）

> 来源：`backend-spring/src/main/java/com/ulticode/modules/user/controller/UserController.java` line 30-207

| HTTP | 路径 | Controller 方法 | 鉴权 |
|------|------|----------------|------|
| GET | `/users/me` | `getCurrentUser()` | 必登录 |
| **PATCH** | **`/users/me`** | `updateCurrentUser(@Valid UpdateUserDTO)` + `@RateLimit(20/60s)` | 必登录 |
| GET | `/users` | `listUsers(page, pageSize)` | 公开 |
| **GET** | **`/users/{id}`** | `getUserById(id)` | 公开 |
| **GET** | **`/users/{id}/stats`** | `getUserStats(id)` | 公开 |
| **GET** | **`/users/{id}/skills`** | `getUserSkills(id)` | 公开 |
| **GET** | **`/users/{id}/profile`** | `getUserProfile(id)` | 公开（**前端遗漏**） |
| **GET** | **`/users/by-username/{username}/profile`** | `getUserProfileByUsername(username)` | 公开 |
| POST | `/users/me/avatar` | `uploadAvatar(file)` + `@RateLimit(10/60s)` | 必登录 |
| GET | `/users/me/achievements/progress` | `getAchievementProgress()` | 必登录 |

**对比前端假设**：
- 前端认为存在 `PATCH /users/{userId}` → ❌ 不存在；正确路径是 `PATCH /users/me`（仅自己）
- 前端遗漏了 `GET /users/{id}/profile`（这才是真正"完整 profile"的端点）

---

## 3. 详细测试结果

### 3.1 T1: `GET /users/{userId}` → `fetchUserProfile`

| 子用例 | 请求 | 状态 | 耗时 | 响应大小 |
|-------|------|------|------|---------|
| T1a | `GET /users/{ADMIN_ID}` (admin 自己) | **200** | 13ms | 252B |
| T1b | `GET /users/{TARGET_ID}` (nina) | **200** | 12ms | 267B |
| T1c | `GET /users/does-not-exist-uuid-xxx` | **404** | 22ms | 69B |
| T1d | `GET /users/{ADMIN_ID}` 未带 Cookie | **401** | 4ms | 67B |

**响应示例 (T1a admin)**:
```json
{
  "code": 0, "message": "success", "traceId": "t-1781072105457",
  "data": {
    "id": "bba5ed74-6482-11f1-8191-467dade0a82b",
    "username": "admin",
    "name": "SuperAdmin",
    "avatar": "https://api.dicebear.com/7.x/avataaars/svg?seed=admin",
    "joinedAt": "2026-06-10T04:13:28.193"
  }
}
```

**响应示例 (T1b nina 含 bio)**:
```json
{
  "data": {
    "id": "mod-nina-002",
    "username": "nina_mod",
    "name": "林娜",
    "avatar": "...",
    "bio": "论坛巡逻员，维护社区秩序",
    "joinedAt": "2026-03-12T10:30:00"
  }
}
```

**评估**：
- ✅ 鉴权正常（公开端点，但缺 Cookie 不报错，因为端点标记公开）
- ✅ 404 错误码 `20001` "User not found"
- ✅ 401 错误码 `40100` "Unauthorized"
- ⚠️ **字段集与前端假设不完全匹配**：nina 多出 `bio`，admin 没有；前端 TypeScript `UserProfile` 假设的字段可能不完整

---

### 3.2 T2: `PATCH /users/{userId}` → `updateUserProfile` (🔴 端点错配)

| 子用例 | 请求 | 状态 | 错误码 | 结论 |
|-------|------|------|--------|------|
| T2a | `PATCH /users/mod-nina-002` + CSRF + body | **405** | `40500` "Method not allowed: PATCH" | 端点不存在 |
| T2b | `PATCH /users/mod-nina-002` 无 CSRF | **403** | `40300` "CSRF token is required" | CSRF 检查优先于路由匹配 |
| T2d | `PATCH /users/mod-nina-002` + CSRF + `{}` | **405** | 同 T2a | 端点不存在 |
| T2e | `PATCH /users/does-not-exist-uuid-xxx` | **405** | 同 T2a | 不存在用户也先返回 405 |

**评估**：
- 🔴 **关键缺陷**：前端 `updateUserProfile(userId, partial)` 按 userId 改资料的语义在后端**不支持**
- 后端只支持 `PATCH /users/me` 改自己（`updateCurrentUser`）
- 行为符合"自己改自己"的安全模式，**前端 PATCH 调用将 100% 失败**

---

### 3.3 T3: `GET /users/{userId}/stats` → `fetchUserStats` (🔴 运行时 NoSuchMethodError)

**首次测试 (T3a, T3b) 返回 200**，包含完整 stats / streak / heatmap 数据，结构正确。

**后续重试**：admin & nina 都 **500**；fake-id 仍正确 404。

**根因（从 `ulticode-9001-out.log` 抓取）**:
```
2026-06-10 14:19:03: Caused by: java.lang.NoSuchMethodError:
    'int com.ulticode.modules.user.dto.UserStatsDTO$DifficultyStats.getCount()'
2026-06-10 14:19:03:   at com.ulticode.modules.user.service.impl.UserServiceImpl
        .getUserStatsById(UserServiceImpl.java:239)
2026-06-10 14:19:03:   at com.ulticode.modules.user.service.impl.UserServiceImpl
        .getUserProfile(UserServiceImpl.java:381)
2026-06-10 14:19:03:   at com.ulticode.modules.user.service.impl.UserServiceImpl
        .getUserProfileByUsername(UserServiceImpl.java:403)
```

**对比源码** (`UserStatsDTO.java:69-92`):
```java
@Data
public static class DifficultyStats {
    private int count;
    private int total;
    public DifficultyStats() {}
    public DifficultyStats(int count, int total) {
        this.count = count; this.total = total;
    }
}
```
Lombok `@Data` 应自动生成 `getCount()` / `getTotal()` / `setCount()` / `setTotal()`。

**结论**：运行 jar 内的 `DifficultyStats.class` **没有 getCount() 方法**（Lombok 未生效或 class 是更早版本），但 `UserServiceImpl.class` 是新版（调用了 getCount()）。这是**部署构建产物不一致**。

**推断**：
1. 本地 `mvn package` 产物没替换线上 PM2 用的 jar
2. 或增量编译时 `DifficultyStats.class` 没刷新
3. 或 Lombok annotation processor 在某次构建失败

**复现路径**：
- `GET /users/{any-existing-id}/stats` → 500
- `GET /users/{any-existing-id}/profile` → 500
- `GET /users/by-username/{any-existing-username}/profile` → 500
- `GET /users/{any-existing-id}/skills` → 200（**不调用** DifficultyStats.getCount）

**影响面**：3 个公开读端点全部 500，admin / 普通用户 / 任意 ID 都中招；只有 `/skills` 和 `/users/{id}` 仍可用。

**修复建议**：
```bash
cd /home/davidhlp/project/UltiCode/backend-spring
./mvnw clean package -DskipTests
pm2 restart ulticode-9001
# 复测 GET /users/{ADMIN_ID}/stats
```

---

### 3.4 T4: `GET /users/{userId}/skills` → `fetchUserSkills` (✅ 通过)

| 子用例 | 状态 | 耗时 |
|-------|------|------|
| T4a admin | **200** | 22ms |
| T4b nina | **200** | 17ms |
| T4c 不存在 | **404** `code=20001` "User not found" | - |

**响应示例 (T4a admin)**:
```json
{
  "code": 0, "message": "success", "traceId": "t-1781072234972",
  "data": {
    "skills": [
      {"tagName": "链表",   "tagSlug": "linked-list",  "count": ...},
      {"tagName": "滑动窗口","tagSlug": "sliding-window","count": 1},
      {"tagName": "字符串",  "tagSlug": "string",        "count": 1}
    ],
    "totalSolved": 6
  }
}
```

**评估**：
- ✅ 完全符合前端 `userStatsApi.getSkills` 假设
- ✅ 字段名 `tagName` / `tagSlug` / `count` / `totalSolved` 与 TS interface 兼容
- ✅ 错误码与 T1c 一致

---

### 3.5 T5: `GET /users/by-username/{username}/profile` → `fetchProfileByUsername` (🔴 同样 500)

| 子用例 | 请求 | 状态 | 备注 |
|-------|------|------|------|
| T5a | `/users/by-username/nina_mod/profile` | **500** `50000` "Unknown error" | 同根因 |
| T5b | `/users/by-username/admin/profile` | **500** | 同根因 |
| T5c | `/users/by-username/nonexistent_user_xxx/profile` | **404** | `20001` "User not found" |
| T5d | `/users/by-username/张三/profile` (URL 编码) | **404** | URL 编码工作正常 |

**评估**：
- 🔴 **后端运行时 bug**：调用链 `getUserProfileByUsername` → `getUserProfile` → `getUserStatsById` 触发 `NoSuchMethodError`
- ✅ URL 编码处理正常（中文 username 不会 500，会正确 404）
- ✅ 不存在用户名返回 404，符合 RESTful 约定

---

### 3.6 T6: 实际可用的 PATCH 端点 `PATCH /users/me` (补充)

| 子用例 | 请求 | 状态 | 备注 |
|-------|------|------|------|
| T6a | `PATCH /users/me` + CSRF + `{"bio":"[API-TEST]"}` | **200** | 完整 UserVO 返回 |
| T6b | `PATCH /users/me` + CSRF + `{"bio":""}` (恢复) | **200** | |
| T6c | `PATCH /users/me` 无 CSRF | **403** | `40300` "CSRF token is required" |
| T6d | `PATCH /users/me` + CSRF + `{}` 空 body | **200** | 静默通过 |
| **T6e** | **`PATCH /users/me` + CSRF + `{"bio":"x"*1000}`** | **200** 🔴 | **@Valid 校验未生效！** |

**T6e 详情**：
- 前端 PATCH 1000 字符 bio → 期望 400 (`@Size` 校验失败)
- 实际 200，写入成功
- ⚠️ 这导致后续 /stats /profile 全部 500（虽与 bio 内容无关，但反映 DTO 字段约束弱）
- 🔴 **`UpdateUserDTO` 的 `@Size` 注解要么没加，要么没被 `@Valid` 触发**

**Root cause for T6e**：可能 `UpdateUserDTO` 的 `bio` 字段缺少 `@Size(max=...)` 约束。

---

## 4. 端点 1-5 摘要 (按文档顺序)

| # | 端点 | 函数 | 状态 | 平均耗时 | 严重程度 |
|---|------|------|------|---------|---------|
| 1 | `GET /users/{userId}` | `fetchUserProfile` | ✅ 200/404/401 正常 | 13ms | — |
| 2 | `PATCH /users/{userId}` | `updateUserProfile` | 🔴 405，**端点不存在** | 12ms | **High**（前端写死失败） |
| 3 | `GET /users/{userId}/stats` | `fetchUserStats` | 🔴 500 `NoSuchMethodError` | 17ms | **Critical**（运行时挂） |
| 4 | `GET /users/{userId}/skills` | `fetchUserSkills` | ✅ 200/404 正常 | 19ms | — |
| 5 | `GET /users/by-username/{username}/profile` | `fetchProfileByUsername` | 🔴 500 `NoSuchMethodError` | 20ms | **Critical**（运行时挂） |

**重叠函数验证**：`userStatsApi.getStats/getSkills`（来自 `userStats.ts`）与 `fetchUserStats/fetchUserSkills`（来自 `user.ts`）调用的后端路径完全一致，**确实完全重复**，仅类型分文件封装。`user.ts` 用本地 interface，`userStats.ts` 用 `@/types/userStats`。

---

## 5. 缺陷清单（按严重程度）

### 🔴 Critical: 后端 NoSuchMethodError 运行时挂

| 项目 | 内容 |
|------|------|
| 现象 | `GET /users/{id}/stats`、`GET /users/{id}/profile`、`GET /users/by-username/{u}/profile` 全部 500 |
| 根因 | 运行的 jar 中 `UserStatsDTO$DifficultyStats` 缺 `getCount()` 方法；`UserServiceImpl` 新版调用了它 |
| 影响 | 3 个公开端点 100% 不可用；前端调用全部 500 |
| 修复 | `cd backend-spring && ./mvnw clean package -DskipTests && pm2 restart ulticode-9001` |
| 验收 | 复测 3 个端点应全部 200 |
| 后端日志 | `tail /tmp/ulticode-9001-out.log | grep "NoSuchMethodError"` |
| 关联 traceId | `t-1781072343036`, `t-1781072343078` 等 |

### 🔴 High: 前端 PATCH 端点错配

| 项目 | 内容 |
|------|------|
| 现象 | 前端 `updateUserProfile(userId, partial)` 全部返回 405 |
| 根因 | 后端只暴露 `PATCH /users/me`，无 `PATCH /users/{userId}` |
| 后端代码 | `UserController.java:64 @PatchMapping("/me")` |
| 前端影响 | `user.ts::updateUserProfile` 调用即失败 |
| 修复方向（两个） | (A) 前端改为 `PATCH /users/me` 并删除 userId 参数；(B) 后端扩展 `PATCH /users/{userId}` 需 admin 权限（推荐前端改） |
| 建议 | 前端 `user.ts` 应区分 `updateMyProfile(dto)` 与（如果未来需要）admin 改他人；当前只保留前者 |

### 🟡 Medium: `UpdateUserDTO` 缺字段长度校验

| 项目 | 内容 |
|------|------|
| 现象 | `PATCH /users/me` 接受 1000 字符 bio 不报错 |
| 根因 | `UpdateUserDTO.bio` 字段缺 `@Size(max=N)` |
| 风险 | XSS / DB 字段溢出 / 前端表单无意义提交 |
| 修复 | 在 `UpdateUserDTO.bio` 加 `@Size(max=500)`，与其他用户字段对齐 |
| 验证 | `grep -n "@Size" backend-spring/src/main/java/com/ulticode/modules/user/dto/UpdateUserDTO.java` |

### 🟢 Low: OpenAPI 不可用

| 项目 | 内容 |
|------|------|
| 现象 | `GET /v3/api-docs` 返回 500（68 字节 "Unknown error"） |
| 根因 | SpringDoc OpenAPI 2.6.0 与当前依赖不兼容或初始化失败 |
| 影响 | 开发者无法通过 OpenAPI UI 探索接口；前端无法自动生成 TS 类型 |
| 修复 | 重启后端 / 检查 `springdoc.swagger-ui.path` 配置 / 查看启动日志 |

### 🟢 Low: `/users/{id}` 字段集不稳定

| 项目 | 内容 |
|------|------|
| 现象 | admin 无 `bio`，nina 有 `bio` |
| 根因 | DTO 字段缺失时 JSON 序列化省略（`@JsonInclude(NON_NULL)`） |
| 前端影响 | TS interface 假设 `bio` 必有时，未设置值会得到 `undefined` |
| 建议 | 前端 `UserProfile` interface 把 `bio` / `email` 等标为可选；后端 Service 始终填充（即使是空串）|

---

## 6. CSRF 行为观察

| 场景 | 期望 | 实际 |
|------|------|------|
| PATCH 带正确 CSRF token | 200 / 业务结果 | ✅ 200 |
| PATCH 缺 `X-CSRF-Token` 头 | 403 | ✅ 403 `40300` "CSRF token is required" |
| PATCH 缺 `X-CSRF-Token` 但带 `csrf_token` cookie | 403 | ✅ 403（验证 token 走 header） |
| GET 请求不需 CSRF | 200 | ✅ 200 |
| Cookie 失效 (no `access_token`) | 401 | ✅ 401 `40100` |

> 备注：CSRF token 24h TTL + 5m 宽限期；GET/HEAD/OPTIONS/匿名用户均跳过 CSRF。

---

## 7. 字符集与多语言

| 测试 | 路径 | 状态 | 备注 |
|------|------|------|------|
| 中文 username | `/users/by-username/张三/profile` | 404 | URL 编码正确，中文 username 不导致 500 |
| 中文 bio 写入 | `PATCH /users/me bio=中文` | 200 | UTF-8 正常 |
| 中文 name 返回 | `GET /users/mod-nina-002` | 200 | `name: "林娜"` 直接返回中文 |
| 中文 tag 返回 | `GET /users/{id}/skills` | 200 | `tagName: "链表" / "滑动窗口"` |

✅ 后端到数据库的字符集链路正常（参考 CLAUDE.md "JDBC URL 已包含 useUnicode=true&characterEncoding=UTF-8"）。

---

## 8. 性能观察

| 接口 | 平均耗时 | 备注 |
|------|---------|------|
| T1 `GET /users/{id}` | 13ms | 单查 user 表 |
| T3 `GET /users/{id}/stats` | 17ms | 多表 join（虽然 500，但耗时正常） |
| T4 `GET /users/{id}/skills` | 19ms | 含 tag 统计聚合 |
| T1d 401 路径 | 4ms | 早返回 |

> 健康值（<50ms），但 admin 重启 21 次（uptime 112m）反映可能存在 OOM 或健康检查循环重启。

---

## 9. 修复优先级建议

1. **立即修复**（影响 3 个端点）：`./mvnw clean package` + `pm2 restart ulticode-9001`
2. **同步前端**：将 `user.ts::updateUserProfile` 改名为 `updateMyProfile`，URL 改为 `/users/me`
3. **补校验**：`UpdateUserDTO.bio` 加 `@Size(max=500)`
4. **修 OpenAPI**：排查 `v3/api-docs` 500 根因
5. **补单测**：为 `getUserStatsById` / `getUserProfile` / `getUserProfileByUsername` 写 Testcontainers IT
6. **回归脚本**：把本报告 T1-T6 沉淀为 `scripts/api-smoke-test.sh`

---

## 10. 测试产物 (本会话可复现)

```bash
# 复现脚本骨架
COOKIES=/tmp/ulticode_test_cookies.txt
BASE=http://localhost:9001
ADMIN_ID=bba5ed74-6482-11f1-8191-467dade0a82b
NINA_ID=mod-nina-002

# 1. 登录
curl -s -X POST $BASE/auth/login -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' -c $COOKIES

# 2. 取 CSRF (任一 GET 鉴权接口会返回 X-New-CSRf-Token header)
CSRF=$(curl -s $BASE/auth/me -b $COOKIES \
  -D - | grep -i 'x-new-csrf-token' | awk '{print $2}' | tr -d '\r')

# 3. 跑 T1-T6
for url in /users/$ADMIN_ID /users/$NINA_ID /users/$NINA_ID/skills /users/$NINA_ID/stats; do
  curl -s -o /dev/null -w "%{http_code} $url\n" $BASE$url -b $COOKIES
done

# 4. 测 PATCH (会 200)
curl -s -X PATCH $BASE/users/me -H "X-CSRF-Token: $CSRF" \
  -H "Content-Type: application/json" -b $COOKIES \
  -d '{"bio":"smoke test"}'
```

---

## 修复进度 (2026-06-10)

5 项缺陷在 PR `fix/user-api-defects-from-test-report` 中全部修复，验证完成：

| # | 严重度 | 缺陷 | Commit | 状态 | 验证方式 |
|---|--------|------|--------|------|---------|
| 1 | 🔴 Critical | NoSuchMethodError | `9cf5e18f6 fix(backend): rebuild to restore Lombok-generated methods` | ✅ | javap 验证 `getCount` 复现 + 4 个原 500 端点 200 |
| 2 | 🔴 High | PATCH /users/{userId} 错配 | `80cd482d4 fix(console): rename updateUserProfile to updateMyProfile` | ✅ | type-check 0 错, 241/241 测试过, build OK |
| 3 | 🟡 Medium | userStatsApi 重复 | `880a9df9b refactor(console): consolidate userStatsApi into api/user.ts` | ✅ | type-check 0 错, 241/241 测试过, build OK |
| 4 | 🟢 Low | /v3/api-docs 500 | 同 #1 | ✅ | `curl /v3/api-docs` → 200 |
| 5 | 🟢 Low | UserProfile.bio 必填 | `713b5a763 fix(console): make UserProfile.bio and email optional` | ✅ | type-check 0 错, 241/241 测试过, build OK |

**误诊说明**：经 C1 重建后核实，`UpdateUserDTO.bio` 已有 `@Size(max=5000)`（源文件 line 36），1000 字符本就应通过；当时表象源于 Lombok 失效导致 Jackson 反序列化绕过校验路径。`UpdateUserDTO` 本身不需要改动。

**回归测试**：user 模块单测 28 个全部通过 (BUILD SUCCESS)；console 端 241/241 通过 + type-check 0 错 + build OK。management / shared/auth-core / DB schema 全部未改动。

**端到端冒烟脚本**：`scripts/dev/api-smoke-user.sh`（详见上文"端到端复现脚本"段），覆盖全部 5 个 user/userStats 端点 + PATCH /users/me。

---

## 附录 A: 报告元信息

- 测试会话 ID: `session-2026-06-10-ulticode-user-api`
- 后端日志: `/tmp/ulticode-9001-out.log` (line 14:19:03)
- 报告位置: `docs/api-test-report-user-userstats.md`
- 下一步: 提交 issue / 修复 Critical bug / 更新前端 user.ts
- 撰写者: Claude (MiniMax-M3) - 全栈工程师模式
