# Local Review: feat/audit-granularity-alignment

**Reviewed**: 2026-05-16
**Branch**: feat/audit-granularity-alignment
**Scope**: 21 files (+362 / -169)
**Decision**: APPROVE (with comments)

## Summary

变更完成了 Audit 模块前后端颗粒度对齐，修复了原始 CR 报告中的 CSV 转义、配置外置、DTO 结构化、search 参数校验和 window.open 认证传递问题。代码符合项目既有模式，无安全漏洞，无阻断性问题。

## Findings

### CRITICAL

None

### HIGH

None

### MEDIUM

None

### LOW

1. **AuditController.java:49-50 — `format` 参数无显式校验**
   `exportAuditLogs` 的 `format` 参数未限制只能是 `"csv"` 或 `"json"`。非法值（如 `"xml"`）会静默 fallback 到 CSV 分支，行为不够明确。建议添加显式校验，非法值返回 400 Bad Request。

2. **AuditLogQueryDTO.java:20-21 — `page` / `limit` 缺少边界校验**
   `search` 字段已添加 `@Size(max=200)`，但 `page` 和 `limit` 缺少 `@Min`/`@Max` 限制。虽为既有代码，但建议统一添加（如 `@Min(1) @Max(1000)`）以防止异常分页请求。

## Validation Results

| Check | Result |
|---|---|
| Backend Compile | Pass |
| Frontend Type-Check | Pass (0 audit-related errors; 14 pre-existing errors in problem-lists) |
| Frontend Lint | Pass (0 audit-related errors) |

## Files Reviewed

| File | Change | Notes |
|---|---|---|
| AuditController.java | Modified | 新增 /export 端点，CSV 转义修复 |
| AuditServiceImpl.java | Modified | buildQueryWrapper 提取，结构化统计，配置化导出限制 |
| AuditService.java | Modified | 新增 getAuditLogsForExport |
| AuditLogQueryDTO.java | Modified | 新增 search、action，@Size 校验 |
| AuditStatsVO.java | Modified | Map -> 结构化 DTO |
| EntityTypeStat.java | Added | record DTO |
| PerformerStat.java | Added | record DTO |
| application.yml | Modified | 新增 audit.export.limit 配置 |
| audit.ts (API) | Modified | PageResult 重构，apiDownload 导出 |
| audit.ts (Store) | Modified | data.items 适配，AuditExportParams |
| AuditLogsView.vue | Modified | 传递 search 参数，终端风格 UI |
| AuditReportView.vue | Modified | topPerformers / actionsByEntity 适配 |
| AuditLogViewer.vue | Modified | response.items，统一 action 选项 |
| router/index.ts | Modified | 新增 audit-report 路由 |
| forum.ts (API) | Modified | AuditEntry -> AuditLog |
| forum.ts (Store) | Modified | AuditLog 导入 |
| AuditTab.vue | Modified | AuditLog，optional chaining |
| comments.ts / CommentsListView.vue | Modified | null 过滤（非 audit 变更） |
| .claude/CLAUDE.md | Modified | 项目文档重写 |

## Notes

- 原始 CR 报告的 3 个 MEDIUM 和 3 个 LOW 问题均已在本次分支中修复：
  1. CSV 导出字段转义（MEDIUM #1）
  2. search 参数 @Size 限制（MEDIUM #2）
  3. EntityTypeStat / PerformerStat 改为 record（LOW #1）
  4. 导出限制从硬编码改为配置读取（LOW #2）
  5. window.open 改为 apiDownload 确保认证 token 传递（LOW #3）
- MEDIUM #3（PageResult 重复定义）为项目既有模式，非本次引入，未在本次修复。
