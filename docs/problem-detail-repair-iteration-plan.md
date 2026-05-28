# Problem Detail 修复与迭代计划

> 生成日期: 2026-05-28 | 目标页面: `http://localhost:9002/problems/two-sum` | 关联分析: `docs/problem-detail-frontend-backend-alignment-analysis.md`

---

## 1. 目标

把题目详情页从“展示可用但评测/交互链路松散”的状态，收敛成清晰的在线判题闭环:

- 公开详情可匿名浏览，字段语义稳定。
- 样例运行与正式提交分层明确。
- 正式提交只跑后端官方测试用例，支持 hidden cases。
- 用户态行为只从认证上下文读取，不让前端传 `userId`。
- 题目交互、收藏、笔记有明确的匿名/登录边界。
- 管理端测试用例维护链路接上后端与数据库。

非目标:

- 不在本轮重做页面布局。
- 不一次性支持所有语言；先保证返回给前端的语言一定可执行。
- 不把 run endpoint 的样例运行替代正式 judge。

---

## 2. 当前关键问题

### P0 阻断级

1. `/problems/**` 被整体 `permitAll`，导致提交、运行、用户提交记录等写/用户接口也被公开。
2. `SecurityUtil.getCurrentUserId()` 会把 Spring anonymous authentication 的 `anonymousUser` 当成有效用户。
3. 正式提交依赖 `test_cases` 表，但迁移中没有该表。
4. 管理端已有 test-cases API 客户端，但后端没有对应接口。
5. 前端提供 `typescript`，后端 run/submit 不支持 `typescript`。

### P1 高优先级

1. 题面使用 `detail.summary` 渲染正文，忽略后端 `detail.content`。
2. 运行接口空测试用例会返回 `Accepted`。
3. run 顶层 verdict 只会是 `Accepted/Wrong Answer`，无法表达 Runtime Error、Compile Error、TLE。
4. 沙箱 Docker volume 使用字面量 `$(pwd)`，ProcessBuilder 不会展开。
5. 本地缺少 `ulticode-sandbox:latest` 时，错误被清洗成泛化 `Runtime error`。

### P2 体验与数据一致性

1. 详情接口 `submission_count/solution_count/tags` 被硬编码为空或 0。
2. `interactions` 不在详情接口里，匿名读取 edge-operation counts 会 401。
3. 笔记前端调用 `/problems/{id}/note`，后端无接口。
4. 自定义运行用例没有 expected output 时，前端和后端没有“只运行不判定”的状态。
5. 前端字段兼容不完整: `errorMessage` vs `error_message`、`passedCases` vs `passed_cases`。

---

## 3. 目标接口契约

### 3.1 题目详情

`GET /problems/slug/{slug}`

公开匿名可读。

推荐响应结构:

```json
{
  "id": 1,
  "slug": "two-sum",
  "title": "Two Sum",
  "difficulty": "Easy",
  "status": "todo",
  "isPremium": false,
  "hasSolution": true,
  "stats": {
    "acceptanceRate": 49.2,
    "submissionCount": 1234,
    "solutionCount": 12
  },
  "detail": {
    "summary": "短摘要",
    "content": "完整 Markdown 正文",
    "constraints": ["1 <= nums.length <= 100"],
    "followUp": "Can you...",
    "hints": ["Use a hash map"],
    "companies": [{ "id": "amazon", "name": "Amazon" }],
    "tags": [{ "id": "array", "label": "数组", "slug": "array" }]
  },
  "examples": [
    {
      "id": "ex-two-sum-1",
      "inputText": "nums = [2,7,11,15], target = 9",
      "outputText": "[0,1]",
      "explanation": "...",
      "inputs": [
        { "name": "nums", "value": "[2,7,11,15]" },
        { "name": "target", "value": "9" }
      ]
    }
  ],
  "languages": [
    {
      "id": "lang-js",
      "label": "JavaScript",
      "value": "javascript",
      "style": "javascript",
      "starterCode": "..."
    }
  ],
  "interactions": {
    "counts": { "likes": 54300, "dislikes": 1800, "favorites": 0 },
    "viewer": { "reaction": null, "saved": false }
  }
}
```

兼容策略:

- 短期保留旧字段 `acceptance_rate/detail.constraints_json/detail.follow_up/starter_code`。
- 前端 mapper 统一兼容 snake_case 和 camelCase。
- 后端内部可以继续用实体原字段，但 public DTO 要收敛到稳定命名。

### 3.2 样例运行

建议新接口:

`POST /problems/{id}/runs`

也可短期保留旧接口:

`POST /problems/{id}/submissions/run`

语义: 同步运行 sample/custom cases，不创建正式 submission。

请求:

```json
{
  "language": "javascript",
  "code": "function twoSum(nums, target) { ... }",
  "cases": [
    {
      "id": "ex-two-sum-1",
      "kind": "sample",
      "label": "示例 1",
      "inputs": [
        { "name": "nums", "value": "[2,7,11,15]" },
        { "name": "target", "value": "9" }
      ],
      "expectedOutput": "[0,1]"
    }
  ]
}
```

返回:

```json
{
  "runId": "uuid",
  "problemId": 1,
  "verdict": "Accepted",
  "runtime": "12ms",
  "memory": "32.1MB",
  "passedCases": 1,
  "totalCases": 1,
  "errorMessage": null,
  "cases": [
    {
      "caseId": "ex-two-sum-1",
      "caseLabel": "示例 1",
      "status": "Accepted",
      "runtime": "12ms",
      "memory": "32.1MB",
      "output": "[0,1]",
      "expectedOutput": "[0,1]",
      "inputs": []
    }
  ]
}
```

规则:

- `cases` 为空返回 400。
- `expectedOutput` 缺失时返回 `status=Ran` 或 `No Expected Output`，不计入 AC/WA。
- 顶层 verdict 按优先级汇总: System Error > Compile Error > Runtime Error > MLE > TLE > WA > Ran > Accepted。

### 3.3 正式提交

`POST /problems/{id}/submissions`

必须登录。

请求:

```json
{
  "language": "javascript",
  "code": "..."
}
```

后端规则:

- userId 只从 `SecurityContext` 读取。
- 创建 `submissions` 状态为 `Pending`。
- 投递 Redis judge queue。
- Worker 从 `test_cases` 表读取 sample + hidden official cases。
- WebSocket 推送最终结果，前端提交记录也可轮询。

### 3.4 测试用例管理

管理端接口:

- `GET /admin/problems/{id}/test-cases`
- `GET /admin/problems/{id}/test-cases/{caseId}`
- `POST /admin/problems/{id}/test-cases`
- `PUT /admin/problems/{id}/test-cases/{caseId}`
- `DELETE /admin/problems/{id}/test-cases/{caseId}`
- `POST /admin/problems/{id}/test-cases/bulk`
- `PUT /admin/problems/{id}/test-cases/reorder`
- `GET /admin/problems/{id}/test-cases/export`

必须 ADMIN/SUPER_ADMIN。

---

## 4. 数据库计划

### 4.1 新增 `test_cases` 表

新增 Flyway:

`db-manager/migrations/V111__test_cases_schema.sql`

建议结构:

```sql
CREATE TABLE `test_cases` (
  `id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `problem_id` bigint NOT NULL,
  `is_sample` tinyint(1) NOT NULL DEFAULT 0,
  `is_hidden` tinyint(1) NOT NULL DEFAULT 1,
  `test_order` int NOT NULL DEFAULT 0,
  `input_text` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `output_text` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `explanation` text COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `constraints` json DEFAULT NULL,
  `inputs` json DEFAULT NULL,
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `test_cases_problem_id_order_idx` (`problem_id`, `test_order`),
  KEY `test_cases_problem_id_hidden_idx` (`problem_id`, `is_hidden`),
  CONSTRAINT `test_cases_problem_id_fkey`
    FOREIGN KEY (`problem_id`) REFERENCES `problems` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
);
```

说明:

- 当前 `TestCase` entity 没有 `inputs` 字段，建议补上。否则 worker 只能把 `input_text` 整体当一个参数。
- `input_text` 保留给展示和导入导出，`inputs` 用于结构化执行。
- sample cases 可以从 `problem_examples` 同步生成，但不能只依赖 examples。

### 4.2 Seed 策略

Two Sum 至少:

- 3 条 sample，与 `problem_examples` 一致。
- 5-8 条 hidden，覆盖:
  - 最小长度
  - 负数
  - 重复值
  - 解在末尾
  - target 为 0
  - 多位数

示例:

```sql
INSERT INTO `test_cases`
(`id`, `problem_id`, `is_sample`, `is_hidden`, `test_order`, `input_text`, `output_text`, `explanation`, `inputs`)
VALUES
('tc-two-sum-001', 1, 1, 0, 1,
 'nums = [2,7,11,15], target = 9',
 '[0,1]',
 'sample 1',
 '[{"name":"nums","value":"[2,7,11,15]"},{"name":"target","value":"9"}]');
```

### 4.3 回滚

Flyway 不做 destructive rollback。若需要禁用:

- worker 发现 `test_cases` 不存在或为空时返回 `System Error`，不删除表。
- 新接口上线前用 feature flag 控制管理端入口。

---

## 5. 后端修复计划

### 5.1 安全边界

文件:

- `backend-spring/src/main/java/com/ulticode/common/config/SecurityConfig.java`
- `backend-spring/src/main/java/com/ulticode/common/util/SecurityUtil.java`

动作:

1. 移除 `PUBLIC_ENDPOINTS` 中的 `/problems/**`。
2. 显式放开只读接口:
   - `GET /problems`
   - `GET /problems/{id}`
   - `GET /problems/slug/{slug}`
   - `GET /problems/{id}/adjacent`
   - `GET /problems/random`
3. 决定 run endpoint 是否匿名:
   - 推荐匿名允许，但作为单独 path 放开并强化 rate limit。
4. `SecurityUtil.getCurrentUserId()` 排除 anonymousUser。

验收:

- 未登录 `POST /problems/1/submissions` 返回 401。
- 未登录 `GET /problems/slug/two-sum` 返回 200。
- 未登录 `POST /problems/1/submissions/run` 按产品决策返回 200 或 401，但不能误写 `anonymousUser`。

### 5.2 详情 DTO 聚合

文件:

- `ProblemDetailResponse.java`
- `ProblemServiceImpl.java`
- `ProblemTagRelationMapper.java`
- `SubmissionMapper.java`
- `SolutionMapper.java`
- `EdgeOperationMapper.java`

动作:

1. `detail.content` 正式返回完整题面。
2. `tags` 返回真实标签。
3. `submissionCount/solutionCount` 返回真实计数。
4. `interactions` 返回公开 counts。
5. viewer state 从认证上下文读取，匿名为 null/false。

验收:

- `GET /problems/slug/two-sum` 的 `detail.content` 有正文。
- `tags` 非空时前端能显示标签。
- 匿名也能看到 likes/dislikes/favorites 计数。

### 5.3 Run API 和 verdict

文件:

- `RunSubmissionDTO.java`
- `RunResultDTO.java`
- `ProblemSubmissionController.java`
- `CodeExecutionService.java`
- `CodeExecutionHelperImpl.java`
- `SandboxServiceImpl.java`

动作:

1. 支持新字段 `cases`，兼容旧字段 `testCases`。
2. 空 cases 返回 400。
3. 顶层 verdict 按优先级汇总。
4. 统一 `errorMessage`。
5. `expectedOutput` 缺失时返回 `Ran` 或 `No Expected Output`。
6. 错误类型区分 compile/runtime/system。

验收:

- 正确 JS 运行 three samples 得到 Accepted。
- 错误答案得到 Wrong Answer。
- 语法错误得到 Compile Error 或 Runtime Error，顶层一致。
- 空测试用例返回 400。

### 5.4 沙箱

文件:

- `SandboxServiceImpl.java`
- `DockerSandboxConfig.java`
- `docker/sandbox/Dockerfile`
- `docker/sandbox/seccomp-profile.json`
- `application.yml`

动作:

1. volume source 使用绝对路径。
2. 启动/执行前检查镜像是否存在，给明确错误。
3. `seccomp-profile-path` 真正参与 Docker command。
4. Dockerfile 安装 node/python/java/gcc/g++，如果支持 TypeScript 则加 `typescript/ts-node`。
5. 错误输出保留内部日志，前端返回稳定错误码。

验收:

- 本地 `docker build -t ulticode-sandbox:latest -f docker/sandbox/Dockerfile docker/sandbox` 成功。
- run endpoint 不再因为镜像/volume 返回泛化 Runtime Error。

### 5.5 正式提交和 worker

文件:

- `JudgeWorkerProcessor.java`
- `TestCase.java`
- `TestCaseMapper.java`
- `SubmissionServiceImpl.java`

动作:

1. `TestCase` 增加 `inputs` 字段。
2. Worker 使用 `inputs` 结构化构造 `RunSubmissionDTO`。
3. 官方用例为空时，保留 `System Error`，但错误 notes 更明确。
4. 更新 `test_details` 保存 input/output/expected/status/time/memory。

验收:

- 提交正确 Two Sum 代码最终 Accepted。
- 提交错误代码最终 Wrong Answer。
- hidden case 可让只过样例的代码失败。

### 5.6 管理端 test-cases 后端

新增文件建议:

- `modules/admin/controller/AdminTestCaseController.java`
- `modules/admin/service/AdminTestCaseService.java`
- `modules/admin/service/impl/AdminTestCaseServiceImpl.java`
- `modules/admin/dto/TestCaseVO.java`
- `modules/admin/dto/CreateTestCaseDTO.java`
- `modules/admin/dto/UpdateTestCaseDTO.java`
- `modules/admin/dto/BulkImportTestCasesDTO.java`

动作:

1. 实现 CRUD。
2. bulk import 支持 replace existing。
3. reorder 只更新 `test_order`。
4. export 返回 JSON。
5. 校验 `problemId` 存在。
6. 校验 `input_text/output_text` 非空。

验收:

- management 现有 `testCasesApi` 全部请求有后端响应。
- 后端单测覆盖 CRUD/import/reorder。

---

## 6. 前端修复计划

### 6.1 详情 mapper

文件:

- `console/src/api/problem-detail.ts`
- `console/src/types/problem-detail.ts`
- `console/src/views/problems/description/DescriptionView.vue`

动作:

1. `content` 优先取 `detail.content`。
2. `constraints_json` 映射为 `constraints`，兼容 `constraints`。
3. `follow_up` 映射为 `followUp`。
4. `starter_code` 映射为 `starterCode`。
5. `interactions`、`stats`、`tags` 类型补齐。

验收:

- two-sum 题面展示完整 Markdown。
- 示例、约束、提示仍正常。

### 6.2 运行用例体验

文件:

- `TestCaseView.vue`
- `useProblemDetail.ts`
- `console/src/api/submission.ts`
- `TestResultsView.vue`

动作:

1. 自定义用例增加 expected output 输入，或明确“不判定”。
2. 请求 DTO 改为 `cases`，兼容旧 `testCases`。
3. 错误信息读取 `errorMessage ?? error_message`。
4. verdict 文案使用 submission 状态字典。
5. run loading 绑定真实请求，而不是固定 1.2 秒动画。

验收:

- 点击运行期间按钮真实 loading。
- 运行成功/失败/编译错误都有明确显示。
- 自定义用例无 expected output 不被判成 WA。

### 6.3 提交与提交记录

文件:

- `LayoutHeaderCenter.vue`
- `SubmissionsView.vue`
- `console/src/api/submission.ts`

动作:

1. 未登录点击提交跳登录或弹登录提示，不发请求。
2. 提交成功显示 `Pending`，并跳转提交记录。
3. 支持轮询或 websocket 更新最新提交状态。
4. 提交失败展示后端 message，不使用泛化 `saveFailed`。

验收:

- 未登录提交不再出现 User not found。
- 登录提交后能看到 Pending/Judging/Accepted 等状态。

### 6.4 语言列表

短期动作:

- 前端过滤 `runnable/submittable=false` 的语言，或后端不返回 TypeScript。

中期动作:

- 如果支持 TypeScript，前端保留 TypeScript，后端沙箱负责转译。

验收:

- 下拉中每一种语言都能 run/submit。

### 6.5 交互和笔记

文件:

- `ProblemEdgeOperations.vue`
- `ProblemSaveButton.vue`
- `ProblemNotesDrawer.vue`
- `console/src/api/interaction.ts`

动作:

1. 匿名可加载 counts。
2. 未登录写操作弹登录提示。
3. 如果后端暂不做笔记，隐藏笔记按钮。
4. 如果后端做笔记，补 loading/error/empty 状态。

验收:

- 匿名页面右上角不再总是 0。
- 笔记入口不会打开后 404。

---

## 7. 迭代拆分

### Iteration 0: 安全止血

预计 0.5-1 天。

范围:

- 修 `SecurityConfig`。
- 修 `SecurityUtil.getCurrentUserId()`。
- 加安全测试。
- 前端未登录提交拦截。

验收:

- 匿名提交返回 401 或前端不发请求。
- 匿名详情仍 200。
- 匿名 run 行为符合产品决策。

### Iteration 1: 正式评测底座

预计 2-3 天。

范围:

- `test_cases` 迁移。
- Two Sum sample/hidden seed。
- `TestCase.inputs` 字段。
- worker 使用 official cases。
- 修沙箱路径和镜像检查。

验收:

- Two Sum 正确提交 Accepted。
- 错误提交 Wrong Answer。
- hidden case 生效。

### Iteration 2: 管理端测试用例维护

预计 2 天。

范围:

- AdminTestCaseController/Service/DTO。
- CRUD/import/export/reorder。
- 管理端现有 test-cases 页面联调。

验收:

- 管理端能新增/编辑/导入/排序用例。
- 正式提交读取更新后的用例。

### Iteration 3: 详情契约和前端体验

预计 1.5-2 天。

范围:

- 前端 mapper 使用 `detail.content`。
- 详情返回 tags/stats/interactions。
- run result 状态和 loading 修复。
- TypeScript 语言短期过滤。

验收:

- 题面显示完整正文。
- 运行结果状态准确。
- 下拉语言均可执行。

### Iteration 4: 用户交互与笔记

预计 1-2 天。

范围:

- 匿名可读 edge counts。
- 登录态 viewer state。
- problem notes 后端，或前端隐藏。

验收:

- 收藏/投票计数准确。
- 未登录写操作提示登录。
- 笔记功能不再 404。

### Iteration 5: TypeScript 支持

预计 2-4 天，可独立延期。

范围:

- 沙箱镜像增加 TypeScript 执行环境。
- 后端支持 `typescript`。
- 编译错误映射 Compile Error。
- 更新 starter language seed。

验收:

- TypeScript two-sum 正确 run/submit。
- TS 类型/语法错误显示清楚。

---

## 8. 测试矩阵

### Backend unit

- `SecurityUtilTest`
  - anonymousUser 返回 null。
  - authenticated user 返回 id。
- `CodeExecutionServiceTest`
  - 空 cases 返回 bad request。
  - verdict 优先级正确。
  - expectedOutput 缺失状态正确。
- `AdminTestCaseServiceTest`
  - create/update/delete/import/reorder。
- `ProblemServiceImplTest`
  - detail content/stats/tags/interactions 映射。

### Backend integration

- `ProblemDetailControllerTest`
  - 匿名 GET detail 200。
  - detail.content 正确。
- `ProblemSubmissionControllerTest`
  - 匿名 submit 401。
  - 登录 submit pending。
  - run endpoint 按产品决策。
- `JudgeWorkerProcessorTest`
  - official cases 为空 -> System Error。
  - hidden cases 参与评测。

### Frontend unit

- `problem-detail.spec.ts`
  - content 优先 detail.content。
  - snake/camel fields 都兼容。
- `submission.spec.ts`
  - run DTO 映射 cases。
  - errorMessage 兼容。
- `TestResultsView.spec.ts`
  - Accepted/WA/RE/CE/TLE 文案。

### E2E

- 匿名访问 `/problems/two-sum`。
- 未登录点击提交跳登录或提示。
- 登录运行样例成功。
- 登录提交正确答案，最终 Accepted。
- 管理端新增 hidden case 后，错误答案不能通过。

---

## 9. 部署与验证顺序

1. 合并 P0 安全修复。
2. dry-run Flyway:

```bash
cd db-manager
db-manager migrate --dry-run
db-manager validate
```

3. 应用 `test_cases` 迁移。
4. 构建沙箱镜像:

```bash
docker build -t ulticode-sandbox:latest -f docker/sandbox/Dockerfile docker/sandbox
```

5. 重启后端前确认 MySQL/Redis/Docker 状态。
6. 重启 `ulticode-9001`。
7. 验证接口:

```bash
curl -i http://localhost:9001/problems/slug/two-sum
curl -i -X POST http://localhost:9001/problems/1/submissions
curl -i -X POST http://localhost:9001/problems/1/submissions/run
```

8. 前端联调 `/problems/two-sum`。
9. 管理端联调 test cases。

---

## 10. 风险与回滚

### 风险 1: 安全配置误伤公开读接口

缓解:

- 明确列出 public GET。
- 添加 SecurityConfig 测试。
- 上线后优先 smoke test problemset 和 problem detail。

### 风险 2: 迁移创建 `test_cases` 后没有数据

缓解:

- Two Sum seed 随迁移一起上。
- Worker 对空用例返回明确 System Error。
- 管理端导入功能用于快速补数据。

### 风险 3: 沙箱镜像不一致

缓解:

- 镜像 build 写进 RUNBOOK。
- 后端启动健康检查或执行前检查。
- 错误码明确。

### 风险 4: TypeScript 支持延期导致前端显示不可用语言

缓解:

- 短期过滤 TypeScript。
- 后端 detail languages 增加 `runnable/submittable`。

---

## 11. Definition of Done

本阶段完成的标准:

- 匿名用户可以浏览题目详情，但不能正式提交。
- 题面显示后端 `detail.content`。
- JavaScript Two Sum 样例运行成功。
- JavaScript Two Sum 正式提交能通过官方 sample + hidden cases。
- 管理端能维护 `test_cases`。
- 所有前端可选语言都能被后端 run/submit。
- 收藏/投票计数匿名可读，写操作要求登录。
- 笔记入口要么可用，要么隐藏。
- 后端单测、前端单测、至少一条端到端路径通过。
