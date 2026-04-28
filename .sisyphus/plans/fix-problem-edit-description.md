# 修复题目编辑页面数据加载问题

## 问题描述
访问 `/problems/1/edit/description` 时，编辑已有题目没有完整渲染数据。

## 根本原因
`EditDescriptionView.vue` 使用 `fetchProblem()` 获取数据，但 `Problem.detail` 字段可能为空。项目中已有专门的 `fetchDescription()` 方法，调用 `/admin/problems/{id}/description` API 返回完整的 description 数据。

---

## 修复计划 (v2 - 经 Metis 审计后优化)

### 问题识别

1. **`problemData` 类型不匹配**: 当前 `ref<Problem | null>`，但 `fetchDescription` 返回的 `DescriptionData` 结构不同
2. **无效代码**: `tags` 字段在 `ProblemData` 中不存在
3. **Store 缓存问题**: `fetchDescription` 有缓存，需要 `forceRefresh` 确保数据新鲜
4. **路由参数变化未监听**: 同组件切换不同 problem ID 时数据不刷新

---

## 修改文件
`management/src/views/problems/edit/EditDescriptionView.vue`

---

## 具体修改

### 1. 添加本地类型定义 (新增)

在 `<script setup>` 开头，导入之后添加：

```typescript
// 本地数据类型，用于 EditDescriptionView 内部数据管理
interface EditDescriptionData {
  slug: string
  title: string
  difficulty: string
  status: string
  isPremium: boolean
  isPublished: boolean
  summary: string
  content: string
}
```

### 2. 修改 `problemData` 类型和初始值 (第21行)

**修改前**:
```typescript
const problemData = ref<Problem | null>(null)
```

**修改后**:
```typescript
const problemData = ref<EditDescriptionData | null>(null)
```

### 3. 修改 `loadData()` 函数 (第33-39行)

**修改前**:
```typescript
async function loadData() {
  const problem = await problemsStore.fetchProblem(problemId.value)
  if (problem) {
    problemData.value = problem
  }
  loadingData.value = false
}
```

**修改后**:
```typescript
async function loadData() {
  // 使用 fetchDescription 获取完整的 description 数据
  const description = await problemsStore.fetchDescription(problemId.value, true)
  if (description) {
    problemData.value = {
      slug: description.slug,
      title: description.title,
      difficulty: description.difficulty,
      status: description.status,
      isPremium: description.isPremium,
      isPublished: description.isPublished,
      summary: description.detail?.summary || '',
      content: description.detail?.content || '',
    }
  }
  loadingData.value = false
}
```

**关键变更**:
- 使用 `fetchDescription` 替代 `fetchProblem`
- 传入 `true` 作为 `forceRefresh` 参数，确保获取最新数据
- 手动映射 DescriptionData 到 EditDescriptionData 格式
- 移除无效的 `tags` 字段（ProblemData 不存在此字段）

### 4. 添加路由参数监听 (新增)

在 `onMounted` 之后添加 `watch`：

```typescript
// 监听路由参数变化，同组件不同 problem ID 切换时刷新数据
watch(problemId, () => {
  loadData()
})
```

### 5. 简化 `formattedProblem` (第68-81行)

**修改后**:
```typescript
const formattedProblem = computed(() => {
  if (!problemData.value) return undefined
  return problemData.value
})
```

---

## 类型映射说明

| `DescriptionData` 字段 | `EditDescriptionData` 字段 | 说明 |
|----------------------|-------------------------|------|
| `description.slug` | `slug` | 直接映射 |
| `description.title` | `title` | 直接映射 |
| `description.difficulty` | `difficulty` | string → string |
| `description.status` | `status` | string → string |
| `description.isPremium` | `isPremium` | 直接映射 |
| `description.isPublished` | `isPublished` | 直接映射 |
| `description.detail?.summary` | `summary` | 可选链 + 空字符串回退 |
| `description.detail?.content` | `content` | 可选链 + 空字符串回退 |

---

## 验证步骤 (Agent 执行 QA)

### 1. TypeScript 类型检查
```bash
cd /home/davidhlp/project/UltiCode-Public-Next/management
npx vue-tsc --noEmit 2>&1 | grep -i "EditDescriptionView\|error" || echo "No TypeScript errors in EditDescriptionView"
```

### 2. API 数据验证
```bash
# 登录获取 cookie
curl -s -X POST http://localhost:9001/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  -c /tmp/cookies.txt > /dev/null

# 验证 description API 返回数据
curl -s http://localhost:9001/admin/problems/1/description \
  -b /tmp/cookies.txt | python3 -m json.tool
```

**期望结果**: 响应包含 `detail.summary` 和 `detail.content` 字段

### 3. 浏览器 UI 验证
使用 Playwright 或手动访问 `http://localhost:9003/problems/1/edit/description` 验证：
- 页面加载完成
- `title` 输入框有值
- `slug` 输入框有值
- `summary` 文本框有值
- MarkdownEditor 中 `content` 有值

---

## 风险评估

| 风险 | 等级 | 缓解措施 |
|-----|------|---------|
| TypeScript 类型错误 | 低 | 已声明正确类型 |
| API 数据格式不匹配 | 低 | 已处理可选链和空字符串回退 |
| 缓存返回旧数据 | 中 | 传入 `forceRefresh: true` |
| 路由参数变化不刷新 | 中 | 添加了 `watch(problemId, ...)` |

---

## 回滚方案

如需回滚，保留原代码备份：
- 恢复 `problemData` 为 `ref<Problem | null>`
- 恢复 `loadData()` 使用 `fetchProblem`
- 删除 `watch(problemId, ...)`