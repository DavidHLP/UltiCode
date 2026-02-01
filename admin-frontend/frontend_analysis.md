# 前端冗余与肿大设计分析

基于对项目文件结构的分析，发现以下几个主要方面的冗余和优化空间：

## 1. 组件重复与未清理的模板代码

### Dashboard 组件冗余
`src/components/dashboard/` 目录下存在大量与 `src/components/table/` 功能重复且实现较差的组件。
- **重复的 DataTable**:
  - `src/components/dashboard/DataTable.vue`: 这是一个硬编码列和数据的特定实现，缺乏灵活性。
  - `src/components/table/DataTable.vue`: 这是一个通用的、基于 `@tanstack/vue-table` 的实现，被 `src/views/` 下的大多数列表页面使用。
  - **建议**: 删除 `src/components/dashboard/DataTable.vue` 及其依赖 (`DraggableRow.vue`, `DragHandle.vue`)，统一使用 `src/components/table/` 下的组件。

- **重复的图表组件**:
  - `src/components/dashboard/AreaChart.vue` 和 `src/components/dashboard/ChartAreaInteractive.vue` 高度相似。后者包含大量硬编码数据，看起来像是开发过程中的遗留原型。
  - **建议**: 保留 `AreaChart.vue`，删除 `ChartAreaInteractive.vue`。

- **遗留的模板页面**:
  - `src/views/dashboard/TemplateDashboardView.vue`: 包含硬编码数据的示例页面，未使用真实数据。
  - **建议**: 删除此文件。

## 2. 目录结构碎片化

### Problem 组件分散
与“题目”相关的组件分散在两个命名极其相似的目录中，造成混淆。
- `src/components/problem/`: 包含编辑器相关组件 (`MarkdownEditor`, `TestCasesEditor`)。
- `src/components/problems/`: 包含展示和管理组件 (`DescriptionMarkdown`, `BulkActionDialog` 等)。
- **建议**: 将 `src/components/problem/` 的内容合并到 `src/components/problems/` 中，保持命名一致性。

## 3. 通用功能的重复实现

### 弹窗 (Dialog) 组件
`EntityActionDialog.vue` 是一个设计良好的通用组件，支持“删除”和“标记”操作（带原因输入）。然而，系统中仍存在功能重叠的专用组件：
- `src/views/users/UserBanDialog.vue`: 功能本质上是“带原因的操作”，与 `EntityActionDialog` 的“Flag”模式（带原因输入）非常相似。
- **建议**: 扩展 `EntityActionDialog` 支持自定义动作类型（如 `action="ban"`）和自定义标题/描述，从而替代 `UserBanDialog`。

### 抽屉 (Drawer) 组件
所有的详情抽屉组件 (`UserDetailDrawer`, `ContestDetailDrawer`, `ForumPostDetailDrawer`, `AuditLogDetailDrawer`) 共享几乎完全相同的壳代码：
- Drawer 容器
- Header (标题 + 描述 + 关闭/操作按钮)
- Loading 状态展示
- ScrollArea 容器
- Error/Empty 状态展示
- **建议**: 提取一个 `BaseDetailDrawer.vue` 组件，封装上述通用 UI 结构，各业务 Drawer 只需通过插槽 (Slot) 传入具体内容区域。

## 4. 样式与资源优化

### 依赖库使用
- 项目中同时引用了 `lucide-vue-next` 和 `@tabler/icons-vue` 两个图标库。
  - `src/components/problem/MarkdownEditor.vue` 使用了 `lucide-vue-next`。
  - `src/views/problems/ProblemDetailView.vue` 混合使用了两者。
  - 大多数其他文件主要使用 `@tabler/icons-vue`。
- **建议**: 统一使用一个图标库（推荐 `@tabler/icons-vue`，因为在项目中用量更大），移除另一个以减小包体积。

## 总结

当前前端代码库存在明显的“复制粘贴开发”痕迹，特别是在 Dashboard 模块和早期的原型文件中。通过清理这些冗余文件、合并碎片化的目录结构以及进一步抽象通用 UI 模式（如 Drawer 和 Dialog），可以显著降低维护成本并减小打包体积。
