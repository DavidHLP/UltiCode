# Submission API 修复 Changelog

> 修复日期: 2026-06-10
> 计划: [.claude/PRPs/plans/completed/submission-api-repair.plan.md](../../.claude/PRPs/plans/completed/submission-api-repair.plan.md)
> 实测基线: [submission-api-test-report-2026-06-10.md](./submission-api-test-report-2026-06-10.md)

## 修复内容

### 1. 后端 `SubmissionVO.memoryDistBinsMb` 类型不一致 — P0
- **Before**: `Object` (实际有时是 JSON 字符串, 有时是数组)
- **After**:  `List<Integer>` (统一为数组)
- **影响**: 详情页 / Best / List 接口全部
- **修改文件**: `backend-spring/.../dto/SubmissionVO.java`

### 2. 后端 `SubmissionDetailVO.runtimeDistBinsMs` / `memoryDistBinsMb` 类型修复 — P0
- 同上,改为 `List<Integer>`
- **修改文件**: `backend-spring/.../dto/SubmissionDetailVO.java`

### 3. 后端 `RunResultDTO` 字段类型 — P0
- `problemId`: `String` → `Long`
- 新增 `runtimeMs: Long` (v2 数值字段)
- 新增 `memoryMb: Double` (v2 数值字段)
- 新增 `RunCaseResult.runtimeMs: Long` / `memoryMb: Double` (per-case 数值字段)
- 保留 `runtime: String` / `memory: String` / `verdict: String` (向后兼容)
- **修改文件**: `backend-spring/.../dto/RunResultDTO.java`

### 4. 后端 service 层: `normalizeBins()` 统一归一 — P0
- 新增 `SubmissionServiceImpl.normalizeBins(Object raw)` 工具方法
- 支持输入形态:
  - `List<Integer>` — 直接过滤数值
  - `String` (JSON 数组) — Jackson 解析
  - `List<Map<String, Number>>` (PerformanceStats 形态) — 提取 `value`/`bin`/`min`/`max` 字段
  - `null` / 其他 — 返回 `List.of()`
- `toDetailVO` 与 `toVO` 都通过该方法赋值
- **修改文件**: `backend-spring/.../service/impl/SubmissionServiceImpl.java`

### 5. 后端 `@Operation` 文档增强 — P1
- `ProblemSubmissionController.runCode` 添加详细 `description`:
  - 入口函数名约定 (`function` / `def` / `class Solution`)
  - 默认入口名 `solution`
  - 响应字段说明 (`problemId: Long` / `runtimeMs` / `memoryMb`)
- `SubmissionController.getSubmission` 添加直方图字段类型说明

### 6. 前端 `mapDistributionBins()` helper — P0
- 新增: 处理 `string` / `number[]` / 其他 输入, 统一返回 `number[]`
- 失败兜底 `[]`
- **修改文件**: `console/src/api/submission.ts`

### 7. 前端 `mapSubmission()` 集成 `mapDistributionBins` — P0
- 两个直方图字段 (`runtimeDistBinsMs` / `memoryDistBinsMb`) 改用 helper 归一
- 保留 `created_at` / `runtimePercentile` / `memoryPercentile` 等映射逻辑

### 8. 前端 `mapRunResult()` 独立映射 — P0
- 新增: 与 `mapSubmission()` 彻底解耦
- 处理 `verdict` / `cases[]` / `runtimeMs` / `memoryMb` 字段
- 新增 `mapRunCase()` 处理 per-case 数据
- 兼容旧后端 `problemId: "1"` 字符串格式 (用 `Number()` 强转)

### 9. 前端 `runSubmission()` 改用 `mapRunResult` — P0
- `runSubmission()` 函数末尾从直接 `apiPost<ProblemRunResult>` 返回改为:
  ```ts
  const response = await apiPost<unknown>(...);
  return mapRunResult(response);
  ```

### 10. 前端 `ProblemRunResult` 类型扩展 — P1
- 新增 `runtimeMs?: number`
- 新增 `memoryMb?: number`
- 新增 `ProblemRunCase.runtimeMs?: number` / `memoryMb?: number`
- 字段标 `?` 可选,旧后端不返回时不会崩
- **修改文件**: `console/src/types/test-results.ts`

### 11. 前端 Vitest 单元测试 — P0
- 新增: `console/src/api/__tests__/submission.spec.ts`
- 26 个测试用例, 覆盖:
  - `mapDistributionBins` (5 个): array / JSON string / null / invalid / mixed
  - `mapSubmission` (7 个): snake_case / camelCase / string-to-array / null / alias
  - `mapRunResult` (7 个): v2 schema / 旧 string problemId / 缺字段 / 嵌套 cases / snake_case / null
  - 7 个 fetcher 集成测试
- **测试结果**: 26/26 通过, 耗时 500ms

## 升级步骤

### 后端
```bash
cd /home/davidhlp/project/UltiCode/backend-spring
./mvnw compile -B    # 应通过
./mvnw test -B       # 应通过 (我的修改影响 0 个测试失败)
pm2 restart ulticode-9001
```

### 前端
```bash
cd /home/davidhlp/project/UltiCode/console
pnpm install
pnpm test                                                # 跑全套测试
pnpm vitest --run src/api/__tests__/submission.spec.ts   # 仅 submission 套件
pnpm type-check                                          # 0 errors
pnpm lint                                                # 0 errors
pnpm build                                               # 0 errors
```

## 验证结果

### 后端
- ✅ Maven 编译: BUILD SUCCESS
- ✅ Submission 相关测试 27/27 通过 (SubmissionServiceImpl 17 + ProblemSubmissionController 2 + CodeExecutionHelperImpl 4 + SandboxServiceImpl 4)
- ⚠️ 8 个 pre-existing 失败 (AdminSolution/AdminProblemList 模块), 与本次修复无关

### 前端
- ✅ TypeScript type-check: 0 errors
- ✅ Vitest: 26/26 通过 (新增 submission 套件)
- ✅ Real curl 验证 v2 schema:
  - `memoryDistBinsMb` 现在是 `list` (详情 + 列表均如此)
  - `problemId` 在 RunResultDTO 现在是 `int`
  - `runtimeMs` / `memoryMb` 字段存在

## 向后兼容矩阵

| 字段 | 旧值 (string) | 新值 (number) | 旧前端能否解析 |
|---|---|---|---|
| `SubmissionVO.memoryDistBinsMb` | `"[8, 16, 32]"` (string) | `[8, 16, 32]` (array) | ❌ 需要 `mapDistributionBins()` 兼容 |
| `SubmissionDetailVO.runtimeDistBinsMs` | (混合) | `[int×8]` | 同上 |
| `RunResultDTO.problemId` | `"1"` (string) | `1` (int) | ✅ `Number(r.problemId)` 兜底 |
| `RunResultDTO.runtimeMs` | (不存在) | `346` (number) | ✅ 可选字段,不读不崩 |
| `RunResultDTO.memoryMb` | (不存在) | `0.0` (number) | ✅ 同上 |
| `RunResultDTO.runtime` | `"346ms"` | `"346ms"` | ✅ 不变 |
| `RunResultDTO.memory` | `"0.0MB"` | `"0.0MB"` | ✅ 不变 |
| `RunResultDTO.verdict` | `"Accepted"` | `"Accepted"` | ✅ 不变 |
| `cases[].runtime` | `"10ms"` | `"10ms"` | ✅ 不变 |
| `cases[].memory` | `"5.0MB"` | `"5.0MB"` | ✅ 不变 |
| `cases[].runtimeMs` | (不存在) | `10` | ✅ 可选 |
| `cases[].memoryMb` | (不存在) | `5.0` | ✅ 可选 |

## 回滚方案

```bash
# 后端: git revert
git revert <commit>

# 前端: 同上,或:
cd console && pnpm install
```

## 已知遗留问题

1. **`mapDistributionBins` 仍保留**: 后端 v2 schema 已是数组, 但前端保留 helper 作为防御性编程 + 平滑迁移
2. **sandbox 入口名 `solution` 兜底仍存在**: 这是 CodeExecutionHelperImpl 设计的"零配置"入口, 不会改变. 完整的多入口支持是独立的大型重构
3. **`AdminSubmissionVO` 仍为 `Object`**: 不在 10 个公开端点范围, 内部管理端使用, 暂不动
4. **前端 RunPanel starter code 来自后端**: 不在本次修复范围, 通过 `@Operation` 文档告知用户入口名约定

## 相关文件

- 后端 DTO/VO:
  - `backend-spring/src/main/java/com/ulticode/modules/submission/dto/SubmissionVO.java`
  - `backend-spring/src/main/java/com/ulticode/modules/submission/dto/SubmissionDetailVO.java`
  - `backend-spring/src/main/java/com/ulticode/modules/submission/dto/RunResultDTO.java`
- 后端 service / controller:
  - `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/SubmissionServiceImpl.java`
  - `backend-spring/src/main/java/com/ulticode/modules/submission/service/CodeExecutionService.java`
  - `backend-spring/src/main/java/com/ulticode/modules/submission/service/impl/CodeExecutionHelperImpl.java`
  - `backend-spring/src/main/java/com/ulticode/modules/submission/controller/ProblemSubmissionController.java`
  - `backend-spring/src/main/java/com/ulticode/modules/submission/controller/SubmissionController.java`
- 后端测试:
  - `backend-spring/src/test/java/com/ulticode/modules/submission/controller/ProblemSubmissionControllerTest.java`
- 前端:
  - `console/src/api/submission.ts`
  - `console/src/types/test-results.ts`
  - `console/src/api/__tests__/submission.spec.ts` (新增)

## Metrics

| 指标 | 计划 | 实际 |
|---|---|---|
| 复杂度 | Medium-Large | Medium-Large |
| 信心 | 9/10 | 9/10 (实施后验证一致) |
| 文件修改 | 9 + 1 新 + 1 doc | 9 + 1 新 + 1 doc (匹配) |
| 新增测试 | 26 | 26 (100% 通过) |
| 后端编译 | SUCCESS | SUCCESS |
| 前端 type-check | 0 errors | 0 errors |
