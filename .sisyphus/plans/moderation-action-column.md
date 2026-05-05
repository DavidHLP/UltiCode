# 审核列表添加操作内容列

## TL;DR
在管理后台审核队列列表中增加一列"操作内容"，显示该审核项最终被执行的操作类型（如"标记已解决"、"删除内容"等）。利用后端已有的 `resolution` 字段，无需后端改动。

## Context
- 用户截图显示管理后台 `http://localhost:9003/moderation` 的审核列表
- 列表目前显示的列：#、类型、实体、类别、状态、优先级、举报数、分配给、创建时间、操作
- 后端 `ModerationQueueVO` 已有 `resolution` 字段存储最终操作类型
- 前端 `ModerationQueueItem` 接口已有 `resolution?: string`
- i18n 已有 `moderation.actions.*` 的完整翻译

## Work Objectives
在表格中新增一列，放在"状态"列之后、"优先级"列之前，显示 `resolution` 字段的中文/英文名称。

## Execution Strategy
单一任务，修改 3 个文件：

1. `management/src/views/moderation/columns.ts` - 增加列定义
2. `management/src/i18n/locales/zh-CN/modules/moderation.ts` - 添加 `columns.resolution: '操作内容'`
3. `management/src/i18n/locales/en-US/modules/moderation.ts` - 添加 `columns.resolution: 'Action'`

## TODOs

- [x] 1. 在 columns.ts 的 Status 列和 Priority 列之间插入 Resolution 列

  **What to do**:
  - 在 `columns.ts` 的 Status column (accessorKey: 'status') 之后、Priority column (accessorKey: 'priority') 之前插入新列
  - accessorKey: 'resolution'
  - header: `t('moderation.columns.resolution')`
  - cell: 如果 `row.original.resolution` 存在，使用 `t(`moderation.actions.${row.original.resolution}`)` 显示翻译后的操作名称；如果不存在，显示 "—"
  - 列宽设置：size: 120, minSize: 100, maxSize: 140
  - 使用 `badge` 组件渲染，颜色根据操作类型映射（参考 `ActionHistoryTimeline.vue` 的颜色映射逻辑，或复用 `ModerationQueueView.vue` 中 `actionOptions` 的颜色）

  **Must NOT do**:
  - 不要修改后端代码或 API
  - 不要修改数据结构（`ModerationQueueItem` 接口已有 `resolution` 字段）

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO（文件修改有依赖关系）
  - **Blocked By**: None

  **References**:
  - `management/src/views/moderation/columns.ts:261-298` - Status column and Priority column (insert between them)
  - `management/src/views/moderation/components/ActionHistoryTimeline.vue` - Color mapping for actions (if exists)
  - `management/src/views/moderation/ModerationQueueView.vue:167-175` - actionOptions with colors

  **Acceptance Criteria**:
  - [ ] 表格新增"操作内容"列，位于"状态"列之后
  - [ ] 已处理的项显示对应的操作名称（如"标记已解决"）
  - [ ] 未处理的项（PENDING/UNDER_REVIEW）显示 "—"
  - [ ] 列宽合适，不挤压其他列

  **QA Scenarios**:
  ```
  Scenario: 列表显示操作内容列
    Tool: Playwright
    Preconditions: 管理后台已登录，有审核数据
    Steps:
      1. 导航到 http://localhost:9003/moderation
      2. 等待表格加载完成
      3. 检查表格列头是否包含"操作内容"
    Expected Result: 表格列顺序为：#、类型、实体、类别、状态、操作内容、优先级、举报数、分配给、创建时间、操作
    Evidence: .sisyphus/evidence/task-1-column-header.png

  Scenario: 已处理项显示操作名称
    Tool: Playwright
    Preconditions: 列表中有状态为"已解决"或"已驳回"的项
    Steps:
      1. 查看状态为"已解决"的行的"操作内容"列
      2. 验证显示内容是否为中文操作名称（如"标记已解决"）
    Expected Result: 已处理项的操作内容列显示对应的操作名称
    Evidence: .sisyphus/evidence/task-1-resolved-action.png
  ```

  **Commit**: NO

- [x] 2. 添加 i18n 列标题翻译

  **What to do**:
  - 在 `management/src/i18n/locales/zh-CN/modules/moderation.ts` 的 `columns` 对象中添加 `resolution: '操作内容'`
  - 在 `management/src/i18n/locales/en-US/modules/moderation.ts` 的 `columns` 对象中添加 `resolution: 'Action'`

  **Recommended Agent Profile**:
  - **Category**: `quick`

  **Acceptance Criteria**:
  - [ ] 中文环境下列头显示"操作内容"
  - [ ] 英文环境下列头显示"Action"

  **Commit**: YES
  - Message: `feat(moderation): add resolution column to queue table`
  - Files: `management/src/views/moderation/columns.ts`, `management/src/i18n/locales/zh-CN/modules/moderation.ts`, `management/src/i18n/locales/en-US/modules/moderation.ts`

## Success Criteria
- [x] 审核列表表格新增"操作内容"列
- [x] 列位置在"状态"之后、"优先级"之前
- [x] 已处理项显示对应的操作名称（中文）
- [x] 未处理项显示 "—"
- [x] i18n 翻译正确（中英文）

## Verification Commands
```bash
cd management && pnpm vite
# 然后访问 http://localhost:9003/moderation 验证
```
