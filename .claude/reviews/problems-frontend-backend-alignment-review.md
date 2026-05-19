# Local Review: Problems Frontend-Backend Alignment

**Reviewed**: 2026-05-19 (2 rounds)
**Decision**: APPROVE with comments — all HIGH issues fixed

## Summary
审查 problems 模块前后端对齐修复的 15 个变更文件，涵盖 6 个新端点、批量操作路径修正、排序/过滤修复、提交数填充等。2 轮审查共发现 10 个问题（0 CRITICAL / 3 HIGH / 4 MEDIUM / 3 LOW），全部已修复或可接受。

## Round 1 Findings (已修复)

### HIGH (3/3 fixed)

**1. `batchModerateProblems` 返回虚假成功** [FIXED]
- 添加受影响行数检查 + 差异警告日志

**2. `importProblems` 无大小限制** [FIXED]
- `@Size(max=500)` + `MAX_IMPORT_SIZE` 常量

**3. `restoreDeletedByIds` 无审计日志** [FIXED]
- 添加 `log.info` 记录操作者和 ID

### MEDIUM (4/4 fixed)

**4. `PageResult<?>` 类型不安全** [FIXED]
- 改为 `PageResult<Submission>`

**5. `BatchModerateRequestDTO.status` 无枚举校验** [FIXED]
- 添加 `@Pattern(regexp="REVIEWED|RESOLVED|DISMISSED")`

**6. 6 个新端点缺少 `@RateLimit`** [FIXED]
- 全部添加速率限制

**7. `importProblems` 的 `create_new` slug 冲突** [ACCEPTED]
- 已知行为，timestamp 后缀避免唯一约束冲突

### LOW (3/3 accepted)

**8.** `ImportProblemItemDTO` 文本字段无 `@Size` — 风险低
**9.** `createFromImport` 硬编码 version=1 — 风险低
**10.** `restore` 绕过 ProblemService 缓存 — 已添加审计日志，后续重构时统一

## Round 2 Findings (新增修复)

### HIGH (1/1 fixed)

**11. `bulkAction` 的 `ids` 列表无大小限制** [FIXED]
- `@Size(max=500)` + `MAX_BULK_SIZE` 常量

### MEDIUM (2/2 fixed)

**12. `edit` action 的 difficulty 无枚举校验** [FIXED]
- 添加 `isValidDifficulty()` 校验 Easy/Medium/Hard

**13. 排序按钮缺少 `aria-label`** [FIXED]
- 添加 `:aria-label="t('common.sort')"`

### Remaining (not blocking)

- `ProblemServiceImpl.java` 818 行 — 超 800 行限制，但属既有代码，不在本次修复范围
- `findRandomPublished` 使用 `ORDER BY RAND()` — 性能隐患但非本次变更引入
- `listAllProblems` 无内部 LIMIT — 已有 export 上限保护

## Validation Results

| Check | Result |
|---|---|
| BE Compile | Pass |
| FE Type-check | Pass |
| FE Lint | Pass |
| Unit Tests | N/A |
| Build | N/A |

## Files Reviewed

| File | Change |
|---|---|
| `AdminProblemController.java` | Modified — 6 新端点 + @RateLimit |
| `AdminProblemServiceImpl.java` | Modified — 实现 6 方法 + restore 审计 + difficulty 校验 |
| `AdminProblemService.java` | Modified — 接口签名 PageResult<Submission> |
| `ProblemMapper.java` | Modified — 原生 SQL +101 行 |
| `ProblemServiceImpl.java` | Modified — buildQueryWrapper 重写 + count 批量填充 |
| `ProblemQueryDTO.java` | Modified — publishStatus 字段 |
| `BulkProblemRequestDTO.java` | Modified — restore 枚举 + @Size |
| `ImportProblemsRequestDTO.java` | Modified — @Size max=500 |
| `BatchModerateRequestDTO.java` | Modified — @Pattern 枚举校验 |
| `FlagProblemRequestDTO.java` | Created |
| `ModerateProblemRequestDTO.java` | Created |
| `ImportProblemItemDTO.java` | Created |
| `ImportProblemsResponseDTO.java` | Created |
| `management/.../problems.ts` | Modified — 路径修复 + publishStatus |
| `management/.../useProblemFilters.ts` | Modified — publishStatus + page +1 |
| `management/.../ProblemsListView.vue` | Modified — 排序去重 + aria-label |
