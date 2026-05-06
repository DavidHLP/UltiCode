# 修复计划：管理后台问题列表编辑数据未保存

## TL;DR

> **核心问题**: 前端表单提交未阻止默认行为，导致页面刷新，数据未保存
> 
> **修复内容**: 
> - 前端：添加 `@submit.prevent`、修复请求去重、改进错误处理
> - 后端：修复事务注解、修复权限检查逻辑
> 
> **涉及文件**: 6个文件（前端3个 + 后端3个）
> **预计工作量**: 30分钟
> **风险等级**: 低（均为明显缺陷，修改范围小）

---

## 上下文

### 原始问题
管理后台问题列表编辑页面，点击"保存更改"后数据未实际保存。

### 用户反馈
- 前端控制台没有任何日志
- 点击保存后出现短暂页面刷新

### 诊断结论
**根因**: `GeneralInfo.vue` 第101行 `@submit` 缺少 `.prevent` 修饰符，HTML 表单默认提交行为触发页面刷新。

**连锁反应**:
1. 页面刷新 → JavaScript 状态丢失 → 请求被中断
2. 控制台被清空 → "没有任何日志"
3. Toast 消息来不及显示 → 用户感知不到错误

---

## 问题清单

### 问题1: 表单提交未阻止默认行为（根因）
**文件**: `management/src/views/problem-lists/components/GeneralInfo.vue:101`
**现状**: `<form @submit="form.handleSubmit(onSubmit)">`
**问题**: 缺少 `.prevent` 修饰符，HTML 默认提交行为导致页面刷新
**修复**: 改为 `<form @submit.prevent="form.handleSubmit(onSubmit)">`

### 问题2: 前端请求去重导致 PATCH 请求被 abort
**文件**: `management/src/utils/request.ts`
**现状**: 相同 method+url+params+data 的请求会 abort 前一个
**问题**: 用户快速点击保存时，第二次请求取消第一次，数据未保存
**修复**: 对 PATCH/PUT/DELETE 等修改类请求禁用去重，或添加防抖机制

### 问题3: 前端错误处理不当
**文件**: `management/src/views/problem-lists/components/GeneralInfo.vue:72-88`
**现状**: 
- catch 块没有参数 `(err)`，无法区分错误类型
- 编辑失败也显示 "创建失败" 提示
**修复**: 
- 添加错误参数，区分 AbortError、网络错误、业务错误
- 根据 mode 显示对应的错误提示（创建/更新）

### 问题4: 后端 Admin Service 缺少 @Transactional
**文件**: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemListServiceImpl.java`
**现状**: `updateProblemList` 方法没有 `@Transactional` 注解
**问题**: 虽然调用的底层方法有事务，但在 Spring 代理场景下可能导致更新未提交
**修复**: 在 `updateProblemList` 方法上添加 `@Transactional(rollbackFor = Exception.class)`

### 问题5: 后端权限检查被绕过（设计缺陷）
**文件**: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemListServiceImpl.java`
**现状**: `return problemListService.updateList(id, list.getAuthorId(), dto);`
**问题**: 传递 `list.getAuthorId()` 导致 `ProblemListServiceImpl` 中的 `authorId.equals(userId)` 总是 true，权限检查形同虚设
**修复**: 传递当前登录用户的 ID 而不是 list 的 authorId

### 问题6: 编辑后未刷新完整数据
**文件**: `management/src/stores/admin/problem-lists.ts`
**现状**: `updateList` 成功后只合并返回的 `ProblemListSummary` 到 `currentList`
**问题**: 完整详情（包含 `problems` 数组）未被刷新
**修复**: 更新成功后重新调用 `fetchListDetail` 获取完整数据

---

## 修复策略

### Wave 1: 前端紧急修复（根因 + 用户体验）
- **任务1**: 修复表单提交阻止默认行为
- **任务2**: 修复请求去重逻辑
- **任务3**: 改进错误处理

### Wave 2: 后端修复（数据一致性 + 安全性）
- **任务4**: 添加 @Transactional
- **任务5**: 修复权限检查逻辑

### Wave 3: 前端体验优化
- **任务6**: 编辑后刷新完整数据

---

## TODOs

- [x] 1. 修复表单提交阻止默认行为

  **What to do**:
  - 修改 `GeneralInfo.vue` 第101行
  - 将 `<form @submit="form.handleSubmit(onSubmit)">` 改为 `<form @submit.prevent="form.handleSubmit(onSubmit)">`

  **Must NOT do**:
  - 不要修改其他表单逻辑
  - 不要修改验证逻辑

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocked By**: None
  - **Blocks**: None

  **References**:
  - `management/src/views/problem-lists/components/GeneralInfo.vue:101`

  **Acceptance Criteria**:
  - [ ] 表单提交后页面不刷新
  - [ ] 点击保存按钮后 URL 不变
  - [ ] 页面状态保持（表单值不丢失）

  **QA Scenarios**:
  ```
  Scenario: 正常提交表单
    Tool: Playwright
    Steps:
      1. 导航到 /problem-lists/:id/edit
      2. 修改名称字段
      3. 点击"保存更改"按钮
      4. 等待 2 秒
    Expected Result: 
      - 页面 URL 不变（无刷新）
      - 表单值保持不变
      - 显示成功 toast
    Evidence: .sisyphus/evidence/task-1-submit-no-reload.png
  ```

  **Commit**: YES
  - Message: `fix(management): prevent form default submit to avoid page reload`
  - Files: `management/src/views/problem-lists/components/GeneralInfo.vue`

- [x] 2. 修复请求去重逻辑

  **What to do**:
  - 修改 `management/src/utils/request.ts` 中的请求去重逻辑
  - 对 PATCH/PUT/DELETE 请求禁用去重（或只去重 GET 请求）
  - 或者：添加请求防抖，300ms 内相同请求只发一次

  **Must NOT do**:
  - 不要完全移除去重逻辑（GET 请求去重是有用的）
  - 不要影响其他模块的请求行为

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocked By**: None
  - **Blocks**: None

  **References**:
  - `management/src/utils/request.ts`

  **Acceptance Criteria**:
  - [ ] 快速点击保存按钮不会 abort 前一个 PATCH 请求
  - [ ] GET 请求仍然保持去重功能

  **QA Scenarios**:
  ```
  Scenario: 快速双击保存按钮
    Tool: Playwright
    Steps:
      1. 导航到编辑页面
      2. 修改名称
      3. 快速双击"保存更改"按钮（间隔 < 300ms）
      4. 检查 Network 面板
    Expected Result: 
      - 只发送一个 PATCH 请求（防抖生效）
      - 或发送两个 PATCH 请求但都不会被 abort
    Evidence: .sisyphus/evidence/task-2-no-abort.png
  ```

  **Commit**: YES
  - Message: `fix(management): disable deduplication for modifying requests`
  - Files: `management/src/utils/request.ts`

- [x] 3. 改进错误处理

  **What to do**:
  - 修改 `GeneralInfo.vue` 的 `onSubmit` 函数
  - catch 块添加错误参数 `(err)`
  - 根据错误类型显示不同提示：
    - AbortError: "请求已取消，请重试"
    - 网络错误: "网络连接失败"
    - 业务错误: 显示后端返回的错误信息
  - 根据 mode（create/edit）显示对应的成功/失败提示

  **Must NOT do**:
  - 不要修改表单验证逻辑
  - 不要修改 API 调用逻辑

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocked By**: None
  - **Blocks**: None

  **References**:
  - `management/src/views/problem-lists/components/GeneralInfo.vue:72-88`

  **Acceptance Criteria**:
  - [ ] 编辑失败时显示 "更新失败" 而非 "创建失败"
  - [ ] 请求被取消时显示友好提示
  - [ ] 网络错误时显示 "网络连接失败"

  **QA Scenarios**:
  ```
  Scenario: 编辑时网络断开
    Tool: Playwright
    Steps:
      1. 导航到编辑页面
      2. 断开网络（或拦截请求返回 500）
      3. 点击保存
    Expected Result: 
      - 显示 "更新失败" 或具体错误信息
      - 不显示 "创建失败"
    Evidence: .sisyphus/evidence/task-3-error-handling.png
  ```

  **Commit**: YES
  - Message: `fix(management): improve error handling for problem list form`
  - Files: `management/src/views/problem-lists/components/GeneralInfo.vue`

- [x] 4. 后端添加 @Transactional

  **What to do**:
  - 修改 `AdminProblemListServiceImpl.java`
  - 在 `updateProblemList` 方法上添加 `@Transactional(rollbackFor = Exception.class)`

  **Must NOT do**:
  - 不要修改业务逻辑
  - 不要修改其他方法

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocked By**: None
  - **Blocks**: None

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemListServiceImpl.java`

  **Acceptance Criteria**:
  - [ ] 方法上有 `@Transactional` 注解
  - [ ] 编译通过

  **QA Scenarios**:
  ```
  Scenario: 验证注解存在
    Tool: Bash
    Steps:
      1. grep -n "@Transactional" AdminProblemListServiceImpl.java
    Expected Result: 
      - 找到 @Transactional 注解
    Evidence: .sisyphus/evidence/task-4-transactional.txt
  ```

  **Commit**: YES
  - Message: `fix(backend): add @Transactional to admin problem list update`
  - Files: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemListServiceImpl.java`

- [x] 5. 修复后端权限检查逻辑

  **What to do**:
  - 修改 `AdminProblemListServiceImpl.java`
  - 将 `problemListService.updateList(id, list.getAuthorId(), dto)` 改为传递当前登录用户的 ID
  - 通过 `SecurityContextHolder` 或方法参数获取当前用户 ID

  **Must NOT do**:
  - 不要修改 `ProblemListServiceImpl` 的权限检查逻辑（它被非 admin 流程使用）
  - 不要降低安全性

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocked By**: None
  - **Blocks**: None

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemListServiceImpl.java`
  - `backend-spring/src/main/java/com/ulticode/modules/problemlist/service/impl/ProblemListServiceImpl.java`

  **Acceptance Criteria**:
  - [ ] Admin 更新时传递当前登录用户 ID
  - [ ] 权限检查逻辑正确执行

  **QA Scenarios**:
  ```
  Scenario: Admin 更新任意列表
    Tool: Bash (curl)
    Steps:
      1. 登录 admin 账号
      2. PATCH /admin/problem-lists/{id} 修改其他用户的列表
      3. 验证是否成功（admin 应该能修改）
    Expected Result: 
      - 返回 200 成功
      - 数据确实被更新
    Evidence: .sisyphus/evidence/task-5-admin-update.txt
  ```

  **Commit**: YES
  - Message: `fix(backend): pass current user id instead of author id in admin update`
  - Files: `backend-spring/src/main/java/com/ulticode/modules/admin/service/impl/AdminProblemListServiceImpl.java`

- [x] 6. 编辑后刷新完整数据

  **What to do**:
  - 修改 `management/src/stores/admin/problem-lists.ts`
  - 在 `updateList` 成功后调用 `fetchListDetail(id)` 重新获取完整数据
  - 或者：修改 API 返回完整详情而非摘要

  **Must NOT do**:
  - 不要破坏现有的状态管理逻辑
  - 不要引入无限循环

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: 无

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocked By**: None（但建议等 Wave 1 完成后测试）
  - **Blocks**: None

  **References**:
  - `management/src/stores/admin/problem-lists.ts`

  **Acceptance Criteria**:
  - [ ] 编辑成功后重新获取完整详情
  - [ ] problems 数组等详细信息被更新

  **QA Scenarios**:
  ```
  Scenario: 编辑后数据完整刷新
    Tool: Playwright
    Steps:
      1. 导航到编辑页面
      2. 修改名称并保存
      3. 检查页面数据是否完整（包含 problems 列表）
    Expected Result: 
      - 名称已更新
      - problems 列表仍然显示
    Evidence: .sisyphus/evidence/task-6-full-refresh.png
  ```

  **Commit**: YES
  - Message: `fix(management): refresh full detail after problem list update`
  - Files: `management/src/stores/admin/problem-lists.ts`

---

## Final Verification Wave

- [x] F1. **功能验证** — `unspecified-high`
  1. 启动前后端服务
  2. 登录管理后台
  3. 导航到问题列表编辑页面
  4. 修改名称并保存
  5. 验证：
     - [x] 页面不刷新
     - [x] 显示成功提示
     - [x] 数据确实被保存（刷新页面后数据保持）
     - [x] 快速双击保存不会导致错误

- [x] F2. **代码审查** — `unspecified-high`
  - [x] 检查所有修改文件的语法正确性
  - [x] 检查 TypeScript/Java 编译是否通过
  - [x] 检查没有引入新的 lint 错误

---

## Commit Strategy

- **1-3**: 前端修复（可以合并为一个 commit 或分开）
- **4-5**: 后端修复（建议分开 commit）
- **6**: 前端体验优化

---

## Success Criteria

### 验证命令
```bash
# 前端编译
cd management && pnpm build

# 后端编译
cd backend-spring && ./mvnw compile -DskipTests
```

### 最终检查清单
- [ ] 点击保存后页面不刷新
- [ ] 数据成功保存到数据库
- [ ] 编辑失败时显示正确的错误提示
- [ ] Admin 可以更新任意列表（权限正确）
- [ ] 编辑后完整数据被刷新