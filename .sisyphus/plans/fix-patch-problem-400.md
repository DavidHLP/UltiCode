# 修复 PATCH /admin/problems/{id} 400 Bad Request 错误

## 问题分析

### 错误现象
前端调用 `PATCH /admin/problems/1` 时返回 400 Bad Request，错误信息为 `Validation failed`。

### 根本原因
**后端 `UpdateProblemDTO.java` 缺少前端发送的字段**：

1. **前端发送的数据**（`EditDescriptionView.vue:24-39`）：
   - `constraintsJson`: JSON.stringify(formData.constraints) - 字符串
   - `hints`: JSON.stringify(formData.hints) - 字符串
   - `examples`: 未发送（但 UpdateProblemDto 接口定义了）
   - `languages`: 未发送（但 UpdateProblemDto 接口定义了）
   - `tags`: 未发送（但 UpdateProblemDto 接口定义了）

2. **后端 `UpdateProblemDTO.java` 现有字段**（仅8个）：
   - slug, title, difficulty, isPremium, isPublished, hasSolution, summary, content, constraintsJson, hints
   - **缺少**: examples, languages, tags

3. **对比 `CreateProblemDTO.java`**（有11个字段）：
   - 包含 examples, constraints, hints, languages, tags
   - 但 UpdateProblemDTO 缺少 examples, languages, tags

4. **前端 `UpdateProblemDto` 接口**（`problems.ts:178-192`）定义了这些字段：
   - examples?: ProblemExample[]
   - tags?: string[]
   - languages?: string[]

### 具体问题
- `EditDescriptionView.vue` 在调用 `updateProblemWithPublish` 时没有发送 `examples`、`tags`、`languages` 字段
- 但即使发送了，后端 `UpdateProblemDTO` 也没有这些字段的定义，会导致 Jackson 反序列化时可能抛出 `UnrecognizedPropertyException`（如果配置了 `FAIL_ON_UNKNOWN_PROPERTIES`）
- 当前 400 错误更可能是因为前端发送的 `constraintsJson` 和 `hints` 是 JSON 字符串，但后端期望的是某种特定格式，或者其他验证失败

## 修复方案

### 方案一：后端添加缺失字段（推荐）

在 `UpdateProblemDTO.java` 中添加缺失的字段：

```java
@Schema(description = "Examples as JSON array", example = "[{\"input\":\"...\", \"output\":\"...\", \"explanation\":\"...\"}]")
private String examples;

@Schema(description = "Supported languages as JSON array", example = "[\"javascript\", \"python\", \"java\", \"c\", \"cpp\"]")
private List<String> languages;

@Schema(description = "Tags as JSON array", example = "[\"array\", \"dynamic-programming\"]")
private List<String> tags;
```

### 方案二：前端移除未使用的字段

修改 `UpdateProblemDto` 接口，移除后端不支持的字段：
- 删除 `examples?: ProblemExample[]`
- 删除 `tags?: string[]`
- 删除 `languages?: string[]`

但这会影响其他可能使用这些字段的地方。

### 方案三：同时修复前后端

1. **后端**：添加缺失字段到 `UpdateProblemDTO`
2. **前端**：确保发送的数据格式正确

## 建议的修复步骤

1. **修改 `UpdateProblemDTO.java`**：添加 `examples`、`languages`、`tags` 字段
2. **检查前端发送的数据**：确保 `constraintsJson` 和 `hints` 是有效的 JSON 字符串
3. **测试验证**：使用 curl 或 Postman 测试 PATCH 请求

## 相关文件

- `/home/david/project/UltiCode-Public-Next/backend-spring/src/main/java/com/ulticode/modules/problem/dto/UpdateProblemDTO.java`
- `/home/david/project/UltiCode-Public-Next/management/src/views/problems/edit/EditDescriptionView.vue`
- `/home/david/project/UltiCode-Public-Next/management/src/api/admin/problems.ts`
- `/home/david/project/UltiCode-Public-Next/management/src/stores/admin/problems.ts`
