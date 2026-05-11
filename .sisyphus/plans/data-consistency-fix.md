# 前后端数据一致性修复计划

## TL;DR

> **核心问题**: 前端编辑题目描述时，PATCH 请求缺少 `examples`, `tags`, `languages` 字段，且 `difficulty` 大小写不匹配导致 400 错误。
>
> **修复范围**: 前端 `EditDescriptionView.vue` 补充缺失字段并转换数据格式，后端 `ProblemServiceImpl.java` 添加对缺失字段的处理逻辑。
>
> **预计工作量**: 中等（Medium）
> **并行执行**: YES - 2 个波次
> **关键路径**: 前端修复 → 后端修复 → 集成验证

---

## Context

### 原始请求
用户要求调查并修复前后端数据不一致问题，确保前端能正确获取、展示和保存所有题目描述数据。

### 调查发现
1. **后端返回完整** ✅: `DescriptionDataVO` 正确返回 `tags`, `examples`, `detail` 等所有字段
2. **前端接收完整** ✅: `formattedProblem` 正确映射所有字段
3. **前端提交缺失** ❌: `handleSubmit` 只发送 8 个字段，缺少 `examples`, `tags`, `languages`
4. **后端处理缺失** ❌: `updateProblemDetail()` 只处理 4 个字段，忽略 `examples`, `tags`, `languages`
5. **难度值不匹配** ❌: 前端发送 `EASY`，后端期望 `Easy`

### Metis 审查
- 已识别边界：不修改数据库 schema，不改动其他 API 消费者
- 已识别风险：`TagsSelector` 返回 IDs 而非 labels，需要转换
- 已识别验收标准：需要验证错误处理和空值处理

---

## Work Objectives

### Core Objective
修复前后端数据流，使题目描述编辑功能能完整保存所有字段（examples, tags, languages, difficulty）。

### Concrete Deliverables
- 前端 `EditDescriptionView.vue` 的 `handleSubmit` 发送完整字段
- 后端 `ProblemServiceImpl.java` 处理 `examples`, `tags`, `languages`
- 难度值大小写转换逻辑
- 验证测试通过

### Definition of Done
- [ ] PATCH /admin/problems/{id} 返回 200，所有字段正确保存
- [ ] 刷新页面后，修改的 examples, tags, languages 正确显示
- [ ] 难度值修改不再导致 400 错误
- [ ] 现有字段（slug, title, summary 等）不受影响

### Must Have
- examples, tags, languages 能正确保存和读取
- 难度值大小写正确转换
- 空数组和 null 值正确处理

### Must NOT Have (Guardrails)
- 不修改数据库 schema
- 不改变其他 API 端点行为
- 不引入新的依赖
- 不修改 `TagsSelector.vue` 组件（除非必要）

---

## Verification Strategy

### Test Decision
- **Infrastructure exists**: YES (backend has tests, frontend has vitest)
- **Automated tests**: Tests-after (先修复后补测试)
- **Framework**: backend (JUnit), frontend (vitest)

### QA Policy
每个任务包含 Agent-Executed QA Scenarios，验证实际行为。

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately - 前端修复):
├── Task 1: 修复 handleSubmit 缺少字段 [quick]
└── Task 2: 修复难度值大小写转换 [quick]

Wave 2 (After Wave 1 - 后端修复):
├── Task 3: 添加 examples 处理逻辑 [unspecified-high]
├── Task 4: 添加 tags 处理逻辑 [unspecified-high]
└── Task 5: 添加 languages 处理逻辑 [unspecified-high]

Wave FINAL (After ALL tasks - 集成验证):
├── Task F1: 端到端测试验证 [unspecified-high]
└── Task F2: 回归测试 [unspecified-high]
```

### Dependency Matrix
- **Task 1**: - → Task F1
- **Task 2**: - → Task F1
- **Task 3**: - → Task F1
- **Task 4**: - → Task F1
- **Task 5**: - → Task F1
- **F1**: Task 1-5 → F2
- **F2**: F1 → -

### Agent Dispatch Summary
- **Wave 1**: Task 1 → `quick`, Task 2 → `quick`
- **Wave 2**: Task 3 → `unspecified-high`, Task 4 → `unspecified-high`, Task 5 → `unspecified-high`
- **Wave FINAL**: F1 → `unspecified-high`, F2 → `unspecified-high`

---

## TODOs

- [x] 1. 修复前端 handleSubmit 缺少字段

  **What to do**:
  - 在 `EditDescriptionView.vue` 的 `handleSubmit` 函数中，补充 `examples`, `tags`, `languages` 字段
  - `examples` 需要 `JSON.stringify` 转换为字符串
  - `tags` 需要从 IDs 转换为 labels（通过 `allTags` 查找）
  - `languages` 直接从 formData 获取

  **Must NOT do**:
  - 不修改 `TagsSelector.vue` 组件
  - 不改变现有字段的处理逻辑

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
  - **Reason**: 前端字段补充，逻辑简单直接

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Task 2)
  - **Blocks**: Task F1
  - **Blocked By**: None

  **References**:
  - `management/src/views/problems/edit/EditDescriptionView.vue:24-47` - handleSubmit 当前逻辑
  - `management/src/stores/admin/problems.ts:178-192` - UpdateProblemDto 接口定义
  - `management/src/views/problems/components/TagsSelector.vue` - 确认返回的是 IDs

  **Acceptance Criteria**:
  - [ ] handleSubmit 发送的 payload 包含 `examples`, `tags`, `languages`
  - [ ] `examples` 是 JSON 字符串格式
  - [ ] `tags` 是 label 字符串数组

  **QA Scenarios**:
  ```
  Scenario: 提交完整数据
    Tool: Bash (curl)
    Preconditions: 已登录，获取 CSRF token
    Steps:
      1. 发送 PATCH 包含所有字段
      2. 验证返回 200，code: 0
    Expected Result: 请求成功，无 400 错误
    Evidence: .sisyphus/evidence/task-1-submit-all-fields.json
  ```

  **Commit**: YES
  - Message: `fix(frontend): add missing fields to problem update submit`
  - Files: `management/src/views/problems/edit/EditDescriptionView.vue`

- [x] 2. 修复前端难度值大小写转换

  **What to do**:
  - 在 `handleSubmit` 中，将 `formData.difficulty` 从 `EASY`/`MEDIUM`/`HARD` 转换为 `Easy`/`Medium`/`Hard`
  - 或者在 `formattedProblem` 中直接转换，确保 form 中存储的就是 Title Case

  **Must NOT do**:
  - 不修改后端验证规则
  - 不改变 Difficulty 枚举定义

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []
  - **Reason**: 简单的字符串转换逻辑

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Task 1)
  - **Blocks**: Task F1
  - **Blocked By**: None

  **References**:
  - `management/src/views/problems/edit/EditDescriptionView.vue:30` - difficulty 当前发送逻辑
  - `backend-spring/src/main/java/com/ulticode/modules/problem/dto/UpdateProblemDTO.java:26` - 后端验证规则

  **Acceptance Criteria**:
  - [ ] 发送的 difficulty 值为 Title Case ("Easy"/"Medium"/"Hard")
  - [ ] 后端验证通过，不返回 400

  **QA Scenarios**:
  ```
  Scenario: 提交不同难度值
    Tool: Bash (curl)
    Preconditions: 已登录
    Steps:
      1. 发送 PATCH difficulty="Easy"
      2. 发送 PATCH difficulty="Medium"
      3. 发送 PATCH difficulty="Hard"
    Expected Result: 全部返回 200
    Evidence: .sisyphus/evidence/task-2-difficulty-case.json
  ```

  **Commit**: YES
  - Message: `fix(frontend): convert difficulty case for backend compatibility`
  - Files: `management/src/views/problems/edit/EditDescriptionView.vue`

- [x] 3. 后端添加 examples 处理逻辑

  **What to do**:
  - 在 `ProblemServiceImpl.java` 的 `updateProblemDetail` 方法中，添加对 `examples` 字段的处理
  - `UpdateProblemDTO.examples` 是 JSON 字符串（数组格式），需要解析为 `List<ExampleData>`
  - 删除该题目旧的所有 `ProblemExample` 记录（通过 `problemExampleMapper`）
  - 为每个 example 创建新的 `ProblemExample` 实体并保存：
    - `id` = 生成新 UUID
    - `problemId` = 当前题目 ID
    - `exampleOrder` = 数组索引 + 1
    - `inputText` = example.inputText
    - `outputText` = example.outputText
    - `explanation` = example.explanation
    - `inputs` = example.inputs (JSON 字符串，可为 null)
  - 参考 `buildExamples()` 方法的逆操作

  **Must NOT do**:
  - 不修改数据库 schema
  - 不修改 `UpdateProblemDTO`（已包含该字段）
  - 不在 `ProblemDetail` 中存储 examples（`ProblemDetail` 没有 `examplesJson` 字段）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
  - **Reason**: 需要理解后端数据流和 MyBatis-Plus 操作

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Task 4, 5)
  - **Blocks**: Task F1
  - **Blocked By**: None

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java:421-458` - updateProblemDetail 当前逻辑
  - `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java:276-304` - buildExamples() 方法（读取 examples 的参考实现）
  - `backend-spring/src/main/java/com/ulticode/modules/problem/entity/ProblemExample.java` - 实体类字段
  - `backend-spring/src/main/java/com/ulticode/modules/problem/mapper/ProblemExampleMapper.java` - Mapper 接口

  **Acceptance Criteria**:
  - [ ] `updateProblemDetail` 处理 `dto.getExamples()`
  - [ ] 旧 examples 被删除，新 examples 被创建
  - [ ] 数据正确保存到 `problem_examples` 表

  **QA Scenarios**:
  ```
  Scenario: 验证 examples 保存
    Tool: Bash (curl)
    Steps:
      1. 发送 PATCH 包含 examples
      2. 发送 GET 验证 examples 正确返回
    Expected Result: examples 数据一致
    Evidence: .sisyphus/evidence/task-3-examples-persist.json
  ```

  **Commit**: YES
  - Message: `fix(backend): process examples in problem update`
  - Files: `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java`

- [x] 4. 后端添加 tags 处理逻辑

  **What to do**:
  - 在 `ProblemServiceImpl.java` 中添加 tags 的更新逻辑
  - 通过 `ProblemTagMapper` 根据 label 查找 tagId（需要先查询 tag 表确认 label 存在）
  - 如果标签不存在，抛出 `BusinessException` 返回 400 错误
  - 删除旧的 tag 关联（通过 `ProblemTagRelationMapper` 按 problemId 删除）
  - 创建新的 tag 关联（插入 `ProblemTagRelation` 记录）
  - 使用 `ProblemTagRelationMapper` 操作关联表

  **Must NOT do**:
  - 不修改 tag 表结构
  - 不创建新 tag（只关联已有 tag）

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
  - **Reason**: 需要处理关联表 CRUD

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Task 3, 5)
  - **Blocks**: Task F1
  - **Blocked By**: None

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/problem/mapper/ProblemTagRelationMapper.java` - Mapper 接口
  - `backend-spring/src/main/java/com/ulticode/modules/problem/entity/ProblemTagRelation.java` - 关联实体

  **Acceptance Criteria**:
  - [ ] 旧的 tag 关联被删除
  - [ ] 新的 tag 关联被创建
  - [ ] 不存在的 tag label 返回 400 错误

  **QA Scenarios**:
  ```
  Scenario: 验证 tags 保存
    Tool: Bash (curl)
    Steps:
      1. 发送 PATCH 包含 tags=["array", "dp"]
      2. 发送 GET 验证 tags 正确返回
      3. 发送 PATCH tags=[] 验证清空
    Expected Result: tags 数据正确更新
    Evidence: .sisyphus/evidence/task-4-tags-persist.json
  ```

  **Commit**: YES
  - Message: `fix(backend): process tags in problem update`
  - Files: `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java`

- [x] 5. 后端添加 languages 处理逻辑

  **What to do**:
  - 在 `ProblemServiceImpl.java` 中添加 languages 的更新逻辑
  - 通过 `ProblemLanguageMapper` 根据 label 查找 languageId（或确认 label 存在）
  - 如果语言不存在，抛出 `BusinessException` 返回 400 错误
  - 删除旧的 language 关联（通过 `ProblemLanguageMapper` 按 problemId 删除）
  - 创建新的 language 关联（插入 `ProblemLanguage` 记录）

  **Must NOT do**:
  - 不修改 language 表结构

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []
  - **Reason**: 类似 tags 的关联表操作

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2 (with Task 3, 4)
  - **Blocks**: Task F1
  - **Blocked By**: None

  **References**:
  - `backend-spring/src/main/java/com/ulticode/modules/problem/mapper/ProblemLanguageMapper.java` - Mapper 接口
  - `backend-spring/src/main/java/com/ulticode/modules/problem/entity/ProblemLanguage.java` - 关联实体
  - `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java:306-320` - buildLanguages() 方法（读取 languages 的参考实现）

  **Acceptance Criteria**:
  - [ ] languages 正确保存到关联表
  - [ ] GET 请求正确返回 languages
  - [ ] 不存在的 language label 返回 400 错误

  **QA Scenarios**:
  ```
  Scenario: 验证 languages 保存
    Tool: Bash (curl)
    Steps:
      1. 发送 PATCH 包含 languages=["Java", "Python"]
      2. 发送 GET 验证 languages 正确返回
    Expected Result: languages 数据正确更新
    Evidence: .sisyphus/evidence/task-5-languages-persist.json
  ```

  **Commit**: YES
  - Message: `fix(backend): process languages in problem update`
  - Files: `backend-spring/src/main/java/com/ulticode/modules/problem/service/impl/ProblemServiceImpl.java`

---

## Final Verification Wave

- [x] F1. **端到端测试验证** — `unspecified-high`
  使用 curl 发送完整 PATCH 请求，验证所有字段正确保存。
  验证步骤:
  1. 获取 CSRF token 和登录 cookie
  2. 发送 PATCH 包含所有字段
  3. 验证返回 200
  4. 发送 GET 验证数据正确持久化
  5. 验证 examples, tags, languages 正确返回

- [x] F2. **回归测试** — `unspecified-high`
  验证现有字段不受影响，空值和边界情况正确处理。

---

## Commit Strategy

- **Task 1**: `fix(frontend): add missing fields to problem update submit`
- **Task 2**: `fix(frontend): convert difficulty case for backend compatibility`
- **Task 3-5**: `fix(backend): process examples/tags/languages in problem update`
- **F1-F2**: `test: verify problem update data consistency`

---

## Success Criteria

### Verification Commands
```bash
# 验证 PATCH 成功
curl -s -X PATCH http://localhost:9001/admin/problems/1 \
  -H "Content-Type: application/json" \
  -H "X-CSRF-Token: <token>" \
  -b cookies.txt \
  -d '{
    "title":"Test",
    "difficulty":"Easy",
    "examples":"[{\"input\":\"1\",\"output\":\"2\",\"explanation\":\"test\"}]",
    "tags":["array","dp"],
    "languages":["Java","Python"]
  }' | jq '.code'
# Expected: 0

# 验证 GET 返回正确数据
curl -s http://localhost:9001/admin/problems/1/description -b cookies.txt | jq '.data.examples, .data.tags, .data.difficulty'
# Expected: examples 数组, tags 数组, "Easy"
```

### Final Checklist
- [ ] All "Must Have" present
- [ ] All "Must NOT Have" absent
- [ ] PATCH 请求返回 200
- [ ] 所有字段正确持久化
- [ ] 难度值大小写正确
- [ ] 现有功能不受影响