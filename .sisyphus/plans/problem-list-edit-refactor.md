# 题单管理编辑重构计画

## TL;DR

> **核心目标**: 将题单编辑页面从"一次性提交所有字段"重构为"按功能模块独立保存"，引入自动保存、乐观锁和细粒度权限控制。
>
> **交付物**:
> - 后端: 新增 version 字段（乐观锁）、部分更新优化、MANAGE_PROBLEMS 角色支持
> - 前端: 4 个独立模块（基本信息/可见性/横幅/题目管理）、自动保存（debounce+blur）、独立权限控制
> - 测试: TDD 方式，每个模块有对应的单元测试
>
> **预估工作量**: Medium-Large（约 15-20 个任务）
> **并行执行**: YES - 4 个 Wave
> **关键路径**: DB Migration → Backend Service → Frontend Components → Integration QA

---

## Context

### 原始需求
用户描述当前题单编辑设计存在"颗粒度"问题，需要前后端对齐。

### 访谈摘要
**关键讨论**:
- **拆分粒度**: 按功能模块拆分（基本信息、可见性、横幅设置、题目管理）
- **自动保存**: 需要，debounce(1s) + blur 结合
- **权限**: 题目管理独立权限，新增 MANAGE_PROBLEMS 角色
- **范围**: 编辑+创建页面
- **并发**: 乐观锁（version 字段）
- **测试**: TDD 方式

**调研发现**:
- 前端 `GeneralInfo.vue` 一次性提交 7 个字段，没有利用后端 PATCH 的部分更新能力
- 后端 `PATCH /problem-lists/{id}` 支持部分更新（只更新非 null 字段）
- 数据库 `problem_lists` 表没有 `version` 字段
- 权限系统：后端 role-based，前端 permission-string-based
- 项目中没有真正的自动保存到后端

### Metis 审查
**识别的差距**（已处理）:
- 版本冲突 UX: 采用"显示错误提示，让用户手动刷新"的简单方案
- 创建页面: 需要先显式创建实体，创建成功后才能自动保存
- 请求合并: 采用防抖，如果请求在进行中则取消前一个
- 权限矩阵: 已定义

---

## Work Objectives

### 核心目标
将题单编辑从粗粒度整体提交重构为细粒度模块独立保存，提升用户体验并防止并发冲突。

### 具体交付物
1. **数据库**: `problem_lists` 表新增 `version` 字段（INT, DEFAULT 1）
2. **后端 DTO**: 新增模块专用 DTO（UpdateBasicInfoDTO, UpdateVisibilityDTO, UpdateBannerDTO）
3. **后端服务**: 支持乐观锁的部分更新服务方法
4. **后端权限**: 新增 MANAGE_PROBLEMS 角色，Controller 方法支持角色检查
5. **前端组件**: 4 个独立模块组件，每个有自己的表单和保存逻辑
6. **前端自动保存**: useDebounceFn(1s) + blur 事件，带保存状态指示
7. **前端权限**: 基于角色的组件级权限控制

### 完成标准
- [ ] 每个模块可以独立编辑和保存
- [ ] 自动保存触发正确（debounce + blur）
- [ ] 乐观锁防止并发冲突
- [ ] MANAGE_PROBLEMS 角色可以独立管理题目
- [ ] 所有测试通过

### 必须有
- 按功能模块拆分保存
- 自动保存功能
- 乐观锁
- 题目管理独立权限

### 必须不有（Guardrails）
- 不新增数据库表（只给 problem_lists 加 version 字段）
- 不改现有 API 端点（复用 PATCH /problem-lists/{id}）
- 不改认证流程（登录/Token/CSRF）
- 不做撤销/重做
- 不做拖拽排序（题目管理保持现有方式）
- 不做草稿状态（保存即持久化）
- 不做变更历史/审计日志
- 不做移动端适配

---

## Verification Strategy

> **零人工干预** - 所有验证由 Agent 执行。不允许任何需要"用户手动测试"的验收标准。

### 测试决策
- **基础设施存在**: YES（后端: JUnit, 前端: Vitest）
- **自动化测试**: TDD（RED → GREEN → REFACTOR）
- **后端框架**: JUnit 5 + Mockito
- **前端框架**: Vitest
- **Agent-Executed QA**: 所有任务必须包含可执行的 QA Scenarios

### QA 政策
每个任务必须包含 Agent-Executed QA Scenarios：
- **前端/UI**: Playwright - 打开浏览器、导航、交互、断言 DOM、截图
- **API/后端**: Bash (curl) - 发送请求、断言状态码和响应字段
- **数据库**: Bash (mysql cli) - 验证表结构、数据

---

## Execution Strategy

### 并行执行 Wave

```
Wave 1 (基础层 - 可立即开始):
├── Task 1: 数据库 Migration（新增 version 字段）
├── Task 2: 后端 DTO 拆分（BasicInfo/Visibility/Banner）
├── Task 3: 后端 Entity 添加 @Version
├── Task 4: 前端权限常量（新增 MANAGE_PROBLEMS）
└── Task 5: 前端 API Client 拆分（按模块）

Wave 2 (后端核心 - 依赖 Wave 1):
├── Task 6: 后端 Service 拆分（按模块的部分更新方法）
├── Task 7: 后端 Controller 添加角色检查
├── Task 8: 后端乐观锁异常处理
└── Task 9: 后端单元测试（Service 层 TDD）

Wave 3 (前端核心 - 依赖 Wave 1):
├── Task 10: 前端 BasicInfo 模块组件
├── Task 11: 前端 Visibility 模块组件
├── Task 12: 前端 Banner 模块组件
├── Task 13: 前端自动保存 Composable
├── Task 14: 前端权限控制 Composable
└── Task 15: 前端单元测试（组件 TDD）

Wave 4 (集成 - 依赖 Wave 2+3):
├── Task 16: 重构 ProblemListDetailView.vue（集成 4 个模块）
├── Task 17: 创建页面适配（先 POST 再启用自动保存）
├── Task 18: 集成测试（端到端 QA）
└── Task 19: 清理旧代码（删除旧 GeneralInfo.vue）

Wave FINAL (审查):
├── Task F1: 计画合规性审查（oracle）
├── Task F2: 代码质量审查
├── Task F3: 实际 QA（所有场景执行）
└── Task F4: 范围保真度检查
```

---

## TODOs

- [x] 1. **数据库 Migration - 新增 version 字段**

  **What to do**:
  - 在 `db-manager/migrations/` 创建新 migration 文件
  - 给 `problem_lists` 表添加 `version` 字段（INT, NOT NULL, DEFAULT 1）
  - 添加索引 `idx_version` 优化乐观锁查询

  **Must NOT do**:
  - 不要修改已有 migration 文件（checksum 会变化）
  - 不要添加其他字段或表

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
  - **Reason**: 纯 SQL 变更，简单直接

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 3, Task 6, Task 8
  - **Blocked By**: None

  **References**:
  - `db-manager/migrations/V2__problem_schema.sql:106-120` - problem_lists 表结构
  - `db-manager/README.md` - migration 规范

  **Acceptance Criteria**:
  - [ ] Migration 文件创建成功
  - [ ] `db-manager/.venv/bin/python -m db_manager.cli validate` → PASS
  - [ ] 数据库中 `problem_lists` 表有 `version` 字段

  **QA Scenarios**:
  ```
  Scenario: version 字段存在
    Tool: Bash (mysql cli)
    Steps:
      1. docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode -e "DESCRIBE problem_lists"
    Expected Result: 输出包含 version 字段（INT, DEFAULT 1）
    Evidence: .sisyphus/evidence/task-1-db-schema.txt
  ```

  **Commit**: YES
  - Message: `feat(db): add version column to problem_lists for optimistic locking`
  - Files: `db-manager/migrations/V{next}__problem_lists_add_version.sql`

- [x] 2. **后端 DTO 拆分 - 按模块定义专用 DTO**

  **What to do**:
  - 在 `backend-spring/src/main/java/com/ulticode/modules/problemlist/dto/` 创建:
    - `UpdateBasicInfoDTO.java`（name, description）
    - `UpdateVisibilityDTO.java`（isPublic, isFeatured）
    - `UpdateBannerDTO.java`（bannerTag, bannerTheme, bannerOrder）
  - 保留原有的 `UpdateProblemListDTO.java` 作为通用 DTO（向后兼容）
  - 每个 DTO 添加 Jakarta Validation 注解

  **Must NOT do**:
  - 不要删除 UpdateProblemListDTO（向后兼容）
  - 不要修改 CreateProblemListDTO

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
  - **Reason**: 纯 POJO 创建，简单直接

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 6
  - **Blocked By**: None

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/problemlist/dto/UpdateProblemListDTO.java` - 现有 DTO 模式
  - `backend-spring/src/main/java/com/ulticode/modules/problemlist/dto/CreateProblemListDTO.java` - 创建 DTO 模式

  **Acceptance Criteria**:
  - [ ] 3 个新 DTO 文件创建
  - [ ] 编译通过: `cd backend-spring && ./mvnw compile -q`

  **QA Scenarios**:
  ```
  Scenario: DTO 编译通过
    Tool: Bash
    Steps:
      1. cd backend-spring && ./mvnw compile -q
    Expected Result: BUILD SUCCESS
    Evidence: .sisyphus/evidence/task-2-compile.txt
  ```

  **Commit**: YES (groups with Task 1)

- [x] 3. **后端 Entity 添加 @Version 注解**

  **What to do**:
  - 在 `ProblemList.java` 实体中添加 `version` 字段
  - 添加 `@Version` 注解（MyBatis-Plus 支持）
  - 确保 MyBatis-Plus 配置正确（乐观锁插件已启用）

  **Must NOT do**:
  - 不要修改其他实体
  - 不要改数据库连接配置

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 6, Task 8
  - **Blocked By**: Task 1

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/problemlist/entity/ProblemList.java` - 实体类
  - MyBatis-Plus 文档: OptimisticLockInterceptor

  **Acceptance Criteria**:
  - [ ] ProblemList 实体有 version 字段和 @Version 注解
  - [ ] 编译通过

  **QA Scenarios**:
  ```
  Scenario: 实体类包含 version 字段
    Tool: Bash
    Steps:
      1. grep -n "version" backend-spring/src/main/java/com/ulticode/modules/problemlist/entity/ProblemList.java
    Expected Result: 包含 private Integer version; 和 @Version
    Evidence: .sisyphus/evidence/task-3-entity.txt
  ```

  **Commit**: YES (groups with Task 1)

- [x] 4. **前端权限常量 - 新增 MANAGE_PROBLEMS**

  **What to do**:
  - 在 `management/src/constants/permissions.ts` 添加:
    - `PROBLEM_LIST_MANAGE_PROBLEMS: { action: 'MANAGE_PROBLEMS', resource: 'PROBLEM_LIST' }`
    - 如果缺少 `PROBLEM_LIST_DELETE`，也一并添加
  - 在 `management/src/stores/auth.ts` 确认 `hasPermission` 支持新常量

  **Must NOT do**:
  - 不要修改后端权限系统（保持 role-based）
  - 不要改现有权限常量

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 14
  - **Blocked By**: None

  **References**:
  - `management/src/constants/permissions.ts` - PERM 常量定义
  - `management/src/stores/auth.ts` - hasPermission 实现

  **Acceptance Criteria**:
  - [ ] PROBLEM_LIST_MANAGE_PROBLEMS 已定义
  - [ ] TypeScript 编译通过

  **QA Scenarios**:
  ```
  Scenario: 新权限常量可用
    Tool: Bash
    Steps:
      1. grep "MANAGE_PROBLEMS" management/src/constants/permissions.ts
    Expected Result: 包含 MANAGE_PROBLEMS 定义
    Evidence: .sisyphus/evidence/task-4-perm.txt
  ```

  **Commit**: YES (groups with Task 5)

- [x] 5. **前端 API Client 拆分 - 按模块定义 API 方法**

  **What to do**:
  - 在 `management/src/api/admin/problem-lists.ts` 中:
    - 保留现有 API 方法（向后兼容）
    - 新增:
      - `updateBasicInfo(id, data)` → PATCH /problem-lists/{id}
      - `updateVisibility(id, data)` → PATCH /problem-lists/{id}
      - `updateBanner(id, data)` → PATCH /problem-lists/{id}
    - 每个方法只发送对应模块的字段

  **Must NOT do**:
  - 不要改现有 API 方法签名
  - 不要新增后端端点

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: Task 10, 11, 12
  - **Blocked By**: None

  **References**:
  - `management/src/api/admin/problem-lists.ts` - 现有 API 客户端
  - `management/src/utils/request.ts` - HTTP 客户端

  **Acceptance Criteria**:
  - [ ] 3 个新 API 方法已定义
  - [ ] TypeScript 编译通过

  **QA Scenarios**:
  ```
  Scenario: API 方法发送部分字段
    Tool: Bash (grep)
    Steps:
      1. grep -n "updateBasicInfo\|updateVisibility\|updateBanner" management/src/api/admin/problem-lists.ts
    Expected Result: 包含 updateBasicInfo, updateVisibility, updateBanner 方法定义
    Evidence: .sisyphus/evidence/task-5-api.txt
  ```

  **Commit**: YES (groups with Task 4)

- [x] 6. **后端 Service 拆分 - 按模块的部分更新方法**

  **What to do**:
  - 在 `ProblemListServiceImpl.java` 中新增:
    - `updateBasicInfo(id, userId, dto)` - 更新 name, description
    - `updateVisibility(id, userId, dto)` - 更新 isPublic, isFeatured
    - `updateBanner(id, userId, dto)` - 更新 bannerTag, bannerTheme, bannerOrder
  - 每个方法:
    - 检查所有权（authorId == userId）
    - 检查乐观锁版本（version 匹配）
    - 只更新对应字段
    - 返回更新后的实体
  - 先写测试（TDD），再实现

  **Must NOT do**:
  - 不要删除现有 `updateList` 方法
  - 不要改数据库连接

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []
  - **Reason**: 需要理解现有 Service 模式，添加乐观锁逻辑

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖 Wave 1）
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 7, Task 9
  - **Blocked By**: Task 1, 2, 3

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/problemlist/service/impl/ProblemListServiceImpl.java:211-247` - 现有 updateList 方法
  - `backend-spring/src/main/java/com/ulticode/modules/problemlist/service/ProblemListService.java` - 接口定义

  **Acceptance Criteria**:
  - [ ] 3 个新方法已实现
  - [ ] 单元测试: `ProblemListServiceTest` 中 3 个测试通过
  - [ ] 乐观锁测试: version 不匹配时抛出异常

  **QA Scenarios**:
  ```
  Scenario: 部分更新只修改指定字段
    Tool: Bash (JUnit)
    Steps:
      1. cd backend-spring && ./mvnw test -Dtest=ProblemListServiceTest#updateBasicInfo
    Expected Result: 测试通过，只修改 name/description，其他字段不变
    Evidence: .sisyphus/evidence/task-6-service-test.txt

  Scenario: 乐观锁版本不匹配
    Tool: Bash (JUnit)
    Steps:
      1. cd backend-spring && ./mvnw test -Dtest=ProblemListServiceTest#updateWithStaleVersion
    Expected Result: 抛出 OptimisticLockException
    Evidence: .sisyphus/evidence/task-6-optimistic-lock-test.txt
  ```

  **Commit**: YES
  - Message: `feat(problem-list): add module-specific update methods with optimistic locking`

- [x] 7. **后端 Controller 添加角色检查**

  **What to do**:
  - 在 `AdminProblemListController.java` 中:
    - 现有方法保持 `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")`
    - 新增题目管理方法:
      - `addProblemToList()` - `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'MANAGE_PROBLEMS')")`
      - `removeProblemFromList()` - 同上
      - `reorderProblems()` - 同上
  - 添加全局异常处理：乐观锁冲突返回 409 Conflict

  **Must NOT do**:
  - 不要改现有方法的权限注解
  - 不要改 SecurityConfig

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO（依赖 Wave 1+2）
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 16
  - **Blocked By**: Task 6

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminProblemListController.java` - 现有 Controller
  - `backend-spring/src/main/java/com/ulticode/common/exception/GlobalExceptionHandler.java` - 全局异常处理

  **Acceptance Criteria**:
  - [ ] Controller 方法有正确的权限注解
  - [ ] 乐观锁冲突返回 409 状态码
  - [ ] 编译通过

  **QA Scenarios**:
  ```
  Scenario: MANAGE_PROBLEMS 角色可以管理题目
    Tool: Bash (curl)
    Steps:
      1. 登录 MANAGE_PROBLEMS 用户
      2. curl -X POST /admin/problem-lists/{id}/problems ...
    Expected Result: HTTP 200
    Evidence: .sisyphus/evidence/task-7-role-access.txt

  Scenario: 乐观锁冲突返回 409
    Tool: Bash (curl)
    Steps:
      1. 发送 PATCH 带旧 version
      2. 再次发送 PATCH 带相同旧 version
    Expected Result: 第二次返回 HTTP 409
    Evidence: .sisyphus/evidence/task-7-conflict.txt
  ```

  **Commit**: YES (groups with Task 6)

- [x] 8. **后端乐观锁异常处理**

  **What to do**:
  - 在 `GlobalExceptionHandler.java` 中添加:
    - `OptimisticLockException` → 返回 409 Conflict，包含当前 version
  - 在 `ProblemListServiceImpl` 中确保版本检查正确
  - 测试异常情况

  **Must NOT do**:
  - 不要改其他异常处理逻辑

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 18
  - **Blocked By**: Task 3, 6

  **References**:
  - `backend-spring/src/main/java/com/ulticode/common/exception/GlobalExceptionHandler.java` - 全局异常处理

  **Acceptance Criteria**:
  - [ ] OptimisticLockException 返回 409
  - [ ] 响应体包含当前 version 和冲突信息

  **QA Scenarios**:
  ```
  Scenario: 版本冲突返回正确信息
    Tool: Bash (curl)
    Steps:
      1. 模拟并发更新
      2. 检查响应体
    Expected Result: { "code": 409, "message": "版本冲突", "data": { "currentVersion": 2 } }
    Evidence: .sisyphus/evidence/task-8-conflict-response.txt
  ```

  **Commit**: YES (groups with Task 6)

- [x] 9. **后端单元测试（Service 层 TDD）**

  **What to do**:
  - 创建 `ProblemListServiceTest.java`:
    - `updateBasicInfo_Success` - 正常更新
    - `updateBasicInfo_NotOwner` - 非所有者拒绝
    - `updateBasicInfo_StaleVersion` - 乐观锁冲突
    - `updateVisibility_Success` - 可见性更新
    - `updateBanner_Success` - 横幅更新
    - `updateBanner_InvalidTheme` - 无效主题拒绝
  - 使用 Mockito 模拟 Mapper

  **Must NOT do**:
  - 不要写集成测试（IT）
  - 不要测试已有方法

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
  - **Reason**: 需要理解 Mockito 和 JUnit 5 的使用模式

  **Parallelization**:
  - **Can Run In Parallel**: YES（与 Task 7, 8 并行）
  - **Parallel Group**: Wave 2
  - **Blocks**: Task 18
  - **Blocked By**: Task 6

  **References**:
  - 现有测试文件模式（搜索 `*Test.java` 或 `*Tests.java`）
  - `backend-spring/src/test/java/com/ulticode/modules/problemlist/` - 已有测试目录

  **Acceptance Criteria**:
  - [ ] 6 个测试用例全部通过
  - [ ] 测试覆盖率 > 80%

  **QA Scenarios**:
  ```
  Scenario: 所有 Service 测试通过
    Tool: Bash (JUnit)
    Steps:
      1. cd backend-spring && ./mvnw test -Dtest=ProblemListServiceTest
    Expected Result: Tests run: 6, Failures: 0
    Evidence: .sisyphus/evidence/task-9-test-results.txt
  ```

  **Commit**: YES (groups with Task 6)

- [x] 10. **前端 BasicInfo 模块组件**

  **What to do**:
  - 创建 `management/src/views/problem-lists/components/BasicInfoSection.vue`
  - 包含字段: name（Input）, description（Textarea）
  - 使用 vee-validate + Zod 验证
  - 支持自动保存（useDebounceFn 1s）
  - 显示保存状态（未保存 / 保存中 / 已保存）
  - 先写测试，再实现

  **Must NOT do**:
  - 不要包含其他模块的字段
  - 不要改路由

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []
  - **Reason**: Vue 3 组件开发，需要理解现有表单模式

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: Task 16
  - **Blocked By**: Task 5

  **References**:
  - `management/src/views/problem-lists/components/GeneralInfo.vue` - 现有表单模式
  - `management/src/api/admin/problem-lists.ts` - updateBasicInfo API

  **Acceptance Criteria**:
  - [ ] 组件渲染 name 和 description 字段
  - [ ] 自动保存触发正确（debounce 1s）
  - [ ] 显示保存状态指示器

  **QA Scenarios**:
  ```
  Scenario: 基本信息编辑和自动保存
    Tool: Playwright
    Steps:
      1. 导航到 /problem-lists/list-concurrency/edit
      2. 修改 name 字段
      3. 等待 1.1 秒
      4. 检查网络请求: PATCH /problem-lists/{id}，payload 只包含 name
    Expected Result: 发送 PATCH 请求，payload 为 { "name": "新名称" }
    Evidence: .sisyphus/evidence/task-10-autosave.png

  Scenario: 保存状态显示
    Tool: Playwright
    Steps:
      1. 修改 name 字段
      2. 检查状态指示器显示"未保存"
      3. 等待自动保存完成
      4. 检查状态指示器显示"已保存"
    Expected Result: 状态从"未保存"变为"已保存"
    Evidence: .sisyphus/evidence/task-10-status.png
  ```

  **Commit**: YES
  - Message: `feat(problem-list): add BasicInfo section component with auto-save`

- [x] 11. **前端 Visibility 模块组件**

  **What to do**:
  - 创建 `management/src/views/problem-lists/components/VisibilitySection.vue`
  - 包含字段: isPublic（Switch/Checkbox）, isFeatured（Switch/Checkbox）
  - isFeatured=true 时显示相关说明
  - 支持自动保存
  - 先写测试，再实现

  **Must NOT do**:
  - 不要包含 banner 字段

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: Task 16
  - **Blocked By**: Task 5

  **References**:
  - `management/src/views/problem-lists/components/GeneralInfo.vue` - 现有 visibility 字段
  - `management/src/api/admin/problem-lists.ts` - updateVisibility API

  **Acceptance Criteria**:
  - [ ] 组件渲染 isPublic 和 isFeatured 字段
  - [ ] 切换 isFeatured 时显示/隐藏说明
  - [ ] 自动保存触发正确

  **QA Scenarios**:
  ```
  Scenario: 可见性切换和自动保存
    Tool: Playwright
    Steps:
      1. 点击 isPublic Switch
      2. 等待 1.1 秒
      3. 检查 PATCH 请求 payload
    Expected Result: PATCH payload 只包含 { "isPublic": true/false }
    Evidence: .sisyphus/evidence/task-11-visibility.png
  ```

  **Commit**: YES (groups with Task 10)

- [x] 12. **前端 Banner 模块组件**

  **What to do**:
  - 创建 `management/src/views/problem-lists/components/BannerSection.vue`
  - 包含字段: bannerTag（Input）, bannerTheme（Select: blue/green/purple/orange/red）, bannerOrder（Number Input）
  - 支持自动保存
  - 先写测试，再实现

  **Must NOT do**:
  - 不要包含 bannerIcon（前端未使用）

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: Task 16
  - **Blocked By**: Task 5

  **References**:
  - `management/src/views/problem-lists/components/GeneralInfo.vue` - 现有 banner 字段
  - `management/src/api/admin/problem-lists.ts` - updateBanner API

  **Acceptance Criteria**:
  - [ ] 组件渲染 3 个 banner 字段
  - [ ] 主题选择器有 5 个选项
  - [ ] 自动保存触发正确

  **QA Scenarios**:
  ```
  Scenario: 横幅设置编辑
    Tool: Playwright
    Steps:
      1. 选择 bannerTheme "purple"
      2. 输入 bannerTag "热门"
      3. 等待 1.1 秒
    Expected Result: PATCH payload 包含 { "bannerTheme": "purple", "bannerTag": "热门" }
    Evidence: .sisyphus/evidence/task-12-banner.png
  ```

  **Commit**: YES (groups with Task 10)

- [x] 13. **前端自动保存 Composable**

  **What to do**:
  - 创建 `management/src/composables/useAutoSave.ts`
  - 功能:
    - 接受 form values、save function、options（debounceMs, blurTrigger）
    - 返回: saveStatus ('idle' | 'saving' | 'saved' | 'error')、lastSavedAt、error
    - 使用 useDebounceFn（VueUse）
    - 支持 blur 事件触发立即保存
    - 支持取消进行中的请求（AbortController）
  - 先写测试，再实现

  **Must NOT do**:
  - 不要依赖具体业务逻辑（通用 composable）
  - 不要改 VueUse 配置

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []
  - **Reason**: 需要设计通用 composable，处理竞态条件

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: Task 10, 11, 12
  - **Blocked By**: None

  **References**:
  - `console/src/views/post-editor/solutions/SolutionsEditView.vue:365-379` - useDebounceFn 模式
  - `management/src/composables/useDataTable.ts:98-105` - watchDebounced 模式
  - VueUse 文档: useDebounceFn

  **Acceptance Criteria**:
  - [ ] Composable 编译通过
  - [ ] 单元测试: debounce 正确、blur 立即保存、请求取消

  **QA Scenarios**:
  ```
  Scenario: debounce 延迟保存
    Tool: Bash (vitest)
    Steps:
      1. cd management && pnpm test src/composables/useAutoSave.test.ts
    Expected Result: 测试通过，验证 debounce 和 blur 行为
    Evidence: .sisyphus/evidence/task-13-composable-test.txt
  ```

  **Commit**: YES (groups with Task 10)

- [x] 14. **前端权限控制 Composable**

  **What to do**:
  - 创建 `management/src/composables/useProblemListPermissions.ts`
  - 功能:
    - `canEditBasicInfo` - 检查 UPDATE:PROBLEM_LIST
    - `canEditVisibility` - 检查 UPDATE:PROBLEM_LIST
    - `canEditBanner` - 检查 UPDATE:PROBLEM_LIST
    - `canManageProblems` - 检查 MANAGE_PROBLEMS:PROBLEM_LIST
  - 基于 auth store 的 hasPermission

  **Must NOT do**:
  - 不要改 auth store

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: Task 16
  - **Blocked By**: Task 4

  **References**:
  - `management/src/stores/auth.ts` - hasPermission 实现
  - `management/src/constants/permissions.ts` - PERM 常量

  **Acceptance Criteria**:
  - [ ] Composable 返回正确的权限状态
  - [ ] 单元测试通过

  **QA Scenarios**:
  ```
  Scenario: 权限检查
    Tool: Bash (vitest)
    Steps:
      1. cd management && pnpm test src/composables/useProblemListPermissions.test.ts
    Expected Result: 测试通过
    Evidence: .sisyphus/evidence/task-14-perm-test.txt
  ```

  **Commit**: YES (groups with Task 10)

- [x] 15. **前端单元测试（组件 TDD）**

  **What to do**:
  - 为 Task 10-13 的组件/composable 编写测试:
    - `BasicInfoSection.test.ts` - 渲染、验证、自动保存触发
    - `VisibilitySection.test.ts` - 渲染、切换、自动保存
    - `BannerSection.test.ts` - 渲染、选择、自动保存
    - `useAutoSave.test.ts` - debounce、blur、取消
    - `useProblemListPermissions.test.ts` - 权限检查
  - 使用 Vitest + Vue Test Utils

  **Must NOT do**:
  - 不要测试已有组件
  - 不要写 E2E 测试

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
  - **Reason**: 需要理解 Vitest 和 Vue Test Utils

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3
  - **Blocks**: Task 18
  - **Blocked By**: Task 10, 11, 12, 13, 14

  **References**:
  - `management/src/components/__tests__/` 或类似目录 - 现有测试模式
  - Vitest 文档: https://vitest.dev/

  **Acceptance Criteria**:
  - [ ] 所有组件测试通过
  - [ ] 测试覆盖率 > 80%

  **QA Scenarios**:
  ```
  Scenario: 所有前端测试通过
    Tool: Bash
    Steps:
      1. cd management && pnpm test
    Expected Result: 所有测试通过
    Evidence: .sisyphus/evidence/task-15-frontend-tests.txt
  ```

  **Commit**: YES (groups with Task 10)

- [x] 16. **重构 ProblemListDetailView.vue（集成 4 个模块）**

  **What to do**:
  - 重构 `management/src/views/problem-lists/ProblemListDetailView.vue`
  - 集成 4 个新模块组件:
    - `BasicInfoSection.vue`
    - `VisibilitySection.vue`
    - `BannerSection.vue`
    - `ProblemsManager.vue`（保持现有）
  - 使用 provide/inject 或 composable 共享 list 数据
  - 移除对旧 `GeneralInfo.vue` 的依赖
  - 显示全局保存状态

  **Must NOT do**:
  - 不要改路由定义
  - 不要改 ProblemsManager

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []
  - **Reason**: 需要协调多个组件，确保数据流正确

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 4
  - **Blocks**: Task 17, 18
  - **Blocked By**: Task 10, 11, 12, 13, 14

  **References**:
  - `management/src/views/problem-lists/ProblemListDetailView.vue` - 现有页面
  - `management/src/stores/admin/problem-lists.ts` - store

  **Acceptance Criteria**:
  - [ ] 4 个模块正确渲染
  - [ ] 每个模块独立保存
  - [ ] 全局状态显示正确

  **QA Scenarios**:
  ```
  Scenario: 所有模块独立保存
    Tool: Playwright
    Steps:
      1. 修改 BasicInfo 的 name
      2. 修改 Visibility 的 isPublic
      3. 等待自动保存
      4. 检查网络请求
    Expected Result: 2 个独立的 PATCH 请求
    Evidence: .sisyphus/evidence/task-16-integration.png
  ```

  **Commit**: YES
  - Message: `feat(problem-list): integrate module sections in detail view`

- [x] 17. **创建页面适配（先 POST 再启用自动保存）**

  **What to do**:
  - 修改创建流程:
    - 创建页面只显示 BasicInfo（name 必填）
    - 用户填写 name → 点击"创建" → POST 请求
    - 创建成功后自动跳转到编辑页面（/problem-lists/{id}/edit）
    - 编辑页面启用自动保存
  - 添加导航守卫：创建页面有未保存更改时提示确认

  **Must NOT do**:
  - 不要改后端创建 API
  - 不要在创建页面启用自动保存

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []
  - **Reason**: 需要处理创建→编辑的状态转换

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 4
  - **Blocks**: Task 18
  - **Blocked By**: Task 16

  **References**:
  - `management/src/views/problem-lists/ProblemListDetailView.vue` - 创建/编辑判断逻辑
  - `management/src/router/index.ts` - 路由定义

  **Acceptance Criteria**:
  - [ ] 创建页面只显示 name 和 description
  - [ ] 创建成功后跳转到编辑页面
  - [ ] 编辑页面启用自动保存

  **QA Scenarios**:
  ```
  Scenario: 创建流程
    Tool: Playwright
    Steps:
      1. 导航到 /problem-lists/new
      2. 输入 name "测试题单"
      3. 点击"创建"
      4. 等待跳转
    Expected Result: 跳转到 /problem-lists/{id}/edit，显示所有模块
    Evidence: .sisyphus/evidence/task-17-create.png
  ```

  **Commit**: YES (groups with Task 16)

- [x] 18. **集成测试（端到端 QA）**

  **What to do**:
  - 编写端到端测试脚本:
    - 完整编辑流程：修改所有模块 → 验证保存
    - 并发冲突：两个标签页同时编辑 → 验证 409
    - 权限测试：MANAGE_PROBLEMS 用户只能管理题目
    - 创建流程：创建 → 编辑 → 保存
  - 使用 Playwright 或 curl + bash

  **Must NOT do**:
  - 不要写单元测试（已有 Task 9, 15）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 4
  - **Blocks**: Task 19
  - **Blocked By**: Task 16, 17

  **References**:
  - 所有之前的任务

  **Acceptance Criteria**:
  - [ ] 所有端到端场景通过

  **QA Scenarios**:
  ```
  Scenario: 完整编辑流程
    Tool: Playwright
    Steps:
      1. 登录 ADMIN
      2. 编辑题单所有字段
      3. 验证数据库中的值
    Expected Result: 所有字段正确保存
    Evidence: .sisyphus/evidence/task-18-e2e.png

  Scenario: 并发冲突
    Tool: Bash (curl)
    Steps:
      1. 获取当前 version
      2. 用旧 version 发送 PATCH
      3. 检查响应
    Expected Result: HTTP 409
    Evidence: .sisyphus/evidence/task-18-conflict.txt
  ```

  **Commit**: YES (groups with Task 16)

- [x] 19. **清理旧代码（删除旧 GeneralInfo.vue）**

  **What to do**:
  - 确认没有其他地方引用 `GeneralInfo.vue`
  - 删除 `management/src/views/problem-lists/components/GeneralInfo.vue`
  - 清理相关未使用的 import 和类型

  **Must NOT do**:
  - 不要删除还在使用的代码

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 4
  - **Blocks**: None
  - **Blocked By**: Task 16

  **References**:
  - `management/src/views/problem-lists/components/GeneralInfo.vue` - 旧组件

  **Acceptance Criteria**:
  - [ ] GeneralInfo.vue 已删除
  - [ ] 没有其他文件引用它
  - [ ] 编译通过

  **QA Scenarios**:
  ```
  Scenario: 旧代码已清理
    Tool: Bash
    Steps:
      1. ls management/src/views/problem-lists/components/GeneralInfo.vue
    Expected Result: 文件不存在
    Evidence: .sisyphus/evidence/task-19-cleanup.txt
  ```

  **Commit**: YES (groups with Task 16)

---

## Final Verification Wave

> 4 个审查 Agent 并行执行。全部通过后才能交付。

- [ ] F1. **计画合规性审查** — `oracle`
  读取计画端到端。对每个"必须有"：验证实现存在。对每个"必须不有"：搜索代码库中是否有禁止的模式。检查证据文件是否存在。
  输出: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [ ] F2. **代码质量审查** — `unspecified-high`
  运行 `tsc --noEmit` + linter + `bun test`。审查所有修改的文件：`as any`/`@ts-ignore`、空 catch、console.log、注释掉的代码、未使用的 import。检查 AI slop。
  输出: `Build [PASS/FAIL] | Lint [PASS/FAIL] | Tests [N pass/N fail] | Files [N clean/N issues] | VERDICT`

- [ ] F3. **实际 QA** — `unspecified-high`
  从干净状态开始。执行每个任务的 QA Scenario。测试跨模块集成。测试边界情况。
  输出: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [ ] F4. **范围保真度检查** — `deep`
  对每个任务：读取"What to do"，读取实际 diff。验证 1:1 对齐。检查"Must NOT do"合规性。
  输出: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

> 4 个审查 Agent 并行执行。全部通过后才能交付。

- [ ] F1. **计画合规性审查** — `oracle`
- [ ] F2. **代码质量审查** — `unspecified-high`
- [ ] F3. **实际 QA** — `unspecified-high`
- [ ] F4. **范围保真度检查** — `deep`

---

## Commit Strategy

- Wave 1: `feat(problem-list): add version field and split DTOs`
- Wave 2: `feat(problem-list): add optimistic locking and role-based permissions`
- Wave 3: `feat(problem-list): split frontend into module components`
- Wave 4: `feat(problem-list): integrate modules and add auto-save`
- Wave FINAL: `test(problem-list): add comprehensive tests`

---

## Success Criteria

### 验证命令
```bash
# 后端编译通过
cd backend-spring && ./mvnw compile -q

# 后端测试通过
cd backend-spring && ./mvnw test -q

# 前端类型检查通过
cd management && pnpm type-check

# 前端测试通过
cd management && pnpm test

# 数据库迁移验证
cd db-manager && .venv/bin/python -m db_manager.cli validate
```

### 最终检查清单
- [ ] 所有"必须有"已实现
- [ ] 所有"必须不有"已排除
- [ ] 所有测试通过
- [ ] 所有 QA Scenarios 通过
- [ ] 代码审查通过
