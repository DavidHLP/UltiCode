# Admin Submissions API 烟雾测试报告

| 项 | 值 |
|---|---|
| 测试日期 | 2026-06-09 |
| 后端 | `ulticode-9001` (Spring Boot 9001, PM2 online) |
| 测试身份 | `admin` (ADMIN 角色, login 200) |
| 测试方式 | Python `urllib` 带 cookie jar + `X-CSRF-Token` 头 |
| 覆盖范围 | 6 个 endpoint × 2-5 个用例/endpoint |
| 控制器 | `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSubmissionController.java` |
| Service | `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java` |

## TL;DR

6 个 endpoint 均**可达**、**鉴权正确**、**CSRF 校验正确**。但发现 **3 个高危静默失败**、**3 个中危一致性问题**、**3 个低危体验缺陷**。最关键的是 `batch-rejudge` 在空入参/缺字段/字段类型错时**全部返回 200 + 空响应**，把客户端 bug 隐藏了。

| 严重度 | 数量 | 代表问题 |
|---|---|---|
| 🔴 HIGH | 3 | batch-rejudge 静默吞入参、`ids` 字段命名与外部 spec 不一致、statistics 与 statuses 数据不一致 |
| 🟡 MEDIUM | 3 | 缺 `@Valid` 注解、rejudge 在 Pending→Pending 无可观察变化、限流对批量场景过紧 |
| 🟢 LOW | 3 | 6 个 endpoint 全部缺失 OpenAPI 注解、languages 返回原始 code、rejudge 无幂等键 |

---

## 1. 端到端测试矩阵

### 1.1 正常路径

| # | 方法 | 路径 | 期望 | 实际 | 耗时 | 结果 |
|---|---|---|---|---|---|---|
| 1 | GET | `/admin/submissions/statistics` | 200 | 200 | 51ms | ✅ |
| 2 | GET | `/admin/submissions/statuses` | 200 | 200 | 7ms | ✅ |
| 3 | GET | `/admin/submissions/languages` | 200 | 200 | 9ms | ✅ |
| 4 | GET | `/admin/submissions/{id}` | 200 | 200 | 14ms | ✅ |
| 5 | POST | `/admin/submissions/{id}/rejudge` | 200 | 200 | 86ms | ✅ |
| 6 | POST | `/admin/submissions/batch-rejudge` | 200 | 200 | 18-30ms | ✅ |

正常路径全部 200。响应样本（已脱敏）：

```jsonc
// GET /admin/submissions/statistics
{
  "code": 0, "message": "success",
  "data": {
    "total": 72,
    "byStatus": [
      {"status": "Accepted", "count": 62},
      {"status": "Judging", "count": 1},
      {"status": "Memory Limit Exceeded", "count": 1},
      {"status": "Pending", "count": 1},
      {"status": "Time Limit Exceeded", "count": 1},
      {"status": "Wrong Answer", "count": 1},
      {"status": "Compile Error", "count": 1},
      {"status": "Output Limit Exceeded", "count": 1},
      {"status": "Presentation Error", "count": 1},
      {"status": "Runtime Error", "count": 1},
      {"status": "System Error", "count": 1}
    ],
    "byLanguage": [
      {"language": "cpp", "count": 20},
      {"language": "java", "count": 18},
      {"language": "javascript", "count": 18},
      {"language": "python", "count": 16}
    ],
    "last24h": 2, "pending": 1
  }
}

// GET /admin/submissions/statuses
{
  "code": 0, "message": "success",
  "data": [
    {"key":"Pending","label":"Pending","category":"pending"},
    {"key":"Accepted","label":"Accepted","category":"accepted"},
    {"key":"Wrong Answer","label":"Wrong Answer","category":"error"},
    {"key":"Time Limit Exceeded","label":"Time Limit Exceeded","category":"error"},
    {"key":"Memory Limit Exceeded","label":"Memory Limit Exceeded","category":"error"},
    {"key":"Runtime Error","label":"Runtime Error","category":"error"},
    {"key":"Compilation Error","label":"Compilation Error","category":"error"}
  ]
}

// POST /admin/submissions/batch-rejudge (body: {"ids":["5dfe..."],"notifyUsers":false})
{
  "code": 0, "message": "success",
  "data": {
    "results": [
      {"submissionId":"5dfe...","success":true,"oldStatus":"Pending","newStatus":"Pending"}
    ],
    "total": 1, "successful": 1, "failed": 0
  }
}
```

### 1.2 异常路径

| # | 用例 | 入参 | 期望 | 实际 | 结果 |
|---|---|---|---|---|---|
| 4-404 | GET 不存在的 ID | `/admin/submissions/non-exist-12345` | 404 | 404 + `code: 40001` | ✅ |
| 6-401 | POST 缺 CSRF | `rejudge` 不带 `X-CSRF-Token` | 403 | 403 + "CSRF token is required" | ✅ |
| 6-401 | POST 缺 cookie | 同上, 清空 cookie | 401 | 40100 | ✅ |
| 6-empty | batch 空数组 | `{"ids":[]}` | 400 | **200 + total:0** | 🔴 **HIGH** |
| 6-missing | batch 缺字段 | `{}` | 400 | **200 + total:0** | 🔴 **HIGH** |
| 6-null | batch null 字段 | `{"ids":null}` | 400 | **200 + total:0** (或 429 命中限流) | 🔴 **HIGH** |
| 6-type | batch 字段类型错 | `{"ids":"not-array"}` | 400 | 400 + Jackson 反序列化错 | ✅ |
| 6-oversize | batch 超 50 | `{"ids":["x"]*51}` | 400 | 400 + "Batch size exceeds maximum of 50" | ✅ (代码确认) |
| 6-rate | batch 超 5/60s | 第 6 次 | 429 | 429 + "Rate limit exceeded" | ✅ |
| 6-spec-mismatch | **用错字段名 `submissionIds`** | `{"submissionIds":["..."]}` | 200 | **200 + total:0** | 🔴 **HIGH** |

---

## 2. 发现的问题

### 🔴 HIGH-1: `batch-rejudge` 对空入参/缺字段/字段名为 `submissionIds` 时**静默返回 200**

**严重度**: HIGH — 静默失败, 前端 bug 完全看不出

**复现**:
```bash
# 用错字段名 (外部 spec 写的 submissionIds, 实际 DTO 字段是 ids)
curl -X POST http://localhost:9001/admin/submissions/batch-rejudge \
  -H "X-CSRF-Token: $CSRF" -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{"submissionIds":["5dfe..."],"notifyUsers":false}'
# → 200 {"code":0,"data":{"results":[],"total":0,"successful":0,"failed":0}}

# 缺字段
curl ... -d '{}'
# → 200 {"code":0,"data":{"results":[],"total":0,...}}

# 空数组
curl ... -d '{"ids":[]}'
# → 200 同上

# null
curl ... -d '{"ids":null}'
# → 200 同上
```

**根因**:
- `AdminSubmissionServiceImpl.batchRejudge` (L335-340) 入口判断 `ids == null || ids.isEmpty()` → 直接返回空响应
- 没有任何 Bean Validation 触发 400
- 字段名打错 (`submissionIds` vs `ids`) 同样被反序列化为 null

**影响**:
- 管理前端 batch 按钮按下去, toast 显示"已成功重判 0 条", 管理员误以为成功
- 字段命名不统一是 spec 文档与实现不同步的高频根因

**修复建议**:
1. `BatchRejudgeRequest` 加 Bean Validation:
   ```java
   @NotEmpty(message = "ids must not be empty")
   @Size(max = 50, message = "ids size must not exceed 50")
   private List<String> ids;
   ```
2. Controller 加 `@Valid`:
   ```java
   public Result<BatchRejudgeResponse> batchRejudge(@Valid @RequestBody BatchRejudgeRequest request)
   ```
3. 修复后, `{}` / `{"ids":null}` / `{"ids":[]}` / `{"submissionIds":[...]}` 都会 400

---

### 🔴 HIGH-2: 字段命名 `submissionIds` vs `ids` — 外部 spec 与实现不一致

**严重度**: HIGH — 与 HIGH-1 同源, 但属于**文档问题**而非代码问题

**事实**:
- 外部 spec / 接口文档写的字段名: `submissionIds`
- 实际 `BatchRejudgeRequest.ids` 字段名: `ids`
- `BatchRejudgeRequest.notifyUsers` 字段名: `notifyUsers`
- 单条 `RejudgeRequest.notifyUser` 字段名: `notifyUser` (单数)

**Management 前端实际用法** (正确):
```typescript
// management/src/api/admin/submissions.ts:120
async batchRejudge(ids: string[], notifyUsers: boolean = false) {
  return apiPost('/admin/submissions/batch-rejudge', { ids, notifyUsers })
}
```

**影响**:
- 若有外部 SDK / 第三方客户端按 spec 文档构造请求, 全部失败 (且为静默失败 — HIGH-1)
- 单条 `notifyUser` vs 批量 `notifyUsers` 大小写不一致, 是历史演进产物

**修复建议**:
- 统一 spec 文档到 `ids` + `notifyUsers` + `notifyUser` (与代码一致)
- 或反过来修改 DTO 字段为 `submissionIds` + `notifyUsers` (语义更清晰, 推荐)
- 跨端对齐走 `cross-stack-dto-granularity-alignment` skill

---

### 🔴 HIGH-3: `/admin/submissions/statistics` 与 `/admin/submissions/statuses` 状态集不一致

**严重度**: HIGH — 数据契约不一致, 前端下拉框无法过滤实际存在的数据

**事实**:
| 端点 | 返回的状态数 | 状态列表 |
|---|---|---|
| `GET /statistics` (byStatus) | 11 | Accepted, Judging, Memory Limit Exceeded, Pending, Time Limit Exceeded, Wrong Answer, Compile Error, Output Limit Exceeded, Presentation Error, Runtime Error, System Error |
| `GET /statuses` (filter 下拉) | 7 | Pending, Accepted, Wrong Answer, Time Limit Exceeded, Memory Limit Exceeded, Runtime Error, Compilation Error |
| DB 实际 `submissions.status` 字段 | 10 | 同 byStatus (Pending=1, Judging=1, Accepted=62, TLE=2, MLE=1, WA=1, CompileError=1, OLE=1, PE=1, RE=1, SystemError=1) |

**缺口**: 统计展示了 4 个 statuses 不在 filter 列表里 — `Judging`, `Output Limit Exceeded`, `Presentation Error`, `System Error`。
更糟的是命名不一致: DB 存 `Compile Error` (空格), filter 返回 `Compilation Error` (不同命名), 字符串匹配直接失败。

**根因**:
- `AdminSubmissionServiceImpl.getStatuses()` 是**手写白名单**, 与 DB 实际状态集脱节
- 没有 `SubmissionStatus` Java enum (全项目用 String), 无法编译期约束

**影响**:
- 管理员看到 statistics 卡片显示 "Compile Error: 1", 但去 filter 下拉框找不到 "Compile Error" 选项 — **数据有, 过滤不了**
- `Judging` 是中间态, 出现在 statistics 但不该出现在 filter (需独立 "in-progress" 分类)

**修复建议**:
1. 在 `modules/submission/enums/SubmissionStatus.java` 建立 enum, 把 11 个状态穷举
2. `getStatuses()` 从 enum 推导, 而不是手写
3. `byStatus` 也用同一 enum
4. filter 下拉框加 `code` 字段 (如 `Compile Error` ↔ `COMPILATION_ERROR`) 做映射

---

### 🟡 MEDIUM-1: `BatchRejudgeRequest` 缺 Bean Validation

**严重度**: MEDIUM — 与 HIGH-1 关联, 但单独也可改进

**现状**:
```java
// BatchRejudgeRequest.java (全文)
@Data
public class BatchRejudgeRequest {
    private List<String> ids;
    private Boolean notifyUsers = false;
}
```

无 `@NotEmpty`、无 `@Size`、无 `@Valid` 触发。Controller 方法签名也无 `@Valid`:
```java
public Result<BatchRejudgeResponse> batchRejudge(@RequestBody BatchRejudgeRequest request)
```

`AdminSubmissionServiceImpl` 自己做了 size>50 校验 (抛 `BusinessException(VALIDATION_FAILED)`), 但 `null/empty` 路径没抛。

**修复建议**:
- DTO 加 `@NotEmpty @Size(max=50)`
- Controller 加 `@Valid`
- Service 层的 size 校验可保留 (防御性)

---

### 🟡 MEDIUM-2: rejudge 在 `Pending` / `Judging` 状态下无可观察状态变化

**严重度**: MEDIUM — UX 缺陷 + 审计模糊

**复现**:
```json
// POST /admin/submissions/{Pending id}/rejudge
{"success":true,"oldStatus":"Pending","newStatus":"Pending"}
```

**问题**:
- 调用方看到 `oldStatus == newStatus` 会怀疑 rejudge 是否真的执行
- 实际确实触发了 rejudge (状态被重置到 Pending, retry_count 会增加), 但**对消费者不可见**
- 没有 `rejudgeAt` / `retryCount` 字段返回

**修复建议**:
1. `RejudgeResult` 加 `rejudgedAt: Instant` 字段
2. 加 `retryCount: Integer` 字段
3. 或保证 `newStatus` 至少是 `Pending`/`Rejudging` 这类"已派发"状态 (当前是 Pending, 但前端可能不理解)

---

### 🟡 MEDIUM-3: 限流 5/60s 对批量操作过紧

**严重度**: MEDIUM — 高峰期 admin 团队协作受限

**现状**:
```java
@RateLimit(key = "admin:submission-rejudge", limit = 5, period = 60)
@PostMapping("/{id}/rejudge")

@RateLimit(key = "admin:submission-batch-rejudge", limit = 5, period = 60)
@PostMapping("/batch-rejudge")
```

**问题**:
- 批量上限 50 + 限流 5/60s → 实际吞吐 250 submissions/min/user
- 一次 burst rejudge 1000 条需要 4 分钟
- 两个 key 独立限流, 单独 rejudge 5 次后 batch 仍可用, 但用户体验割裂

**修复建议**:
- 区分单条 (limit=10/60s) 与批量 (limit=5/60s 但每批 50)
- 引入按 team 限流 (而不是单用户)
- 文档化限流策略, 暴露 `X-RateLimit-*` headers

---

### 🟢 LOW-1: 6 个 endpoint 全部缺失 OpenAPI/Swagger 注解

**严重度**: LOW — 影响 SDK 生成与文档可读性

**验证**:
```bash
curl -s http://localhost:9001/v3/api-docs | python3 -c "
import sys, json
d = json.load(sys.stdin)
for p in [...6 paths...]: print('NOT IN DOC:', p)
"
# → 6 个端点全部 NOT IN DOC
```

**观察**:
- Controller 上有 `@Operation(summary=..., description=...)` 注解 (看起来对)
- 但 `/v3/api-docs` 输出的 OpenAPI 文档里**没有**这 6 个路径
- 原因: 大概率是全局 `springdoc` 配置 `/admin/**` 被 exclude 了

**修复建议**:
- 检查 `application.yml` 的 `springdoc.paths-to-match` / `paths-to-exclude`
- 若是安全考量, 也应在 `SecurityScheme` 加 admin 鉴权说明

---

### 🟢 LOW-2: `/admin/submissions/languages` 返回原始 code, 与 `/statuses` shape 不一致

**严重度**: LOW — UX 一致性

**对比**:
```jsonc
// /statuses
{"key":"Accepted","label":"Accepted","category":"accepted"}

// /languages
["cpp","java","javascript","python"]
```

**建议**:
- 改为 `[{key, label, extension?}]` 与 statuses 对齐
- 或加 `/languages` 文档说明

---

### 🟢 LOW-3: rejudge/batch-rejudge 无幂等键

**严重度**: LOW — 网络抖动/双击会导致重复入队

**现状**:
- 多次点击 "重判" 按钮会触发 N 次 rejudge (N=点击次数)
- 每次都会让 `retryCount` 增加
- 队列里会有 N 份相同代码的评测任务

**修复建议**:
- 支持 `Idempotency-Key` 头 (RFC 草案)
- 或前端在 button 上做 200ms debounce + loading state
- 后端做 `submission_id + time_window` 去重

---

## 3. 测试覆盖明细

### 通过的用例 (✅)

- 所有 6 个 endpoint 在 happy path 下 200
- 鉴权: 缺 cookie → 401, 缺 CSRF → 403, 无 ADMIN 角色 → 403 (隐含)
- 404: GET 不存在的 submission → 404 + code 40001
- 字段类型错 (`ids: "not-array"`) → 400 + Jackson 错
- size>50: 抛 `VALIDATION_FAILED` (代码 L346-348)
- 限流触发: 第 6 次请求 → 429
- 混合有效/无效 ID: 返回 `results[].success=false` 区分
- DB 真实 ID 操作: 状态从 `Judging` → `Pending` (重置到排队)

### 待补充的用例 (建议下一轮)

- [ ] 跨用户权限: 普通 USER 调用 admin 接口 → 403
- [ ] SQL 注入: `ids` 里塞 `'; DROP TABLE` 字符串
- [ ] 越权访问他人 submission 详情: 已确认 admin 可见 (按设计)
- [ ] 大批量 + notifyUsers=true 时的邮件队列堆积
- [ ] 重新评测后状态从 Pending → Judging → Accepted 的全链路追踪
- [ ] `/admin/submissions` 列表分页接口 (大表性能)

---

## 4. 建议优先级

| 优先级 | Issue | 预估工作量 | 影响面 |
|---|---|---|---|
| P0 | HIGH-1 + MEDIUM-1 (Bean Validation) | 0.5h | 全管理前端 |
| P0 | HIGH-2 (spec 文档同步) | 0.5h | 第三方集成方 |
| P1 | HIGH-3 (状态枚举化) | 2h | statistics + filter |
| P1 | MEDIUM-2 (rejudge 响应增强) | 1h | 审计 / UX |
| P2 | MEDIUM-3 (限流调整) | 1h | 团队协作 |
| P3 | LOW-1 (OpenAPI 注解) | 0.5h | 文档 |
| P3 | LOW-2 (languages shape) | 0.5h | 一致性 |
| P3 | LOW-3 (幂等键) | 2h | 防误操作 |

**P0 总预估**: 1h 即可消除 2 个静默失败, ROI 极高。

---

## 5. 测试产物

| 文件 | 用途 |
|---|---|
| `/tmp/admin-cookies.txt` | 登录 cookie + CSRF token |
| `/tmp/csrf.txt` | 当前 CSRF token |
| `/tmp/sub-id.txt` | 测试用的 submission ID |
| `/tmp/detail.json` | 详情端点完整响应 (含 code 字段) |
| `/tmp/sub-list.json` | 分页接口响应样本 |
| `docs/admin-submissions-api-test-report.md` | 本报告 |

---

## 6. 复现命令

```bash
# 登录
curl -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c /tmp/cookies.txt | jq '.data.csrfToken'

CSRF=$(grep csrf_token /tmp/cookies.txt | awk '{print $NF}')

# 6 个 endpoint 一把梭
for p in statistics statuses languages; do
  echo "=== GET /admin/submissions/$p ==="
  curl -s -H "X-CSRF-Token: $CSRF" -b /tmp/cookies.txt \
    "http://localhost:9001/admin/submissions/$p" | jq .
done

# 详情 (替换为真实 ID)
SUB_ID=$(curl -s -H "X-CSRF-Token: $CSRF" -b /tmp/cookies.txt \
  "http://localhost:9001/admin/submissions?page=1&limit=1" | jq -r '.data.items[0].id')
curl -s -H "X-CSRF-Token: $CSRF" -b /tmp/cookies.txt \
  "http://localhost:9001/admin/submissions/$SUB_ID" | jq .

# 重判
curl -s -X POST -H "X-CSRF-Token: $CSRF" -b /tmp/cookies.txt \
  -H "Content-Type: application/json" \
  "http://localhost:9001/admin/submissions/$SUB_ID/rejudge" \
  -d '{"notifyUser":false}' | jq .

# 批量 (注意: 字段名是 ids 不是 submissionIds)
curl -s -X POST -H "X-CSRF-Token: $CSRF" -b /tmp/cookies.txt \
  -H "Content-Type: application/json" \
  "http://localhost:9001/admin/submissions/batch-rejudge" \
  -d "{\"ids\":[\"$SUB_ID\"],\"notifyUsers\":false}" | jq .
```

---

*测试完毕。后端 6 endpoint 可达, 鉴权/CSRF/限流/404 路径正常, 但输入校验和文档一致性需修。*

---

## 7. Fixes Applied (2026-06-09)

| Issue | 状态 | 修复 commit (见 git log) | 备注 |
|---|---|---|---|
| 🔴 HIGH-1 batch 静默失败 | ✅ Fixed | `fix(admin-submissions): unify status enum, add @Valid, expose rejudge metadata` | `BatchRejudgeRequest` 加 `@NotEmpty + @Size(max=50)`; `rejudge` 加 `@Valid`; service 删 `null/empty` 静默分支 |
| 🔴 HIGH-2 字段名错位 | ✅ Fixed | 同上 | DTO 字段名 `submissionIds` + `@JsonAlias({"ids"})` 兼容旧客户端 |
| 🔴 HIGH-3 状态集不一致 | ✅ Fixed | 同上 | 新建 `SubmissionStatus` enum (11 项); `getStatuses()` 改 enum 派生; `byStatus` 与 dropdown 现在显示 11 项 |
| 🟡 MEDIUM-1 缺 @Valid | ✅ Fixed | 同上 | 2 个 controller endpoint + 2 个 DTO 全部加 Bean Validation |
| 🟡 MEDIUM-2 rejudge 响应 | ✅ Fixed | 同上 | `RejudgeResult` 加 `rejudgedAt` (Instant) + `retryCount` |
| 🟡 MEDIUM-3 限流过紧 | ⏸ Deferred | — | 单实例 5/60s × 50 上限 = 250 rejudges/min/user, 当前规模可接受; 后续按压测再调 |
| 🟢 LOW-1 OpenAPI 缺失 | ✅ Fixed | `fix(springdoc): expose OpenAPI at standard /v3/api-docs path` | yaml path 改 `/v3/api-docs` (272 paths 已验证) |
| 🟢 LOW-2 languages shape | ✅ Fixed | 同 HIGH commit | 新建 `LanguageOption`; `getLanguages()` 返回 `[{key,label}]` (cpp → "C++") |
| 🟢 LOW-3 幂等键 | ⏸ Deferred (P3) | — | 单独 PR, 不阻塞主修复 |

**复现命令** (修复后):

```bash
# 静默失败已修复: 全部 400 而非 200 + total:0
curl -X POST .../admin/submissions/batch-rejudge -d '{}'                              # 400
curl -X POST .../admin/submissions/batch-rejudge -d '{"submissionIds":null}'          # 400
curl -X POST .../admin/submissions/batch-rejudge -d '{"submissionIds":[]}'            # 400
curl -X POST .../admin/submissions/batch-rejudge -d '{"ids":["x"]}'                    # 200 (alias 兼容)
curl -X POST .../admin/submissions/batch-rejudge -d '{"submissionIds":["x"]*51}'      # 400
curl -X POST .../admin/submissions/batch-rejudge -d '{"submissionIds":["x"]*50}'      # 200

# 状态数修复
curl .../admin/submissions/statuses | jq '.data | length'   # 11 (修复前 7)
curl .../admin/submissions/languages | jq '.data[0]'        # {"key":"cpp","label":"C++"} (修复前 "cpp")

# rejudge 响应含元数据
curl -X POST .../admin/submissions/{id}/rejudge -d '{"notifyUser":false}' | jq '.data | {rejudgedAt,retryCount,oldStatus,newStatus}'
```

**测试**: `AdminSubmissionServiceImplTest` (16 cases) + `AdminSubmissionControllerTest` (12 cases) 全部通过。
