# 修复管理端前端异常与后端 500 错误

## TL;DR

> **快速摘要**: 修复管理端（management/）三类异常：Vue Router `next()` 弃用警告（1文件5处）、intlify 中文国际化键值缺失（11个键 + 2个路径错误）、后端 `/admin/submissions/statistics` 500 错误（MyBatis GROUP BY NULL 强转 NPE）。
>
> **交付物**:
> - `management/src/router/index.ts` — 移除 `next()` 回调，返回路由对象
> - `management/src/i18n/locales/zh-CN/modules/` — 补充 11 个缺失键 + 修复 2 个路径错误
> - `backend-spring/src/main/java/.../submission/mapper/SubmissionMapper.java` — @Select 注解添加 WHERE IS NOT NULL
>
> **Estimated Effort**: Short
> **Parallel Execution**: YES — 9 个任务并行（Wave 1），4 个审查并行（Wave FINAL）
> **Critical Path**: Wave 1（全部并行）→ Wave FINAL（审查）→ 用户确认

---

## Context

### Original Request
用户报告管理端浏览器控制台出现三类异常：
1. Vue Router 弃用警告：`The \`next()\` callback in navigation guards is deprecated`
2. intlify 键值缺失：`Not found 'xxx' key in 'zh' locale messages`
3. 后端 500 错误：`GET /admin/submissions/statistics 500`

### Interview Summary
**无用户访谈** — 用户直接提供了控制台日志，异常信息充分，无需额外澄清。

### Research Findings

**探索代理发现**:

| 问题 | 文件/位置 | 根因 |
|------|----------|------|
| Router `next()` 弃用 | `management/src/router/index.ts` L337-414 | Vue Router 4.x 弃用了导航守卫中的 `next()` 回调 |
| 国际化键缺失 | `zh-CN/modules/*.ts` 多处 | 代码引用路径与翻译文件中的键路径不匹配，或键完全缺失 |
| 后端 500 | `SubmissionMapper.java` @Select 注解 | `countByStatus()` / `countByLanguage()` 的 GROUP BY 可能返回 NULL 分组，强转时 NPE |

### Metis Review
**识别的偏差**（已处理）：
- **偏差1**：国际化不是「位置错误需移动」，而是「代码引用路径与翻译文件不匹配」。**默认策略**：保持代码引用不变，在翻译文件中新增对应键（更安全，避免修改多处代码）。
- **偏差2**：`common.actions` 在 zh-CN 中是字符串类型，改为对象可能破坏 `t('common.actions')` 的直接渲染。**处理**：计划中包含引用检查任务，先定位所有引用再修改。
- **偏差3**：后端没有 Mapper XML，SQL 通过 `@Select` 注解写在 Java 接口中。**处理**：修改 `SubmissionMapper.java` 中的注解。
- **偏差4**：添加 `WHERE IS NOT NULL` 会排除 NULL 状态的提交，改变统计结果。**默认接受**：NULL status/language 视为脏数据，排除不影响业务逻辑。

---

## Work Objectives

### Core Objective
修复管理端控制台所有异常输出，使前端无弃用警告、无国际化缺失错误，后端统计接口正常返回 200。

### Concrete Deliverables
- [ ] `management/src/router/index.ts` — `beforeEach` 签名移除 `next` 参数，5 处 `next()` 替换为返回值
- [ ] `management/src/i18n/locales/zh-CN/modules/common.ts` — `actions` 改为对象，添加 `toggleLanguage`
- [ ] `management/src/i18n/locales/zh-CN/modules/users.ts` — 添加 `actions.bulkBanUser`
- [ ] `management/src/i18n/locales/zh-CN/modules/contests.ts` — 添加 `type.biweekly`、`type.weekly`
- [ ] `management/src/i18n/locales/zh-CN/modules/comments.ts` — `status` 改为对象，添加 `unknown`
- [ ] `management/src/i18n/locales/zh-CN/modules/moderation.ts` — 添加 `flagDescription`
- [ ] `management/src/i18n/locales/zh-CN/modules/table.ts` — 添加 `selectAll`
- [ ] `management/src/i18n/locales/zh-CN/modules/problems.ts` — 确认 `dialog.delete.*` 键是否正确加载
- [ ] `backend-spring/.../submission/mapper/SubmissionMapper.java` — `@Select` 注解添加 `WHERE status IS NOT NULL` 和 `WHERE language IS NOT NULL`

### Definition of Done
- [ ] 管理端 `pnpm type-check` 通过，无类型错误
- [ ] 管理端 `pnpm build` 通过，无 intlify 警告
- [ ] `curl /admin/submissions/statistics` 返回 200 + 有效 JSON

### Must Have
- 修复所有导致控制台输出的异常
- 保持现有功能不变（除了排除 NULL 数据的统计行为变更）
- en-US 翻译同步更新（如有缺失）

### Must NOT Have (Guardrails)
- 不修改 console/ 前端代码
- 不修改除 submissions/statistics 外的其他后端接口
- 不重构路由守卫逻辑，只做 `next()` → 返回值的语法迁移
- 不主动扫描和修复未报告的国际化问题
- `common.actions` 改为对象时，不破坏现有直接渲染 `t('common.actions')` 的引用

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: YES（management/ 使用 Vitest + TypeScript）
- **Automated tests**: None — 本次修复为翻译和配置修改，不涉及业务逻辑，无需新增单元测试
- **Agent-Executed QA**: ALWAYS — 每个任务完成后执行验证

### QA Policy
每个任务包含 Agent-Executed QA Scenarios，证据保存到 `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`。

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately — 全部并行):
├── Task 1: Vue Router next() 迁移 [quick]
├── Task 2: 后端 @Select NULL 修复 [quick]
├── Task 3: common.ts actions 结构修复 + toggleLanguage [quick]
├── Task 4: users.ts 添加 bulkBanUser [quick]
├── Task 5: contests.ts 添加 biweekly/weekly [quick]
├── Task 6: comments.ts status 结构修复 + unknown [quick]
├── Task 7: moderation.ts 添加 flagDescription [quick]
├── Task 8: table.ts 添加 selectAll [quick]
└── Task 9: problems.ts dialog.delete 加载检查 [quick]

Wave FINAL (After ALL tasks — 4 并行审查):
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: Real manual QA (unspecified-high)
└── Task F4: Scope fidelity check (deep)
-> 呈现结果 -> 获取用户明确 okay
```

### Dependency Matrix

| Task | Blocked By | Blocks |
|------|-----------|--------|
| 1 (Router) | — | F1-F4 |
| 2 (Backend) | — | F1-F4 |
| 3 (common.ts) | — | F1-F4 |
| 4 (users.ts) | — | F1-F4 |
| 5 (contests.ts) | — | F1-F4 |
| 6 (comments.ts) | — | F1-F4 |
| 7 (moderation.ts) | — | F1-F4 |
| 8 (table.ts) | — | F1-F4 |
| 9 (problems.ts) | — | F1-F4 |
| F1-F4 | 1-9 | — |

> 所有 Wave 1 任务完全独立，无交叉依赖，可 9 个并行执行。

### Agent Dispatch Summary

- **Wave 1**: 9 个任务全部使用 `quick` agent（单行/少量修改）
- **Wave FINAL**: F1 → `oracle`, F2 → `unspecified-high`, F3 → `unspecified-high`, F4 → `deep`

---

## TODOs

- [x] 1. Vue Router `next()` 弃用迁移

  **What to do**:
  - 修改 `management/src/router/index.ts` L337：移除 `beforeEach` 的 `next` 参数
  - L363：`return next({ name: 'login', query: { redirect: to.fullPath } })` → `return { name: 'login', query: { redirect: to.fullPath } }`
  - L390：`return next({ name: 'dashboard' })` → `return { name: 'dashboard' }`
  - L405：`return next({ name: 'dashboard' })` → `return { name: 'dashboard' }`
  - L411：`return next({ name: 'dashboard' })` → `return { name: 'dashboard' }`
  - L414：`next()` → `return true`

  **Must NOT do**:
  - 不修改路由守卫逻辑（权限判断、重定向规则保持不变）
  - 不修改 `beforeEach` 之外的代码

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `frontend-patterns`
    - Vue Router 4.x 导航守卫语法变更属于前端路由模式

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: F1-F4
  - **Blocked By**: —

  **References**:
  - `management/src/router/index.ts:337-414` — 当前 `beforeEach` 实现，包含所有 5 处 `next()` 调用
  - Vue Router 4.x 官方文档 — 导航守卫新语法（返回 false / 路由对象 / true）

  **Acceptance Criteria**:
  - [ ] `cd management && pnpm type-check` → PASS（无 `next` 参数相关类型错误）

  **QA Scenarios**:

  ```
  Scenario: 未认证用户访问受保护路由 → 重定向到登录页
    Tool: Playwright
    Preconditions: 清除浏览器 cookie/session，确保未登录
    Steps:
      1. 访问 http://localhost:9003/users（管理端用户列表页，需认证）
      2. 等待页面加载
    Expected Result: URL 变为 http://localhost:9003/login?redirect=/users，页面显示登录表单
    Evidence: .sisyphus/evidence/task-1-unauth-redirect.png

  Scenario: 已认证用户访问登录页 → 重定向到 dashboard
    Tool: Playwright
    Preconditions: 先登录管理端（admin/admin123）
    Steps:
      1. 访问 http://localhost:9003/login
      2. 等待页面加载
    Expected Result: URL 自动跳转到 http://localhost:9003/dashboard
    Evidence: .sisyphus/evidence/task-1-auth-redirect.png
  ```

  **Commit**: YES
  - Message: `fix(router): migrate beforeEach from next() callback to return value`
  - Files: `management/src/router/index.ts`
  - Pre-commit: `cd management && pnpm type-check`

---

- [x] 2. 后端 `SubmissionMapper` @Select NULL 修复

  **What to do**:
  - 修改 `backend-spring/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java`
  - 找到 `countByStatus()` 方法（约 L249），在 `@Select` 注解 SQL 中添加 `WHERE status IS NOT NULL`：
    ```java
    @Select("SELECT status, COUNT(*) as count FROM submissions WHERE status IS NOT NULL GROUP BY status")
    ```
  - 找到 `countByLanguage()` 方法（约 L257），在 `@Select` 注解 SQL 中添加 `WHERE language IS NOT NULL`：
    ```java
    @Select("SELECT language, COUNT(*) as count FROM submissions WHERE language IS NOT NULL GROUP BY language ORDER BY count DESC")
    ```

  **Must NOT do**:
  - 不修改其他 `@Select` 方法
  - 不修改 `AdminSubmissionServiceImpl.java`（Service 层逻辑正确，问题在 SQL）

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `springboot-patterns`
    - MyBatis @Select 注解语法和 Spring Boot 数据访问模式

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: F1-F4
  - **Blocked By**: —

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java:249-258` — 当前 @Select 注解 SQL
  - `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminSubmissionServiceImpl.java:165-207` — Service 层调用 `countByStatus()` / `countByLanguage()`
  - `db-manager/migrations/V1__core_schema.sql:151,153` — status/language 定义为 NOT NULL，但可能存在历史脏数据

  **Acceptance Criteria**:
  - [ ] `curl -s http://localhost:9001/admin/submissions/statistics` → HTTP 200，返回有效 JSON（包含 statusDistribution 和 languageDistribution 字段）

  **QA Scenarios**:

  ```
  Scenario: 统计接口返回 200 且数据完整
    Tool: Bash (curl)
    Preconditions: 后端服务已启动（port 9001）
    Steps:
      1. curl -s http://localhost:9001/admin/submissions/statistics
      2. 解析 JSON 响应
    Expected Result:
      - HTTP status: 200
      - JSON 包含 statusDistribution 数组（每个元素有 status 和 count 字段）
      - JSON 包含 languageDistribution 数组（每个元素有 language 和 count 字段）
      - statusDistribution 中无 null 键
      - languageDistribution 中无 null 键
    Evidence: .sisyphus/evidence/task-2-statistics-200.json

  Scenario: 验证 NULL 数据已被排除
    Tool: Bash (mysql via docker)
    Preconditions: MySQL 容器运行中
    Steps:
      1. docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode -e "SELECT status, COUNT(*) FROM submissions WHERE status IS NULL GROUP BY status;"
      2. docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode -e "SELECT language, COUNT(*) FROM submissions WHERE language IS NULL GROUP BY language;"
    Expected Result: 查询返回空结果（无 NULL 分组），证明 SQL 正确排除了 NULL
    Evidence: .sisyphus/evidence/task-2-null-check.txt
  ```

  **Commit**: YES
  - Message: `fix(mapper): exclude NULL status/language from submission statistics`
  - Files: `backend-spring/src/main/java/com/ulticode/modules/submission/mapper/SubmissionMapper.java`
  - Pre-commit: 无（后端编译在运行时，无静态检查命令）

---

- [x] 3. `common.ts` actions 结构修复 + 添加 `toggleLanguage`

  **What to do**:
  - 修改 `management/src/i18n/locales/zh-CN/modules/common.ts`
  - **步骤1**：检查 `common.actions` 的所有引用（确保没有 `t('common.actions')` 直接作为字符串渲染）
  - **步骤2**：将 `actions` 从字符串 `'操作'` 改为对象，保留原有内容并新增 `toggleLanguage`：
    ```typescript
    actions: {
      // 如果原来有使用 actions 作为字符串的地方，保留一个默认键
      label: '操作',
      toggleLanguage: '切换语言',
    },
    ```
  - **步骤3**：同步检查 `en-US/modules/common.ts`，如有缺失则补充对应英文翻译

  **Must NOT do**:
  - 不删除现有 `actions` 键，避免破坏已有引用
  - 不修改 `common.ts` 中其他键

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: `frontend-patterns`
    - Vue i18n (intlify) 键结构约定

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: F1-F4
  - **Blocked By**: —

  **References**:
  - `management/src/i18n/locales/zh-CN/modules/common.ts` — 当前 common 翻译模块
  - `management/src/i18n/locales/en-US/modules/common.ts` — 英文对照（如有）
  - `management/src/components/LanguageSwitcher.vue:20` — 调用 `t('common.actions.toggleLanguage')`

  **Acceptance Criteria**:
  - [ ] `cd management && pnpm build` → 无 intlify `common.actions.toggleLanguage` 警告
  - [ ] `grep -r "t('common.actions')" management/src/` → 无直接渲染引用（或已修改为 `t('common.actions.label')`）

  **QA Scenarios**:

  ```
  Scenario: LanguageSwitcher 组件无 intlify 警告
    Tool: Playwright
    Preconditions: 管理端已启动（port 9003）
    Steps:
      1. 打开浏览器 DevTools Console
      2. 访问 http://localhost:9003/
      3. 等待页面加载
      4. 查看 Console 输出
    Expected Result: 无 `[intlify] Not found 'common.actions.toggleLanguage'` 警告
    Evidence: .sisyphus/evidence/task-3-toggleLanguage.png

  Scenario: 验证 common.actions 引用未破坏
    Tool: Bash (grep)
    Preconditions: 无
    Steps:
      1. grep -rn "t('common.actions')" management/src/ | grep -v "toggleLanguage"
      2. 如有结果，检查是否为直接模板渲染（{{ }}）
    Expected Result: 无 `{{ t('common.actions') }}` 直接渲染（否则需改为 `t('common.actions.label')`）
    Evidence: .sisyphus/evidence/task-3-actions-refs.txt
  ```

  **Commit**: YES
  - Message: `fix(i18n): add common.actions.toggleLanguage and restructure actions as object`
  - Files: `management/src/i18n/locales/zh-CN/modules/common.ts`, `management/src/i18n/locales/en-US/modules/common.ts`
  - Pre-commit: `cd management && pnpm build`

---

- [x] 4. `users.ts` 添加 `bulkBanUser`

  **What to do**:
  - 修改 `management/src/i18n/locales/zh-CN/modules/users.ts`
  - 在 `actions` 对象（约 L73-87）中添加 `bulkBanUser: '批量封禁用户'`
  - 同步检查 `en-US/modules/users.ts`，添加对应英文 `bulkBanUser: 'Bulk Ban Users'`

  **Must NOT do**:
  - 不修改 users.ts 中其他键

  **Recommended Agent Profile**:
  - **Category**: `quick`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: F1-F4
  - **Blocked By**: —

  **References**:
  - `management/src/i18n/locales/zh-CN/modules/users.ts:73-87` — 当前 actions 对象
  - `management/src/views/users/UsersListView.vue:436` — 调用 `t('users.actions.bulkBanUser')`

  **Acceptance Criteria**:
  - [ ] `cd management && pnpm build` → 无 intlify `users.actions.bulkBanUser` 警告

  **QA Scenarios**:

  ```
  Scenario: UsersListView 无 bulkBanUser 警告
    Tool: Playwright
    Preconditions: 管理端已启动，已登录
    Steps:
      1. 访问 http://localhost:9003/users
      2. 打开 DevTools Console
      3. 等待页面加载完成
    Expected Result: Console 无 `[intlify] Not found 'users.actions.bulkBanUser'` 警告
    Evidence: .sisyphus/evidence/task-4-bulkBanUser.png
  ```

  **Commit**: YES
  - Message: `fix(i18n): add users.actions.bulkBanUser translation`
  - Files: `management/src/i18n/locales/zh-CN/modules/users.ts`, `management/src/i18n/locales/en-US/modules/users.ts`
  - Pre-commit: `cd management && pnpm build`

---

- [x] 5. `contests.ts` 添加 `biweekly` 和 `weekly`

  **What to do**:
  - 修改 `management/src/i18n/locales/zh-CN/modules/contests.ts`
  - 在 `type` 对象（约 L25-38）中添加：
    ```typescript
    biweekly: '双周赛',
    weekly: '周赛',
    ```
  - 同步检查 `en-US/modules/contests.ts`，添加对应英文翻译

  **Recommended Agent Profile**:
  - **Category**: `quick`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1

  **References**:
  - `management/src/i18n/locales/zh-CN/modules/contests.ts:25-38` — 当前 type 对象
  - `management/src/views/contests/columns.ts:47` — 调用 `t('contests.type.biweekly')` 和 `t('contests.type.weekly')`

  **Acceptance Criteria**:
  - [ ] `cd management && pnpm build` → 无 intlify `contests.type.biweekly` / `contests.type.weekly` 警告

  **QA Scenarios**:
  ```
  Scenario: Contests 列表页无 type 警告
    Tool: Playwright
    Steps:
      1. 访问 http://localhost:9003/contests
      2. 打开 DevTools Console
    Expected Result: 无 `[intlify] Not found 'contests.type.weekly'` 或 `contests.type.biweekly` 警告
    Evidence: .sisyphus/evidence/task-5-contest-types.png
  ```

  **Commit**: YES
  - Message: `fix(i18n): add contests.type.biweekly and contests.type.weekly translations`
  - Files: `management/src/i18n/locales/zh-CN/modules/contests.ts`, `management/src/i18n/locales/en-US/modules/contests.ts`

---

- [x] 6. `comments.ts` status 结构修复 + 添加 `unknown`

  **What to do**:
  - 修改 `management/src/i18n/locales/zh-CN/modules/comments.ts`
  - **步骤1**：检查 `comments.status` 的所有引用路径（确认代码引用的是 `comments.status.unknown` 还是 `comments.columns.status`）
  - **步骤2**：如果代码引用 `comments.status.unknown`，则在 comments.ts 中新增 `status` 对象：
    ```typescript
    status: {
      unknown: '未知',
    },
    ```
  - 注意：comments.ts 中已有 `columns: { status: '状态' }`，不要破坏现有结构
  - 同步检查 `en-US/modules/comments.ts`

  **Must NOT do**:
  - 不删除或修改 `columns.status` 键（可能其他代码引用 `comments.columns.status`）

  **Recommended Agent Profile**:
  - **Category**: `quick`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1

  **References**:
  - `management/src/i18n/locales/zh-CN/modules/comments.ts` — 当前 comments 翻译模块
  - `management/src/views/comments/columns.ts:125` — 调用 `t('comments.status.unknown')`

  **Acceptance Criteria**:
  - [ ] `cd management && pnpm build` → 无 intlify `comments.status.unknown` 警告

  **QA Scenarios**:
  ```
  Scenario: Comments 列表页无 status unknown 警告
    Tool: Playwright
    Steps:
      1. 访问 http://localhost:9003/comments
      2. 打开 DevTools Console
    Expected Result: 无 `[intlify] Not found 'comments.status.unknown'` 警告
    Evidence: .sisyphus/evidence/task-6-comments-status.png
  ```

  **Commit**: YES
  - Message: `fix(i18n): add comments.status.unknown translation`
  - Files: `management/src/i18n/locales/zh-CN/modules/comments.ts`, `management/src/i18n/locales/en-US/modules/comments.ts`

---

- [x] 7. `moderation.ts` 添加 `flagDescription`

  **What to do**:
  - 修改 `management/src/i18n/locales/zh-CN/modules/moderation.ts`
  - 添加 `flagDescription` 键：
    ```typescript
    flagDescription: '标记 "{title}" 供审核。请提供标记原因。',
    ```
  - 同步检查 `en-US/modules/moderation.ts`

  **Recommended Agent Profile**:
  - **Category**: `quick`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1

  **References**:
  - `management/src/i18n/locales/zh-CN/modules/moderation.ts` — 当前 moderation 翻译模块
  - `management/src/views/problems/ProblemsListView.vue:369` — 调用 `t('moderation.flagDescription')`

  **Acceptance Criteria**:
  - [ ] `cd management && pnpm build` → 无 intlify `moderation.flagDescription` 警告

  **QA Scenarios**:
  ```
  Scenario: ProblemsListView 无 flagDescription 警告
    Tool: Playwright
    Steps:
      1. 访问 http://localhost:9003/problems
      2. 打开 DevTools Console
    Expected Result: 无 `[intlify] Not found 'moderation.flagDescription'` 警告
    Evidence: .sisyphus/evidence/task-7-flagDescription.png
  ```

  **Commit**: YES
  - Message: `fix(i18n): add moderation.flagDescription translation`
  - Files: `management/src/i18n/locales/zh-CN/modules/moderation.ts`, `management/src/i18n/locales/en-US/modules/moderation.ts`

---

- [x] 8. `table.ts` 添加 `selectAll`

  **What to do**:
  - 修改 `management/src/i18n/locales/zh-CN/modules/table.ts`
  - 添加 `selectAll` 键：
    ```typescript
    selectAll: '全选',
    ```
  - 同步检查 `en-US/modules/table.ts`

  **Recommended Agent Profile**:
  - **Category**: `quick`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1

  **References**:
  - `management/src/i18n/locales/zh-CN/modules/table.ts` — 当前 table 翻译模块
  - `management/src/components/table/columns.ts:57,65` — 调用 `t('table.selectAll')`

  **Acceptance Criteria**:
  - [ ] `cd management && pnpm build` → 无 intlify `table.selectAll` 警告

  **QA Scenarios**:
  ```
  Scenario: Table 组件无 selectAll 警告
    Tool: Playwright
    Steps:
      1. 访问 http://localhost:9003/users（含表格选择功能）
      2. 打开 DevTools Console
    Expected Result: 无 `[intlify] Not found 'table.selectAll'` 警告
    Evidence: .sisyphus/evidence/task-8-selectAll.png
  ```

  **Commit**: YES
  - Message: `fix(i18n): add table.selectAll translation`
  - Files: `management/src/i18n/locales/zh-CN/modules/table.ts`, `management/src/i18n/locales/en-US/modules/table.ts`

---

- [x] 9. `problems.ts` `dialog.delete.*` 加载问题检查

  **What to do**:
  - 检查 `management/src/i18n/locales/zh-CN/modules/problems.ts` 中 `dialog.delete` 键的结构
  - 确认以下键是否存在且路径正确：
    - `problems.dialog.delete.title`
    - `problems.dialog.delete.thisProblem`
    - `problems.dialog.delete.description`
    - `problems.dialog.delete.confirm`
  - 如果键存在但 vue-i18n 仍报错，检查是否因为 `dialog` 是字符串而非对象，或键路径嵌套错误
  - 如有问题则修复结构；如无问题则记录原因

  **Recommended Agent Profile**:
  - **Category**: `quick`

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1

  **References**:
  - `management/src/i18n/locales/zh-CN/modules/problems.ts:345-350` — 当前 dialog.delete 键定义
  - `management/src/views/problems/ProblemsListView.vue:368-369` — 调用 `t('problems.dialog.delete.*')`

  **Acceptance Criteria**:
  - [ ] `cd management && pnpm build` → 无 intlify `problems.dialog.delete.*` 警告

  **QA Scenarios**:
  ```
  Scenario: ProblemsListView 删除对话框无警告
    Tool: Playwright
    Steps:
      1. 访问 http://localhost:9003/problems
      2. 点击某题目的删除按钮（触发删除对话框）
      3. 打开 DevTools Console
    Expected Result: 无 `[intlify] Not found 'problems.dialog.delete.*'` 警告
    Evidence: .sisyphus/evidence/task-9-delete-dialog.png
  ```

  **Commit**: YES（如需要修复）/ NO（如键已正确）
  - Message: `fix(i18n): fix problems.dialog.delete key structure`
  - Files: `management/src/i18n/locales/zh-CN/modules/problems.ts`

---

## Final Verification Wave

> 4 个审查代理并行运行。全部通过后才能向用户呈现结果并获取明确 "okay"。

- [x] F1. **Plan Compliance Audit** — `oracle`
  通读计划。对每个 "Must Have"：验证实现存在（读文件、curl 接口、运行命令）。对每个 "Must NOT Have"：搜索代码库中的禁止模式 — 如发现则返回 file:line 拒绝。检查 `.sisyphus/evidence/` 中证据文件存在。对比交付物与计划。
  **输出**: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **Code Quality Review** — `unspecified-high`
  运行 `cd management && pnpm type-check && pnpm build`。审查所有修改文件：`as any`/`@ts-ignore`、空 catch、`console.log` 生产代码、注释掉的代码、未使用导入。检查 AI slop：过度注释、过度抽象、通用命名。
  **输出**: `TypeCheck [PASS/FAIL] | Build [PASS/FAIL] | Files [N clean/N issues] | VERDICT`

- [x] F3. **Real Manual QA** — `unspecified-high`（+ `playwright` 技能 + `browse` 技能）
  从干净状态开始。执行每个任务的 QA Scenario — 严格按步骤执行，捕获证据。测试跨任务集成（功能协同工作，而非孤立）。测试边界情况：空状态、无效输入、快速操作。保存到 `.sisyphus/evidence/final-qa/`。
  **输出**: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [x] F4. **Scope Fidelity Check** — `deep`
  对每个任务：读取 "What to do"，读取实际 diff（`git diff`）。验证 1:1 — 规格中的一切都已构建（无遗漏），规格外的一切都没构建（无蔓延）。检查 "Must NOT do" 合规性。检测跨任务污染：任务 N 触碰了任务 M 的文件。标记未计入的变更。
  **输出**: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

| Wave | Commit | Message | Files | Pre-commit |
|------|--------|---------|-------|-----------|
| 1 | Task 1 | `fix(router): migrate beforeEach from next() callback to return value` | `management/src/router/index.ts` | `pnpm type-check` |
| 1 | Task 2 | `fix(mapper): exclude NULL status/language from submission statistics` | `backend-spring/.../SubmissionMapper.java` | — |
| 1 | Task 3 | `fix(i18n): add common.actions.toggleLanguage and restructure actions` | `zh-CN/en-US common.ts` | `pnpm build` |
| 1 | Task 4 | `fix(i18n): add users.actions.bulkBanUser translation` | `zh-CN/en-US users.ts` | `pnpm build` |
| 1 | Task 5 | `fix(i18n): add contests.type.biweekly and weekly` | `zh-CN/en-US contests.ts` | `pnpm build` |
| 1 | Task 6 | `fix(i18n): add comments.status.unknown translation` | `zh-CN/en-US comments.ts` | `pnpm build` |
| 1 | Task 7 | `fix(i18n): add moderation.flagDescription translation` | `zh-CN/en-US moderation.ts` | `pnpm build` |
| 1 | Task 8 | `fix(i18n): add table.selectAll translation` | `zh-CN/en-US table.ts` | `pnpm build` |
| 1 | Task 9 | `fix(i18n): fix problems.dialog.delete key structure` | `zh-CN problems.ts` | `pnpm build` |

---

## Success Criteria

### Verification Commands

```bash
# 前端类型检查 + 构建（无 intlify 警告）
cd management && pnpm type-check && pnpm build

# 后端统计接口测试
curl -s http://localhost:9001/admin/submissions/statistics | python3 -m json.tool

# 前端构建产物检查 intlify 警告（如 build 无错误但仍有警告）
cd management && pnpm build 2>&1 | grep -i "intlify\|Not found" || echo "No intlify warnings"
```

### Final Checklist
- [ ] 所有 "Must Have" 已修复（Router 弃用、11个国际化键、后端 500）
- [ ] 所有 "Must NOT Have" 未触碰（console/、其他后端接口、路由逻辑重构）
- [ ] `management` 前端 `pnpm type-check` 通过
- [ ] `management` 前端 `pnpm build` 通过，无 intlify 警告
- [ ] `/admin/submissions/statistics` 返回 200 + 有效 JSON
- [ ] 所有 4 个 Final Verification 任务通过

