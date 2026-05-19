# Implementation Report: Problems 前后端颗粒度对齐修复

## Summary
修复 `/problems` 管理模块中 10 个前后端对齐问题，涵盖排序/过滤功能修复、6 个缺失后端端点新增、批量操作路径修正、提交数真实填充等。

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | XL | XL |
| Confidence | N/A | High — all compile/type-check/lint pass |
| Files Changed | 25-30 | 14 |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1.1 | 修复前端 bulkAction 路径 | [done] Complete | `/admin/bulk/problems/publish` → `/admin/problems/bulk`; bulkEdit 同理 |
| 1.2 | BulkProblemRequestDTO 增加 restore | [done] Complete | 枚举新增 restore 值 |
| 1.3 | AdminProblemServiceImpl 支持 restore | [done] Complete | 使用原生 SQL `restoreDeletedByIds` 绕过 @TableLogic |
| 2.1 | 修复后端排序逻辑 | [done] Complete | 动态 orderBy + 默认 `createdAt DESC` |
| 2.2 | 修复 status 过滤器值域 | [done] Complete | 新增 `publishStatus` 字段 (DRAFT/PUBLISHED/ARCHIVED); 前端发送 `publishStatus` 而非 `status` |
| 2.3 | 修复 isDeleted + 绕过 @TableLogic | [done] Complete | `selectDeletedProblems`/`countDeletedProblems` 原生 SQL; ARCHIVED 自动走此路径 |
| 2.4 | 前端去重排序选项 | [done] Complete | 移除 `created_at`/`updated_at` snake_case 重复项 |
| 3.1 | 修复 tag 过滤器 | [done] Complete | `apply` 子查询 + 参数化 `{0}` 防注入 |
| 3.2 | 修复 difficulty 大小写 | [done] Complete | `UPPER(difficulty) = UPPER({0})` |
| 3.3 | 修复分页参数 | [done] Complete | `pageIndex + 1` 修复 0-based → 1-based |
| 3.4 | 填充 submissionCount/solutionCount | [done] Complete | 批量查询 `countSubmissionsByProblemIds`/`countSolutionsByProblemIds` |
| 4.1 | 新增 Flag 端点 | [done] Complete | `POST /admin/problems/{id}/flag` |
| 4.2 | 新增 Moderate 端点 | [done] Complete | `POST /admin/problems/{id}/moderate` |
| 4.3 | 新增 Flagged 端点 | [done] Complete | `GET /admin/problems/flagged` |
| 4.4 | 新增 Batch Moderate 端点 | [done] Complete | `POST /admin/problems/flagged/batch-moderate` |
| 4.5 | 新增 Submissions 端点 | [done] Complete | `GET /admin/problems/{id}/submissions` |
| 4.6 | 新增 Import 端点 | [done] Complete | `POST /admin/problems/import` |
| 5.1 | 删除 AdminProblemListQueryDTO | Deviated | 非孤立 DTO — 被 AdminProblemListController 等引用，保留 |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis (BE compile) | [done] Pass | `./mvnw compile -DskipTests` 零错误 |
| Static Analysis (FE type-check) | [done] Pass | `pnpm type-check` 零错误 |
| Lint (FE) | [done] Pass | `pnpm lint` 零问题 |
| Unit Tests | N/A | 未新增单元测试（后续补充） |
| Build | N/A | 未执行完整 build（编译已通过） |

## Files Changed

| File | Action | Notes |
|---|---|---|
| `management/src/api/admin/problems.ts` | UPDATED | bulkAction/bulkEdit 路径修复, ProblemQueryParams 新增 publishStatus |
| `management/src/views/problems/composables/useProblemFilters.ts` | UPDATED | publishStatus 映射, page +1 修复, 移除未用 import |
| `management/src/views/problems/ProblemsListView.vue` | UPDATED | 去重排序选项 |
| `backend-spring/.../problem/dto/ProblemQueryDTO.java` | UPDATED | 新增 publishStatus 字段 |
| `backend-spring/.../problem/service/impl/ProblemServiceImpl.java` | UPDATED | buildProblemQueryWrapper 重写(排序/tag/difficulty/publishStatus); listProblems/listAllProblems 支持提交数填充+ARCHIVED 查询; toVO 新增 4 参数重载 |
| `backend-spring/.../problem/mapper/ProblemMapper.java` | UPDATED | 新增 restoreDeletedByIds, countSubmissions/SolutionsByProblemIds, selectDeletedProblems, countDeletedProblems, flagProblem, moderateProblem, selectFlaggedProblems, countFlaggedProblems, batchModerateProblems |
| `backend-spring/.../admin/dto/problem/BulkProblemRequestDTO.java` | UPDATED | BulkAction 枚举新增 restore |
| `backend-spring/.../admin/controller/AdminProblemController.java` | UPDATED | 新增 6 个端点 |
| `backend-spring/.../admin/service/AdminProblemService.java` | UPDATED | 新增 6 个接口方法 |
| `backend-spring/.../admin/service/impl/AdminProblemServiceImpl.java` | UPDATED | 新增 restore case; 实现 flagProblem, moderateProblem, getFlaggedProblems, batchModerateProblems, getProblemSubmissions, importProblems |
| `backend-spring/.../admin/dto/problem/FlagProblemRequestDTO.java` | CREATED | |
| `backend-spring/.../admin/dto/problem/ModerateProblemRequestDTO.java` | CREATED | |
| `backend-spring/.../admin/dto/problem/BatchModerateRequestDTO.java` | CREATED | |
| `backend-spring/.../admin/dto/problem/ImportProblemsRequestDTO.java` | CREATED | |
| `backend-spring/.../admin/dto/problem/ImportProblemItemDTO.java` | CREATED | |
| `backend-spring/.../admin/dto/problem/ImportProblemsResponseDTO.java` | CREATED | |

## Deviations from Plan

1. **AdminProblemListQueryDTO 保留** — 计划中标记为 DELETE，但实际被 AdminProblemListController/AdminProblemListService/AdminProblemListServiceImpl 引用，非孤立 DTO
2. **ProblemSubmissionsVO 未创建** — `getProblemSubmissions` 直接返回 PageResult<Submission>，前端已有 `getProblemSubmissions` API 定义

## Issues Encountered

None — 所有修改编译和类型检查一次通过。

## Next Steps
- [ ] Code review via `/code-review`
- [ ] 手动验证所有过滤器和排序功能
- [ ] 创建 PR via `/prp-pr`
