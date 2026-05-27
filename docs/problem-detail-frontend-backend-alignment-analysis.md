# Problem Detail 前后端颗粒度与逻辑对齐分析报告

> 生成日期: 2026-05-27 | 分析范围: `http://localhost:9002/problems/two-sum`、console 题目详情页、后端 `/problems/*`、`/problems/{id}/submissions/*`、沙箱执行、提交评测、交互/笔记接口 | 数据来源: 源码审查 + `localhost:9001` 实际 API 响应

---

## 1. 总结

`/problems/two-sum` 当前是“一个公开详情聚合接口 + 前端本地多面板状态 + 两条执行链路”的页面:

- 题面详情: `GET /problems/slug/{slug}` 返回基础题目信息、`detail`、`examples`、`languages`。这条主链路基本可用。
- 运行代码: 前端把当前测试面板里的样例直接传给 `POST /problems/{id}/submissions/run`，后端同步调用 Docker 沙箱执行。这不是正式评测，只是“前端传什么就跑什么”。
- 提交代码: 前端只提交 `language/code` 到 `POST /problems/{id}/submissions`，后端入库并投递 Redis judge queue，worker 再从 `test_cases` 表读取官方用例评测。
- 交互功能: 投票/收藏/笔记是独立接口，不在详情接口里闭环；未登录或接口缺失时，页面会显示 0 或静默失败。

运行态验证:

- `GET http://localhost:9001/problems/slug/two-sum` 返回 `200`，包含 `detail.summary="Test summary"`、`detail.content="## Test\nContent"`、3 个 examples、JavaScript/TypeScript 两种语言。
- 截图中的题面只展示 `Test summary`，因为前端把 `detail.summary` 同时当成 `content/summary` 使用，忽略了后端返回的 `detail.content`。
- `POST http://localhost:9001/problems/1/submissions/run` 未登录也能进入 Controller，返回 `userId="anonymousUser"`，两条样例均为 `Runtime Error`，错误细节被清洗为笼统的 `Runtime error`。
- `POST http://localhost:9001/problems/1/submissions` 未登录返回 `404 User not found`，不是预期的 `401 Unauthorized`。
- `GET http://localhost:9001/edge-operations/PROBLEM/1` 未登录返回 `401`，因此公开页右上角点赞/收藏计数没有公共来源。
- `GET http://localhost:9001/problems/1/note` 返回 `404 Not found`，前端笔记抽屉对应后端接口不存在。

核心判断: 详情展示层可用，但“题面内容、用户交互、运行代码、正式提交评测、测试用例管理、安全边界”没有统一成一个稳定契约。现在最需要先收敛接口颗粒度和数据模型，再修页面体验。

---

## 2. 页面颗粒度

### 2.1 路由与面板

前端路由 `console/src/router/index.ts:121` 把 `/problems/:slug/:tab?` 指向 `ProblemDetailView.vue`。详情页读取 `route.params.slug`，并通过 `useProblemDetail(slug)` 拉取数据: `console/src/views/problems/ProblemDetailView.vue:60-74`。

页面面板由数字 ID 映射:

- `1` 题目描述
- `2` 题解
- `3` 提交记录
- `4` 代码
- `5` 测试用例
- `6` 测试结果

证据: `ProblemDetailView.vue:142-150`。

这个页面颗粒本身是合理的，接近 LeetCode: 左侧题面/题解/提交记录，右侧代码，下方测试/结果。问题不在布局，而在每个面板背后的 API 契约不一致。

### 2.2 题面描述

前端 `fetchProblemDetailById()` 对 slug 调用 `/problems/slug/{id}`: `console/src/api/problem-detail.ts:53-61`。后端 Controller 对应 `GET /problems/slug/{slug}`: `ProblemController.java:79-87`。

后端 DTO 同时有:

- `detail.summary`
- `detail.content`
- `detail.constraints_json`
- `detail.hints`
- `detail.follow_up`
- `examples`
- `languages`

证据: `ProblemDetailResponse.java:113-128`、`ProblemServiceImpl.java:347-352`。

但前端映射时把 `detail.summary` 写入 `content` 和 `summary`，没有使用 `detail.content`: `console/src/api/problem-detail.ts:125-128`。`DescriptionMarkdown` 只渲染 `description.content`: `DescriptionMarkdown.vue:53-55`。因此 live API 虽然返回了 `detail.content="## Test\nContent"`，页面仍只显示 `Test summary`。

建议契约:

- `summary`: 列表摘要或题面短描述。
- `content`: 题目完整 Markdown 正文。
- 前端 `content: detail.content ?? detail.summary ?? response.summary ?? ""`。
- 只有在 `content` 缺失时才降级使用 `summary`。

### 2.3 examples 同时承担“题面示例”和“测试样例”

后端 `problem_examples` 表有 `input_text/output_text/explanation/inputs`: `V2__problem_schema.sql:43-53`，Two Sum 三条样例种子在 `V2__problem_schema.sql:216-218`。

前端把同一份 `examples` 映射成两种对象:

- 题面 examples: `input_text/output_text/explanation` -> `DescriptionMarkdown`
- 测试用例: `inputs/output` -> `TestCaseView`

证据: `console/src/api/problem-detail.ts:64-85`、`:134-135`。

这个设计短期可行，但颗粒需要命名清楚: `examples` 是公开样例，不是正式 judge 用例。运行代码可以默认跑 examples，正式提交必须跑 official test cases。

### 2.4 测试用例面板

`TestCaseView` 将 props 复制到本地，并同步到 bottom panel store: `console/src/views/problems/test/TestCaseView.vue:48-60`。用户新增用例时，只根据当前样例输入结构生成空值，不要求 expected output: `TestCaseView.vue:27-45`、`:140-148`。

风险:

- 新增用例没有 `output`，后端会用空字符串作为 expected output，比对结果会天然失败或误导。
- 面板 tab 文案总是本地生成 `示例 N`: `TestCaseView.vue:189-207`，没有使用后端 example label。
- 这套测试用例只存在前端内存里，不会落库，也不会影响正式提交。

建议:

- 运行用例 DTO 显式拆为 `sampleCases` 和 `customCases`。
- 自定义用例允许不填 expected output 时，应显示“仅运行，不判定 Accepted/WA”，后端返回 `status=Ran` 或 `No Expected Output`。
- 如果仍要判定，新增用例必须要求 expected output。

### 2.5 运行结果面板

`TestResultsView` 读取 `runResult.cases`，按 `caseLabel` 联动测试用例 tab: `console/src/views/problems/test/TestResultsView.vue:27-52`。它能展示每个 case 的 input/output/expected。

不对齐点:

- 后端 `RunResultDTO` 返回 `errorMessage`，前端模板只看 `error_message`: `TestResultsView.vue:168-176`。Compile Error 的错误信息可能展示不出来。
- 前端把总 verdict 的 `Accepted` 显示为 `problem.status.solved`，`Wrong Answer` 显示为 `problem.status.attempted`: `TestResultsView.vue:55-65`。这更像用户题目状态，不是评测 verdict 文案。
- 后端顶层 verdict 只有 `Accepted` 或 `Wrong Answer`: `CodeExecutionService.java:57`。即使所有 case 都是 Runtime Error，顶层也会是 Wrong Answer，细节在 cases 里。

建议:

- 前端统一读取 `errorMessage ?? error_message`。
- verdict 文案用 submission 状态字典，不复用题目状态。
- 后端顶层 verdict 应按优先级汇总: Runtime Error > Compile Error > TLE > MLE > WA > Accepted，与异步 worker 的 `determineVerdict()` 逻辑一致。

---

## 3. 接口颗粒度

### 3.1 详情接口颗粒度基本合理，但缺用户态与交互态

当前详情接口返回:

- 题目基础字段
- `detail`
- `examples`
- `languages`

后端组装位置: `ProblemServiceImpl.java:286-335`。

缺口:

- `submission_count/solution_count/tags` 被硬编码为 0 或空数组: `ProblemServiceImpl.java:313-315`。
- `problem_details.likes/dislikes/interactions` 没有进入 `ProblemDetailResponse`。
- 前端会附加 `?userId=...`: `console/src/api/problem-detail.ts:57-60`，但后端详情 Controller 不接收也不使用这个参数。

建议:

- 公共详情接口返回公开计数: `likes/dislikes/favorites/submissionCount/solutionCount/tags`。
- 用户态字段从认证上下文读取，不由前端传 `userId`。
- 如果不想让详情接口过重，可加并行公共接口 `GET /problems/{id}/interactions`，但至少点赞/收藏计数应该允许匿名读取。

### 3.2 运行接口是“前端样例运行”，不应和正式评测混同

前端点击运行时，`LayoutHeaderCenter.handleRun()` 只触发 bottom panel token: `LayoutHeaderCenter.vue:40-52`。`useProblemDetail` 监听该 token，取当前代码和测试用例调用 `runSubmission()`: `useProblemDetail.ts:58-82`。API 客户端发送 `language/code/testCases`: `console/src/api/submission.ts:115-155`。

后端 `POST /problems/{problemId}/submissions/run` 直接把请求交给 `CodeExecutionService.execute()`: `ProblemSubmissionController.java:128-142`。`CodeExecutionService` 如果没有 testCases，会返回 `verdict="Accepted"` 且 `totalCases=0`: `CodeExecutionService.java:35-38`、`CodeExecutionHelperImpl.java:321-332`。

建议:

- 将接口命名或文档明确为 `Run sample/custom cases`。
- 空用例不应 Accepted，应返回 `BAD_REQUEST` 或 `No Test Cases`。
- run endpoint 可以允许匿名，但必须明确产品语义，并在 `SecurityConfig` 中单独放开 `POST /problems/{id}/submissions/run`，而不是放开整个 `/problems/**`。

### 3.3 提交接口是“正式评测”，但官方测试用例链路断裂

前端提交只发送 `language/code`: `console/src/views/problems/headers/LayoutHeaderCenter.vue:54-79`、`console/src/api/submission.ts:104-112`。

后端提交:

- Controller 从 path 设置 `problemId`: `ProblemSubmissionController.java:100-125`
- Service 验证用户、题目、语言，插入 `submissions`，再投递 judge queue: `SubmissionServiceImpl.java:81-124`
- Worker 从 `test_cases` 表读取官方用例: `JudgeWorkerProcessor.java:123-136`

但是当前 Flyway 迁移里没有创建 `test_cases` 表，只有 `problem_examples` 和 `problem_languages`: `V2__problem_schema.sql:43-66`。实体和 mapper 却指向 `test_cases`: `TestCase.java:12`、`TestCaseMapper.java:13-34`。管理端也预定义了 `/admin/problems/{problemId}/test-cases` API: `management/src/api/admin/test-cases.ts:68-132`，但后端没有对应 Controller。

影响:

- 正式提交无法可靠评测隐藏用例。
- 即使补上表，如果没有 seed/import official cases，worker 会把提交标记为 `System Error`: `JudgeWorkerProcessor.java:123-129`。
- 管理端测试用例编辑是“幽灵 API”: 前端写好了，后端没有实现。

建议优先级最高:

1. 新增 Flyway 迁移创建 `test_cases` 表，字段与 `TestCase` 实体一致。
2. 给 Two Sum 和现有题目导入 sample + hidden cases。
3. 实现管理端 `/admin/problems/{id}/test-cases` CRUD/import/reorder/export。
4. Worker 读取 `test_cases` 时保留结构化输入，而不是把整段 `input_text` 作为单个 `input` 参数。

### 3.4 语言契约不一致: 前端提供 TypeScript，后端不支持 TypeScript 执行

Two Sum live detail 返回 JavaScript 和 TypeScript 两种语言。种子中也有 TypeScript starter code: `V2__problem_schema.sql:229`。前端会将语言 value 原样传给 run/submit。

后端执行和提交支持列表只有:

- `javascript`
- `python`
- `java`
- `c`
- `cpp`

证据: `CodeExecutionHelper.java:15-17`、`SubmissionServiceImpl.java:75-77`。

影响:

- 用户选择 TypeScript 后运行会得到 unsupported language。
- 用户选择 TypeScript 后正式提交也会被拒绝。
- 截图里默认语言是 JavaScript，所以初始体验没暴露，但语言下拉一切到 TypeScript 就断。

建议:

- 短期: 不向前端返回 `typescript`，只返回后端可执行语言。
- 中期: 后端支持 TypeScript，执行前用 `ts-node` 或 `tsc` 转译到 JS，并统一沙箱镜像。
- 同步更新 `problem_languages` 种子、后端 supported languages、前端语言列表。

### 3.5 交互/收藏/笔记颗粒度没有闭环

右上角投票/收藏组件 `ProblemEdgeOperations` 会优先用 `problem.interactions`，否则仅在登录后调用 `/edge-operations/{targetType}/{targetId}`: `ProblemEdgeOperations.vue:73-86`。

但:

- 当前详情响应没有 `interactions`。
- `GET /edge-operations/PROBLEM/1` 未登录返回 401。
- 因此公开页面计数显示 0，即使数据库 `problem_details` 有 likes/dislikes 字段。

笔记更直接: 前端调用 `/problems/{problemId}/note`: `console/src/api/interaction.ts:18-30`、`ProblemNotesDrawer.vue:23-40`，但后端没有该 endpoint，localhost 返回 404。

建议:

- 公开计数: `GET /edge-operations/{targetType}/{targetId}` 允许匿名读取，viewer 状态为默认值。
- 写操作: `POST /edge-operations` 保持登录和 CSRF。
- 笔记: 如果产品需要，补 `problem_notes` 的 Controller/Service；如果暂不做，隐藏笔记按钮。

---

## 4. 安全边界问题

### 4.1 `/problems/**` 放开过大，写接口也被公开

`SecurityConfig.PUBLIC_ENDPOINTS` 里包含 `/problems/**`: `SecurityConfig.java:40-52`，并在授权规则中 `permitAll`: `SecurityConfig.java:107-112`。

这会放开:

- `POST /problems/{id}/submissions`
- `POST /problems/{id}/submissions/run`
- `GET /problems/{id}/submissions`
- `GET /problems/{id}/submissions/best`
- 未来任何 `/problems/**` 下的写接口

Controller 注释说 submissions/run “Requires authentication”，但实际未登录可以进入: `ProblemSubmissionController.java:92-142`。

### 4.2 anonymousUser 被当成有效 userId

`SecurityUtil.getCurrentUserId()` 只判断 `authentication.isAuthenticated()`，然后返回 `authentication.getName()`: `SecurityUtil.java:20-24`。Spring anonymous authentication 会满足 authenticated，于是返回 `anonymousUser`。

运行接口因此返回 `userId="anonymousUser"`。提交接口继续查用户表，最后变成 `404 User not found`: `SubmissionServiceImpl.java:104-108`。

建议:

- `SecurityUtil.getCurrentUserId()` 与 `isAuthenticated()` 一样排除 `anonymousUser`。
- 安全配置改为方法 + path 粒度:
  - `GET /problems`, `GET /problems/{id}`, `GET /problems/slug/{slug}`, `GET /problems/{id}/adjacent`, `GET /problems/random` 公开。
  - `POST /problems/{id}/submissions/run` 是否公开由产品决定，若公开要限流更严格。
  - `POST /problems/{id}/submissions`、submissions list/best、note、写操作要求登录。
- 加 Controller/Security 测试覆盖匿名访问行为。

---

## 5. 沙箱运行问题

当前 localhost run 返回 Runtime Error。源码和运行态有两个直接风险:

1. 本地没有 `ulticode-sandbox:latest` 镜像。`docker images | rg 'ulticode|sandbox'` 无结果。
2. `ProcessBuilder` 不会展开 `$(pwd)`，但 Docker volume 参数写的是字面量 `"$(pwd)/docker/sandbox:/seccomp-profile:ro"`: `SandboxServiceImpl.java:172`、`:214`。

后端还会过滤包含 `docker` 或 `OCI runtime` 的错误行: `CodeExecutionHelperImpl.java:306-318`，所以用户和前端只能看到笼统的 `Runtime error`。

建议:

- 启动前检查沙箱镜像是否存在，缺失时给出明确健康检查失败。
- volume source 使用绝对路径，优先来自配置 `seccomp-profile-path`，不要写 `$(pwd)`。
- 对系统错误保留内部日志，同时给前端返回可分层的错误码: `SANDBOX_IMAGE_NOT_FOUND`、`SANDBOX_CONFIG_ERROR`、`SANDBOX_RUNTIME_ERROR`。
- run endpoint 的 case status 可以是 `System Error`，不要都折成 Runtime Error。

---

## 6. 推荐目标契约

### 6.1 页面读取

`GET /problems/slug/{slug}`

返回:

- `id/slug/title/difficulty/status/isPremium/hasSolution`
- `stats`: `acceptanceRate/submissionCount/solutionCount`
- `detail`: `summary/content/constraints/followUp/hints/companies/tags`
- `examples`: 公开样例，含 `inputText/outputText/inputs/explanation`
- `languages`: 只包含后端支持执行的语言，或带 `runnable/submittable` 标记
- `interactions`: 公开 counts + viewer state

### 6.2 运行代码

`POST /problems/{id}/runs`

语义: 运行 sample/custom cases，不落正式 submissions。

请求:

- `language`
- `code`
- `cases`
  - `id`
  - `kind`: `sample | custom`
  - `inputs`
  - `expectedOutput?`

返回:

- `runId`
- `verdict`: 支持 Accepted/WA/RE/CE/TLE/MLE/System Error/No Expected Output
- `cases`
- `errorMessage`

### 6.3 正式提交

`POST /problems/{id}/submissions`

语义: 入库，异步评测 official cases。

请求:

- `language`
- `code`

后端:

- 只从认证上下文拿 userId。
- 从 `test_cases` 读取 official cases。
- `test_cases` 支持 sample/hidden。
- 通过 queue + websocket/polling 返回最终状态。

### 6.4 测试用例管理

实现管理端已经预留的接口:

- `GET /admin/problems/{id}/test-cases`
- `POST /admin/problems/{id}/test-cases`
- `PUT /admin/problems/{id}/test-cases/{caseId}`
- `DELETE /admin/problems/{id}/test-cases/{caseId}`
- `POST /admin/problems/{id}/test-cases/bulk`
- `PUT /admin/problems/{id}/test-cases/reorder`
- `GET /admin/problems/{id}/test-cases/export`

---

## 7. 修复路线

### P0: 先恢复正确边界

1. 收紧 `SecurityConfig`: 移除 `/problems/**`，逐条放开公开 GET。
2. 修复 `SecurityUtil.getCurrentUserId()`，排除 anonymousUser。
3. 决定 run endpoint 是否允许匿名；如果允许，单独配置。

### P1: 打通正式评测

1. 新增 `test_cases` Flyway 迁移和 seed。
2. 实现 admin test-cases 后端接口。
3. Worker 改为结构化输入，不把整段 `input_text` 包成一个 `input` 参数。
4. 修复沙箱镜像/volume 路径/错误码。

### P2: 对齐详情体验

1. 前端详情映射使用 `detail.content`。
2. 后端详情返回真实 tags、submissionCount、solutionCount、interactions。
3. 公开 edge-operation counts，登录态再返回 viewer state。
4. 补 problem note 后端或隐藏笔记入口。

### P3: 语言与结果模型

1. 要么移除 TypeScript 语言，要么后端支持 TypeScript 执行。
2. 前后端统一 `errorMessage/passedCases/totalCases/problemId` 命名。
3. run 与 submit 共用 verdict 字典。

---

## 8. 结论

当前页面“看起来像完整刷题页”，但真实闭环只完成了详情读取和样例编辑展示。运行代码依赖前端传参和本地 Docker 沙箱，正式提交依赖尚未落库的 `test_cases` 官方用例体系，用户交互与笔记接口也没有接上。建议先按 P0/P1 修安全和评测底座，再做 P2/P3 的页面体验对齐。这样改完后，题目页的颗粒会变清楚: 公开详情、样例运行、正式提交、用户交互、管理端用例维护各自独立，但数据契约一致。
