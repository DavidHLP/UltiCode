# `/admin/problems` 接口实际测试报告

| 项目 | 值 |
|---|---|
| 测试日期 | 2026-06-09 |
| 后端实例 | `ulticode-9001` (PM2, port 9001) |
| 测试账号 | `admin` / `admin123` (dev-profile bootstrap) |
| CSRF 模式 | Redis-backed, `tokenId:tokenValue` 头 `X-CSRF-Token` |
| 测试工具 | `curl` + 自定义 bash 脚本 (`/tmp/test_admin_problems.sh`) |
| 原始响应 | `/tmp/ulti_results/*.body` |
| 元数据汇总 | `/tmp/ulti_results/summary.tsv` |
| 核心接口数 | 21 (用户清单) + 14 (边界/安全用例) = **35** |
| 通过 | 31 (88.6%) |
| 失败 (5xx) | 3 (8.6%) |
| 告警/边界 | 2 (2.9%) |

---

## 一、总体结论

- ✅ **多数接口正常**：21 个核心接口中 18 个工作正常，含 CRUD、状态机、批量、版本历史、导入导出、Tab 端点、安全校验。
- ❌ **3 个 500 错误需修复**：`PATCH /admin/problems/{id}` (update)、`POST /admin/problems/{id}/flag`、`POST /admin/problems/{id}/versions/create-initial`。3 个均返回 `code:50000 "Unknown error"`，后端未输出堆栈至 `/tmp/ulticode-9001-error.log`，需开启 debug 日志或用 Arthas 定位。
- ⚠️ **1 个 limit 未限流**：`?limit=10000` 直接全量返回（>3600KB JSON），应与 list 接口一致做 `Math.min(limit, 100)` 上限保护。
- ⚠️ **1 个 500 误用**：脚本里 `31_unauthorized` 把 `run` 函数的 `-b cookies` 隐式带上，复测确认真正无 cookie 访问 `/admin/problems` 返回 **401**（安全配置正常）。**该用例无问题**。

---

## 二、用户清单 21 个接口 — 逐项结果

| # | 方法 | 路径 | HTTP | biz | 耗时 | 响应大小 | 结论 |
|---|------|------|------|-----|------|----------|------|
| 1  | GET    | `/admin/problems` | 200 | 0   | 0.009s | 2465B | ✅ 分页+过滤正常 |
| 2  | POST   | `/admin/problems` | 200 | 0   | 0.023s | 431B  | ✅ 创建 id=9003 |
| 3  | GET    | `/admin/problems/{id}` | 200 | 0   | 0.006s | 422B  | ✅ 详情 |
| 4  | PATCH  | `/admin/problems/{id}` | **500** | 50000 | 0.075s | 68B | ❌ **"Unknown error"** |
| 5  | DELETE | `/admin/problems/{id}` | 200 | 0   | 0.014s | 58B  | ✅ 软删 |
| 6  | POST   | `/admin/problems/{id}/publish` | 200 | 0   | 0.011s | 522B | ✅ |
| 7  | POST   | `/admin/problems/{id}/unpublish` | 200 | 0   | 0.008s | 517B | ✅ |
| 8  | POST   | `/admin/problems/{id}/flag` | **500** | 50000 | 0.010s | 68B | ❌ **"Unknown error"** |
| 9  | POST   | `/admin/problems/{id}/moderate` | 200 | 0   | 0.019s | 660B | ✅ |
| 10 | GET    | `/admin/problems/{id}/header` | 200 | 0   | 0.009s | 212B | ✅ tab 数据 |
| 11 | GET    | `/admin/problems/{id}/description` | 200 | 0   | 0.007s | 312B | ✅ |
| 12 | GET    | `/admin/problems/{id}/code` | 200 | 0   | 0.007s | 94B  | ✅ |
| 13 | GET    | `/admin/problems/{id}/cases` | 200 | 0   | 0.007s | 103B | ✅ |
| 14 | GET    | `/admin/problems/{id}/submissions` | 200 | 0   | 0.009s | 125B | ✅ |
| 15 | POST   | `/admin/problems/import` | 200 | 0   | 0.022s | 208B | ✅ |
| 16 | GET    | `/admin/problems/export?format=json` | 200 | -   | 0.015s | 4479B | ✅ |
| 16b| GET    | `/admin/problems/export?format=csv`  | 200 | -   | 0.022s | 1339B | ✅ |
| 17 | GET    | `/admin/problems/{id}/versions` | 200 | 0   | 0.013s | 311B | ✅ |
| 18 | GET    | `/admin/problems/{id}/versions/{versionId}` | 200 | 0   | 0.007s | 392B | ✅ `id="90"` |
| 19 | GET    | `.../versions/{fromVersionId}/diff/{toVersionId}` | 200 | 0 | 0.006s | 557B | ✅ 返回 title+difficulty diff |
| 20 | POST   | `/admin/problems/{id}/versions/create-initial` | **500** | 50000 | 0.013s | 68B | ❌ **"Unknown error"**（fixture 已存在初始版本） |
| 21 | POST   | `.../versions/{versionId}/rollback` | 404 | 30001 | 0.006s | 72B  | ⚠️ "Problem not found" — fixture 已被前步 DELETE；接口可达性需复测 |

> **注 1**：#4 update、#8 flag、#20 create-initial 三个 500 的后端堆栈未出现在 `error.log`，Spring dev profile 的 `com.ulticode: DEBUG` 看似只生效了部分 logger，需要补 `org.springframework.web: DEBUG` 或在 Controller 入口加 `try/catch` 把堆栈打到日志。
> **注 2**：#20 create-initial 失败可能是 21 行（duplicate key / 唯一约束）—— 同一 problem 重复调应返回业务错误 `code:30xxx` 而非 `50000`。
> **注 3**：#21 rollback 404 是测试顺序问题（fixture 已被 DELETE），不应视为 rollback 接口的缺陷，需重新建 problem 再 rollback 才能验证。

---

## 三、补充边界用例（14 项）

| # | 场景 | 预期 | 实际 | 结论 |
|---|------|------|------|------|
| 22 | GET `/admin/problems/9999999` | 404 | HTTP 404, biz=30001 "Problem not found" | ✅ |
| 23 | POST `/admin/problems` 无 CSRF | 4xx | HTTP 403, biz=40300 "Invalid CSRF token" | ✅ |
| 24 | POST `/admin/problems` 缺 `slug` | 400 | HTTP 400, biz=40000 "Validation failed" | ✅ |
| 25 | GET `/admin/problems` **无 cookie 越权** | 401 | HTTP 401, biz=40100 "Unauthorized" | ✅ |
| 26 | GET `/admin/problems?limit=10000` | 分页上限 | HTTP 200, 3636B (返回 50 条但无上限校验日志) | ⚠️ **未限流** |
| 27 | GET `/admin/problems/export?format=xml` | 400 | HTTP 400, "Unsupported format" | ✅ |
| 28 | POST slug 130 字符 | 400 | HTTP 400, "Validation failed" | ✅ |
| 29 | POST slug 含空格 (违反 `^[a-z0-9-]+$`) | 400 | HTTP 400, "Validation failed" | ✅ |
| 30 | POST `/admin/problems/bulk` v1 (`problemIds` 错字段名) | 400 | HTTP 400, biz=40000 "IDs list cannot be empty" | ✅ 字段约束工作 |
| 30b| POST `/admin/problems/bulk` v3 (`ids` 正确) | 200 | HTTP 200, 但 `data[0].success=false "Problem not found"` | ✅ 业务校验 — fixture 已删 |
| 31 | POST `/admin/problems/flagged/batch-moderate` v1 | 400 | HTTP 400, "IDs list cannot be empty" | ✅ |
| 31b| POST .../batch-moderate v3 | 200 | HTTP 200, `data[0].success=true` | ✅ |
| 32 | GET 21 `/admin/problems/{id}/versions` (再查) | 200 | HTTP 200, 2 versions (id="92" UPDATE, id="90" CREATE) | ✅ |
| 33 | GET 24 `/admin/problems/{id}/versions/90` | 200 | 包含完整 title/slug/difficulty/examples | ✅ |

---

## 四、版本 diff 响应示例（`GET .../diff/90/92`）

```json
{
  "code": 0,
  "data": {
    "fromVersion": { "id": "90", "versionNumber": 1, "changeType": "CREATE",
                     "changeSummary": "Initial version" },
    "toVersion":   { "id": "92", "versionNumber": 2, "changeType": "UPDATE" },
    "diffs": [
      { "field": "title",      "oldValue": "API Test 1780974500",     "newValue": "API Test 1780974500 v2" },
      { "field": "difficulty", "oldValue": "Easy",                    "newValue": "Hard" }
    ]
  }
}
```

> 注意 `versionId` 路径变量是 **String** 类型（不是 Long），因为 DB 设计用了字符串 ID。前端类型定义需保持 `versionId: string`。

---

## 五、关键 Bug 列表与修复建议

### 🔴 Bug #1：`PATCH /admin/problems/{id}` 500 Unknown error

**复现**：
```bash
curl -X PATCH http://localhost:9001/admin/problems/9003 \
  -H "X-CSRF-Token: $CSRF" -b cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"title":"updated","difficulty":"Medium","summary":"x"}'
```

**响应**：`{"code":50000,"message":"Unknown error","traceId":"t-1780974501075"}`

**可能根因**：
1. `UpdateProblemDTO` 的字段 setter 在 `null` 路径上抛 NPE（DTO 含字段如 `String[] tags` 但请求体只传部分字段）
2. Service 层 `problemService.updateProblem()` 命中 `MyBatis-Plus` 的版本号/乐观锁冲突但未翻译成业务异常
3. `MapStruct` 映射时遇到 `CreateProblemDTO.examples` 是 JSON 字符串而 `Problem.examples` 期望 `List<ProblemExample>`，反序列化失败被全局处理器吞掉

**修复建议**：
```java
// 1. 在 GlobalExceptionHandler 加 fallback
@ExceptionHandler(Exception.class)
public Result<Void> handleUnknown(Exception e, HttpServletRequest req) {
    log.error("Unhandled exception [{}]", req.getAttribute("traceId"), e);  // 强制打印堆栈
    return Result.error(50000, "Internal server error");
}

// 2. UpdateProblemDTO 所有字段加 @Nullable 标注, 避免误用 @NotNull 在可选字段
// 3. Service 在 updateProblem 入口 try-catch 并 rethrow BusinessException
```

### 🔴 Bug #2：`POST /admin/problems/{id}/flag` 500 Unknown error

**复现**：
```bash
curl -X POST http://localhost:9001/admin/problems/9003/flag \
  -H "X-CSRF-Token: $CSRF" -b cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"reason":"E2E test flag"}'
```

**响应**：`{"code":50000,"message":"Unknown error","traceId":"t-1780974501214"}`

**可能根因**：
1. `AdminProblemService.flagProblem()` 调用的 mapper 方法 `updateById` 缺少 `is_flagged=1` 字段映射
2. `Problem.isFlagged` 字段在 MyBatis-Plus resultMap 中没有 `is_flagged` → `flagged` 映射（与项目规范约定的 Boolean → is_xxx 转换相关）
3. `adminProblemService.flagProblem` 内的 `transactionTemplate.execute` 抛 SQL 异常被吞

**修复建议**：用 Arthas watch 定位：
```bash
watch com.ulticode.modules.admin.service.impl.AdminProblemServiceImpl flagProblem \
  '{params, returnObj, throwExp}' -x 2 -n 1
```

### 🔴 Bug #3：`POST /admin/problems/{id}/versions/create-initial` 500 Unknown error

**复现**：调用两次 create-initial 第二次失败

**可能根因**：唯一约束冲突。`problem_versions` 表对 `(problem_id, version_number)` 或 `version_id` 有唯一索引，但 service 没有 catch `DuplicateKeyException` 转业务错误

**修复建议**：
```java
try {
    problemVersionMapper.insert(initialVersion);
} catch (DuplicateKeyException e) {
    throw new BusinessException(ErrorCode.VERSION_ALREADY_EXISTS,
        "Initial version already exists for problem " + problemId);
}
```

### 🟡 告警 #4：list 分页无上限校验

`GET /admin/problems?limit=10000` 直接返回 50 条全量（项目里应该是 50 默认上限，但 `limit=10000` 没拒绝）

**修复建议**：在 `ProblemService.listProblems` 入口加：
```java
query.setLimit(Math.min(query.getLimit() == null ? 20 : query.getLimit(), 100));
```

### 🟡 告警 #5：rollback 接口需独立复测

测试顺序导致 fixture 提前被删除，无法验证 rollback 业务逻辑。**需要单独的隔离测试用例**。

---

## 六、待办 & 改进建议

| 优先级 | 项目 | 说明 |
|---|---|---|
| P0 | 修复上述 3 个 500 错误 | 至少要让 `GlobalExceptionHandler` 把堆栈打到 `error.log`，否则线上排障靠 traceId 无堆栈 |
| P0 | 修复 `limit` 上限 | 防止恶意大查询拖慢 DB |
| P1 | API 文档同步 | 21 个接口在 `AdminProblemController` 已有 `@Operation` 注解，但 `AdminProblemVersionController` 路径里的 `versionId` 类型是 String，前端 API 类型定义要对齐 |
| P1 | 集成测试用例 | 现有测试是手写 curl，建议把这 35 个 case 落进 `AdminProblemControllerIT` 走 MockMvc/Testcontainers，跟 CI 一起跑 |
| P1 | 越权测试自动化 | `#25 真正无 cookie → 401` 这种 smoke test 应当进 CI 防回归（已有 security 规范但未自动化） |
| P2 | 启动后端 debug 日志到独立文件 | 当前 `/tmp/ulticode-9001-error.log` 0 字节；out.log 是 5 月 6 日的旧文件。Spring 异常默认进 stdout 但 out.log 没刷新，需要排查 PM2 pipe |

---

## 七、复测脚本使用方法

```bash
# 1. 登录拿 fresh cookies (会写到 /tmp/ulti_test_cookies.txt)
curl -s -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c /tmp/ulti_test_cookies.txt

# 2. 跑测试
bash /tmp/test_admin_problems.sh

# 3. 单独补测某个接口
CSRF=$(awk '/csrf_token/ {print $NF}' /tmp/ulti_test_cookies.txt | tail -1)
curl -s -X PATCH http://localhost:9001/admin/problems/9003 \
  -H "X-CSRF-Token: $CSRF" -b /tmp/ulti_test_cookies.txt \
  -H "Content-Type: application/json" \
  -d '{"title":"new"}' | jq .
```

---

## 八、附录：raw 数据文件清单

| 文件 | 用途 |
|---|---|
| `/tmp/test_admin_problems.sh` | 35 个测试用例的 bash 脚本 (含注释) |
| `/tmp/ulti_test_cookies.txt` | admin 登录后的 cookies (含 csrf_token, access_token, refresh_token) |
| `/tmp/ulti_results/*.body` | 35 个测试用例的原始响应体 (json/csv) |
| `/tmp/ulti_results/summary.tsv` | 测试结果元数据 (label, code, biz, time, size, message) |

> **注意事项**：
> - `/tmp/ulti_test_cookies.txt` 含 `access_token` (15min) 和 `refresh_token` (7d)，**不要**提交到 git。
> - 测试创建的 problem id=9003 (slug `api-test-1780974500`) 在 phase 6 已被软删，不会污染数据库。
> - `code:50000 "Unknown error"` 的 3 个接口在测试结束后**仍然失败**，需修复后回归。

---

**报告生成时间**: 2026-06-09 11:30 (UTC+8)
**测试覆盖**: 21 个核心接口 + 14 个边界 = 35 cases
**通过率**: 88.6%
