# 修复题目版本历史功能

## TL;DR

> **目标**: 完整实现管理端题目版本历史功能，消除 i18n 缺失警告和 API 404 异常
>
> **交付物**:
> - 后端：数据库表 `problem_versions` + 实体/Mapper/Service/Controller + 5 个 REST API 端点
> - 前端：补充管理端 zh-CN/en-US i18n 翻译键（约 20 个缺失键）
> - 集成：ProblemServiceImpl 自动触发版本创建（创建/更新时）
> - 测试：后端单元测试 + 前端 i18n 键完整性验证
>
> **预估工作量**: Medium（约 15-20 个任务）
> **并行执行**: YES - 3 个 Wave
> **关键路径**: T1(迁移) → T2(实体) → T7(服务) → T8(控制器) → T9(集成) → T13(集成验证) → F1-F4(审查)

---

## Context

### 原始问题
管理端前端 `VersionHistoryTimeline.vue` 组件触发多个异常：
1. i18n 翻译键缺失：`problems.versionHistory.description`、`loadError`、`noVersions`、`createInitial` 等约 20 个键在 zh-CN 中不存在
2. API 404：`GET /admin/problems/{id}/versions` 后端端点完全未实现

### 调研发现
- **后端**：无 `problem_version` 表/实体/端点。`Problem.java` 仅有乐观锁 `version` 字段（技术字段，无关）
- **前端（管理端）**：组件和 API 客户端已完整实现，i18n 严重缺失（25 个引用中仅 8 个已定义）
- **前端（控制台）**：无版本历史功能，不在本次修复范围
- **AuditService**（在 `admin` 模块）：已有 JSON 快照模式（`JacksonTypeHandler`），可作为实现参考

### Metis 审查要点
- **快照范围**：前端 `ProblemVersionDetail` 类型期望完整题目快照（含 content、examples、languages、tags），需快照 `Problem` + `ProblemDetail` + `ProblemExample` + `ProblemLanguage` + `ProblemTag`
- **存储策略**：采用 JSON 列存储快照（参考 `AuditLog` 模式），而非独立列
- **触发点**：在 `ProblemServiceImpl.createProblem()` 和 `updateProblem()` 中自动触发版本创建
- **命名**：业务版本号用 `versionNumber`（与前端类型一致），技术乐观锁保持 `version`
- **回滚语义**：创建 `ROLLBACK` 类型新版本，复制目标版本数据到当前 Problem

---

## Work Objectives

### Core Objective
在管理端完整实现题目版本历史功能：每次创建或更新题目时自动保存版本快照，管理员可查看历史版本、对比差异、回滚到任意版本。

### Concrete Deliverables
- `db-manager/migrations/V{next}__add_problem_version_table.sql` - Flyway 迁移文件
- `backend-spring/src/main/java/com/ulticode/modules/problem/entity/ProblemVersion.java` - 实体类
- `backend-spring/src/main/java/com/ulticode/modules/problem/mapper/ProblemVersionMapper.java` - Mapper 接口
- `backend-spring/src/main/resources/mapper/problem/ProblemVersionMapper.xml` - MyBatis XML
- `backend-spring/src/main/java/com/ulticode/modules/problem/service/ProblemVersionService.java` - Service 接口
- `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemVersionServiceImpl.java` - Service 实现
- `backend-spring/src/main/java/com/ulticode/modules/problem/controller/AdminProblemVersionController.java` - 5 个端点
- `backend-spring/src/main/java/com/ulticode/modules/problem/vo/` - VO 类（ProblemVersionVO, ProblemVersionDetailVO, VersionDiffVO）
- `management/src/i18n/locales/zh-CN/modules/problems.ts` - 补充缺失翻译键
- `management/src/i18n/locales/en-US/modules/problems.ts` - 补充缺失翻译键
- `management/src/components/problems/VersionHistoryTimeline.vue` - 修复 `by` → `author`

### Definition of Done
- [ ] 5 个 API 端点全部可用（curl 验证 200）
- [ ] 创建/更新题目后自动产生版本记录
- [ ] 前端无 i18n 缺失警告
- [ ] 后端单元测试全部通过
- [ ] 前端 i18n 键完整性验证通过

### Must Have
- 数据库表 `problem_versions` 及 Flyway 迁移
- 5 个 REST API 端点（列表、详情、对比、回滚、创建初始版本）
- 完整题目快照（JSON 列）
- 版本自动触发（create/update）
- zh-CN 和 en-US i18n 翻译键完整
- 后端单元测试覆盖

### Must NOT Have (Guardrails)
- 不为控制台前端添加版本历史功能
- 不扩展至其他实体（User、Contest 等）
- 不实现版本导出/标签/搜索
- 不修改前端 API 客户端或组件接口（仅修复 i18n 键引用）
- 不复用 AuditService（独立 Service）
- 不为 problem_versions 外键设置级联删除

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: YES（Spring Boot + MyBatis-Plus + JUnit + Vitest）
- **Automated tests**: YES（Tests after）
- **Backend framework**: JUnit 5 + Spring Boot Test + Testcontainers
- **Frontend framework**: Vitest（管理端已有）
- **If tests-after**: 实现后编写测试，验证功能正确性

### QA Policy
每个任务包含 Agent-Executed QA Scenarios。证据保存至 `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`。

- **Backend API**: Bash (curl) - 发送请求，断言状态码和响应字段
- **Frontend i18n**: Bash (node) - 脚本检查 t() 引用与 locale 定义匹配
- **Integration**: Bash (curl + 数据库查询) - 验证端到端流程

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (基础搭建 - 全部可并行):
├── T1: 数据库迁移文件（Flyway SQL）
├── T2: ProblemVersion 实体类
├── T3: ProblemVersionMapper + XML
├── T4: VO/DTO 类（3 个 VO）
├── T5: 前端 i18n zh-CN 补充
└── T6: 前端 i18n en-US 补充

Wave 2 (核心逻辑 - 依赖 Wave 1):
├── T7: ProblemVersionService 接口 + 实现
├── T8: AdminProblemVersionController（5 端点）
├── T9: ProblemServiceImpl 集成版本触发
└── T10: 前端组件修复（by → author）

Wave 3 (测试 + 验证 - 依赖 Wave 2):
├── T11: 后端单元测试
├── T12: 前端 i18n 键完整性验证脚本
└── T13: 集成验证（启动服务，测试完整流程）

Wave FINAL (审查 - 依赖全部):
├── F1: Plan compliance audit (oracle)
├── F2: Code quality review (unspecified-high)
├── F3: Real manual QA (unspecified-high)
└── F4: Scope fidelity check (deep)
-> 呈现结果 -> 获取用户显式确认

Critical Path: T1 → T2 → T7 → T8 → T9 → T13 → F1-F4 → user okay
Parallel Speedup: ~60%（Wave 1 全并行，Wave 2 中 T7/T8 可部分并行）
Max Concurrent: 6（Wave 1）
```

### Dependency Matrix

| Task | Depends On | Blocks |
|------|-----------|--------|
| T1 | - | T2, T7 |
| T2 | T1 | T3, T7 |
| T3 | T2 | T7 |
| T4 | - | T8 |
| T5 | - | T10, T12 |
| T6 | - | T10, T12 |
| T7 | T1-T3 | T8, T9, T11 |
| T8 | T4, T7 | T11, T13 |
| T9 | T7 | T11, T13 |
| T10 | T5, T6 | T12 |
| T11 | T7-T9 | T13 |
| T12 | T5, T6, T10 | T13 |
| T13 | T8, T9, T11, T12 | F1-F4 |

### Agent Dispatch Summary

- **Wave 1**: **6** - T1-T4 → `quick`, T5-T6 → `quick`
- **Wave 2**: **4** - T7 → `deep`, T8 → `unspecified-high`, T9 → `deep`, T10 → `quick`
- **Wave 3**: **3** - T11 → `unspecified-high`, T12 → `quick`, T13 → `unspecified-high`
- **FINAL**: **4** - F1 → `oracle`, F2 → `unspecified-high`, F3 → `unspecified-high`, F4 → `deep`

---

## TODOs

- [x] 1. **数据库迁移文件 - 创建 problem_versions 表**

  **What to do**:
  - 在 `db-manager/migrations/` 下创建新的 Flyway 迁移文件（使用下一个可用版本号，如 `V{next}__add_problem_version_table.sql`）
  - 表结构：
    ```sql
    CREATE TABLE problem_versions (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      problem_id BIGINT NOT NULL,
      version_number INT NOT NULL,
      snapshot_json JSON NOT NULL COMMENT '完整题目快照（Problem + ProblemDetail + Examples + Languages + Tags）',
      change_type VARCHAR(20) NOT NULL COMMENT 'CREATE | UPDATE | ROLLBACK',
      change_summary VARCHAR(255) COMMENT '变更摘要',
      created_by VARCHAR(40) NOT NULL,
      created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      UNIQUE KEY uk_problem_version (problem_id, version_number),
      KEY idx_problem_id (problem_id),
      KEY idx_created_at (created_at)
    );
    ```
  - 外键：`problem_id` 引用 `problems(id)`，**不设置 ON DELETE CASCADE**
  - 迁移文件必须包裹在 `SET FOREIGN_KEY_CHECKS=0` ... `SET FOREIGN_KEY_CHECKS=1` 中
  - 检查现有迁移版本号，确保新文件版本号连续

  **Must NOT do**:
  - 不要添加级联删除（保留历史记录即使题目被删除）
  - 不要修改现有迁移文件
  - 不要删除或修改 `problems` 表

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: T2, T7
  - **Blocked By**: None

  **References**:
  - `db-manager/migrations/` - 查看现有迁移文件命名和版本号模式
  - `db-manager/README.md` - Flyway 迁移规范
  - `AGENTS.md` - 数据库迁移关键要点

  **Acceptance Criteria**:
  - [ ] 迁移文件命名正确（`V{version}__add_problem_version_table.sql`）
  - [ ] 文件包含 `SET FOREIGN_KEY_CHECKS=0` ... `SET FOREIGN_KEY_CHECKS=1`
  - [ ] 表结构包含所有指定字段
  - [ ] 有唯一索引 `(problem_id, version_number)`

  **QA Scenarios**:
  ```
  Scenario: 验证迁移文件语法正确
    Tool: Bash
    Preconditions: 无
    Steps:
      1. cat db-manager/migrations/V{next}__add_problem_version_table.sql
      2. 确认包含 CREATE TABLE problem_versions
      3. 确认包含 SET FOREIGN_KEY_CHECKS=0/1
      4. 确认有 UNIQUE KEY uk_problem_version
    Expected Result: 文件存在且包含所有必需元素
    Evidence: .sisyphus/evidence/task-1-migration-file.txt
  ```

  **Commit**: YES
  - Message: `feat(db): add problem_version migration`
  - Files: `db-manager/migrations/V{next}__add_problem_version_table.sql`

- [x] 2. **ProblemVersion 实体类**

  **What to do**:
  - 创建 `backend-spring/src/main/java/com/ulticode/modules/problem/entity/ProblemVersion.java`
  - 使用 MyBatis-Plus 注解映射 `problem_versions` 表
  - 字段映射：
    - `id` (Long) - `@TableId(type = IdType.AUTO)`
    - `problemId` (Long) - 外键
    - `versionNumber` (Integer) - 业务版本号（1, 2, 3...）
    - `snapshotJson` (String) - JSON 快照字符串，使用 `@TableField(typeHandler = JacksonTypeHandler.class)` 或存为 String 后在 Service 层序列化/反序列化
    - `changeType` (String) - ENUM: "CREATE", "UPDATE", "ROLLBACK"
    - `changeSummary` (String) - 变更摘要
    - `createdBy` (String) - 创建者 ID
    - `createdAt` (LocalDateTime) - 创建时间
  - 参考现有实体风格（如 `Problem.java`）
  - 添加 Lombok `@Data` 或手写 getter/setter

  **Must NOT do**:
  - 不要添加乐观锁字段（与 Problem 的技术 version 字段区分）
  - 不要添加逻辑删除字段（版本历史不软删）
  - 不要添加 `@Version` 注解

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（依赖 T1 确认表结构）
  - **Parallel Group**: Wave 1
  - **Blocks**: T3, T7
  - **Blocked By**: T1

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/problem/entity/Problem.java` - 参考实体风格和注解用法
  - `backend-spring/src/main/java/com/ulticode/modules/admin/entity/AuditLog.java` - 参考 JacksonTypeHandler 用法（如果 snapshot 需要对象映射）
  - `backend-spring/src/main/java/com/ulticode/common/entity/BaseEntity.java` - 参考基础实体（如果存在）

  **Acceptance Criteria**:
  - [ ] 实体类文件存在且编译通过
  - [ ] 所有字段与数据库表结构一一对应
  - [ ] 使用正确的 MyBatis-Plus 注解

  **QA Scenarios**:
  ```
  Scenario: 验证实体类编译通过
    Tool: Bash
    Preconditions: 无
    Steps:
      1. cd backend-spring && ./mvnw compile -pl . -am -q
      2. 检查无编译错误
    Expected Result: BUILD SUCCESS
    Evidence: .sisyphus/evidence/task-2-entity-compile.txt
  ```

  **Commit**: YES（与 T3 合并提交）
  - Message: `feat(problem): add ProblemVersion entity and mapper`
  - Files: `backend-spring/.../entity/ProblemVersion.java`

- [x] 3. **ProblemVersionMapper 接口（注解式）**

  **What to do**:
  - 创建 `backend-spring/src/main/java/com/ulticode/modules/problem/mapper/ProblemVersionMapper.java`
    - 继承 `com.baomidou.mybatisplus.core.mapper.BaseMapper<ProblemVersion>`
    - 使用 MyBatis 注解（`@Select`）实现自定义方法：
      - `selectByProblemId(@Param("problemId") Long problemId, Page<ProblemVersion> page)` - 按题目 ID 分页查询，按 `version_number DESC` 排序
      - `selectLatestVersionNumber(@Param("problemId") Long problemId)` - 查询最大版本号
    - 参考现有注解式 Mapper 风格（如 `ProblemMapper.java`）

  **Must NOT do**:
  - 不要创建 XML Mapper 文件（项目使用注解式 Mapper）
  - 不要修改现有 Mapper 文件

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（依赖 T2）
  - **Parallel Group**: Wave 1
  - **Blocks**: T7
  - **Blocked By**: T2

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/problem/mapper/ProblemMapper.java` - 参考注解式 Mapper 风格
  - `com.baomidou.mybatisplus.core.mapper.BaseMapper` - MyBatis-Plus BaseMapper

  **Acceptance Criteria**:
  - [ ] Mapper 接口存在
  - [ ] 使用 `@Select` 注解定义 SQL
  - [ ] 分页查询按 `version_number DESC` 排序

  **QA Scenarios**:
  ```
  Scenario: 验证 Mapper 编译通过
    Tool: Bash
    Preconditions: 无
    Steps:
      1. cd backend-spring && ./mvnw compile -pl . -am -q
      2. 检查无编译错误
    Expected Result: BUILD SUCCESS
    Evidence: .sisyphus/evidence/task-3-mapper-compile.txt
  ```

  **Commit**: YES（与 T2 合并提交）
  - Message: `feat(problem): add ProblemVersion entity and mapper`
  - Files: `backend-spring/.../mapper/ProblemVersionMapper.java`

- [x] 4. **VO/DTO 类 - 4 个 VO**

  **What to do**:
  - 在 `backend-spring/src/main/java/com/ulticode/modules/problem/vo/` 下创建 4 个 VO 类：
  1. `ProblemVersionVO.java` - 版本列表项（匹配前端 `ProblemVersion` 类型）：
     - `id` (String)
     - `versionNumber` (Integer)
     - `changeSummary` (String)
     - `changeType` (String)
     - `createdAt` (String，ISO 8601 格式)
     - `createdBy` (String)
  2. `ProblemVersionDetailVO.java` - 版本详情（匹配前端 `ProblemVersionDetail` 类型）：
     - 包含 `ProblemVersionVO` 的所有字段
     - 额外字段：`title`, `slug`, `difficulty` (String), `isPremium` (Boolean), `isPublished` (Boolean)
     - `summary` (String), `content` (String), `constraints` (List<String>), `hints` (List<String>)
     - `examples` (List<Map<String, Object>>), `languages` (List<Map<String, Object>>), `tags` (List<String>)
  3. `VersionDiffVO.java` - 单个字段差异：
     - `field` (String) - 字段名
     - `oldValue` (Object) - 旧值
     - `newValue` (Object) - 新值
  4. `VersionWithDiffVO.java` - 版本对比结果（匹配前端 `VersionWithDiff` 类型）：
     - `fromVersion` (ProblemVersionVO) - 源版本
     - `toVersion` (ProblemVersionVO) - 目标版本
     - `diffs` (List<VersionDiffVO>) - 差异列表
  - 参考现有 VO 风格（如 `ProblemVO.java` 或 `ProblemListVO.java`）
  - 使用 Lombok `@Data` 或手写 getter/setter

  **Must NOT do**:
  - 不要修改前端类型定义（前端已确定，后端需匹配）
  - 不要添加前端不需要的字段
  - 不要使用 `Date` 类型（使用 String 或 LocalDateTime）

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: T8
  - **Blocked By**: None（参考前端类型即可）

  **References**:
  - `management/src/api/admin/problems.ts:205-268` - 前端 TypeScript 类型定义（必须严格匹配）
  - `backend-spring/src/main/java/com/ulticode/modules/problem/vo/` - 现有 VO 文件风格和包结构
  - `backend-spring/src/main/java/com/ulticode/modules/problem/dto/UpdateProblemDTO.java` - 参考字段命名

  **Acceptance Criteria**:
  - [ ] 3 个 VO 类文件存在
  - [ ] 所有字段与前端类型一一对应
  - [ ] 字段命名使用 camelCase（Java 风格）

  **QA Scenarios**:
  ```
  Scenario: 验证 VO 类字段与前端类型匹配
    Tool: Bash
    Preconditions: 无
    Steps:
      1. 读取前端类型定义：grep -A 20 "export interface ProblemVersion" management/src/api/admin/problems.ts
      2. 读取后端 VO：grep -A 20 "public class ProblemVersionVO" backend-spring/.../ProblemVersionVO.java
      3. 对比字段列表
    Expected Result: 字段名称和类型一致（忽略 TS/Java 类型差异）
    Evidence: .sisyphus/evidence/task-4-vo-fields.txt
  ```

  **Commit**: YES
  - Message: `feat(problem): add version history VO classes`
  - Files: `backend-spring/.../vo/ProblemVersionVO.java`, `ProblemVersionDetailVO.java`, `VersionDiffVO.java`

- [x] 5. **前端 i18n - 补充 zh-CN 缺失翻译键**

  **What to do**:
  - 编辑 `management/src/i18n/locales/zh-CN/modules/problems.ts`
  - 在 `versionHistory` 命名空间下补充以下缺失键（保留现有 8 个键）：
    ```typescript
    versionHistory: {
      // 已有键（保留）
      title: "版本历史",
      noHistory: "暂无版本历史",
      version: "版本",
      author: "作者",
      changes: "变更",
      restore: "恢复",
      restoreSuccess: "已恢复到该版本",
      viewDiff: "查看差异",
      // 新增键
      description: "查看和管理题目的历史版本",
      compareWith: "对比版本 {version}",
      noVersions: "暂无版本记录",
      createInitial: "创建初始版本",
      by: "由", // 或统一改为 author
      versionDetails: "版本详情",
      compareVersions: "版本对比",
      noChanges: "无变更",
      oldValue: "旧值",
      newValue: "新值",
      rollbackTitle: "回滚到版本 {version}",
      rollbackConfirm: "确定要回滚到版本 {version} 吗？此操作将创建一个新版本记录。",
      rollbackReasonPlaceholder: "请输入回滚原因（可选）",
      rollbackButton: "确认回滚",
      loadError: "加载版本历史失败",
      loadDetailError: "加载版本详情失败",
      compareError: "版本对比失败",
      rollbackError: "回滚失败",
      rollbackSuccess: "已成功回滚到版本 {version}",
      createInitialSuccess: "初始版本创建成功",
      alreadyHasVersions: "该题目已有版本记录",
      createInitialError: "创建初始版本失败",
      action: {
        CREATE: "创建",
        UPDATE: "更新",
        ROLLBACK: "回滚"
      }
    }
    ```
  - 保留现有键的已有翻译值，不要修改

  **Must NOT do**:
  - 不要修改 console 前端的 i18n 文件
  - 不要修改 `management/src/i18n/locales/zh-CN/modules/problems.ts` 中其他命名空间
  - 不要删除现有键

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: T10, T12
  - **Blocked By**: None

  **References**:
  - `management/src/components/problems/VersionHistoryTimeline.vue` - 检查所有 `t('problems.versionHistory.*')` 引用
  - `management/src/i18n/locales/zh-CN/modules/problems.ts` - 现有翻译结构

  **Acceptance Criteria**:
  - [ ] 所有组件中引用的键在 locale 中存在
  - [ ] 现有键未被修改或删除
  - [ ] 新增键有中文翻译值

  **QA Scenarios**:
  ```
  Scenario: 验证 zh-CN 翻译键完整
    Tool: Bash (node)
    Preconditions: 无
    Steps:
      1. cd management && node -e "
         const locale = require('./src/i18n/locales/zh-CN/modules/problems.ts');
         const keys = ['description','compareWith','noVersions','createInitial',
           'versionDetails','compareVersions','noChanges','oldValue','newValue',
           'rollbackTitle','rollbackConfirm','rollbackReasonPlaceholder','rollbackButton',
           'loadError','loadDetailError','compareError','rollbackError',
           'createInitialSuccess','alreadyHasVersions','createInitialError',
           'action.CREATE','action.UPDATE','action.ROLLBACK'];
         const missing = keys.filter(k => !k.split('.').reduce((o,p) => o?.[p], locale));
         console.log(missing.length === 0 ? 'PASS' : 'MISSING: ' + missing.join(', '));
      "
    Expected Result: PASS
    Evidence: .sisyphus/evidence/task-5-zh-cn-check.txt
  ```

  **Commit**: YES（与 T6 合并提交）
  - Message: `feat(i18n): add missing versionHistory translations`
  - Files: `management/src/i18n/locales/zh-CN/modules/problems.ts`

- [x] 6. **前端 i18n - 补充 en-US 缺失翻译键**

  **What to do**:
  - 编辑 `management/src/i18n/locales/en-US/modules/problems.ts`
  - 在 `versionHistory` 命名空间下补充与 T5 完全对应的英文翻译键
  - 英文翻译：
    ```typescript
    versionHistory: {
      title: "Version History",
      noHistory: "No version history yet",
      version: "Version",
      author: "Author",
      changes: "Changes",
      restore: "Restore",
      restoreSuccess: "Restored to this version",
      viewDiff: "View Diff",
      description: "View and manage problem version history",
      compareWith: "Compare with version {version}",
      noVersions: "No versions available",
      createInitial: "Create Initial Version",
      by: "by",
      versionDetails: "Version Details",
      compareVersions: "Compare Versions",
      noChanges: "No changes",
      oldValue: "Old Value",
      newValue: "New Value",
      rollbackTitle: "Rollback to Version {version}",
      rollbackConfirm: "Are you sure you want to rollback to version {version}? This will create a new version record.",
      rollbackReasonPlaceholder: "Enter rollback reason (optional)",
      rollbackButton: "Confirm Rollback",
      loadError: "Failed to load version history",
      loadDetailError: "Failed to load version details",
      compareError: "Version comparison failed",
      rollbackError: "Rollback failed",
      rollbackSuccess: "Successfully rolled back to version {version}",
      createInitialSuccess: "Initial version created successfully",
      alreadyHasVersions: "This problem already has version records",
      createInitialError: "Failed to create initial version",
      action: {
        CREATE: "Created",
        UPDATE: "Updated",
        ROLLBACK: "Rolled Back"
      }
    }
    ```
  - 保持与 zh-CN 完全相同的键结构

  **Must NOT do**:
  - 不要修改 console 前端的 i18n 文件
  - 不要遗漏任何 zh-CN 中新增的键

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: T10, T12
  - **Blocked By**: None

  **References**:
  - `management/src/i18n/locales/zh-CN/modules/problems.ts`（T5 修改后）- 确保键完全对应
  - `management/src/i18n/locales/en-US/modules/problems.ts` - 现有英文翻译结构

  **Acceptance Criteria**:
  - [ ] 英文键结构与 zh-CN 完全一致
  - [ ] 所有键有英文翻译值

  **QA Scenarios**:
  ```
  Scenario: 验证 en-US 与 zh-CN 键一致
    Tool: Bash (node)
    Preconditions: 无
    Steps:
      1. cd management && node -e "
         const zh = require('./src/i18n/locales/zh-CN/modules/problems.ts');
         const en = require('./src/i18n/locales/en-US/modules/problems.ts');
         const zhKeys = Object.keys(zh.versionHistory);
         const enKeys = Object.keys(en.versionHistory);
         const missing = zhKeys.filter(k => !enKeys.includes(k));
         console.log(missing.length === 0 ? 'PASS' : 'MISSING: ' + missing.join(', '));
      "
    Expected Result: PASS
    Evidence: .sisyphus/evidence/task-6-en-us-check.txt
  ```

  **Commit**: YES（与 T5 合并提交）
  - Message: `feat(i18n): add missing versionHistory translations`
  - Files: `management/src/i18n/locales/en-US/modules/problems.ts`

- [x] 7. **ProblemVersionService 接口 + 实现**

  **What to do**:
  - 创建 `backend-spring/src/main/java/com/ulticode/modules/problem/service/ProblemVersionService.java`
    - 接口方法：
      - `VersionsResponseVO listVersions(Long problemId, Integer page, Integer limit)` - 分页查询版本列表（返回结构匹配前端 `VersionsResponse`：{ versions: List<ProblemVersionVO>, pagination: {...} }）
      - `ProblemVersionDetailVO getVersionDetail(Long problemId, String versionId)` - 查询版本详情
      - `VersionWithDiffVO compareVersions(Long problemId, String fromVersionId, String toVersionId)` - 对比两个版本
      - `ProblemVersionVO rollbackToVersion(Long problemId, String versionId, String reason, String operatorId)` - 回滚到指定版本
      - `ProblemVersionVO createInitialVersion(Long problemId, String operatorId)` - 创建初始版本（v1）
      - `ProblemVersionVO createVersion(Long problemId, String changeType, String changeSummary, String operatorId)` - 内部方法：创建新版本快照
  - 创建 `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemVersionServiceImpl.java`
    - 实现上述所有方法
    - `createVersion` 核心逻辑：
      1. 查询当前 Problem 完整数据（含 ProblemDetail、Examples、Languages、Tags）
      2. 序列化为 JSON 字符串存入 `snapshot_json`
      3. 查询当前最大 `version_number`，+1 作为新版本号
      4. 插入 `problem_versions` 记录
    - `rollbackToVersion` 核心逻辑：
      1. 查询目标版本快照 JSON
      2. 反序列化 JSON 为 Problem 对象
      3. 更新当前 Problem 数据为目标版本数据
      4. 创建新版本（changeType="ROLLBACK"），快照为回滚后的数据
    - `compareVersions` 核心逻辑：
      1. 查询两个版本的 `snapshot_json`
      2. 反序列化为 Map 结构
      3. 递归对比字段差异，返回 `VersionDiffVO[]`
      4. 仅返回有变化的字段
    - 使用 `ProblemService` 或 `ProblemMapper` 查询 Problem 完整数据
    - 使用 `ObjectMapper`（Jackson）进行 JSON 序列化/反序列化
    - 事务控制：所有写操作在 `@Transactional` 中

  **Must NOT do**:
  - 不要复用 AuditService（独立 Service）
  - 不要在回滚时删除历史版本（创建新的 ROLLBACK 版本）
  - 不要修改 Problem 的乐观锁 version 字段（技术字段）
  - 不要遗漏关联数据的快照（ProblemDetail、Examples、Languages、Tags）

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO（需要 T1-T4 完成）
  - **Parallel Group**: Wave 2
  - **Blocks**: T8, T9, T11
  - **Blocked By**: T1, T2, T3, T4

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/problem/service/ProblemService.java` - 参考 Service 接口风格
  - `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java` - 参考 Service 实现风格，了解如何查询 Problem 完整数据
  - `backend-spring/src/main/java/com/ulticode/modules/admin/service/AuditService.java` - 参考 JSON 快照模式
  - `management/src/api/admin/problems.ts:369-412` - 前端期望的 API 行为

  **Acceptance Criteria**:
  - [ ] Service 接口和实现文件存在
  - [ ] 所有方法实现完整
  - [ ] `createVersion` 能正确序列化题目数据为 JSON
  - [ ] `compareVersions` 能正确计算字段差异
  - [ ] `rollbackToVersion` 创建 ROLLBACK 类型新版本

  **QA Scenarios**:
  ```
  Scenario: 验证 Service 编译通过
    Tool: Bash
    Preconditions: 无
    Steps:
      1. cd backend-spring && ./mvnw compile -pl . -am -q
      2. 检查无编译错误
    Expected Result: BUILD SUCCESS
    Evidence: .sisyphus/evidence/task-7-service-compile.txt

  Scenario: 验证 createVersion 逻辑
    Tool: Bash
    Preconditions: 数据库已启动，problem_versions 表存在
    Steps:
      1. 启动后端服务（或运行集成测试）
      2. 调用 createVersion 方法创建版本
      3. 查询数据库：SELECT version_number, change_type, snapshot_json FROM problem_versions WHERE problem_id = ?
    Expected Result: version_number=1, change_type="CREATE", snapshot_json 不为空
    Evidence: .sisyphus/evidence/task-7-create-version.txt
  ```

  **Commit**: YES（与 T8 合并提交）
  - Message: `feat(problem): implement version history service`
  - Files: `backend-spring/.../service/ProblemVersionService.java`, `.../impl/ProblemVersionServiceImpl.java`

- [x] 8. **AdminProblemVersionController - 5 个 REST API 端点**

  **What to do**:
  - 创建 `backend-spring/src/main/java/com/ulticode/modules/problem/controller/AdminProblemVersionController.java`
  - 端点列表（严格匹配前端 API）：
    1. `GET /admin/problems/{id}/versions`
       - Query: `page` (default=1), `limit` (default=20)
       - 调用 `problemVersionService.listVersions(problemId, page, limit)`
       - 返回 `Result<VersionsResponseVO>`（结构：{ versions: [...], pagination: { total, page, limit, totalPages } }）
    2. `GET /admin/problems/{id}/versions/{versionId}`
       - 调用 `problemVersionService.getVersionDetail(problemId, versionId)`
       - 返回 `Result<ProblemVersionDetailVO>`
    3. `GET /admin/problems/{id}/versions/{fromVersionId}/diff/{toVersionId}`
       - 调用 `problemVersionService.compareVersions(problemId, fromVersionId, toVersionId)`
       - 返回 `Result<VersionWithDiffVO>`
    4. `POST /admin/problems/{id}/versions/{versionId}/rollback`
       - Body: `{ reason?: String }`
       - 从 SecurityContext 获取当前用户 ID
       - 调用 `problemVersionService.rollbackToVersion(problemId, versionId, reason, operatorId)`
       - 返回 `Result<ProblemVersionVO>`
    5. `POST /admin/problems/{id}/versions/create-initial`
       - 从 SecurityContext 获取当前用户 ID
       - 调用 `problemVersionService.createInitialVersion(problemId, operatorId)`
       - 返回 `Result<ProblemVersionVO>`
  - 参考现有 Controller 风格（如 `AdminProblemController.java`）
  - 使用 `@RestController` + `@RequestMapping("/admin/problems")`
  - 权限注解：使用 `@PreAuthorize` 或项目现有的权限注解

  **Must NOT do**:
  - 不要修改前端 API 客户端（前端已定义好）
  - 不要修改 URL 路径（必须完全匹配前端）
  - 不要遗漏权限控制

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（依赖 T4 VO 和 T7 Service）
  - **Parallel Group**: Wave 2
  - **Blocks**: T11, T13
  - **Blocked By**: T4, T7

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminProblemController.java` - 参考 Controller 风格、权限注解、Result 包装
  - `management/src/api/admin/problems.ts:369-412` - 前端 API 端点定义（URL、方法、参数）
  - `backend-spring/src/main/java/com/ulticode/common/Result.java` - Result 包装类用法

  **Acceptance Criteria**:
  - [ ] 5 个端点全部实现
  - [ ] URL 路径与前端 API 完全一致
  - [ ] 响应类型匹配前端 TypeScript 类型
  - [ ] 有权限控制

  **QA Scenarios**:
  ```
  Scenario: 验证端点可访问（需登录）
    Tool: Bash (curl)
    Preconditions: 后端服务已启动，已登录获取 cookie
    Steps:
      1. curl -s "http://localhost:9001/admin/problems/1/versions?page=1&limit=10" -b /tmp/cookies.txt | jq '.code'
      2. 期望返回 0（成功）或业务错误码，不是 404
    Expected Result: code == 0 或 code == 业务错误码（非 404）
    Evidence: .sisyphus/evidence/task-8-endpoints.txt
  ```

  **Commit**: YES（与 T7 合并提交）
  - Message: `feat(problem): add admin version history controller`
  - Files: `backend-spring/.../controller/AdminProblemVersionController.java`

- [x] 9. **ProblemServiceImpl 集成版本触发**

  **What to do**:
  - 修改 `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java`
  - 在 `createProblem()` 方法成功后：
    - 注入 `ProblemVersionService`
    - 调用 `problemVersionService.createInitialVersion(problem.getId(), operatorId)`
    - 使用 `@Transactional` 确保原子性
  - 在 `updateProblem()` 方法成功后：
    - 调用 `problemVersionService.createVersion(problemId, "UPDATE", changeSummary, operatorId)`
    - `changeSummary` 可生成简要描述（如 "Updated title and difficulty"）或留空
  - 注意：如果 `createProblem()` 本身有事务，确保版本创建在同一个事务中
  - 如果 `updateProblem()` 修改了内容相关的数据（summary, examples, languages, tags），也需要触发版本创建（检查是否有单独的内容更新方法）

  **Must NOT do**:
  - 不要破坏现有 createProblem/updateProblem 的功能
  - 不要在没有事务保护的情况下创建版本
  - 不要在异常时创建版本（事务回滚会同时回滚版本记录）

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（依赖 T7 Service）
  - **Parallel Group**: Wave 2
  - **Blocks**: T11, T13
  - **Blocked By**: T7

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java` - 找到 createProblem 和 updateProblem 方法，确定注入点和调用位置
  - `backend-spring/src/main/java/com/ulticode/modules/problem/service/ProblemVersionService.java` - 确认可调用的方法签名

  **Acceptance Criteria**:
  - [ ] createProblem 成功后自动创建 v1
  - [ ] updateProblem 成功后自动创建新版本
  - [ ] 版本创建与业务操作在同一事务中

  **QA Scenarios**:
  ```
  Scenario: 验证创建题目后自动产生版本
    Tool: Bash (curl + mysql)
    Preconditions: 数据库已启动，后端服务已启动
    Steps:
      1. 创建新题目：curl -s -X POST http://localhost:9001/admin/problems -H "Content-Type: application/json" -d '{"title":"Test","slug":"test","difficulty":"EASY"}' -b /tmp/cookies.txt | jq '.data.id'
      2. 查询数据库：docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode -e "SELECT version_number, change_type FROM problem_versions WHERE problem_id = {newId}"
    Expected Result: version_number=1, change_type="CREATE"
    Evidence: .sisyphus/evidence/task-9-auto-version.txt

  Scenario: 验证更新题目后自动产生版本
    Tool: Bash (curl + mysql)
    Preconditions: 已有题目和版本记录
    Steps:
      1. 更新题目：curl -s -X PATCH http://localhost:9001/admin/problems/{id} -H "Content-Type: application/json" -d '{"title":"Updated Title"}' -b /tmp/cookies.txt
      2. 查询数据库版本记录数量：SELECT COUNT(*) FROM problem_versions WHERE problem_id = {id}
    Expected Result: 数量增加 1
    Evidence: .sisyphus/evidence/task-9-update-version.txt
  ```

  **Commit**: YES
  - Message: `feat(problem): integrate version creation on problem changes`
  - Files: `backend-spring/.../service/impl/ProblemServiceImpl.java`

- [x] 10. **前端组件修复 - 统一 i18n 键命名**

  **What to do**:
  - 编辑 `management/src/components/problems/VersionHistoryTimeline.vue`
  - 检查所有 `t('problems.versionHistory.*')` 调用
  - 修复键名不一致：
    - 如果组件使用 `t('problems.versionHistory.by')` 但 locale 中使用 `author`，统一改为 `author`
    - 或者如果 locale 中需要添加 `by`，则添加（与 Metis 建议一致：统一用 `author`）
  - 检查是否有其他键名不一致的情况
  - 确保组件中引用的所有键在 zh-CN 和 en-US 中都存在

  **Must NOT do**:
  - 不要修改组件的业务逻辑（只修复 i18n 键引用）
  - 不要修改组件的 props 或 emits

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（依赖 T5, T6）
  - **Parallel Group**: Wave 2
  - **Blocks**: T12
  - **Blocked By**: T5, T6

  **References**:
  - `management/src/components/problems/VersionHistoryTimeline.vue` - 检查所有 t() 调用
  - `management/src/i18n/locales/zh-CN/modules/problems.ts` - 确认键名
  - `management/src/i18n/locales/en-US/modules/problems.ts` - 确认键名

  **Acceptance Criteria**:
  - [ ] 组件中所有 t() 引用的键在 locale 中存在
  - [ ] 键名统一（by/author 问题解决）

  **QA Scenarios**:
  ```
  Scenario: 验证组件中无缺失 i18n 键
    Tool: Bash (grep + node)
    Preconditions: T5, T6 已完成
    Steps:
      1. grep -oP "t\('problems\.versionHistory\.\K[^']+" management/src/components/problems/VersionHistoryTimeline.vue | sort -u > /tmp/used-keys.txt
      2. node -e "const zh = require('./management/src/i18n/locales/zh-CN/modules/problems.ts'); const keys = require('fs').readFileSync('/tmp/used-keys.txt','utf8').split('\n').filter(Boolean); const missing = keys.filter(k => !k.split('.').reduce((o,p) => o?.[p], zh)); console.log(missing.length === 0 ? 'PASS' : 'MISSING: ' + missing.join(', '));"
    Expected Result: PASS
    Evidence: .sisyphus/evidence/task-10-component-keys.txt
  ```

  **Commit**: YES
  - Message: `fix(i18n): unify versionHistory key references in component`
  - Files: `management/src/components/problems/VersionHistoryTimeline.vue`

- [x] 11. **后端单元测试**

  **What to do**:
  - 创建 `backend-spring/src/test/java/com/ulticode/modules/problem/service/ProblemVersionServiceTest.java`
  - 测试覆盖以下场景：
    1. `createInitialVersion` - 创建初始版本（v1）
       - 验证 versionNumber=1, changeType="CREATE"
       - 验证 snapshot_json 包含题目数据
    2. `createVersion` - 创建更新版本
       - 验证 versionNumber 递增
       - 验证 changeType="UPDATE"
    3. `listVersions` - 分页查询版本列表
       - 验证分页参数生效
       - 验证按 versionNumber DESC 排序
    4. `getVersionDetail` - 查询版本详情
       - 验证返回完整快照数据
    5. `compareVersions` - 版本对比
       - 验证仅返回有变化的字段
       - 验证 oldValue/newValue 正确
    6. `rollbackToVersion` - 回滚
       - 验证创建 ROLLBACK 类型新版本
       - 验证当前题目数据被更新
  - 使用 `@SpringBootTest` 或 `@MybatisPlusTest`
  - 使用 Testcontainers（MySQL）或 H2 内存数据库
  - 参考现有测试风格（如 `ProblemServiceTest.java`）

  **Must NOT do**:
  - 不要仅测试 happy path（至少覆盖 1 个异常场景）
  - 不要遗漏关键业务逻辑测试
  - 不要修改生产代码来适配测试（测试应验证现有行为）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（依赖 T7-T9）
  - **Parallel Group**: Wave 3
  - **Blocks**: T13
  - **Blocked By**: T7, T8, T9

  **References**:
  - `backend-spring/src/test/java/com/ulticode/modules/problem/service/` - 现有测试文件风格
  - `backend-spring/src/test/resources/` - 检查现有测试配置文件（如 `application.yml` 或 `bootstrap.yml`）
  - JUnit 5 + Spring Boot Test 文档

  **Acceptance Criteria**:
  - [ ] 测试文件存在
  - [ ] 至少 6 个测试方法（每个 Service 方法至少 1 个）
  - [ ] 全部测试通过：`./mvnw test -Dtest="ProblemVersionServiceTest"`

  **QA Scenarios**:
  ```
  Scenario: 运行单元测试
    Tool: Bash
    Preconditions: 无
    Steps:
      1. cd backend-spring && ./mvnw test -Dtest="ProblemVersionServiceTest" -q
      2. 检查测试结果
    Expected Result: Tests run: N, Failures: 0, Errors: 0
    Evidence: .sisyphus/evidence/task-11-unit-tests.txt
  ```

  **Commit**: YES
  - Message: `test(problem): add version history unit tests`
  - Files: `backend-spring/src/test/java/.../ProblemVersionServiceTest.java`

- [x] 12. **前端 i18n 键完整性验证脚本**

  **What to do**:
  - 创建验证脚本（如 `management/scripts/validate-i18n.js` 或内联在 package.json scripts 中）
  - 脚本逻辑：
    1. 扫描 `management/src/components/` 中所有 `.vue` 文件
    2. 提取所有 `t('problems.versionHistory.*')` 引用
    3. 与 `zh-CN/modules/problems.ts` 和 `en-US/modules/problems.ts` 中的键对比
    4. 输出缺失的键（如果有）
  - 将脚本添加到 `package.json` 的 scripts 中（如 `"validate:i18n": "node scripts/validate-i18n.js"`）
  - 或者作为 vitest 测试用例实现

  **Must NOT do**:
  - 不要仅检查 versionHistory 命名空间（也可以扩展检查其他命名空间，但非必需）
  - 不要引入复杂依赖（纯 Node.js 实现）

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES（依赖 T5, T6, T10）
  - **Parallel Group**: Wave 3
  - **Blocks**: T13
  - **Blocked By**: T5, T6, T10

  **References**:
  - `management/package.json` - 现有 scripts 结构
  - `management/src/components/problems/VersionHistoryTimeline.vue` - 待扫描的组件

  **Acceptance Criteria**:
  - [ ] 验证脚本存在且可运行
  - [ ] 脚本报告无缺失键

  **QA Scenarios**:
  ```
  Scenario: 运行 i18n 验证脚本
    Tool: Bash
    Preconditions: 无
    Steps:
      1. cd management && pnpm run validate:i18n（或 node scripts/validate-i18n.js）
      2. 检查输出
    Expected Result: 无缺失键，输出 "All i18n keys found" 或类似
    Evidence: .sisyphus/evidence/task-12-i18n-validation.txt
  ```

  **Commit**: YES
  - Message: `test(i18n): add i18n key completeness validation`
  - Files: `management/scripts/validate-i18n.js`, `management/package.json`

- [x] 13. **集成验证 - 端到端测试**

  **What to do**:
  - 启动完整环境（MySQL + Redis + 后端 + 管理端前端）
  - 验证以下完整流程：
    1. 创建题目 → 检查是否自动产生 v1
    2. 更新题目 → 检查是否自动产生 v2
    3. 访问版本历史页面 → 检查是否正常显示（无 404，无 i18n 警告）
    4. 查看版本详情 → 检查数据完整
    5. 对比两个版本 → 检查差异显示
    6. 回滚到 v1 → 检查是否创建 ROLLBACK 版本，题目数据是否恢复
  - 记录浏览器控制台是否有 i18n 缺失警告
  - 使用 curl 或 Playwright 进行验证

  **Must NOT do**:
  - 不要跳过任何步骤
  - 不要仅验证 API 而忽略前端

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO（需要所有前置任务完成）
  - **Parallel Group**: Wave 3
  - **Blocks**: F1-F4
  - **Blocked By**: T8, T9, T11, T12

  **References**:
  - `AGENTS.md` - 项目启动命令和端口参考
  - `management/src/components/problems/VersionHistoryTimeline.vue` - 前端组件行为

  **Acceptance Criteria**:
  - [ ] 创建题目后数据库有 v1 记录
  - [ ] 更新题目后数据库有新版本记录
  - [ ] API 端点全部 200
  - [ ] 前端无 i18n 缺失警告

  **QA Scenarios**:
  ```
  Scenario: 端到端验证完整流程
    Tool: Bash (curl + 数据库查询)
    Preconditions: 所有服务已启动
    Steps:
      1. 登录获取 cookie
      2. 创建题目，记录返回的 id
      3. 查询数据库确认 v1 存在
      4. 更新题目
      5. 查询数据库确认 v2 存在
      6. 获取版本列表：curl /admin/problems/{id}/versions
      7. 获取版本详情：curl /admin/problems/{id}/versions/{v1Id}
      8. 对比版本：curl /admin/problems/{id}/versions/{v1Id}/diff/{v2Id}
      9. 回滚：curl -X POST /admin/problems/{id}/versions/{v1Id}/rollback
      10. 查询数据库确认 ROLLBACK 版本存在
    Expected Result: 所有步骤返回 200，数据库记录正确
    Evidence: .sisyphus/evidence/task-13-integration.txt
  ```

  **Commit**: NO（验证任务，不单独提交）

---

## Final Verification Wave

> 4 个审查代理并行运行。全部通过后才向用户呈现结果并获取显式确认。

- [x] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file, curl endpoint, run command). For each "Must NOT Have": search codebase for forbidden patterns. Check evidence files exist in `.sisyphus/evidence/`. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [x] F2. **Code Quality Review** — `unspecified-high`
  Run `cd backend-spring && ./mvnw test` + `cd management && pnpm lint`. Review changed files for: `as any`/`@ts-ignore`, empty catches, console.log in prod, unused imports. Check AI slop patterns.
  Output: `Build [PASS/FAIL] | Lint [PASS/FAIL] | Tests [N pass/N fail] | Files [N clean/N issues] | VERDICT`

- [x] F3. **Real Manual QA** — `unspecified-high`
  Start from clean state. Execute EVERY QA scenario from EVERY task — follow exact steps, capture evidence. Test cross-task integration. Save to `.sisyphus/evidence/final-qa/`.
  Output: `Scenarios [N/N pass] | Integration [N/N] | Edge Cases [N tested] | VERDICT`

- [x] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual diff. Verify 1:1 — everything in spec was built, nothing beyond spec was built. Detect cross-task contamination.
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

- **T1-T4**: `feat(problem): add problem_version database table and entity` - 迁移文件、实体、Mapper、VO
- **T5-T6**: `feat(i18n): add missing versionHistory translations` - zh-CN, en-US locale files
- **T7-T9**: `feat(problem): implement version history service and controller` - Service、Controller、集成触发
- **T10**: `fix(i18n): unify versionHistory key naming` - 组件修复
- **T11**: `test(problem): add version history unit tests` - 测试文件
- **T12**: `test(i18n): add i18n key completeness validation` - 验证脚本

---

## Success Criteria

### Verification Commands

```bash
# 1. 数据库迁移
# 运行后检查表是否存在
docker exec ulticode-mysql mysql -u ulticode -pulticode ulticode -e "SHOW TABLES LIKE 'problem_versions'"
# Expected: problem_versions

# 2. API 端点验证
# 登录获取 cookie
curl -s -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c /tmp/cookies.txt

# 测试版本列表
curl -s "http://localhost:9001/admin/problems/1/versions?page=1&limit=10" \
  -b /tmp/cookies.txt | jq '.data.records | length > 0'
# Expected: true (如果已有版本)

# 3. 前端 i18n 验证
cd management && pnpm test
# Expected: PASS

# 4. 后端测试
cd backend-spring && ./mvnw test -Dtest="ProblemVersion*"
# Expected: BUILD SUCCESS
```

### Final Checklist
- [ ] 数据库表 `problem_versions` 存在
- [ ] 5 个 API 端点全部 200
- [ ] 创建/更新题目后 `problem_versions` 有记录
- [ ] 前端无 i18n 缺失警告
- [ ] 后端单元测试全部通过
- [ ] 前端 lint/test 通过
