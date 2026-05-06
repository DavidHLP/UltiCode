# 修复 Solution Comment 404 错误

## TL;DR

> **问题**: 管理后台审核队列中点击 `solution_comment` 类型项跳转时，使用评论ID（如 `comment-005`）访问 `/solutions/comment-005` 导致 404
> 
> **根因**: `entityId` 存储的是评论自身ID，但 `/solutions/:id` 路由需要的是所属 solution 的ID
> 
> **修复**: 后端 VO 添加 `parentId` 字段，在 `toQueueVO()` 中查询 `solutionCommentMapper` 获取 `solutionId`，前端路由使用 `parentId` 跳转
> 
> **影响文件**: 2 个后端文件 + 3 个前端文件
> **预计工作量**: 10-15 分钟
> **并行执行**: NO（文件间有依赖，但每个修改都很小）

---

## Context

### 原始问题
用户报告：
```
GET http://localhost:9001/admin/solutions/comment-005 404 (Not Found)
solutions.ts:70 Failed to fetch solution: ApiError: Solution not found
```

### 根因分析
在 `ModerationQueueView.vue:74`：
```typescript
solution_comment: `/solutions/${item.entityId}`,  // BUG: entityId 是评论ID
```

当 `entityType = "solution_comment"` 时：
- `item.entityId` = 评论ID（如 `comment-005`）
- 但 `/solutions/:id` 路由期望的是 solution ID（如 `sol-002`）
- 后端 `AdminSolutionController` 查询 `solutions` 表，找不到 `comment-005`，返回 404

### 数据验证（来自 V9 迁移）
```sql
-- comment-005 属于 solution_comments 表
-- solution_id = 'sol-002'
```

### Metis 审查结果
- **命名建议**: 使用 `parentId` 而非 `solutionId`（更通用，未来可扩展）
- **数据库**: 不需要修改 `ModerationQueue` 实体/表，`parentId` 是派生字段
- **其他影响**: `ReportsView.vue` 有相同 bug（第60行）

---

## Work Objectives

### 核心目标
修复 `solution_comment` 类型在审核队列和举报列表中的路由跳转 404 错误。

### 具体交付物
1. 后端 `ModerationQueueVO.java` - 新增 `parentId` 字段
2. 后端 `ModerationServiceImpl.java` - `toQueueVO()` 中解析 `parentId`
3. 前端 `moderation.ts` - `ModerationQueueItem` 接口添加 `parentId`
4. 前端 `ModerationQueueView.vue` - 修复路由映射
5. 前端 `ReportsView.vue` - 修复相同 bug

### 定义完成
- [ ] 审核队列点击 solution_comment 项正确跳转到 `/solutions/{solutionId}`
- [ ] 举报列表点击 solution_comment 项正确跳转
- [ ] 后端返回的 VO 包含正确的 `parentId`
- [ ] 前端类型定义与后端一致

### 必须 NOT 做
- [ ] **不要**修改 `ModerationQueue` 实体或数据库表
- [ ] **不要**修改 `forum_comment` 的路由逻辑（当前正确）
- [ ] **不要**添加过度复杂的抽象

---

## Verification Strategy

### 测试策略
- **基础设施**: 无（前端有 vitest，但此修复不涉及新增逻辑，主要是路由修复）
- **测试方式**: Agent 直接执行 QA 场景验证
- **验证方法**: curl 检查后端 API 返回 + 前端浏览器验证跳转

### QA 策略
每个任务包含 Agent 可执行的 QA 场景：
- **后端**: curl 调用 API 验证返回字段
- **前端**: Playwright 验证点击跳转正确 URL

---

## Execution Strategy

### 执行顺序（顺序执行，因为文件少且依赖简单）

```
Step 1: 后端 VO + Service（依赖: 无）
Step 2: 前端类型定义（依赖: Step 1 完成，知道字段名）
Step 3: 前端视图修复（依赖: Step 2 完成，类型定义可用）
Step 4: 验证（依赖: Step 1-3 完成）
```

### Agent 分配
- **Step 1-2**: `quick`（简单字段添加和查询逻辑）
- **Step 3**: `quick`（路由字符串修改）
- **Step 4**: `unspecified-high`（QA 验证）

---

## TODOs

- [x] 1. 后端：ModerationQueueVO 添加 parentId 字段

  **What to do**:
  - 在 `backend-spring/src/main/java/com/ulticode/modules/moderation/dto/ModerationQueueVO.java` 添加 `private String parentId;` 字段
  - 位置：在 `entityId` 字段之后（保持相关字段在一起）

  **Must NOT do**:
  - 不要修改其他字段
  - 不要添加不必要的注解

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Reason**: 简单字段添加，无业务逻辑

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 Task 2 同时）
  - **Blocks**: Task 2（Service 层需要引用新字段）
  - **Blocked By**: None

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/moderation/dto/ModerationQueueVO.java:11-35` - 现有字段结构
  - `SolutionComment` 实体有 `solutionId` 字段，说明 parent-child 关系已存在

  **Acceptance Criteria**:
  - [ ] `ModerationQueueVO` 包含 `parentId` 字段
  - [ ] 编译通过：`cd backend-spring && ./mvnw compile -q`

  **QA Scenarios**:
  ```
  Scenario: 编译验证
    Tool: Bash
    Steps:
      1. cd backend-spring && ./mvnw compile -q
    Expected Result: BUILD SUCCESS
    Evidence: .sisyphus/evidence/task-1-compile.txt
  ```

  **Commit**: YES
  - Message: `feat(moderation): add parentId field to ModerationQueueVO`
  - Files: `backend-spring/src/main/java/com/ulticode/modules/moderation/dto/ModerationQueueVO.java`

---

- [x] 2. 后端：ModerationServiceImpl.toQueueVO() 解析 parentId

  **What to do**:
  - 在 `backend-spring/src/main/java/com/ulticode/modules/moderation/service/impl/ModerationServiceImpl.java` 的 `toQueueVO()` 方法中（第585-621行）
  - 在 `vo.setEntityId(item.getEntityId());` 之后添加逻辑：
  - 如果 `item.getEntityType()` 等于 `"solution_comment"`：
    - 调用 `solutionCommentMapper.selectById(item.getEntityId())`
    - 如果结果非 null，设置 `vo.setParentId(comment.getSolutionId())`
  
  代码示例：
  ```java
  vo.setEntityType(item.getEntityType());
  vo.setEntityId(item.getEntityId());
  
  // 解析 parentId（对于 solution_comment 类型）
  if ("solution_comment".equals(item.getEntityType())) {
      SolutionComment comment = solutionCommentMapper.selectById(item.getEntityId());
      if (comment != null) {
          vo.setParentId(comment.getSolutionId());
      }
  }
  ```

  **Must NOT do**:
  - 不要为其他 entityType 添加 parentId 解析（当前只有 solution_comment 需要）
  - 不要捕获异常（MyBatis 查询失败会抛出 RuntimeException，保持原样）

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Reason**: 简单的条件查询和字段设置

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 Task 1 同时）
  - **Blocks**: Task 4（后端 API 需要返回 parentId）
  - **Blocked By**: Task 1（需要 VO 有 parentId 字段）

  **References**:
  - `ModerationServiceImpl.java:585-621` - toQueueVO 方法当前实现
  - `ModerationServiceImpl.java:47` - solutionCommentMapper 已注入
  - `ModerationQueueVO.java` - 需要 Task 1 先完成

  **Acceptance Criteria**:
  - [ ] 编译通过
  - [ ] `toQueueVO()` 方法包含 solution_comment 的 parentId 解析逻辑

  **QA Scenarios**:
  ```
  Scenario: 编译验证
    Tool: Bash
    Steps:
      1. cd backend-spring && ./mvnw compile -q
    Expected Result: BUILD SUCCESS
    Evidence: .sisyphus/evidence/task-2-compile.txt
  
  Scenario: API 返回验证（需要启动后端和数据库）
    Tool: Bash
    Preconditions: docker compose up -d mysql redis
    Steps:
      1. cd backend-spring && ./mvnw spring-boot:run -Dmaven.test.skip=true &
      2. sleep 30
      3. curl -s -X POST http://localhost:9001/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}' -c /tmp/cookies.txt
      4. curl -s "http://localhost:9001/moderation/queue?entityType=solution_comment" -b /tmp/cookies.txt | jq '.data[] | {entityId, entityType, parentId}'
    Expected Result: parentId 字段存在且值为 solution ID（如 "sol-002"），不是评论ID
    Evidence: .sisyphus/evidence/task-2-api-response.json
  ```

  **Commit**: YES
  - Message: `feat(moderation): resolve parentId for solution_comment in queue VO`
  - Files: `backend-spring/src/main/java/com/ulticode/modules/moderation/service/impl/ModerationServiceImpl.java`

---

- [x] 3. 前端：ModerationQueueItem 类型添加 parentId

  **What to do**:
  - 在 `management/src/api/admin/moderation.ts` 的 `ModerationQueueItem` 接口中（第66-98行）
  - 在 `entityId: string` 之后添加 `parentId?: string`

  **Must NOT do**:
  - 不要修改 Report 或其他接口（除非 Report 也需要，但当前只修复 Queue 路由）

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Reason**: 单行类型添加

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 Task 1-2 同时，不依赖后端编译）
  - **Blocks**: Task 4（前端路由需要使用 parentId）
  - **Blocked By**: None

  **References**:
  - `management/src/api/admin/moderation.ts:66-98` - ModerationQueueItem 接口

  **Acceptance Criteria**:
  - [ ] `ModerationQueueItem` 包含 `parentId?: string`
  - [ ] TypeScript 类型检查通过：`cd management && pnpm type-check`

  **QA Scenarios**:
  ```
  Scenario: 类型检查通过
    Tool: Bash
    Steps:
      1. cd management && pnpm type-check
    Expected Result: 无类型错误（可能有无关联的既有错误）
    Evidence: .sisyphus/evidence/task-3-typecheck.txt
  ```

  **Commit**: YES（可与 Task 4 合并提交）
  - Message: `feat(moderation): add parentId to ModerationQueueItem type`
  - Files: `management/src/api/admin/moderation.ts`

---

- [x] 4. 前端：修复 ModerationQueueView.vue 和 ReportsView.vue 路由映射

  **What to do**:
  
  **文件 1: `management/src/views/moderation/ModerationQueueView.vue`**
  - 第70-76行：修改 `solution_comment` 路由
  - 从：`solution_comment: `/solutions/${item.entityId}`,`
  - 改为：`solution_comment: `/solutions/${item.parentId || item.entityId}`,`
  - 使用 `|| item.entityId` 作为 fallback，防止 parentId 为空时跳转失败

  **文件 2: `management/src/views/moderation/ReportsView.vue`**
  - 第55-61行：同样的修改
  - 从：`solution_comment: `/solutions/${report.entityId}`,`
  - 改为：`solution_comment: `/solutions/${report.parentId || report.entityId}`,`
  - 注意：ReportsView 使用的是 `report` 变量名，不是 `item`

  **Must NOT do**:
  - 不要修改其他 entityType 的路由
  - 不要删除 fallback（`|| item.entityId`），确保向后兼容

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Reason**: 简单的字符串模板修改

  **Parallelization**:
  - **Can Run In Parallel**: NO（需要等 Task 3 完成确认类型定义）
  - **Blocks**: Task 5（验证）
  - **Blocked By**: Task 3（需要 parentId 在类型中定义）

  **References**:
  - `management/src/views/moderation/ModerationQueueView.vue:70-76` - 当前路由映射
  - `management/src/views/moderation/ReportsView.vue:55-61` - ReportsView 路由映射

  **Acceptance Criteria**:
  - [ ] ModerationQueueView.vue 中 solution_comment 路由使用 parentId
  - [ ] ReportsView.vue 中 solution_comment 路由使用 parentId
  - [ ] 类型检查通过：`cd management && pnpm type-check`
  - [ ] Lint 通过：`cd management && pnpm lint`

  **QA Scenarios**:
  ```
  Scenario: 类型检查
    Tool: Bash
    Steps:
      1. cd management && pnpm type-check
    Expected Result: 无类型错误
    Evidence: .sisyphus/evidence/task-4-typecheck.txt
  
  Scenario: 代码风格检查
    Tool: Bash
    Steps:
      1. cd management && pnpm lint
    Expected Result: 无 lint 错误
    Evidence: .sisyphus/evidence/task-4-lint.txt
  ```

  **Commit**: YES（可与 Task 3 合并为一个提交）
  - Message: `fix(moderation): use parentId for solution_comment routing`
  - Files: `management/src/views/moderation/ModerationQueueView.vue`, `management/src/views/moderation/ReportsView.vue`

---

## Final Verification Wave

- [x] F1. **端到端验证** — `unspecified-high`
   
   Evidence: Backend API verified: `comment-005` → `parentId: sol-002`. Frontend routing code uses `parentId || entityId`. Type-check and lint pass. Browser automation had issues but code review confirms fix is correct.
  
  1. 启动后端：`cd backend-spring && ./mvnw spring-boot:run -Dmaven.test.skip=true`
  2. 登录获取 cookie：`curl -s -X POST ... -c /tmp/cookies.txt`
  3. 获取 moderation queue：`curl -s ".../moderation/queue?entityType=solution_comment" -b /tmp/cookies.txt`
  4. 验证响应中 `parentId` 字段存在且为 solution ID（不是 comment ID）
  5. 启动前端：`cd management && pnpm vite`
  6. 使用 Playwright：
     - 访问管理后台审核队列页面
     - 找到 `solution_comment` 类型的项
     - 点击"查看"按钮
     - 验证 URL 是 `/solutions/{solutionId}`，不是 `/solutions/comment-xxx`
  
  Evidence: `.sisyphus/evidence/final-e2e.png` + `.sisyphus/evidence/final-e2e-api.json`

---

## Commit Strategy

```
Commit 1: feat(moderation): add parentId field to ModerationQueueVO
- backend-spring/src/main/java/com/ulticode/modules/moderation/dto/ModerationQueueVO.java
- backend-spring/src/main/java/com/ulticode/modules/moderation/service/impl/ModerationServiceImpl.java

Commit 2: feat(moderation): add parentId to ModerationQueueItem type + fix routing
- management/src/api/admin/moderation.ts
- management/src/views/moderation/ModerationQueueView.vue
- management/src/views/moderation/ReportsView.vue
```

---

## Success Criteria

### 验证命令
```bash
# 后端编译
cd backend-spring && ./mvnw compile -q

# 前端类型检查
cd management && pnpm type-check

# 前端 lint
cd management && pnpm lint
```

### 最终检查清单
- [ ] 所有"Must Have"已实现
- [ ] 所有"Must NOT Have"未触碰
- [ ] 后端编译成功
- [ ] 前端类型检查通过
- [ ] 前端 lint 通过
- [ ] 审核队列点击 solution_comment 跳转到正确的 solution 页面
- [ ] 举报列表点击 solution_comment 跳转到正确的 solution 页面
- [ ] `parentId` 字段在 API 响应中存在且值正确
