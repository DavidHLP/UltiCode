# 题目详情页数据架构重构设计

**日期**: 2026-03-15
**状态**: 待审批
**范围**: management 前端 + backend API

## 问题背景

当前题目详情页 (`ProblemDetailView.vue`) 存在以下问题：

1. **API 调用冗余**：每个 tab 都调用同一个 `getProblem(id)` 获取完整数据，然后只使用部分字段
2. **Store 职责混乱**：混合了列表数据、单个问题数据、tab 数据，状态结构复杂
3. **缓存逻辑冲突**：`tabData` 缓存与"每次切换都请求"的需求冲突
4. **错误处理不完善**：缺乏独立的错误状态，无法精确定位失败区域

## 设计目标

1. 按需加载 - 每个 tab 只请求必要的数据
2. 无缓存策略 - 每次切换 tab 都重新请求，保证数据最新
3. 状态扁平化 - Store 结构清晰，易于维护
4. 独立错误处理 - 每个数据块有独立的 loading/error 状态

## 架构设计

### 数据流

```
┌─────────────────────────────────────────────────────────────┐
│                      页面加载流程                            │
├─────────────────────────────────────────────────────────────┤
│  1. 进入页面                                                │
│     └─> fetchHeader() → 显示 Header                        │
│     └─> fetchTabData(currentTab) → 显示 Tab 内容           │
│                                                             │
│  2. 切换 Tab                                                │
│     └─> fetchTabData(newTab) → 更新 Tab 内容               │
│                                                             │
│  3. 切换题目                                                │
│     └─> clearCurrentProblem() → 清除所有数据               │
│     └─> fetchHeader() + fetchTabData() → 加载新数据        │
│                                                             │
│  4. 离开页面                                                │
│     └─> clearCurrentProblem() → 清除所有数据               │
└─────────────────────────────────────────────────────────────┘
```

## 后端 API 设计

### 新增 4 个轻量 API 端点

#### 1. Header API - 页面进入时加载

```
GET /admin/problems/:id/header

Response: {
  id: string
  title: string
  slug: string
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  is_premium: boolean
  is_published: boolean
  published_at?: Date
}
```

#### 2. Description API - 描述 tab

```
GET /admin/problems/:id/description

Response: {
  id: string
  title: string
  slug: string
  difficulty: string
  is_premium: boolean
  is_published: boolean
  detail?: {
    summary?: string
    content?: string
    constraints_json?: string[]
    hints?: string[]
  }
  tags: Array<{ id: string; label: string }>
  examples?: ProblemExample[]
  created_at: Date
  updated_at: Date
  published_at?: Date
}
```

#### 3. Code API - 代码模板 tab

```
GET /admin/problems/:id/code

Response: {
  id: string
  languages?: Array<{
    id: string
    language: string
    value: string
    starter_code: string
  }>
}
```

#### 4. Cases API - 测试用例 tab

```
GET /admin/problems/:id/cases

Response: {
  id: string
  examples?: ProblemExample[]
  detail?: {
    constraints_json?: string[]
    hints?: string[]
  }
  tags?: Array<{ id: string; label: string }>
}
```

### 后端改动文件

- `backend/src/problem/problem.controller.ts` - 新增 4 个路由
- `backend/src/problem/problem.service.ts` - 新增 4 个查询方法
- `backend/src/problem/dto/` - 新增响应 DTO（可选）

## 前端 Store 重构

### 新的 Store 结构

```typescript
// stores/admin/problems.ts

// ========== Header 状态 ==========
const headerData = ref<HeaderData | null>(null)
const headerLoading = ref(false)
const headerError = ref<string | null>(null)

// ========== Description Tab 状态 ==========
const descriptionData = ref<DescriptionData | null>(null)
const descriptionLoading = ref(false)
const descriptionError = ref<string | null>(null)

// ========== Code Tab 状态 ==========
const codeData = ref<CodeData | null>(null)
const codeLoading = ref(false)
const codeError = ref<string | null>(null)

// ========== Cases Tab 状态 ==========
const casesData = ref<CasesData | null>(null)
const casesLoading = ref(false)
const casesError = ref<string | null>(null)

// ========== 列表状态（保持不变）==========
const problems = ref<Problem[]>([])
const listLoading = ref(false)
const listError = ref<string | null>(null)
const total = ref(0)
```

### 移除的状态

- `currentProblem` - 不再需要
- `loadedProblemId` - 不再需要
- `tabData` - 拆分为独立状态
- `tabLoading` - 拆分为独立状态

### API 函数

```typescript
async function fetchHeader(id: string) {
  headerLoading.value = true
  headerError.value = null
  try {
    headerData.value = await problemsApi.getHeader(id)
  } catch (err) {
    headerError.value = extractErrorMessage(err)
  } finally {
    headerLoading.value = false
  }
}

async function fetchDescription(id: string) { /* 同理 */ }
async function fetchCode(id: string) { /* 同理 */ }
async function fetchCases(id: string) { /* 同理 */ }

function clearCurrentProblem() {
  headerData.value = null
  headerError.value = null
  descriptionData.value = null
  descriptionError.value = null
  codeData.value = null
  codeError.value = null
  casesData.value = null
  casesError.value = null
}
```

## 前端视图重构

### ProblemDetailView.vue

```typescript
// ========== 核心逻辑 ==========

const problemId = computed(() => route.params.id as string)
const currentTab = computed(() => route.params.tab as TabType)

const store = useProblemsStore()

// 进入页面：加载 Header + 当前 Tab 数据
onMounted(() => loadPageData())

// 切换 Tab：只加载对应 Tab 数据
watch(currentTab, (newTab) => fetchTabData(newTab))

// 切换题目：清除旧数据，加载新数据
watch(problemId, (newId) => {
  store.clearCurrentProblem()
  loadPageData()
})

// 离开页面：清除数据
onUnmounted(() => store.clearCurrentProblem())

async function loadPageData() {
  await store.fetchHeader(problemId.value)
  await fetchTabData(currentTab.value)
}

async function fetchTabData(tab: TabType) {
  switch (tab) {
    case 'description': await store.fetchDescription(problemId.value); break
    case 'code': await store.fetchCode(problemId.value); break
    case 'cases': await store.fetchCases(problemId.value); break
    case 'audit': break // Audit 组件自己管理数据
  }
}
```

### 子组件变化

**之前**：通过 props 接收数据
```vue
<DescriptionDisplay :problem="descriptionData" />
```

**之后**：直接从 Store 读取数据
```vue
<!-- DescriptionDisplay.vue -->
<script setup>
const store = useProblemsStore()
const { descriptionData, descriptionLoading, descriptionError } = storeToRefs(store)
</script>

<template>
  <div v-if="descriptionLoading">加载中...</div>
  <div v-else-if="descriptionError">错误: {{ descriptionError }}</div>
  <div v-else-if="descriptionData">
    <!-- 渲染内容 -->
  </div>
</template>
```

## 前端 API 层更新

### api/admin/problems.ts

```typescript
export const problemsApi = {
  // ========== 新增的轻量 API ==========
  async getHeader(id: string): Promise<HeaderData> {
    return apiGet<HeaderData>(`/admin/problems/${id}/header`)
  },

  async getDescription(id: string): Promise<DescriptionData> {
    return apiGet<DescriptionData>(`/admin/problems/${id}/description`)
  },

  async getCode(id: string): Promise<CodeData> {
    return apiGet<CodeData>(`/admin/problems/${id}/code`)
  },

  async getCases(id: string): Promise<CasesData> {
    return apiGet<CasesData>(`/admin/problems/${id}/cases`)
  },

  // ========== 保留原有 API ==========
  getProblems(params) { ... },
  getProblem(id) { ... },  // 编辑页面可能仍需要
  createProblem(data) { ... },
  updateProblem(id, data) { ... },
  // ...其他 CRUD 操作保持不变
}
```

## 错误处理

### 错误层级

1. **API 层** - 捕获网络错误，返回标准错误格式
2. **Store 层** - 提取错误信息，存入对应的 `xxxError` 状态
3. **视图层** - 根据 `xxxError` 显示错误 UI + 重试按钮

### 错误 UI 展示

**Header 错误**（整个页面无法继续）：
```
┌─────────────────────────────────────────────────────────┐
│ > ERROR: 题目不存在或已被删除                           │
│                                    [返回列表] [重试]    │
└─────────────────────────────────────────────────────────┘
```

**Tab 内容错误**（仅 Tab 区域显示错误）：
```
┌─────────────────────────────────────────────────────────┐
│ [Header 正常显示]                                       │
├─────────────────────────────────────────────────────────┤
│ > ERROR: 加载代码模板失败                               │
│                                               [重试]    │
└─────────────────────────────────────────────────────────┘
```

## 改动文件清单

### 后端

| 文件 | 改动 |
|------|------|
| `backend/src/problem/problem.controller.ts` | 新增 4 个路由 |
| `backend/src/problem/problem.service.ts` | 新增 4 个查询方法 |

### 前端

| 文件 | 改动 |
|------|------|
| `management/src/api/admin/problems.ts` | 新增 4 个 API 函数 |
| `management/src/stores/admin/problems.ts` | 重构状态结构和 fetch 函数 |
| `management/src/views/problems/ProblemDetailView.vue` | 简化数据加载逻辑 |
| `management/src/views/problems/components/DescriptionDisplay.vue` | 改为直接读取 Store |
| `management/src/views/problems/components/CodeDisplay.vue` | 改为直接读取 Store |
| `management/src/views/problems/components/CasesDisplay.vue` | 改为直接读取 Store |

## 测试计划

1. **后端 API 测试**
   - 测试 4 个新 API 的响应格式
   - 测试不存在的题目 ID 返回 404

2. **前端集成测试**
   - 测试页面加载时 Header + Tab 数据正确加载
   - 测试 Tab 切换时数据正确刷新
   - 测试题目切换时旧数据清除
   - 测试错误状态正确显示
   - 测试重试功能

3. **E2E 测试**
   - 完整的用户浏览题目流程
