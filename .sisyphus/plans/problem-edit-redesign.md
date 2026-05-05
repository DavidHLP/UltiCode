# 题目编辑页面重新设计（Description Tab）

## TL;DR

> **目标**: 重新设计 management 端题目 description 编辑页面，从当前单一平铺表单升级为分区块多层编辑界面（类似 LeetCode），支持实时预览。
>
> **核心改动**:
> - 新增 examples（示例用例）、constraints（约束条件）、hints（提示）、tags（标签）编辑
> - 引入实时预览面板（右侧渲染 console 端最终效果）
> - 验证模式从手动 errors ref 升级为 vee-validate + Zod
> - 采用分区块 Card 布局，每个内容类型独立可折叠
>
> **交付物**:
> - 重构 `DescriptionForm.vue`（主表单组件）
> - 新增子组件：`ExamplesEditor.vue`, `ConstraintsEditor.vue`, `HintsEditor.vue`, `TagsSelector.vue`, `LivePreviewPanel.vue`
> - Zod 验证 schema (`problemDescriptionSchema.ts`)
> - 测试文件覆盖所有新组件
>
> **Estimated Effort**: Medium
> **Parallel Execution**: YES - 6 waves
> **Critical Path**: T1 (Zod Schema) → T2 (Editors) → T3 (Preview) → T4 (Form Assembly) → T5 (View Integration) → T6 (Tests) → F1-F4 (Verification)

---

## Context

### Original Request
用户要求重新设计 `http://localhost:9003/problems/1/edit/description` 题目编辑页面，匹配题目内容的多层设计结构。

### Interview Summary
**Key Discussions**:
- **多层设计**: 题目包含标题、描述、输入说明、输出说明、约束条件、示例等多个独立区块
- **当前痛点**: 布局不合理、缺少 examples/constraints/hints/tags 编辑字段
- **设计范围**: 仅 description tab（非 code/cases tabs）
- **视觉参考**: 类似 LeetCode 的编辑界面
- **实时预览**: 需要右侧预览面板
- **验证升级**: 从手动 errors ref 升级为 vee-validate + Zod
- **测试策略**: TDD（每个功能先写测试）

### Research Findings
**前端现状**:
- `DescriptionForm.vue` 当前只编辑 8 个字段：title, slug, difficulty, status, isPremium, isPublished, summary, content
- 缺少：examples, constraints, hints, tags 编辑
- 使用手动验证（errors ref），项目已有 vee-validate + Zod 模式（TagEditDialog.vue）
- 使用 shadcn-vue 组件库 + Terminal 风格设计系统
- `MarkdownEditor.vue` 可复用（双栏编辑/预览、工具栏、全屏）

**后端数据模型**:
- `UpdateProblemDTO` 支持字段：slug, title, difficulty, isPremium, isPublished, hasSolution, status, summary, content, examples(String), constraints(String), hints(String), languages(List), tags(List)
- `DescriptionData` 返回：detail.summary, detail.content, detail.constraintsJson[], detail.hints[], examples[], tags[]
- examples 和 constraints/hints 在前端和后端之间需要做 JSON 序列化/反序列化转换

**Console 展示端**:
- `DescriptionMarkdown.vue` 渲染最终效果（含 examples、constraints、followUp）
- `DescriptionView.vue` 展示题目完整信息

### Metis Review
**Identified Gaps** (addressed in plan):
- **Examples 数据格式**: DTO 中为 JSON 字符串，前端需 stringify/parse 转换
- **Tags 编辑模式**: 采用从现有标签列表选择（项目已有标签管理 API）
- **Live Preview 范围**: 包含 content Markdown 渲染 + examples + constraints + hints
- **Terminal 风格**: 保持现有设计系统（OKLCH 颜色、sharp corners、monospace font）
- **状态管理**: 表单数据在组件内管理，提交时统一序列化

---

## Work Objectives

### Core Objective
重构 management 端题目 description 编辑页面，支持题目内容的多层结构编辑（examples、constraints、hints、tags），提供实时预览功能，并升级为现代化的表单验证方案。

### Concrete Deliverables
1. `DescriptionForm.vue` - 重构后的主表单组件（分区块布局）
2. `ExamplesEditor.vue` - 示例用例编辑器（动态添加/删除/排序）
3. `ConstraintsEditor.vue` - 约束条件编辑器（动态添加/删除）
4. `HintsEditor.vue` - 提示编辑器（动态添加/删除/排序）
5. `TagsSelector.vue` - 标签选择器（从现有标签列表多选）
6. `LivePreviewPanel.vue` - 实时预览面板
7. `problemDescriptionSchema.ts` - Zod 验证 schema
8. 测试文件覆盖所有新组件和表单验证

### Definition of Done
- [ ] 所有字段可编辑（title, slug, difficulty, status, summary, content, examples, constraints, hints, tags, isPremium, isPublished）
- [ ] 实时预览面板正确渲染最终效果
- [ ] 表单验证使用 vee-validate + Zod，所有字段有验证规则
- [ ] 所有测试通过（bun test）
- [ ] 保持现有 Terminal 风格设计系统
- [ ] 向后兼容现有 API（updateProblemWithPublish）

### Must Have
- 分区块 Card 布局（基本信息、题目描述、示例用例、约束条件、提示、标签）
- 每个区块可折叠/展开
- 实时预览面板
- vee-validate + Zod 验证
- examples 支持动态添加/删除/排序（input, output, explanation）
- constraints 支持动态添加/删除
- hints 支持动态添加/删除/排序
- tags 从现有标签列表多选
- TDD 测试覆盖

### Must NOT Have (Guardrails)
- 不修改 code tab 和 cases tab
- 不修改后端 API
- 不修改路由配置
- 不修改 ProblemEditView.vue 的 tab 切换逻辑
- 不添加全新的 UI 组件库（只使用现有 shadcn-vue）
- 不修改现有 MarkdownEditor.vue 组件
- 不修改 console 端展示代码

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: YES（vitest + @vue/test-utils）
- **Automated tests**: TDD
- **Framework**: vitest + @vue/test-utils + jsdom
- **TDD Workflow**: 每个新组件先写测试（RED）→ 实现组件（GREEN）→ 重构

### QA Policy
Every task MUST include agent-executed QA scenarios. Evidence saved to `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`.

- **Frontend/UI**: Use Playwright - Navigate, interact, assert DOM, screenshot
- **Form Validation**: Use @vue/test-utils - Mount component, trigger events, assert errors
- **Component Integration**: Use vitest - Test component interactions

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Foundation - Start Immediately):
├── T1: Zod Schema + Types [quick]
└── T2: Tags API Integration [quick]

Wave 2 (Independent Editors - MAX PARALLEL):
├── T3: ExamplesEditor Component [unspecified-high]
├── T4: ConstraintsEditor Component [unspecified-high]
├── T5: HintsEditor Component [unspecified-high]
└── T6: TagsSelector Component [unspecified-high]

Wave 3 (Preview + Form Assembly):
├── T7: LivePreviewPanel Component [unspecified-high]
└── T8: DescriptionForm Refactor (integrate all editors) [deep]

Wave 4 (View Integration + Store Updates):
├── T9: EditDescriptionView.vue Updates [quick]
└── T10: UpdateProblem Store Method [quick]

Wave 5 (Tests):
├── T11: Component Unit Tests [unspecified-high]
└── T12: Form Integration Tests [unspecified-high]

Wave 6 (Cleanup + Polish):
├── T13: i18n Strings [quick]
└── T14: Remove Old DescriptionForm.vue Code [quick]

Wave FINAL (Verification):
├── F1: Plan Compliance Audit (oracle)
├── F2: Code Quality Review (unspecified-high)
├── F3: Real Manual QA (unspecified-high)
└── F4: Scope Fidelity Check (deep)
```

### Dependency Matrix

| Task | Blocked By | Blocks |
|------|-----------|--------|
| T1 (Schema) | - | T3-T6, T8, T11-T12 |
| T2 (Tags API) | - | T6 |
| T3 (ExamplesEditor) | T1 | T8 |
| T4 (ConstraintsEditor) | T1 | T8 |
| T5 (HintsEditor) | T1 | T8 |
| T6 (TagsSelector) | T1, T2 | T8 |
| T7 (PreviewPanel) | - | T8 |
| T8 (Form Refactor) | T1, T3-T7 | T9-T10 |
| T9 (View Update) | T8 | - |
| T10 (Store Update) | - | - |
| T11 (Unit Tests) | T1-T7 | - |
| T12 (Integration Tests) | T1, T8 | - |
| T13 (i18n) | T8 | - |
| T14 (Cleanup) | T8-T10 | - |

### Agent Dispatch Summary

- **Wave 1**: 2 tasks → `quick`
- **Wave 2**: 4 tasks → `unspecified-high`
- **Wave 3**: 2 tasks → `unspecified-high`, `deep`
- **Wave 4**: 2 tasks → `quick`
- **Wave 5**: 2 tasks → `unspecified-high`
- **Wave 6**: 2 tasks → `quick`
- **FINAL**: 4 tasks → `oracle`, `unspecified-high`, `unspecified-high`, `deep`

---

## TODOs

- [x] T1. **Zod Schema + Extended Types**

  **What to do**:
  - 在 `management/src/lib/schemas/` 下新建 `problemDescription.ts`
  - 使用 zod 定义完整的题目描述表单 schema：
    - `title`: string, min(1), max(255)
    - `slug`: string, min(1), regex(/^[a-z0-9-]+$/), max(120)
    - `difficulty`: enum('EASY', 'MEDIUM', 'HARD')
    - `status`: enum('solved', 'attempted', 'todo')
    - `isPremium`: boolean
    - `isPublished`: boolean
    - `summary`: string, max(500).optional()
    - `content`: string, min(1)
    - `examples`: array of { input: string, output: string, explanation?: string }, min(1)
    - `constraints`: array of string, min(1)
    - `hints`: array of string
    - `tags`: array of string (tag IDs)
  - 导出 `type ProblemDescriptionFormData = z.infer<typeof problemDescriptionSchema>`
  - 在 `management/src/api/admin/problems.ts` 中扩展 `UpdateProblemDto` 类型，确保包含所有字段

  **Must NOT do**:
  - 不要修改 `CreateProblemDto`（不在本次范围内）
  - 不要把 schema 放在组件文件里

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with T2)
  - **Blocks**: T3-T6, T8, T11-T12
  - **Blocked By**: None

  **References**:
  - `management/src/lib/schemas/problem.ts` - 现有 problem schema 参考
  - `backend-spring/src/main/java/com/ulticode/modules/problem/dto/UpdateProblemDTO.java` - 后端字段约束
  - `TagEditDialog.vue` - vee-validate + zod 使用模式

  **Acceptance Criteria**:
  - [ ] Schema 文件存在且导出正确
  - [ ] Type inference 工作正常
  - [ ] `bun test management/src/lib/schemas/problemDescription.test.ts` → PASS

  **QA Scenarios**:
  ```
  Scenario: Schema validates valid data
    Tool: Bash (bun test)
    Preconditions: Schema file已创建
    Steps:
      1. bun test management/src/lib/schemas/problemDescription.test.ts
    Expected Result: 所有测试通过
    Evidence: .sisyphus/evidence/task-t1-schema-pass.txt

  Scenario: Schema rejects invalid data
    Tool: Bash (bun test)
    Preconditions: Schema file已创建
    Steps:
      1. bun test management/src/lib/schemas/problemDescription.test.ts --grep "invalid"
    Expected Result: 验证错误测试通过
    Evidence: .sisyphus/evidence/task-t1-schema-invalid.txt
  ```

  **Commit**: YES
  - Message: `feat(schemas): add problem description zod schema`
  - Files: `management/src/lib/schemas/problemDescription.ts`, `management/src/lib/schemas/problemDescription.test.ts`

- [x] T2. **Tags API Integration**

  **What to do**:
  - 确认项目中已有的标签管理 API（在 `management/src/api/admin/tags.ts` 或类似位置）
  - 如果没有，在 `management/src/api/admin/problems.ts` 中添加 `getAllTags()` 方法
  - 返回类型：`Promise<Array<{ id: string; label: string }>>`
  - 在 `useProblemsStore` 中添加 `fetchAllTags()` action

  **Must NOT do**:
  - 不要创建新的标签 CRUD API（只读获取）
  - 不要修改后端

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with T1)
  - **Blocks**: T6 (TagsSelector)
  - **Blocked By**: None

  **References**:
  - `management/src/api/admin/problems.ts` - 现有 API 模式
  - `management/src/stores/admin/problems.ts` - store 模式
  - `console/src/api/problems.ts` - console 端标签 API 参考

  **Acceptance Criteria**:
  - [ ] API 方法可调用并返回标签列表
  - [ ] Store action 正确集成
  - [ ] `bun test` 相关测试通过

  **QA Scenarios**:
  ```
  Scenario: Fetch tags from API
    Tool: Bash (curl)
    Preconditions: Backend running
    Steps:
      1. curl http://localhost:9001/admin/tags -b /tmp/cookies.txt
    Expected Result: 返回标签列表 JSON
    Evidence: .sisyphus/evidence/task-t2-tags-api.json
  ```

  **Commit**: YES
  - Message: `feat(api): add tags fetching for problem edit`
  - Files: `management/src/api/admin/problems.ts`, `management/src/stores/admin/problems.ts`

- [x] T3. **ExamplesEditor Component**

  **What to do**:
  - 新建 `management/src/views/problems/components/ExamplesEditor.vue`
  - 支持动态添加/删除/排序示例用例
  - 每个示例包含：input (textarea), output (textarea), explanation (textarea, optional)
  - 使用 `useFieldArray` from vee-validate 管理数组字段
  - UI：每个示例为一个 Card，可折叠；支持拖拽排序或上下按钮排序
  - 空状态显示提示文字

  **Must NOT do**:
  - 不要做代码编辑器（只使用 textarea）
  - 不要添加 Monaco 或 CodeMirror

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with T4, T5, T6)
  - **Blocks**: T8
  - **Blocked By**: T1 (Schema)

  **References**:
  - `management/src/components/problem/TestCasesEditor.vue` - 类似的多项编辑器模式
  - `management/src/views/problems/components/DescriptionForm.vue` - 当前表单结构
  - vee-validate docs: `useFieldArray` for array field management

  **Acceptance Criteria**:
  - [ ] 可添加新示例
  - [ ] 可删除示例
  - [ ] 可排序示例（上下移动）
  - [ ] 空状态显示友好提示
  - [ ] 与 vee-validate 集成正确

  **QA Scenarios**:
  ```
  Scenario: Add example
    Tool: Playwright
    Preconditions: 页面已加载
    Steps:
      1. 点击 "Add Example" 按钮
      2. 填写 input: "[1,2,3]"
      3. 填写 output: "[2,3]"
      4. 填写 explanation: "因为..."
    Expected Result: 新示例卡片出现，数据正确
    Evidence: .sisyphus/evidence/task-t3-add-example.png

  Scenario: Delete example
    Tool: Playwright
    Preconditions: 至少有一个示例
    Steps:
      1. 点击示例卡片上的删除按钮
    Expected Result: 示例被移除
    Evidence: .sisyphus/evidence/task-t3-delete-example.png
  ```

  **Commit**: YES
  - Message: `feat(components): add ExamplesEditor for problem description`
  - Files: `management/src/views/problems/components/ExamplesEditor.vue`, `management/src/views/problems/components/__tests__/ExamplesEditor.spec.ts`

- [x] T4. **ConstraintsEditor Component**

  **What to do**:
  - 新建 `management/src/views/problems/components/ConstraintsEditor.vue`
  - 动态添加/删除约束条件字符串
  - 每个约束为一个 Input 行，带删除按钮
  - 使用 `useFieldArray` 管理
  - 添加新约束按钮在最下方
  - 空状态显示提示

  **Must NOT do**:
  - 不要做复杂的约束解析器（只存字符串）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with T3, T5, T6)
  - **Blocks**: T8
  - **Blocked By**: T1 (Schema)

  **References**:
  - `management/src/views/problems/components/DescriptionForm.vue` - 当前表单
  - `console/src/views/problems/description/DescriptionMarkdown.vue` - 约束条件展示方式

  **Acceptance Criteria**:
  - [ ] 可添加约束
  - [ ] 可删除约束
  - [ ] 空状态显示提示
  - [ ] 与 vee-validate 集成

  **QA Scenarios**:
  ```
  Scenario: Manage constraints
    Tool: Playwright
    Preconditions: 页面已加载
    Steps:
      1. 点击 "Add Constraint"
      2. 输入 "1 <= nums.length <= 10^4"
      3. 再添加一个 "-10^9 <= nums[i] <= 10^9"
      4. 删除第一个约束
    Expected Result: 约束列表正确更新
    Evidence: .sisyphus/evidence/task-t4-constraints.png
  ```

  **Commit**: YES
  - Message: `feat(components): add ConstraintsEditor for problem description`
  - Files: `management/src/views/problems/components/ConstraintsEditor.vue`, `management/src/views/problems/components/__tests__/ConstraintsEditor.spec.ts`

- [x] T5. **HintsEditor Component**

  **What to do**:
  - 新建 `management/src/views/problems/components/HintsEditor.vue`
  - 动态添加/删除/排序提示字符串
  - 每个提示为一个 Textarea（支持多行）
  - 使用 `useFieldArray` 管理
  - 支持上下排序
  - 空状态显示提示

  **Must NOT do**:
  - 不要做富文本编辑器（纯文本即可）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with T3, T4, T6)
  - **Blocks**: T8
  - **Blocked By**: T1 (Schema)

  **References**:
  - `console/src/views/problems/description/DescriptionView.vue` - hints 展示方式（Accordion 中）

  **Acceptance Criteria**:
  - [ ] 可添加/删除/排序提示
  - [ ] 与 vee-validate 集成

  **QA Scenarios**:
  ```
  Scenario: Manage hints
    Tool: Playwright
    Preconditions: 页面已加载
    Steps:
      1. 添加 hint: "Try using a hash map"
      2. 添加 hint: "Consider time complexity"
      3. 交换两个 hint 的顺序
    Expected Result: 提示列表正确排序
    Evidence: .sisyphus/evidence/task-t5-hints.png
  ```

  **Commit**: YES
  - Message: `feat(components): add HintsEditor for problem description`
  - Files: `management/src/views/problems/components/HintsEditor.vue`, `management/src/views/problems/components/__tests__/HintsEditor.spec.ts`

- [x] T6. **TagsSelector Component**

  **What to do**:
  - 新建 `management/src/views/problems/components/TagsSelector.vue`
  - 从现有标签列表中多选（使用 store 中 fetchAllTags）
  - UI：标签列表显示为 badge/toggle 按钮组，点击选中/取消
  - 或采用 Select + multiple 模式（如果标签很多）
  - 已选标签在上方显示为 removable badges
  - 搜索/过滤功能（如果标签超过 20 个）

  **Must NOT do**:
  - 不要创建新标签（只从现有列表选择）
  - 不要修改标签 API

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with T3, T4, T5)
  - **Blocks**: T8
  - **Blocked By**: T1 (Schema), T2 (Tags API)

  **References**:
  - `management/src/views/problems/components/ProblemForm.vue` - 现有标签选择模式
  - `management/src/components/ui/badge/` - Badge 组件
  - `management/src/components/ui/select/` - Select 组件

  **Acceptance Criteria**:
  - [ ] 可从列表中选择标签
  - [ ] 可取消选择
  - [ ] 已选标签正确显示
  - [ ] 与 vee-validate 集成

  **QA Scenarios**:
  ```
  Scenario: Select tags
    Tool: Playwright
    Preconditions: 页面已加载，标签列表已获取
    Steps:
      1. 点击 "Array" 标签
      2. 点击 "Hash Table" 标签
      3. 取消 "Array" 标签
    Expected Result: 已选标签正确更新
    Evidence: .sisyphus/evidence/task-t6-tags.png
  ```

  **Commit**: YES
  - Message: `feat(components): add TagsSelector for problem description`
  - Files: `management/src/views/problems/components/TagsSelector.vue`, `management/src/views/problems/components/__tests__/TagsSelector.spec.ts`

- [x] T7. **LivePreviewPanel Component**

  **What to do**:
  - 新建 `management/src/views/problems/components/LivePreviewPanel.vue`
  - 接收 `ProblemDescriptionFormData` 作为 props，实时渲染预览
  - 复用 `console/src/views/problems/description/DescriptionMarkdown.vue` 的渲染逻辑
  - 由于 console 和 management 是不同项目，需要：
    - 方案 A：将 `DescriptionMarkdown.vue` + `renderMarkdown`/`sanitizeHtml` utils 提取到 shared 目录
    - 方案 B：在 management 端复制/重新实现渲染逻辑
  - **推荐方案 A**（如果共享目录容易操作），否则方案 B
  - 预览包含：content（Markdown 渲染）、examples（格式化显示）、constraints（列表）、hints（折叠面板）
  - 面板固定在右侧，支持滚动

  **Must NOT do**:
  - 不要引入新的 Markdown 渲染库（复用现有逻辑）
  - 不要做双向编辑（预览只读）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with T8)
  - **Blocks**: T8 (Form Refactor)
  - **Blocked By**: None（可独立开发，但测试需等 T8）

  **References**:
  - `console/src/views/problems/description/DescriptionMarkdown.vue` - 渲染逻辑
  - `console/src/utils/markdown.ts` - Markdown 渲染工具
  - `console/src/utils/sanitize.ts` - HTML 消毒工具
  - `management/src/views/problems/components/DescriptionDisplay.vue` - 现有预览组件

  **Acceptance Criteria**:
  - [ ] 预览面板正确渲染 Markdown
  - [ ] examples 正确格式化显示
  - [ ] constraints 显示为列表
  - [ ] hints 可折叠展开
  - [ ] 数据变化时实时更新

  **QA Scenarios**:
  ```
  Scenario: Live preview updates on input
    Tool: Playwright
    Preconditions: 编辑页面已加载
    Steps:
      1. 在 content 编辑器中输入 "# Two Sum\n\nGiven an array..."
      2. 在右侧预览面板查看
    Expected Result: 预览面板显示正确渲染的 Markdown（含标题）
    Evidence: .sisyphus/evidence/task-t7-preview.png

  Scenario: Preview shows examples
    Tool: Playwright
    Preconditions: 已添加示例
    Steps:
      1. 添加示例 input="[1,2]", output="[0,1]"
      2. 查看预览面板
    Expected Result: 预览面板显示格式化的示例
    Evidence: .sisyphus/evidence/task-t7-preview-examples.png
  ```

  **Commit**: YES
  - Message: `feat(components): add LivePreviewPanel for real-time problem preview`
  - Files: `management/src/views/problems/components/LivePreviewPanel.vue`, `management/src/views/problems/components/__tests__/LivePreviewPanel.spec.ts`

- [x] T8. **DescriptionForm Refactor**

  **What to do**:
  - 完全重构 `management/src/views/problems/components/DescriptionForm.vue`
  - 使用 `vee-validate` + `zod`（`useForm`, `FormField` 等）
  - 布局改为分区块 Card 结构：
    ```
    ┌──────────────────────────────┬──────────────────┐
    │ 基本信息 Card                 │                  │
    │  - title, slug                │   实时预览面板    │
    │  - difficulty, status         │   (T7)           │
    ├──────────────────────────────┤                  │
    │ 题目描述 Card                 │                  │
    │  - summary (textarea)         │                  │
    │  - content (MarkdownEditor)   │                  │
    ├──────────────────────────────┤                  │
    │ 示例用例 Card (T3)            │                  │
    │  - ExamplesEditor             │                  │
    ├──────────────────────────────┤                  │
    │ 约束条件 Card (T4)            │                  │
    │  - ConstraintsEditor          │                  │
    ├──────────────────────────────┤                  │
    │ 提示 Card (T5)                │                  │
    │  - HintsEditor                │                  │
    ├──────────────────────────────┤                  │
    │ 标签 Card (T6)                │                  │
    │  - TagsSelector               │                  │
    └──────────────────────────────┴──────────────────┘
    │ 发布设置 Sidebar (右侧)       │                  │
    │  - isPremium, isPublished     │                  │
    │  - Save/Cancel 按钮           │                  │
    └──────────────────────────────┘                  │
    ```
  - 每个区块使用 Accordion 或独立 Card，支持折叠
  - 数据提交时：
    - examples → JSON.stringify
    - constraints → JSON.stringify
    - hints → JSON.stringify
    - tags → 保持数组（后端接收 List<String>）
  - 保持 `DescriptionFormData` 接口（向后兼容）或更新为新接口
  - 保持 `defineExpose({ setLoading })` 接口

  **Must NOT do**:
  - 不要修改组件的 props 接口（如果可能）
  - 不要修改 emit 的事件签名
  - 不要删除现有功能（只增加）

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖 T1-T7）
  - **Blocks**: T9, T10, T12
  - **Blocked By**: T1, T3, T4, T5, T6, T7

  **References**:
  - `TagEditDialog.vue` - vee-validate + zod 表单模式
  - `ProblemForm.vue` - 复杂表单布局参考
  - `management/src/components/ui/accordion/` - 折叠面板
  - `management/src/components/ui/form/` - shadcn-vue Form 组件

  **Acceptance Criteria**:
  - [ ] 所有字段可编辑
  - [ ] vee-validate 验证正常工作
  - [ ] 提交时数据正确序列化
  - [ ] 布局为分区块结构
  - [ ] 向后兼容现有接口

  **QA Scenarios**:
  ```
  Scenario: Form validates and submits
    Tool: Playwright
    Preconditions: 编辑页面已加载
    Steps:
      1. 清空 title 字段
      2. 点击 Save
    Expected Result: 显示验证错误 "Title is required"
    Evidence: .sisyphus/evidence/task-t8-validation.png

  Scenario: Full form submission
    Tool: Playwright
    Preconditions: 编辑页面已加载
    Steps:
      1. 填写所有字段
      2. 添加 2 个示例、3 个约束、1 个提示
      3. 选择 2 个标签
      4. 点击 Save
    Expected Result: Toast 显示 "Update successful"，数据正确保存
    Evidence: .sisyphus/evidence/task-t8-submit.png
  ```

  **Commit**: YES
  - Message: `feat(forms): refactor DescriptionForm with multi-section layout and vee-validate`
  - Files: `management/src/views/problems/components/DescriptionForm.vue`

- [x] T9. **EditDescriptionView.vue Updates**

  **What to do**:
  - 更新 `management/src/views/problems/edit/EditDescriptionView.vue`
  - 调整 `formattedProblem` computed 以包含新字段（examples, constraints, hints, tags）
  - 更新 `handleSubmit` 以传递所有字段到 `updateProblemWithPublish`
  - 确保序列化正确：examples/constraints/hints → JSON.stringify
  - 保持 Terminal Header 和 Loading State 不变
  - 调整布局以容纳 LivePreviewPanel（右侧固定宽度）

  **Must NOT do**:
  - 不要修改路由逻辑
  - 不要修改 Tab 切换

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖 T8）
  - **Blocked By**: T8

  **References**:
  - `EditDescriptionView.vue` - 当前实现
  - `useProblemsStore` - updateProblemWithPublish 方法

  **Acceptance Criteria**:
  - [ ] 新字段正确传递给 store
  - [ ] 页面布局正确

  **QA Scenarios**:
  ```
  Scenario: Edit page loads with all fields
    Tool: Playwright
    Preconditions: 题目已存在且有完整数据
    Steps:
      1. 访问 /problems/1/edit/description
    Expected Result: 所有字段正确填充
    Evidence: .sisyphus/evidence/task-t9-load.png
  ```

  **Commit**: YES
  - Message: `feat(views): update EditDescriptionView for new form fields`
  - Files: `management/src/views/problems/edit/EditDescriptionView.vue`

- [x] T10. **Store Data Serialization Update**

  **What to do**:
  - 在 `management/src/stores/admin/problems.ts` 中
  - 更新 `updateProblemWithPublish` 方法的数据序列化逻辑
  - 确保 examples/constraints/hints 在提交前 JSON.stringify
  - 确保接收数据时 JSON.parse（如果需要）
  - 注意：`updateProblem` API 接收 `UpdateProblemDto`，其中 examples/constraints/hints 为 String 类型

  **Must NOT do**:
  - 不要修改 API 调用方式
  - 不要修改 store 的其他方法

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T9 同时）
  - **Blocked By**: T8（逻辑上）

  **References**:
  - `management/src/stores/admin/problems.ts:368-404` - updateProblemWithPublish
  - `UpdateProblemDTO.java` - 后端 DTO 字段类型

  **Acceptance Criteria**:
  - [ ] examples 提交时为 JSON 字符串
  - [ ] constraints 提交时为 JSON 字符串
  - [ ] hints 提交时为 JSON 字符串

  **QA Scenarios**:
  ```
  Scenario: Data serialization
    Tool: Bash (curl)
    Preconditions: Backend running
    Steps:
      1. 通过表单提交带 examples 的数据
      2. 抓包查看请求体
    Expected Result: examples 字段为 JSON 字符串格式
    Evidence: .sisyphus/evidence/task-t10-serialization.json
  ```

  **Commit**: YES
  - Message: `feat(store): update data serialization for new fields`
  - Files: `management/src/stores/admin/problems.ts`

- [x] T11. **Component Unit Tests**

  **What to do**:
  - 为 T3-T7 的每个新组件编写单元测试
  - 使用 `@vue/test-utils` + `vitest`
  - 测试覆盖：
    - 渲染测试（mount 后 DOM 结构正确）
    - 交互测试（添加/删除/排序操作）
    - Props 测试（数据正确传递）
    - 事件测试（emit 正确触发）
  - 测试文件位置：`management/src/views/problems/components/__tests__/` 或同级目录

  **Must NOT do**:
  - 不要测试第三方库内部逻辑
  - 不要做 E2E 测试（那是 T12 的工作）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（T3-T7 完成后）
  - **Blocked By**: T3, T4, T5, T6, T7

  **References**:
  - `management/vitest.config.ts` - 测试配置
  - 项目现有测试文件模式

  **Acceptance Criteria**:
  - [ ] ExamplesEditor 测试通过
  - [ ] ConstraintsEditor 测试通过
  - [ ] HintsEditor 测试通过
  - [ ] TagsSelector 测试通过
  - [ ] LivePreviewPanel 测试通过

  **QA Scenarios**:
  ```
  Scenario: Run all unit tests
    Tool: Bash
    Preconditions: 所有组件已实现
    Steps:
      1. cd management && bun test
    Expected Result: 所有新组件测试通过
    Evidence: .sisyphus/evidence/task-t11-unit-tests.txt
  ```

  **Commit**: YES (可以合并提交)
  - Message: `test(components): add unit tests for new editor components`
  - Files: `management/src/views/problems/components/__tests__/*.spec.ts`

- [x] T12. **Form Integration Tests**

  **What to do**:
  - 为重构后的 `DescriptionForm.vue` 编写集成测试
  - 测试完整表单流程：
    - 表单加载和初始化
    - 字段编辑和验证
    - 提交数据和序列化
    - 错误处理
  - 模拟 API 调用（使用 `vi.fn()`）

  **Must NOT do**:
  - 不要连接真实后端

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 T11 同时）
  - **Blocked By**: T1, T8

  **References**:
  - `DescriptionForm.vue` - 待测试组件
  - `TagEditDialog.vue` - 现有表单测试参考（如有）

  **Acceptance Criteria**:
  - [ ] 表单完整流程测试通过
  - [ ] 验证规则测试覆盖

  **QA Scenarios**:
  ```
  Scenario: Full form integration test
    Tool: Bash
    Steps:
      1. cd management && bun test src/views/problems/components/__tests__/DescriptionForm.spec.ts
    Expected Result: 所有测试通过
    Evidence: .sisyphus/evidence/task-t12-integration-tests.txt
  ```

  **Commit**: YES
  - Message: `test(forms): add integration tests for DescriptionForm`
  - Files: `management/src/views/problems/components/__tests__/DescriptionForm.spec.ts`

- [x] T13. **i18n Strings**

  **What to do**:
  - 在 `management/src/locales/` 下的中文和英文翻译文件中添加新字段的翻译
  - 覆盖所有新增 UI 文本：区块标题、按钮文字、占位符、提示信息、验证错误消息
  - 保持命名一致性：`problems.descriptionForm.*`

  **Must NOT do**:
  - 不要修改其他页面的翻译
  - 不要遗漏英文翻译

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Blocked By**: T8（需要知道所有 UI 文本）

  **References**:
  - `management/src/i18n/locales/zh-CN/modules/problems.ts` - 中文翻译
  - `management/src/i18n/locales/en-US/modules/problems.ts` - 英文翻译

  **Acceptance Criteria**:
  - [ ] 所有新增 UI 文本有翻译
  - [ ] 中英文都完整

  **Commit**: YES
  - Message: `feat(i18n): add translations for new description form fields`
  - Files: `management/src/i18n/locales/zh-CN/modules/problems.ts`, `management/src/i18n/locales/en-US/modules/problems.ts`

- [x] T14. **Cleanup Old Code**

  **What to do**:
  - 删除 `DescriptionForm.vue` 中旧的验证逻辑（errors ref, validate 函数）
  - 清理未使用的 imports
  - 如果 `DescriptionDisplay.vue` 不再需要，标记为 deprecated 或删除
  - 更新 `EditDescriptionView.vue` 中未使用的代码

  **Must NOT do**:
  - 不要删除还在使用的代码
  - 不要破坏向后兼容

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Blocked By**: T8-T10

  **Acceptance Criteria**:
  - [ ] 无未使用变量/导入
  - [ ] 构建无警告

  **Commit**: YES
  - Message: `refactor: clean up old DescriptionForm code`
  - Files: 相关清理文件

---

## Final Verification Wave

> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.

- [x] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file, curl endpoint, run command). For each "Must NOT Have": search codebase for forbidden patterns — reject with file:line if found. Check evidence files exist in .sisyphus/evidence/. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **Code Quality Review** — `unspecified-high`
  Run `pnpm lint` + `pnpm type-check` + `bun test`. Review all changed files for: `as any`/`@ts-ignore`, empty catches, console.log in prod, commented-out code, unused imports. Check AI slop: excessive comments, over-abstraction, generic names.
  Output: `Build [PASS/FAIL] | Lint [PASS/FAIL] | Tests [N pass/N fail] | Files [N clean/N issues] | VERDICT`

- [x] F3. **Real Manual QA** — `unspecified-high` (+ `playwright` skill)
  Start from clean state. Execute EVERY QA scenario from EVERY task — follow exact steps, capture evidence. Test cross-task integration. Test edge cases: empty state, invalid input, rapid actions. Save to `.sisyphus/evidence/final-qa/`.
  Output: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [x] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual diff (git log/diff). Verify 1:1 — everything in spec was built, nothing beyond spec was built. Check "Must NOT do" compliance. Detect cross-task contamination.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

- **Wave 1**: `feat(schemas): add problem description zod schema` + `feat(api): add tags fetching for problem edit`
- **Wave 2**: `feat(components): add ExamplesEditor` + `feat(components): add ConstraintsEditor` + `feat(components): add HintsEditor` + `feat(components): add TagsSelector`
- **Wave 3**: `feat(components): add LivePreviewPanel` + `feat(forms): refactor DescriptionForm with multi-section layout and vee-validate`
- **Wave 4**: `feat(views): update EditDescriptionView for new form fields` + `feat(store): update data serialization for new fields`
- **Wave 5**: `test(components): add unit tests for new editor components` + `test(forms): add integration tests for DescriptionForm`
- **Wave 6**: `feat(i18n): add translations for new description form fields` + `refactor: clean up old DescriptionForm code`

---

## Success Criteria

### Verification Commands
```bash
# 1. 构建检查
cd management && pnpm build

# 2. 类型检查
cd management && pnpm type-check

# 3. 代码检查
cd management && pnpm lint

# 4. 单元测试
cd management && bun test

# 5. 集成测试（手动 Playwright）
# - 访问 http://localhost:9003/problems/1/edit/description
# - 验证所有字段可编辑
# - 验证实时预览正常工作
# - 验证保存后数据正确
```

### Final Checklist
- [ ] 所有 "Must Have" 字段可编辑（examples, constraints, hints, tags）
- [ ] 实时预览面板正确渲染
- [ ] vee-validate + Zod 验证正常工作
- [ ] 所有测试通过（bun test）
- [ ] 构建无错误（pnpm build）
- [ ] 类型检查无错误（pnpm type-check）
- [ ] 代码检查无错误（pnpm lint）
- [ ] i18n 中英文完整
- [ ] 向后兼容现有 API
- [ ] Terminal 风格设计系统保持一致
