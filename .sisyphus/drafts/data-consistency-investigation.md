# Draft: 前后端数据一致性调查

## 用户要求
- 查看前端数据为什么没有获取或展示缺少的数据
- 确保前后端数据统一
- 前端能获取与展示所需要的所有数据
- 确保数据传输完整

## 已知问题
1. PATCH /admin/problems/{id} 400 错误已修复（后端添加 examples, languages, tags 字段）
2. 难度值大小写不匹配：前端 EASY/MEDIUM/HARD vs 后端 Easy/Medium/Hard

## 需要调查的文件
- 后端: AdminProblemController.java (GET /{id}/description)
- 后端: DescriptionDataVO.java
- 后端: AdminProblemService.java / getDescriptionData()
- 前端: EditDescriptionView.vue (formattedProblem computed)
- 前端: DescriptionForm.vue (form schema)
- 前端: useProblemTab composable
- 前端: problems.ts (UpdateProblemDto 接口)

## 调查结果汇总

### 后端返回的数据结构 (GET /{id}/description)

**DescriptionDataVO 完整字段**:
- `id`, `title`, `slug`, `difficulty`, `status`, `isPremium`, `isPublished`
- `detail`: `summary`, `content`, `constraintsJson` (List<String>), `hints` (List<String>)
- `tags`: List<{id, label}>
- `examples`: List<{id, input, output, explanation, order}>
- `createdAt`, `updatedAt`, `publishedAt`

### 前端 formattedProblem 映射

**EditDescriptionView.vue (L49-71)**:
```typescript
{
  slug, title, difficulty, status, isPremium, isPublished,
  summary: problem.detail?.summary,
  content: problem.detail?.content,
  examples: problem.examples?.map(ex => ({ input: ex.input, output: ex.output, explanation: ex.explanation })),
  constraints: problem.detail?.constraintsJson || [],
  hints: problem.detail?.hints || [],
  tags: problem.tags?.map(t => t.label) || []
}
```

**问题**: formattedProblem 正确映射了所有字段 ✅

### 前端 handleSubmit 提交的数据

**EditDescriptionView.vue (L24-47)**:
```typescript
{
  slug: formData.slug,
  title: formData.title,
  difficulty: formData.difficulty,    // ❌ 发送 "EASY" (大写)
  isPremium: formData.isPremium,
  summary: formData.summary,
  content: formData.content,
  constraintsJson: JSON.stringify(formData.constraints),
  hints: JSON.stringify(formData.hints),
  // ❌ 缺少: examples, tags, languages
}
```

### 发现的问题

#### 问题 1: handleSubmit 缺少字段
- **缺失**: `examples`, `tags`, `languages`
- **影响**: 用户修改这些字段后无法保存
- **修复**: 在 handleSubmit 中添加这些字段

#### 问题 2: examples 格式错误
- **后端期望**: `String` (JSON 字符串)
- **前端发送**: 数组对象
- **修复**: `JSON.stringify(formData.examples)`

#### 问题 3: tags 格式错误
- **TagsSelector 组件**: 返回 tag IDs (如 "1", "2")
- **后端 UpdateProblemDTO.tags**: 期望 label 字符串数组 (如 "array", "dp")
- **修复**: 将 IDs 转换为 labels

#### 问题 4: 难度值大小写不匹配
- **后端期望**: "Easy", "Medium", "Hard" (UpdateProblemDTO.java L26)
- **前端发送**: "EASY", "MEDIUM", "HARD" (Difficulty 枚举)
- **修复**: 提交前转换大小写

#### 问题 5: 后端不处理 examples/tags/languages
- **位置**: ProblemServiceImpl.java L421-458
- **问题**: `updateProblemDetail()` 只处理 summary, content, constraintsJson, hints
- **缺失**: 完全不处理 examples, tags, languages
- **修复**: 后端需要添加更新逻辑

### 修复优先级

| 优先级 | 问题 | 位置 |
|--------|------|------|
| P0 | handleSubmit 缺少 examples, tags | EditDescriptionView.vue L24-47 |
| P0 | 后端不处理 examples/tags/languages | ProblemServiceImpl.java L421-458 |
| P1 | examples 格式错误 (应为 JSON 字符串) | EditDescriptionView.vue L35 |
| P1 | 难度值大小写不匹配 | EditDescriptionView.vue L30 |
| P1 | tags 发送 IDs 而非 labels | EditDescriptionView.vue / TagsSelector.vue |

### 需要修复的具体代码

#### 前端修复 (EditDescriptionView.vue)

```typescript
// 在 handleSubmit 中添加:
examples: JSON.stringify(formData.examples),  // 数组 → JSON 字符串
tags: formData.tags,  // 需要确认是 IDs 还是 labels
languages: formData.languages,  // 需要确认从哪获取

// 难度值转换:
difficulty: formData.difficulty.charAt(0) + formData.difficulty.slice(1).toLowerCase(),
// EASY → Easy
```

#### 后端修复 (ProblemServiceImpl.java)

在 `updateProblemDetail()` 方法中添加:
```java
// 处理 examples
if (dto.getExamples() != null) {
    detail.setExamplesJson(dto.getExamples());
}

// 处理 tags (需要添加标签关联逻辑)
if (dto.getTags() != null) {
    // 更新 problem_tag_relation 表
}

// 处理 languages
if (dto.getLanguages() != null) {
    // 更新 problem_language 表
}
```
