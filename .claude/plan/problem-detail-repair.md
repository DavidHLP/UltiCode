# Problem Detail 修复与迭代实施计划

> 基于 `docs/problem-detail-repair-iteration-plan.md` 和 `docs/problem-detail-frontend-backend-alignment-analysis.md`
> 状态：P0 安全与评测底座大部分已在上轮会话修复，本计划聚焦剩余未修复项

---

## 差距分析总结

### 已修复（上轮会话）
- SecurityConfig 收紧：只放开特定 GET /problems 端点
- SecurityUtil 排除 anonymousUser
- test_cases 迁移（V111）与实体（含 inputs JSON 字段）
- CodeExecutionService：VERDICT_PRIORITY、空用例 400 检查
- JudgeWorkerProcessor：从 test_cases 读取、结构化 inputs 解析、determineVerdict
- ProblemServiceImpl：真实 submissionCount/solutionCount/tags/interactions
- ProblemServiceImpl buildLanguages：过滤后端不支持的语言（如 TypeScript）
- 前端 problem-detail.ts mapper：优先取 detail.content，snake/camel 兼容
- 前端 TestResultsView：verdict 文案、errorMessage/error_message 兼容
- 前端 submission.ts：snake_case/camelCase 兼容

### 剩余未修复
| # | 问题 | 位置 | 优先级 |
|---|------|------|--------|
| 1 | DescriptionView 仍用 summary 渲染正文，忽略 content | `DescriptionView.vue:74` | P1 |
| 2 | Run loading 是固定 1.2s 动画，未绑定真实请求 | `LayoutHeaderCenter.vue:38-50` | P1 |
| 3 | Submit 未拦截未登录用户 | `LayoutHeaderCenter.vue:52-84` | P1 |
| 4 | Docker volume 使用 `$(pwd)` 字面量，ProcessBuilder 不展开 | `SandboxServiceImpl:172,214` | P1 |
| 5 | 无 expected output 的自定义用例被判 WA | `SandboxServiceImpl:73-74`, `CodeExecutionHelperImpl:200-201` | P1 |
| 6 | 管理端 test-cases 后端 API 未实现 | Admin 模块缺失 | P2 |

---

## 实施步骤

### Step 1: 前端题面正文修复

**文件**: `console/src/views/problems/description/DescriptionView.vue:74`

**修改**:
```ts
// BEFORE
const problemDescription = computed<ProblemDescription>(() => ({
  content: props.problem.summary || "",

// AFTER
const problemDescription = computed<ProblemDescription>(() => ({
  content: props.problem.content || props.problem.summary || "",
```

**验收**: Two Sum 页面显示完整 Markdown 正文（而非仅摘要）

---

### Step 2: Run/Submit 按钮状态与登录拦截

**文件**: `console/src/views/problems/headers/LayoutHeaderCenter.vue`

#### 2a. Run 按钮绑定真实请求状态

当前 `isRunning` 由本地 `setTimeout(1200)` 控制。应改为监听 `useProblemDetail` 提供的真实运行状态。

需要确认 `useProblemDetail.ts` 是否暴露 `isRunning` 状态。如未暴露，需：
1. 在 `useProblemDetail.ts` 中增加 `isRunning` ref
2. `handleRun` 触发时设置 `isRunning = true`
3. `runSubmission` 完成后（无论成功失败）设置 `isRunning = false`
4. `LayoutHeaderCenter.vue` 中 `isRunning` 改为从 `useProblemDetail` 读取

#### 2b. Submit 登录拦截

在 `handleSubmit` 开头增加登录检查：
```ts
import { useAuthStore } from "@/stores/auth";

const authStore = useAuthStore();
if (!authStore.isAuthenticated) {
  toast.info(t("problem.messages.loginRequired"));
  // 或跳转登录页
  return;
}
```

**验收**:
- 点击 Run 后按钮 loading 持续到请求完成
- 未登录点击 Submit 弹出登录提示，不发请求

---

### Step 3: 沙箱 Docker volume 路径修复

**文件**: `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SandboxServiceImpl.java`

**问题**: `buildDockerCommand` 和 `buildBatchDockerCommand` 中 volume 参数为 `"$(pwd)/docker/sandbox:/seccomp-profile:ro"`。ProcessBuilder 不会展开 shell 变量 `$(pwd)`，导致 seccomp profile 挂载失败。

**方案**: 使用绝对路径，从配置或运行时获取项目根目录。

建议修改 `DockerSandboxConfig` 增加 `seccompProfilePath` 字段，默认值为 `docker/sandbox`（相对路径），在 `SandboxServiceImpl` 中解析为绝对路径：

```java
private String resolveSeccompPath() {
    String path = sandboxConfig.seccompProfilePath();
    if (path.startsWith("/")) return path + ":/seccomp-profile:ro";
    // Resolve relative to working directory
    return System.getProperty("user.dir") + "/" + path + ":/seccomp-profile:ro";
}
```

**文件变更**:
- `DockerSandboxConfig.java`: 增加 `seccompProfilePath()` 方法/字段
- `SandboxServiceImpl.java`: 替换两处 `"$(pwd)/docker/sandbox:/seccomp-profile:ro"` 为 `resolveSeccompPath()`
- `application.yml`: 增加 `sandbox.seccomp-profile-path=docker/sandbox`

**验收**: Docker run 命令中 volume 参数为绝对路径

---

### Step 4: 自定义用例无 expected output 时不判 WA

**文件**:
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SandboxServiceImpl.java:73-74`
- `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/CodeExecutionHelperImpl.java:200-201`

**当前逻辑**:
```java
String expected = testCase.getOutput() != null ? testCase.getOutput().trim() : "";
boolean passed = helper.normalizeOutput(stdout).equals(helper.normalizeOutput(expected));
```

当 `testCase.getOutput()` 为 null 或空字符串时，`expected` 为空字符串，`passed` 取决于 stdout 是否为空，这是错误的。

**方案**: 当 expected output 缺失时，返回 `status=Ran`（或 `No Expected Output`），不计入 AC/WA。

修改 `CodeExecutionHelperImpl.parseBatchResults`:
```java
// 在 buildCaseResult 之前判断 expectedOutput 是否缺失
String expected = testCase.getOutput() != null ? testCase.getOutput().trim() : "";
if (expected.isEmpty()) {
    caseResults.add(buildCaseResult(testCase, runId, userId,
        "Ran", runtime, output, "No expected output provided", memoryMb));
} else {
    boolean passed = normalizeOutput(output).equals(normalizeOutput(expected));
    caseResults.add(buildCaseResult(testCase, runId, userId,
        passed ? "Accepted" : "Wrong Answer", runtime, output, null, memoryMb));
}
```

同步修改 `SandboxServiceImpl.executeInSandbox`:
```java
String expected = testCase.getOutput() != null ? testCase.getOutput().trim() : "";
if (expected.isEmpty()) {
    return helper.buildCaseResult(testCase, runId, userId, "Ran",
            elapsedMs, stdout, "No expected output provided", 0.0);
}
boolean passed = helper.normalizeOutput(stdout).equals(helper.normalizeOutput(expected));
```

**验收**: 自定义用例不填 expected output 时，结果显示 "Ran" 而非 "Wrong Answer"

---

### Step 5: 管理端 Test-Cases 后端 API

**目标**: 实现管理端已预留的 `/admin/problems/{id}/test-cases` 接口

**新增文件**:
1. `modules/admin/controller/AdminTestCaseController.java`
2. `modules/admin/service/AdminTestCaseService.java`
3. `modules/admin/service/impl/AdminTestCaseServiceImpl.java`
4. `modules/admin/dto/testcase/TestCaseVO.java`
5. `modules/admin/dto/testcase/CreateTestCaseDTO.java`
6. `modules/admin/dto/testcase/UpdateTestCaseDTO.java`
7. `modules/admin/dto/testcase/BulkImportTestCasesDTO.java`

**接口清单**:
- `GET /admin/problems/{problemId}/test-cases`
- `GET /admin/problems/{problemId}/test-cases/{caseId}`
- `POST /admin/problems/{problemId}/test-cases`
- `PUT /admin/problems/{problemId}/test-cases/{caseId}`
- `DELETE /admin/problems/{problemId}/test-cases/{caseId}`
- `POST /admin/problems/{problemId}/test-cases/bulk`
- `PUT /admin/problems/{problemId}/test-cases/reorder`
- `GET /admin/problems/{problemId}/test-cases/export`

**依赖**:
- `TestCaseMapper` 已存在
- `TestCase` 实体已存在（含 inputs 字段）

**验收**: management 前端 testCasesApi 所有请求都有后端响应

---

## 关键文件变更汇总

| 文件路径 | 操作 | 说明 |
|----------|------|------|
| `console/src/views/problems/description/DescriptionView.vue` | 修改 | content 优先取 props.problem.content |
| `console/src/views/problems/headers/LayoutHeaderCenter.vue` | 修改 | Run 绑定真实状态，Submit 登录拦截 |
| `console/src/views/problems/useProblemDetail.ts` | 修改 | 暴露 isRunning 状态 |
| `backend-spring/.../SandboxServiceImpl.java` | 修改 | volume 路径改为绝对路径 |
| `backend-spring/.../DockerSandboxConfig.java` | 修改 | 增加 seccompProfilePath |
| `backend-spring/.../CodeExecutionHelperImpl.java` | 修改 | 无 expected output 返回 Ran |
| `backend-spring/.../application.yml` | 修改 | 增加 sandbox.seccomp-profile-path |
| `backend-spring/.../admin/controller/AdminTestCaseController.java` | 新增 | 管理端测试用例 CRUD |
| `backend-spring/.../admin/service/AdminTestCaseService.java` | 新增 | Service 接口 |
| `backend-spring/.../admin/service/impl/AdminTestCaseServiceImpl.java` | 新增 | Service 实现 |
| `backend-spring/.../admin/dto/testcase/*.java` | 新增 | DTO/VO |

---

## 风险与缓解

| 风险 | 缓解 |
|------|------|
| 沙箱路径改为绝对路径后，不同环境（本地/CI/生产）路径不一致 | 通过 application.yml 配置化，各环境覆盖 |
| 自定义用例状态从 WA 改为 Ran，前端 verdict 文案可能未覆盖 | TestResultsView 已支持 "Ran" 的默认样式 |
| 管理端 API 新增量大，可能与现有管理端 frontend 契约不一致 | 对照 `management/src/api/admin/test-cases.ts` 实现 |

---

## Definition of Done

- [ ] Two Sum 题面显示完整 Markdown 正文
- [ ] Run 按钮 loading 绑定真实请求状态
- [ ] 未登录 Submit 被前端拦截并提示
- [ ] 沙箱 volume 使用绝对路径，ProcessBuilder 正确挂载
- [ ] 自定义用例无 expected output 显示 "Ran" 而非 "Wrong Answer"
- [ ] 管理端能新增/编辑/导入/排序 test cases
- [ ] 后端单测覆盖新增逻辑
