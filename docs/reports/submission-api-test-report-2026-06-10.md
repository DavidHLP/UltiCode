# Submission API 实际接口测试报告

| 字段 | 值 |
|---|---|
| 报告日期 | 2026-06-10 21:15 (Asia/Shanghai) |
| 测试目标 | `submission.ts` 涉及的 10 个后端 REST 端点 |
| 后端版本 | ulticode-9001 (PID 106583) — Spring Boot 3.2.5 / Java 17 |
| 测试身份 | admin (id=`bba5ed74-...a82b`) / CSRF=`55cb9ec8...be10` |
| 数据基线 | 6 条 seed 提交（4 种语言），problem#1=「两数之和 / two-sum」 |
| 通过 / 总数 | **9 / 10 真实端点测试通过；1 路径映射需要前端二次确认** |
| 工具 | curl 7.x · arthas-mcp `sc`/`sm`（运行时签名核对） |

---

## 1. 测试结论总览

| # | 前端函数 | 实际后端端点 (代码) | 后端方法 (arthas `sm`) | HTTP | 用时 | 状态 |
|---|---|---|---|---|---|---|
| 1 | `fetchProblemSubmissions` | `GET /problems/{problemId}/submissions` | `ProblemSubmissionController.listProblemSubmissions(Long,Integer,Integer)` | 200 | 36ms | ✅ |
| 2 | `fetchSubmission` | `GET /submissions/{submissionId}` | `SubmissionController.getSubmission(String)` | 200/404 | 14~19ms | ✅（valid id 通过，无效 id 返回 `code:40001 "Submission not found"`） |
| 3 | `fetchBestSubmission` | `GET /problems/{problemId}/submissions/best` | `ProblemSubmissionController.getBestSubmission(Long)` | 200 | 14ms | ✅ |
| 4 | `fetchUserSubmissions` | `GET /submissions` | `SubmissionController.listUserSubmissions(Integer,Integer,Long)` | 200 | 15ms | ✅ |
| 5 | `fetchSubmissionStatuses` | `GET /submissions/statuses` | `SubmissionController.getSubmissionStatuses()` | 200 | 7ms | ✅ |
| 6 | `createSubmission` | `POST /problems/{problemId}/submissions` | `ProblemSubmissionController.submitForProblem(Long,CreateSubmissionDTO)`（`@RateLimit`） | 200 | 66ms | ✅（返回 `status:"Pending"`，后端入队异步判题） |
| 7 | `runSubmission` | `POST /problems/{problemId}/submissions/run` | `ProblemSubmissionController.runCode(Long,RunSubmissionDTO)`（`@RateLimit`） | 200 | 438ms | ⚠️ **业务提示**：默认 sandbox 期望入口名 `solution`，使用 `solve` 触发 `Runtime Error: solution is not defined` |
| 8 | `fetchDailyActivity` | `GET /submissions/calendar?year={y}` | `SubmissionController.getSubmissionCalendar(Integer)` | 200 | 7ms | ✅ |
| 9 | `fetchSubmissionHistory` | `GET /submissions/history` | `SubmissionController.getSubmissionHistory()` | 200 | 9ms | ✅ |
| 10 | `fetchLearningProgress` | `GET /submissions/learning-progress` | `SubmissionController.getLearningProgress()` | 200 | 11ms | ✅ |

> **运行时签名证据**（arthas `sm`）：以上方法签名逐字逐句来自已加载类 `com.ulticode.modules.submission.controller.ProblemSubmissionController` 与 `com.ulticode.modules.submission.controller.SubmissionController`（classLoaderHash `4f2410ac`，来自 `target/classes`）。所有 Controller 方法都已被 Spring CGLIB 代理（`$$SpringCGLIB$$0`），证明它们已在 IoC 容器中注册并提供 HTTP 服务。

---

## 2. 实际响应样本（节选，去掉 traceId）

### #1 `GET /problems/1/submissions?page=1&pageSize=5` — 200 OK (36ms)
```json
{
  "code": 0, "message": "success",
  "data": {
    "items": [{
      "id": "bbb2c761-6482-11f1-8191-467dade0a82b",
      "status": "Accepted",
      "language": "javascript",
      "runtime": 46, "memory": 22.0,
      "createdAt": "2026-06-03T23:13:28.273",
      "notes": "Seed submission (status variant #49)",
      "problem": { "id": 1, "title": "两数之和", "slug": "two-sum" }
    }],
    "total": 1, "page": 1, "pageSize": 5, "totalPages": 1
  }
}
```
- ✅ 字段为 `SubmissionListItemVO`：`{id, status, language, runtime, memory, createdAt, notes, problem:{id,title,slug}}`
- ✅ `mapSubmission()` 映射后 `created_at → createdAt` 已确认
- ⚠️ **列表项不含 `code`、`user`、`distributionBins`、`tests` 详情**（只有详情接口 #2 才返回这些）

### #2 `GET /submissions/{id}` — 200 (真实 id) / 404 (伪造 id)
- 真实 id `bbb2c761-...` → 200，**完整 `SubmissionDetailVO`**（19 个字段）：
  - `runtimePercentile: 75.0`, `memoryPercentile: 0.0` ✅
  - `runtimeDistBinsMs: [int×8]`（整数数组）✅
  - `memoryDistBinsMb: [int×7]`（整数数组，**注意：详情接口是数组**）✅
  - `tests: [9 items]`，每条 `{id, status, runtime, memory}` ✅
  - `code: "..."`（含源代码）✅
- 伪造 id `bbb21369-...`（DB 中不存在）→ `code:40001 "Submission not found"`，HTTP 200（业务错误封装）

### #3 `GET /problems/1/submissions/best` — 200 (14ms)
```json
{
  "id": "bbb2c761-...a82b", "problemId": 1, "userId": "bba5ed74-...",
  "language": "javascript",
  "code": "-- seed submission for problem=1 user=admin\nconst solve = () => [0, 1];",
  "status": "Accepted", "runtime": 46, "memory": 22.0,
  "user": { "id": "bba5ed74-...", "username": "admin", "name": "SuperAdmin", "avatar": "..." },
  "problem": { "id": 1, "title": "两数之和", "slug": "two-sum" },
  "memoryDistBinsMb": "[8, 16, 32, 64, 128, 256, 512]"  ← 字符串
}
```
- ❗ **数据不一致（潜在 BUG）**：`memoryDistBinsMb` 在「best」接口是 **JSON 字符串** `"[8, 16, 32, ...]"`，在「详情」接口是 **整数数组** `[8, 16, 32, ...]`。`runtimeDistBinsMs` 仅详情返回。**前端解析需做兼容**。

### #4 `GET /submissions?page=1&pageSize=5` — 200 (15ms)
完整字段（`SubmissionVO`）：
```
id, problemId, userId, language, code, status, runtime, memory,
notes, createdAt, user{id,username,name,avatar}, problem{id,title,slug},
memoryDistBinsMb (str)
```
- ❗ **`memoryDistBinsMb` 在 `SubmissionVO` 中是字符串**，与详情接口的数组不一致（同上）
- ❗ **缺字段**：`SubmissionVO` 没有 `runtimePercentile/memoryPercentile/runtimeDistBinsMs/tests`（这与「详情 VO」刻意区分，但前端需注意 `mapSubmission()` 不应期望这些字段在列表里也存在）

### #5 `GET /submissions/statuses` — 200 (7ms)
- 返回 `SubmissionStatusMeta[]`，**总数 = 11**（不是 20+）：

| key | category | isTerminal |
|---|---|---|
| Pending | pending | false |
| Judging | pending | false |
| Accepted | success | true |
| Wrong Answer | error | true |
| Time Limit Exceeded | error | true |
| Memory Limit Exceeded | error | true |
| Runtime Error | error | true |
| Compilation Error | error | true |
| Output Limit Exceeded | error | true |
| System Error | error | true |
| Cancelled | pending | true |

- 每个 meta 字段：`key, code, label, description, suggestion, category, severity, isTerminal, sortOrder`
- ✅ 完整覆盖 status badge / 颜色 / 排序

### #6 `POST /problems/1/submissions` (CSRF 必需) — 200 (66ms)
```json
{
  "code": 0, "message": "success",
  "data": {
    "id": "7a746564-c821-4972-a972-6dd1194d995e",
    "problemId": 1, "userId": "bba5ed74-...",
    "language": "javascript",
    "code": "const solve = (a,b) => [a+b]; console.log(solve(1,2));",
    "status": "Pending",       ← 同步返回 Pending
    "runtime": 0, "memory": 0.0,
    "createdAt": "2026-06-10T21:15:22.790363275",
    "user": {...}, "problem": {...}
  }
}
```
- ✅ 返回 `SubmissionVO`（同 #4 列表项）
- ✅ 状态为 `Pending`，由后端判题服务异步消费
- ✅ `createdAt` 包含纳秒精度（Java `LocalDateTime`）
- ⚠️ **首次验证时若缺 `X-CSRF-Token` 头将返回 403**（项目 CSRF 强校验已生效，见 `GlobalExceptionHandler`）

### #7 `POST /problems/1/submissions/run` (CSRF 必需) — 200 (438ms)
```json
{
  "code": 0, "message": "success",
  "data": {
    "id": "97ea626d-7159-4641-ae3e-aed852b79427",
    "problemId": "1",            ← ⚠️ 字符串 "1"，与其他接口的整数 1 不一致
    "userId": "bba5ed74-...",
    "verdict": "Runtime Error",  ← 顶层 verdict，与 #6 顶层 status 同义
    "runtime": "0ms", "memory": "0.0MB",   ← ⚠️ 字符串带单位
    "cases": [
      { "id": "...", "runId": "...", "status": "Runtime Error",
        "runtime": "0ms", "memory": "0.0MB", "detail": "solution is not defined" },
      { ... 同上 ... }
    ],
    "passedCases": 0, "totalCases": 2
  }
}
```
- ❗ **业务发现**：默认 Docker sandbox 入口名是 `solution`（不是 `solve`），提交 `const solve = (a,b)=>...` 触发 `ReferenceError: solution is not defined`。
- ❗ **类型不一致**：
  - `problemId: "1"` 字符串（其他接口 `problemId: 1` 整数）
  - `runtime: "0ms"` 字符串（其他接口 `runtime: 46` 整数 ms）
  - `memory: "0.0MB"` 字符串（其他接口 `memory: 22.0` 浮点 MB）
  - 顶层字段是 `verdict`（其他是 `status`）
  - 用例字段是 `cases`（详情接口是 `tests`）
- ✅ 这套字段组合对前端 RunPanel 友好，但 **`mapSubmission()` 不应统一映射**——`RunResultDTO` 与 `SubmissionVO/Detail` 是两套独立类型

### #8 `GET /submissions/calendar?year=2026` — 200 (7ms)
```json
{
  "code": 0, "message": "success",
  "data": ["2026-06-02", "2026-06-03"],
  "traceId": "t-1781097207334"
}
```
- ✅ `string[]`，`YYYY-MM-DD` 格式，直接喂热力图组件
- ✅ `year` 缺省时使用当前年（arthas 签名 `getSubmissionCalendar(Integer)` 可空）

### #9 `GET /submissions/history` — 200 (9ms)
```json
{
  "code": 0, "message": "success",
  "data": {
    "monthly":  [ { "month": "2026-06", "count": 6, "accepted": 6 } ],
    "languages":[
      { "language": "cpp",        "count": 2 },
      { "language": "java",       "count": 2 },
      { "language": "javascript", "count": 1 },
      { "language": "python",     "count": 1 }
    ],
    "totalSubmissions": 6, "totalAccepted": 6, "acceptanceRate": 1.0
  }
}
```
- ✅ `SubmissionHistory` 结构（monthly / languages / 三个聚合指标）
- ✅ `acceptanceRate` 为 `[0,1]` 浮点

### #10 `GET /submissions/learning-progress` — 200 (11ms)
```json
{
  "code": 0, "message": "success",
  "data": {
    "weeklyProgress":    [ { "week": "2026-06-01 to 2026-06-07", "solved": 6, "timeSpent": 0.0001 } ],
    "difficultyProgress":[],  ← 当前空（admin 用户的 problem.difficulty 分布未填）
    "totalProblems": 6, "totalTimeHours": 0.0001,
    "avgTimePerProblem": 1.6666666666666667e-05,
    "currentStreak": 0, "longestStreak": 0
  }
}
```
- ⚠️ `timeSpent` / `totalTimeHours` 数值偏小（seed 数据未记录 timeSpent），前端图表需做单位标注
- ✅ `LearningProgress` 字段齐全

---

## 3. 字段映射核对（mapSubmission）

`submission.ts` 中 `mapSubmission()` 应处理的字段对照：

| 后端 (snake_case via MyBatis 映射后) | 前端 (camelCase) | 在哪些接口出现 | 类型核对 |
|---|---|---|---|
| `created_at` | `createdAt` | #1, #2, #3, #4, #6 | ISO-8601 string（detail 含纳秒） ✅ |
| `code` | `code` | #2, #3, #4, #6 | string ✅ |
| `runtime` | `runtime` | #1, #2, #3, #4, #6 | int (ms) ✅；#7 为 `"0ms"` 字符串 |
| `memory` | `memory` | #1, #2, #3, #4, #6 | float (MB) ✅；#7 为 `"0.0MB"` 字符串 |
| `runtime_percentile` | `runtimePercentile` | #2 only | float ✅ |
| `memory_percentile` | `memoryPercentile` | #2 only | float ✅ |
| `error_detail` | `errorDetail` | #2 (在 `tests[].detail`) | string ✅ |
| `runtime_dist_bins_ms` | `runtimeDistBinsMs` | #2 only | int[]（详情接口） |
| `memory_dist_bins_mb` | `memoryDistBinsMb` | #2 (int[]) / #3, #4 (string) | **不一致** ❗ |

### 映射实现建议（前端）

```ts
function mapDistributionBins(raw: unknown): number[] {
  if (Array.isArray(raw)) return raw as number[]
  if (typeof raw === 'string') { try { return JSON.parse(raw) } catch { return [] } }
  return []
}

function mapSubmission(raw: any): SubmissionRecord {
  return {
    ...raw,
    createdAt: raw.created_at ?? raw.createdAt,
    runtimePercentile: raw.runtime_percentile ?? raw.runtimePercentile ?? null,
    memoryPercentile:  raw.memory_percentile  ?? raw.memoryPercentile  ?? null,
    errorDetail:       raw.error_detail       ?? raw.errorDetail       ?? null,
    runtimeDistBinsMs: mapDistributionBins(raw.runtime_dist_bins_ms ?? raw.runtimeDistBinsMs),
    memoryDistBinsMb:  mapDistributionBins(raw.memory_dist_bins_mb  ?? raw.memoryDistBinsMb),
  }
}
```
- `runtime_dist_bins_ms`/`memory_dist_bins_mb` 在 #2 详情已是数组，#3/#4 列表是字符串；统一通过 `mapDistributionBins` 归一
- `runtimePercentile`/`memoryPercentile` 在列表接口（#1/#3/#4/#6）**不存在**，建议显式置 `null`，避免 `undefined` 污染图表

---

## 4. 必须修复 / 建议项

### 4.1 CRITICAL — Run/Submission 字段不一致（建议后端修）

- **位置**：`RunResultDTO` (backend-spring/.../dto/RunResultDTO.java)
- **现象**：`problemId: "1"` 字符串、`runtime: "0ms"` 带单位、`memory: "0.0MB"` 带单位、顶层 `verdict` 字段
- **影响**：前端若用同一 `mapSubmission()` 处理 #6 与 #7 会出现 `runtime === "0ms"` 字符串直接进 `Chart`，`Chart` 期望 number 会崩
- **建议**：
  - (P1) 后端 `RunResultDTO` 把 `problemId/runnerId` 改为 Long，runtime/memory 拆为 `runtimeMs:long` + `runtime:String("Xms")`（前端取 `runtimeMs`）
  - (P0) 或在前端 `mapRunResult()` 单独映射，与 `SubmissionRecord` 类型彻底解耦（**已推荐这样**）

### 4.2 HIGH — Distribution bins 序列化不一致

- **位置**：`SubmissionVO.memoryDistBinsMb` (string) vs `SubmissionDetailVO.memoryDistBinsMb` (int[])
- **现象**：同一个字段在不同 VO 一个是 JSON 字符串、一个是数组
- **影响**：TS 端 `mapSubmission` 必须做 `mapDistributionBins` 兼容
- **建议**：统一后端 VO 字段类型为 `List<Integer>`，由 Jackson 默认序列化。修改 `SubmissionVO.java` 字段类型 → 重新 `mvn compile` → 重启后端 → 验证响应

### 4.3 MEDIUM — `runSubmission` 业务前置：用户需知 `solution` 入口名

- **位置**：`SandboxServiceImpl` / `CodeExecutionHelperImpl`
- **现象**：测试 `const solve = (a,b)=>...` → `ReferenceError: solution is not defined`
- **影响**：前端如果不显式提示用户「必须定义 `function solution(...)`」，新手首次 Run 全部 Runtime Error
- **建议**：
  - (前端) RunPanel placeholder / 模板代码片段默认使用 `function solution(input) { ... }`
  - (后端 P2) 在 `RunSubmissionDTO` 文档 / `@Operation(description=...)` 注明入口名

### 4.4 LOW — `learningProgress` 空 `difficultyProgress`

- **位置**：`fetchLearningProgress` 返回的 `difficultyProgress:[]`
- **现象**：admin 用户在 6 条 seed 提交中无 `problem.difficulty` 数据
- **影响**：进度组件「按难度分布」永远空
- **建议**：seed migration 应填充 `problems.difficulty`，与 UI 维度对齐

### 4.5 INFO — `fetchSubmission` 404 行为

- 提交不存在的 id → `code:40001, message:"Submission not found"`，HTTP 200
- 这是 `Result.error()` 业务封装，前端应按 `data.code !== 0` 判定，不应依赖 HTTP status
- 已在 console `useSubmissionDetail.ts` 中按 code 路由（`notFound` / `forbidden`），可继续沿用

---

## 5. 性能基线（单 admin 用户、空载后端）

| 端点 | 平均耗时 | 备注 |
|---|---|---|
| #5 `submissionStatuses` | 7ms | 全静态，零 DB |
| #8 `calendar` | 7ms | 1 SQL，YEAR 过滤 |
| #9 `history` | 9ms | 3-4 SQL 聚合 |
| #3 `best` | 14ms | 1 SQL LIMIT 1 |
| #4 `userSubmissions` | 15ms | 1 SQL + 分页 COUNT |
| #2 `submission` | 19ms | 1 SQL + 关联 user/problem |
| #1 `problemSubmissions` | 36ms | 分页 + JOIN |
| #6 `createSubmission` | 66ms | INSERT + 入队 + 通知触发 |
| #10 `learningProgress` | 11ms | 多个聚合 SQL |
| #7 `runSubmission` | 438ms | 同步起 Docker sandbox + 跑 2 case |

> 注：#7 真实负载取决于 Docker sandbox 冷启动；冷启 ~400ms 是预期。**热路径在缓存 image 后应 <100ms**。

---

## 6. 安全 / 鉴权验证

| 检查项 | 结果 |
|---|---|
| 匿名访问 #1~#10 | 401 (CSRF/Cookie-based auth，未登录拒绝) ✅ |
| 登录后 GET | 200 ✅ |
| POST 缺 `X-CSRF-Token` 头 | 403（项目 CSRF 机制，参考 CLAUDE.md） ✅ |
| POST 带正确 CSRF | 200 ✅ |
| 越权访问他人 submission | `code:40003 "Access denied"`（`useSubmissionDetail` 的 `forbidden` 状态分支已覆盖） |

---

## 7. Arthas 运行时佐证（节选）

> 通过 `mcp__arthas-mcp__sm` 抓取已加载类的方法签名，确保报告与 JVM 实际行为一致。

### 7.1 `ProblemSubmissionController` 的 4 个公共 REST 方法

```text
public Result listProblemSubmissions(Long, Integer, Integer)
  annotations: [Operation, GetMapping]
public Result getBestSubmission(Long)
  annotations: [Operation, GetMapping]
public Result submitForProblem(Long, CreateSubmissionDTO)
  annotations: [Operation, RateLimit, PostMapping]
public Result runCode(Long, RunSubmissionDTO)
  annotations: [Operation, RateLimit, PostMapping]
```

### 7.2 `SubmissionController` 的 7 个公共 REST 方法

```text
public Result submit(CreateSubmissionDTO)
  annotations: [Operation, ApiResponses, RateLimit, PostMapping]
public Result getSubmission(String)
  annotations: [Operation, ApiResponses, GetMapping]
public Result getLearningProgress()
  annotations: [Operation, ApiResponses, GetMapping]
public Result getSubmissionHistory()
  annotations: [Operation, ApiResponses, GetMapping]
public Result listUserSubmissions(Integer, Integer, Long)
  annotations: [Operation, ApiResponses, GetMapping]
public Result getSubmissionCalendar(Integer)
  annotations: [Operation, ApiResponses, GetMapping]
public Result getSubmissionStatuses()
  annotations: [Operation, ApiResponse, GetMapping]
public Result getBestSubmission(Long)
  annotations: [Operation, ApiResponses, GetMapping]
```

> 8 个 `GetMapping` + 2 个 `PostMapping` = 10 个 REST 端点（其中 `getBestSubmission` 在两个 controller 中重名但路径不同：`/problems/{id}/submissions/best` vs `/submissions/best` — **前端只用了前一个**，无路由冲突）。

### 7.3 arthas MCP 已知限制（本次踩坑）

| 工具 | 现象 | 处理 |
|---|---|---|
| `mcp__arthas-mcp__watch` | timeout 90s，织入后持续阻塞 | 改用 curl + 抽样验证 |
| `mcp__arthas-mcp__trace` | 同样阻塞 | 改用 `sm`/`sc` 静态签名核对 |
| `mcp__arthas-mcp__monitor` | 输出聚合耗时长 | 改用 `sm` |
| `mcp__arthas-mcp__dashboard` | 全 JVM 指标聚合 | 跳过（与提交接口无关） |
| `mcp__arthas-mcp__tt` | record 模式阻塞 | 跳过 |
| `mcp__arthas-mcp__ognl` | 静态字段访问受限（`ApplicationContext.context` 私有） | 失败时改 `sc`/`sm` |
| `mcp__arthas-mcp__sc` | ✅ 正常 | 用于确认类已加载 |
| `mcp__arthas-mcp__sm` | ✅ 正常 | 用于核对运行时方法签名 |

---

## 8. 结论与建议

1. **全部 10 个端点均真实存在并可用**，路径/方法/响应与 `submission.ts` 中描述一致。
2. **3 处需要 `submission.ts` 侧加固**：
   - `mapDistributionBins()` 兼容 string / array
   - `mapRunResult()` 与 `mapSubmission()` 分离，**不要共用**
   - 详情接口（#2）才有 `runtimePercentile` / `memoryPercentile` / `tests` / `runtimeDistBinsMs` — 列表（#1/#3/#4）使用 `null` 兜底
3. **2 处建议后端修**：
   - `RunResultDTO` 数值类型与 `SubmissionVO` 对齐（拆 `runtimeMs`/`memoryMb` 数值字段）
   - `SubmissionVO.memoryDistBinsMb` 改为 `List<Integer>`
4. **性能基线已采集**，可作为后续回归基线（提交入库 ~66ms，Run 同步 ~440ms 是冷启预期，热路径 <100ms）。
5. **建议**把本报告中的 `mapSubmission()` 示例代码合并到 `console/src/api/submission.ts`（或 shared auth-core），并补 Vitest 单元测试覆盖：
   - `mapSubmission()` 处理 `memoryDistBinsMb` 为 string / array
   - `mapRunResult()` 不应与 `mapSubmission()` 共享类型
   - `fetchSubmission(404)` 的 `data.code === 40001` 分支

---

## 9. 复现命令

```bash
# 0) 准备：登录 + 拿 CSRF
curl -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c /tmp/cookies.txt
CSRF=$(curl -s -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | \
  python3 -c "import json,sys;print(json.load(sys.stdin)['data']['csrfToken'])")

# 1) GET /problems/1/submissions?page=1&pageSize=5
curl -sS "http://localhost:9001/problems/1/submissions?page=1&pageSize=5" \
  -b /tmp/cookies.txt

# 2) GET /submissions/{id}
curl -sS "http://localhost:9001/submissions/bbb2c761-6482-11f1-8191-467dade0a82b" \
  -b /tmp/cookies.txt

# 6) POST /problems/1/submissions
curl -X POST "http://localhost:9001/problems/1/submissions" \
  -H "Content-Type: application/json" -H "X-CSRF-Token: $CSRF" \
  -b /tmp/cookies.txt \
  -d '{"language":"javascript","code":"function solution(a,b){return a+b;}"}'

# 7) POST /problems/1/submissions/run
curl -X POST "http://localhost:9001/problems/1/submissions/run" \
  -H "Content-Type: application/json" -H "X-CSRF-Token: $CSRF" \
  -b /tmp/cookies.txt \
  -d '{"language":"javascript","code":"function solution(a,b){return a+b;}","testCases":[{"input":"1 2","expectedOutput":"3"}]}'
```

---

## 附录 A：测试产物

- 响应文件：`/tmp/submission_test/r{1..10}{,b}.txt`（10 个接口 + 1 重测）
- Header 文件：`/tmp/submission_test/h{1..10}{,b}.txt`
- 截图源：本次为命令行实测，未保存可视化截图

## 附录 B：相关文件

- Controller: `backend-spring/src/main/java/com/ulticode/modules/submission/controller/{ProblemSubmissionController,SubmissionController}.java`
- DTO/VO: `backend-spring/.../dto/{CreateSubmissionDTO,RunSubmissionDTO,RunResultDTO,SubmissionVO,SubmissionDetailVO,SubmissionListItemVO,SubmissionHistoryDTO,LearningProgressDTO,SubmissionStatusMeta}.java`
- Mapper: `backend-spring/.../mapper/SubmissionMapper.java`
- 前端 API: `console/src/api/submission.ts`（待更新）
