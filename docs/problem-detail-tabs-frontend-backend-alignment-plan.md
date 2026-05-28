# Problem Detail Tabs 前后端颗粒度与逻辑对齐分析报告

> 生成日期: 2026-05-28  
> 分析范围: `http://localhost:9002/problems/two-sum/description`、`/solutions`、`/submissions`  
> 数据来源: 本地源码审查、`localhost:9001/9002` 实际请求、运行日志抽样  
> 结论类型: 分析 + 修复方案 + 分阶段迭代计划

---

## 1. 总览结论

这三个 URL 实际共享同一个前端页面: `console/src/router/index.ts` 中的 `/problems/:slug/:tab?` 统一进入 `ProblemDetailView.vue`，再由 tab 参数切换题面、题解、提交记录三个面板。当前整体骨架是合理的，`description` 主链路已经基本打通；真正的问题集中在 `solutions` 的权限/接口命名空间不一致、`submissions` 的列表/详情 DTO 颗粒过粗，以及移动端 tab 深链没有同步 URL。

运行态核心证据:

- `GET http://localhost:9002/problems/two-sum/solutions` 返回前端 HTML `200 OK`。
- `GET http://localhost:9001/problems/slug/two-sum` 返回后端业务 `code=0`，包含 `detail.content`、3 个 examples、1 个 JavaScript language、interaction counts、`submission_count=44`、`solution_count=7`。
- `GET http://localhost:9001/api/problems/1/solutions` 返回 `401 Unauthorized`，但 `SolutionController` 注释写的是 Public endpoint，前端题解页也按公开列表调用。
- `GET http://localhost:9001/problems/1/submissions` 返回 `401 Unauthorized`，这与“提交记录是登录用户私有数据”的产品语义一致；前端也会在未登录时直接显示登录提示，不主动请求列表。
- `GET http://localhost:9001/submissions/statuses` 返回 `200 OK`，提交状态字典作为公共元数据可用。

一句话判断: `description` 是“公开聚合详情接口”，颗粒度基本对；`solutions` 是“公开内容 + 登录用户态”混在一个接口里，但安全层没放行，导致匿名页破；`submissions` 是“私有列表 + 详情 + 代码 + 统计图”混用同一个 VO，短期能跑，长期会拖慢和泄露过多字段。

---

## 2. 页面与接口链路矩阵

| URL | 前端 tab | 主数据 API | 运行态 | 当前判断 |
| --- | --- | --- | --- | --- |
| `/problems/two-sum/description` | `description -> headerId=1` | `GET /problems/slug/two-sum` | 200 | 主链路可用，DTO 偏重，公开字段需要收敛 |
| `/problems/two-sum/solutions` | `solutions -> headerId=2` | `GET /api/problems/1/solutions` | 401 | P0: 后端 controller 认为公开，安全层未放行 |
| `/problems/two-sum/submissions` | `submissions -> headerId=3` | `GET /problems/1/submissions` | 未登录 401 | 权限合理，但列表/详情 DTO 和分页体验要拆 |

关键代码证据:

- tab 映射: `TAB_MAP` 把 `description/solutions/submissions` 映射到 `1/2/3`，见 `console/src/views/problems/composables/useProblemLayout.ts:12-22`。
- 桌面端 URL -> store 同步、store -> URL 同步分别在 `useProblemLayout.ts:144-187`。
- 题目详情 API 由 slug 决定 `GET /problems/slug/{id}`，见 `console/src/api/problem-detail.ts:63-69`。
- 题解列表 API 是 `/api/problems/{problemId}/solutions`，见 `console/src/api/solution.ts:122-135`。
- 提交记录 API 是 `/problems/{problemId}/submissions`，见 `console/src/api/submission.ts:70-75`。

---

## 3. Description 页面分析

### 3.1 当前已经对齐的部分

`description` 页面使用同一个详情聚合接口获取:

- problem 基础字段: id、slug、title、difficulty、status、premium/published 状态。
- detail: summary、content、constraints、followUp、hints、companies。
- examples: 同时用于题面示例和默认运行样例。
- languages: 后端已按执行服务支持语言过滤。
- interactions: likes/dislikes/favorites。
- stats: submission_count、solution_count。

后端组装位置在 `ProblemServiceImpl.buildDetailResponse()`，其中基础字段、真实提交/题解计数、tags、detail、interactions、examples、languages 都在一个响应里完成，见 `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java:293-370`。

前端 mapper 当前也已经优先使用 `detail.content`，再降级到 `summary`，见 `console/src/api/problem-detail.ts:133-143`。这说明旧文档里“只展示 summary”的问题已经修复，不应再按旧结论处理。

### 3.2 仍需收敛的颗粒度

公开详情接口当前返回了明显偏管理态的字段，例如:

- `is_deleted`
- `is_flagged`
- `published_by`
- flag 审核相关字段

这些字段在 `ProblemServiceImpl` 直接进入 public response，见 `ProblemServiceImpl.java:303-319`。对公开题面页而言，这些字段不属于用户可见业务契约，建议拆成:

- Public `ProblemDetailPublicVO`: 面向 console。
- Admin `ProblemDetailAdminVO`: 面向 management。

这样可以降低公开接口字段泄露面，也能让前端类型更稳定。

### 3.3 interactions 的用户态读取方式不理想

后端 `buildInteractions()` 能返回 likes/dislikes/favorites，见 `ProblemServiceImpl.java:409-440`。但 viewer reaction 仍从 `problem_details.interactions` JSON 里读，而不是从统一的 edge operation 表按当前登录用户查询。这会产生两个问题:

- 公共计数与用户态交互来源不一致。
- `viewer_reaction` 不应由前端传 userId，也不应依赖详情 JSON。

建议:

- 公开计数继续在详情接口返回。
- 用户态字段统一通过 `SecurityContext` 查询 edge operations。
- 返回结构收敛为 `interactions.counts` + `interactions.viewer`，前端 mapper 已经接近这个结构。

---

## 4. Solutions 页面分析

### 4.1 P0: 题解列表权限配置与 Controller 语义冲突

`SolutionController.findByProblemId()` 注释写明 Public endpoint，并暴露 `GET /api/problems/{problemId}/solutions`，见 `backend-spring/src/main/java/com/ulticode/modules/solution/controller/SolutionController.java:37-58`。

但 `SecurityConfig.PUBLIC_ENDPOINTS` 只放行了:

- `/api/solutions`
- `/api/solutions/**`
- `/api/views/solution/**`

没有放行 `/api/problems/*/solutions`，见 `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java:41-58`。实际 curl 验证也返回 401。

影响:

- 匿名访问 `/problems/two-sum/solutions` 时，前端 `fetchSolutionFeed()` 失败。
- `ProblemSolutionsView` catch 后把 `feed` 清空，页面最终显示 follow-up 兜底，而不是 7 条公开题解，见 `console/src/views/problems/solutions/ProblemSolutionsView.vue:40-66`。
- 这会让用户以为“两数之和没有题解，只剩提示”。

短期修复:

1. 在 `SecurityConfig` 增加 GET-only 放行:
   - `GET /api/problems/*/solutions`
2. 或更推荐，新增规范路径:
   - `GET /problems/{problemId}/solutions`
   - 保留 `/api/problems/{problemId}/solutions` 作为兼容 alias。

### 4.2 P0: 前端更新题解使用 PATCH，后端只支持 PUT

前端 `updateSolution()` 调用 `apiPatch('/api/solutions/{id}')`，见 `console/src/api/solution.ts:100-105`。

后端更新接口是 `@PutMapping("/api/solutions/{id}")`，见 `SolutionController.java:112-127`。

影响:

- 从题解页进入编辑后保存，可能直接 405。
- 这是典型前后端方法语义未对齐。

短期修复二选一:

- 前端改为 `apiPut`，并在 request utility 中补 `apiPut()`。
- 后端同时支持 `@PatchMapping`，把 PATCH 作为部分更新契约。

建议中期选 PATCH，因为 `UpdateSolutionDTO` 本质是更新资源局部字段。

### 4.3 题解列表颗粒度偏重

当前题解列表返回 `SolutionVO`，其中包含完整 `content`。前端列表搜索也直接在 `item.content` 里本地搜索，见 `console/src/views/problems/solutions/SolutionListView.vue:89-103`。

问题:

- 列表页每条题解都带完整 Markdown，题解变多后响应会迅速膨胀。
- 前端筛选/排序只作用于当前 page 的 `items`，不是全量题解。
- 后端默认 pageSize=20，见 `SolutionServiceImpl.java:201-225`，但前端没有显式分页 UI。
- `toVO()` 对每条题解分别查询作者、投票、评论数、标签、成就，见 `SolutionServiceImpl.java:389-455`。日志也显示题解列表请求触发了多轮 SQL，是典型 N+1 风险。

建议拆分:

- `SolutionListItemVO`: id、title、summary、language、tags、author、counts、score、publishedAt、isPinned、viewerVote。
- `SolutionDetailVO`: 在用户点击后再取 content、comments、完整正文。
- 列表 API 支持 `page/pageSize/sort/language/search`，由服务端完成筛选排序。

### 4.4 userId 查询参数应移除

前端 `fetchSolutionFeed(problemId, userId)` 会拼 `?userId=...`，见 `console/src/api/solution.ts:122-128`。后端 `findByProblemId()` 并不接收这个参数，也没有把 current user 传进 `toVO(solution, currentUserId)`，因此列表里的 `userVote` 永远不会按登录用户填充。

建议:

- 前端停止传 `userId`。
- 后端从 `SecurityContext` 读取当前用户；匿名时 viewer fields 为 null/0。
- 列表 `toVO` 或新 mapper 支持批量 viewer vote 查询。

---

## 5. Submissions 页面分析

### 5.1 权限边界基本正确

提交记录是登录用户自己的记录。前端在未登录时不会请求列表，而是展示登录提示，见 `console/src/views/problems/submissions/SubmissionsView.vue:67-72` 和 `SubmissionsListView.vue:81-99`。

后端 `GET /problems/{problemId}/submissions` 也要求认证，见 `backend-spring/src/main/java/com/ulticode/modules/submission/controller/ProblemSubmissionController.java:47-68`。实际 curl 未登录返回 401，与产品语义一致。

### 5.2 列表和详情共用 `SubmissionVO`，颗粒过粗

`SubmissionVO` 包含 `code`、tests、errorDetail、input/output/expected、user、problem、percentile、memory distribution，见 `backend-spring/src/main/java/com/ulticode/modules/submission/dto/SubmissionVO.java:20-151`。

这对详情页是合理的，但对列表页过重。当前 `findByProblemId()` 直接返回 `SubmissionVO` page，见 `SubmissionServiceImpl.java:191-204`。

建议拆分:

- `SubmissionListItemVO`: id、status、language、runtime、memory、createdAt、notes、problem summary。
- `SubmissionDetailVO`: code、tests、errorDetail、failed input/output、runtime/memory distribution。

前端流程改成:

1. `/problems/{id}/submissions` 只拉列表。
2. 点击某条记录时再调用 `/submissions/{submissionId}` 拉详情。
3. WebSocket 推送到达时只刷新当前页或 patch 当前 item。

### 5.3 前端期待 runtime distribution，后端 VO 没有返回

前端详情图表读取 `submission.runtimeDistBinsMs`，见 `console/src/views/problems/submissions/composables/useSubmissionDetail.ts:110-116`。

实体 `Submission` 有 `runtimeDistBinsMs` 字段，见 `backend-spring/src/main/java/com/ulticode/modules/submission/entity/Submission.java:103-107`；但 `SubmissionVO` 只声明了 `memoryDistBinsMb`，没有 `runtimeDistBinsMs`，见 `SubmissionVO.java:148-151`。

影响:

- Runtime 分布图通常拿不到数据。
- 用户看到“beats xx%”但图表可能为空或只有高亮缺失。

短期修复:

- 在 `SubmissionVO` 加 `runtimeDistBinsMs`。
- `SubmissionServiceImpl.toVO()` 和 `toVO(SubmissionWithProblem)` 都映射该字段。
- 如果数据暂时没有生成，前端图表区域显示“分布数据不足”，不要渲染空图。

### 5.4 提交列表 UI 有一个文案条件反了

`SubmissionsListView` 的 `TableCaption` 在有记录时显示 `noSubmissionsDesc`，无记录时显示空字符串，见 `console/src/views/problems/submissions/SubmissionsListView.vue:101-106`。

这会在列表有数据时显示“不存在提交记录”类文案。应改为:

- 有数据: 显示“共 N 条提交”或不显示 caption。
- 无数据: 由 Empty 组件显示 `noSubmissionsDesc`。

### 5.5 正式评测链路已有数据库与管理端基础

之前旧分析里提到 `test_cases` 缺失，但当前仓库已经补上:

- `V111__create_test_cases.sql` 创建 `test_cases` 表，见 `db-manager/migrations/V111__create_test_cases.sql:4-20`。
- `JudgeWorkerProcessor` 从 `test_cases` 读取官方用例并执行，见 `backend-spring/src/main/java/com/ulticode/modules/queue/processor/JudgeWorkerProcessor.java:128-169`。
- 管理端测试用例 Controller 已存在，见 `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminTestCaseController.java:26-110`。

后续重点不是“从零补表”，而是验证迁移是否已应用、Two Sum hidden cases 是否在当前数据库里、管理端 CRUD 是否通过 E2E 验证。

---

## 6. Run / Submit 逻辑边界

### 6.1 Run 按钮未做登录判断，但后端要求登录

前端 `handleRun()` 只触发运行 token，没有检查登录态，见 `console/src/views/problems/headers/LayoutHeaderCenter.vue:38-43`。后端 `POST /problems/{id}/submissions/run` 要求 `SecurityUtil.getCurrentUserId()` 非空，见 `ProblemSubmissionController.java:128-142`。

影响:

- 匿名用户点击 Run 会进入失败路径，但 UI 没有像 Submit 一样提前提示登录。
- 如果产品希望匿名也能跑样例，应显式放开 run endpoint；如果不希望匿名运行，前端应和 Submit 一样 toast 登录提示。

建议先做产品决策:

- OJ 公开体验优先: 匿名可 Run sample/custom cases，但 Submit 必须登录。
- 资源保护优先: Run/Submit 都必须登录，前端按钮态一致。

### 6.2 Submit 逻辑相对清晰

Submit 前端已检查登录态，见 `LayoutHeaderCenter.vue:56-60`。后端提交后进入 Pending -> queue -> worker -> WebSocket 的链路，符合正式评测语义。

但要继续补:

- 评测结果列表与详情拆 DTO。
- WebSocket 结果到达后只更新当前 problem/submission 对应数据。
- 对 queue/sandbox 失败给用户可理解的状态，而不是泛化失败。

---

## 7. 移动端深链问题

桌面端 `useProblemLayout()` 会读取 URL tab 并同步到 header store，见 `useProblemLayout.ts:144-204`。

但移动端 `MobileProblemLayout.vue` 自己定义 `activeTab = ref("description")`，没有读取 `route.params.tab`，也没有在切 tab 时 push URL，见 `console/src/views/problems/components/MobileProblemLayout.vue:31-71`。

影响:

- 手机访问 `/problems/two-sum/solutions` 会默认显示 description。
- 手机访问 `/problems/two-sum/submissions` 也会默认显示 description。
- 分享链接在移动端不可靠。

修复:

- 在 `MobileProblemLayout` 中读取 `useRoute()` 初始化 activeTab。
- watch `route.params.tab` -> activeTab。
- watch activeTab -> router.push，同桌面端保持一致。
- tab 白名单仍用 `description/solutions/submissions/code/testcases/testresults`，其中非 URL 主 tab 可选择不 push 或 push query。

---

## 8. 修复优先级

### P0: 本周应修

1. 放行公开题解列表 GET
   - 后端: `SecurityConfig` 加 `GET /api/problems/*/solutions`。
   - 验收: 匿名 `curl /api/problems/1/solutions` 返回 200 且有 7 条或分页总数 7。

2. 对齐题解更新方法
   - 前端加 `apiPut` 并改 `updateSolution()`，或后端补 `@PatchMapping`。
   - 验收: 编辑题解保存不再 405。

3. 修移动端 URL tab 同步
   - `MobileProblemLayout` 与 route 双向同步。
   - 验收: 移动 viewport 打开 `/solutions` 直接显示题解 tab。

4. 修提交列表 caption
   - 有记录时不显示 `noSubmissionsDesc`。
   - 验收: 有提交时没有“暂无提交”类文案。

5. 明确 Run 匿名策略
   - 若允许匿名 run: 后端放行 run，限制 rate limit 和 case 数。
   - 若不允许匿名 run: 前端 Run 也做登录提示。

### P1: 下一迭代

1. 题解列表拆 `SolutionListItemVO / SolutionDetailVO`
   - 列表不返回完整 content。
   - 点击详情时再拉正文和评论。

2. 题解服务端筛选排序分页
   - 参数: `page/pageSize/sort/language/search`。
   - 前端移除只对第一页做本地搜索的假象。

3. `userId` 查询参数下线
   - 所有 viewer state 从认证上下文读取。
   - 匿名 viewer fields 返回 null。

4. 提交列表/详情 DTO 拆分
   - 列表轻量。
   - 详情按需加载 code、tests、distribution。

5. 补 `runtimeDistBinsMs`
   - 后端 VO 和 mapper 补字段。
   - 前端为空时优雅降级。

### P2: 后续优化

1. Public/Admin problem detail DTO 分离。
2. Solution `toVO()` 批量化，消除 author/vote/comment/tag/achievement N+1。
3. 题解列表加缓存和失效策略。
4. `solution_count` 只统计 published 且未删除题解。
5. Run result 和 Submission status 共用同一状态字典，避免文案/颜色重复维护。

---

## 9. 推荐目标接口契约

### 9.1 题目详情

```http
GET /problems/slug/{slug}
```

公开可读，返回 public DTO:

```json
{
  "id": 1,
  "slug": "two-sum",
  "title": "Two Sum",
  "difficulty": "HARD",
  "status": "todo",
  "isPremium": false,
  "stats": {
    "acceptanceRate": 49.2,
    "submissionCount": 44,
    "solutionCount": 7
  },
  "detail": {
    "summary": "Test summary",
    "content": "## Test\nContent",
    "constraints": ["1 <= nums.length <= 100"],
    "followUp": "Can you come up with...",
    "hints": ["Use a hash map"],
    "companies": []
  },
  "tags": [{ "id": "array", "label": "数组" }],
  "examples": [],
  "languages": [],
  "interactions": {
    "counts": { "likes": 54300, "dislikes": 1800, "favorites": 1 },
    "viewer": null
  }
}
```

兼容期可保留旧 snake_case 字段，但新前端类型应优先使用 camelCase/public schema。

### 9.2 题解列表

```http
GET /problems/{problemId}/solutions?page=1&pageSize=20&sort=hot&language=javascript&search=hash
```

返回轻量列表:

```json
{
  "items": [
    {
      "id": "sol-001",
      "title": "哈希表解法",
      "summary": "O(n) 时间复杂度...",
      "language": "typescript",
      "tags": ["hash-map", "array"],
      "author": { "id": "user-yuki", "name": "Yuki1", "avatar": "..." },
      "counts": { "views": 151, "comments": 4, "likes": 0, "dislikes": 0 },
      "score": 0,
      "viewerVote": 0,
      "publishedAt": "2026-05-06T15:03:35.672"
    }
  ],
  "total": 7,
  "page": 1,
  "pageSize": 20,
  "totalPages": 1
}
```

详情:

```http
GET /solutions/{solutionId}
```

返回完整 content。

### 9.3 提交记录

列表:

```http
GET /problems/{problemId}/submissions?page=1&pageSize=20
```

返回轻量:

```json
{
  "items": [
    {
      "id": "sub-xxx",
      "status": "Accepted",
      "language": "javascript",
      "runtime": 12,
      "memory": 32.1,
      "createdAt": "2026-05-28T21:00:00"
    }
  ],
  "total": 44
}
```

详情:

```http
GET /submissions/{submissionId}
```

返回 code、tests、errorDetail、runtime/memory distribution。

### 9.4 运行代码

需先确定匿名策略。推荐公开样例运行但加限流:

```http
POST /problems/{problemId}/runs
```

或者保留兼容:

```http
POST /problems/{problemId}/submissions/run
```

要求:

- cases 为空直接 400。
- 自定义 case 无 expected output 时返回 `Ran`，不判定 WA。
- 顶层 verdict 与提交状态字典共用优先级。

---

## 10. 迭代计划

### Sprint 0: 契约冻结与冒烟测试

目标: 先把“应该公开、应该私有、应该轻量、应该详情”的边界写成测试。

任务:

- 增加 API contract smoke tests:
  - 匿名 `GET /problems/slug/two-sum` -> 200。
  - 匿名 `GET /api/problems/1/solutions` -> 200。
  - 匿名 `GET /problems/1/submissions` -> 401。
  - 匿名 `GET /submissions/statuses` -> 200。
- 补前端 API 单测:
  - `mapProblemDetail()` 优先使用 `detail.content`。
  - `fetchSolutionFeed()` 解析 PageResult。
  - `mapSubmission()` 兼容 camel/snake。

验收: 这些测试先红后绿，后续改接口不会再悄悄破。

### Sprint 1: P0 修复

目标: 三个 URL 在匿名/登录状态下都符合产品预期。

任务:

- SecurityConfig 放行公开题解列表 GET。
- `updateSolution` 方法对齐。
- `MobileProblemLayout` 同步 route tab。
- 修 SubmissionsList caption。
- Run 按钮匿名策略落地。

验收:

- 匿名打开 `/solutions` 能看到题解列表，不再落到 follow-up。
- 匿名打开 `/submissions` 看到登录提示。
- 移动端深链不丢 tab。
- 编辑题解保存成功。

### Sprint 2: 题解列表重构

目标: 列表轻量化、服务端分页筛选、去 N+1。

任务:

- 新增 `SolutionListItemVO`。
- 新增规范路径 `GET /problems/{id}/solutions`，老 `/api/problems/{id}/solutions` 保留兼容。
- 服务端实现 `sort/language/search/page/pageSize`。
- 批量查询作者、投票、评论数、topic、badges。
- 前端点击列表项时按需获取详情。

验收:

- 大量题解时首屏响应体显著下降。
- 搜索/排序跨全量结果，不局限当前 20 条。
- SQL 日志不再随题解条数线性爆炸。

### Sprint 3: 提交记录重构

目标: 私有提交列表轻量、详情按需、图表字段闭环。

任务:

- 新增 `SubmissionListItemVO` 与 `SubmissionDetailVO`。
- `GET /problems/{id}/submissions` 返回轻量列表。
- `GET /submissions/{id}` 返回完整详情。
- 补 `runtimeDistBinsMs`。
- 前端选中提交时再拉详情。

验收:

- 提交列表响应不包含 code。
- Runtime/memory 图表都有数据或明确空态。
- WebSocket 结果到达后列表更新准确。

### Sprint 4: Public/Admin DTO 分离

目标: 公开接口不携带管理字段。

任务:

- 拆 `ProblemDetailPublicVO` / `ProblemDetailAdminVO`。
- console API 使用 public VO。
- management 使用 admin VO。
- 移除 public response 中 flag/delete/review 字段。

验收:

- console public response 不再有 `flag_*`、`deleted_*`、`published_by` 等管理字段。
- management 审核/管理功能不受影响。

---

## 11. 回归验证清单

后端:

- `./mvnw test`
- `./mvnw verify -Pci`，如果涉及 test_cases / judge worker。
- `db-manager validate`
- `db-manager migrate --dry-run`，如果新增迁移。

前端 console:

- `pnpm type-check`
- `pnpm test`
- `pnpm build`

运行态:

- `curl -i http://localhost:9001/problems/slug/two-sum`
- `curl -i http://localhost:9001/api/problems/1/solutions`
- `curl -i http://localhost:9001/problems/1/submissions`
- `curl -i http://localhost:9001/submissions/statuses`
- 浏览器验证:
  - `/problems/two-sum/description`
  - `/problems/two-sum/solutions`
  - `/problems/two-sum/submissions`
  - 移动 viewport 下同样验证三条深链。

运维备注:

- 当前 shell 中 `pm2` 不在 PATH，但进程实际存在: `backend-spring/start.cjs`、console Vite、management Vite 都在运行。
- 日志路径来自根 `ecosystem.config.cjs`: `/tmp/ulticode-9001-out.log`、`/tmp/ulticode-9002-out.log`。
- 本次未重启服务，只做只读验证与文档输出。

---

## 12. 最终建议

先不要马上大改布局。当前真正阻塞用户体验的是接口契约，而不是页面结构。

建议执行顺序:

1. 先修 `solutions` 匿名 401、PATCH/PUT、移动深链、caption 这些低风险高收益问题。
2. 再做题解列表轻量化和服务端分页筛选。
3. 最后拆提交列表/详情 DTO 与 public/admin problem DTO。

这样每一步都能独立上线，也能避免把题目详情、题解、提交、评测、管理端测试用例一次性搅在一起。
