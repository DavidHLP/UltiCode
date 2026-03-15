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
5. 组件复用性 - 保持 Display 组件的 props 接口，便于跨项目复用

## 架构设计

### 数据流

```
┌─────────────────────────────────────────────────────────────┐
│                      页面加载流程                            │
├─────────────────────────────────────────────────────────────┤
│  1. 进入页面（并行加载）                                     │
│     └─> Promise.all([fetchHeader(), fetchTabData()])        │
│                                                             │
│  2. 切换 Tab                                                │
│     └─> fetchTabData(newTab) → 更新 Tab 内容               │
│     └─> 使用 AbortController 取消未完成的请求              │
│                                                             │
│  3. 切换题目                                                │
│     └─> clearCurrentProblem() → 清除所有数据               │
│     └─> loadPageData() → 加载新数据                        │
│                                                             │
│  4. 离开页面                                                │
│     └─> abortAllRequests() → 取消进行中的请求              │
│     └─> clearCurrentProblem() → 清除所有数据               │
└─────────────────────────────────────────────────────────────┘
```

## 类型定义

所有类型定义放在 `management/src/api/admin/problems.ts` 文件中，与 API 函数放在一起。

```typescript
// ========== Header 类型 ==========
export interface HeaderData {
  id: string
  title: string
  slug: string
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  status: ProblemStatus  // 新增：用于显示题目状态
  is_premium: boolean
  is_published: boolean
  published_at?: Date
}

// ========== Description 类型 ==========
export interface DescriptionData {
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

// ========== Code 类型 ==========
export interface CodeData {
  id: string
  languages?: Array<{
    id: string
    language: string
    value: string
    style?: string  // 保留：代码风格配置
    starter_code: string
  }>
}

// ========== Cases 类型 ==========
export interface CasesData {
  id: string
  examples?: Array<{
    id: string
    input: string
    output: string
    explanation?: string
    order: number
  }>
  detail?: {
    constraints_json?: string[]
    hints?: string[]
  }
  tags?: Array<{ id: string; label: string }>
}
```

## 后端 API 设计

### 新增 4 个轻量 API 端点

> **注意**：这些是 Admin API，路由前缀为 `/admin/problems`

#### 1. Header API - 页面进入时加载

```
GET /admin/problems/:id/header

Response: {
  id: string
  title: string
  slug: string
  difficulty: 'EASY' | 'MEDIUM' | 'HARD'
  status: 'solved' | 'attempted' | 'todo'
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
    style?: string
    starter_code: string
  }>
}
```

#### 4. Cases API - 测试用例 tab

```
GET /admin/problems/:id/cases

Response: {
  id: string
  examples?: Array<{
    id: string
    input: string
    output: string
    explanation?: string
    order: number
  }>
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
- `backend/src/problem/dto/problem-header.dto.ts` - Header 响应 DTO
- `backend/src/problem/dto/problem-description.dto.ts` - Description 响应 DTO
- `backend/src/problem/dto/problem-code.dto.ts` - Code 响应 DTO
- `backend/src/problem/dto/problem-cases.dto.ts` - Cases 响应 DTO

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

// ========== 请求取消控制器 ==========
const abortControllers = ref<Map<string, AbortController>>(new Map())

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

### API 函数（带 AbortController）

```typescript
// 创建或获取 AbortController
function getAbortController(key: string): AbortController {
  let controller = abortControllers.value.get(key)
  if (controller) {
    controller.abort() // 取消之前的请求
  }
  controller = new AbortController()
  abortControllers.value.set(key, controller)
  return controller
}

// 取消所有请求
function abortAllRequests() {
  abortControllers.value.forEach(controller => controller.abort())
  abortControllers.value.clear()
}

async function fetchHeader(id: string) {
  const controller = getAbortController('header')
  headerLoading.value = true
  headerError.value = null
  try {
    headerData.value = await problemsApi.getHeader(id, controller.signal)
  } catch (err) {
    if (err.name !== 'AbortError') {
      headerError.value = extractErrorMessage(err)
    }
  } finally {
    headerLoading.value = false
  }
}

async function fetchDescription(id: string) {
  const controller = getAbortController('description')
  descriptionLoading.value = true
  descriptionError.value = null
  try {
    descriptionData.value = await problemsApi.getDescription(id, controller.signal)
  } catch (err) {
    if (err.name !== 'AbortError') {
      descriptionError.value = extractErrorMessage(err)
    }
  } finally {
    descriptionLoading.value = false
  }
}

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

// 进入页面：并行加载 Header + 当前 Tab 数据
onMounted(() => loadPageData())

// 切换 Tab：只加载对应 Tab 数据
watch(currentTab, (newTab) => fetchTabData(newTab))

// 切换题目：清除旧数据，加载新数据
watch(problemId, () => {
  store.clearCurrentProblem()
  loadPageData()
})

// 离开页面：取消请求 + 清除数据
onUnmounted(() => {
  store.abortAllRequests()
  store.clearCurrentProblem()
})

// 并行加载 Header + Tab 数据
async function loadPageData() {
  const id = problemId.value
  await Promise.all([
    store.fetchHeader(id),
    fetchTabData(currentTab.value)
  ])
}

async function fetchTabData(tab: TabType) {
  const id = problemId.value
  switch (tab) {
    case 'description': await store.fetchDescription(id); break
    case 'code': await store.fetchCode(id); break
    case 'cases': await store.fetchCases(id); break
    case 'audit': break // Audit 组件自己管理数据
  }
}

// Tab 重试函数
async function retryTab(tab: TabType) {
  await fetchTabData(tab)
}

// Header 重试函数
async function retryHeader() {
  await store.fetchHeader(problemId.value)
}
```

### 子组件：保持 Props 接口（保持复用性）

**设计决策**：Display 组件继续使用 props 接收数据，保持组件的复用性。父组件负责从 Store 获取数据并传递。

```vue
<!-- DescriptionDisplay.vue - 保持不变 -->
<script setup>
const props = defineProps<{
  problem: DescriptionData
}>()
</script>

<!-- CodeDisplay.vue - 保持不变 -->
<script setup>
const props = defineProps<{
  languages?: ProblemLanguage[]
}>()
</script>

<!-- CasesDisplay.vue - 保持不变 -->
<script setup>
const props = defineProps<{
  problem: CasesData
}>()
</script>
```

### ProblemDetailView.vue 模板

```vue
<template>
  <div class="min-h-[calc(100vh-4rem)] bg-background flex flex-col">
    <!-- Header -->
    <header>
      <div v-if="store.headerLoading">加载中...</div>
      <div v-else-if="store.headerError">
        <span>> ERROR: {{ store.headerError }}</span>
        <Button @click="router.push({ name: 'problems' })">返回列表</Button>
        <Button @click="retryHeader">重试</Button>
      </div>
      <div v-else-if="store.headerData">
        <!-- 标题、难度、发布状态、操作按钮 -->
      </div>
    </header>

    <!-- Tab 内容：父组件负责 loading/error 状态，然后传递数据给子组件 -->
    <main>
      <!-- Description Tab -->
      <template v-if="currentTab === 'description'">
        <div v-if="store.descriptionLoading">加载中...</div>
        <div v-else-if="store.descriptionError">
          <span>> ERROR: {{ store.descriptionError }}</span>
          <Button @click="retryTab('description')">重试</Button>
        </div>
        <DescriptionDisplay v-else-if="store.descriptionData" :problem="store.descriptionData" />
      </template>

      <!-- Code Tab -->
      <template v-if="currentTab === 'code'">
        <div v-if="store.codeLoading">加载中...</div>
        <div v-else-if="store.codeError">
          <span>> ERROR: {{ store.codeError }}</span>
          <Button @click="retryTab('code')">重试</Button>
        </div>
        <CodeDisplay v-else-if="store.codeData" :languages="store.codeData.languages" />
      </template>

      <!-- Cases Tab -->
      <template v-if="currentTab === 'cases'">
        <div v-if="store.casesLoading">加载中...</div>
        <div v-else-if="store.casesError">
          <span>> ERROR: {{ store.casesError }}</span>
          <Button @click="retryTab('cases')">重试</Button>
        </div>
        <CasesDisplay v-else-if="store.casesData" :problem="store.casesData" />
      </template>

      <!-- Audit Tab：自己管理数据 -->
      <AuditLogViewer v-if="currentTab === 'audit'" entity-type="PROBLEM" :entity-id="problemId" />
    </main>
  </div>
</template>
```

## 前端 API 层更新

### api/admin/problems.ts

```typescript
export const problemsApi = {
  // ========== 新增的轻量 API ==========
  async getHeader(id: string, signal?: AbortSignal): Promise<HeaderData> {
    return apiGet<HeaderData>(`/admin/problems/${id}/header`, { signal })
  },

  async getDescription(id: string, signal?: AbortSignal): Promise<DescriptionData> {
    return apiGet<DescriptionData>(`/admin/problems/${id}/description`, { signal })
  },

  async getCode(id: string, signal?: AbortSignal): Promise<CodeData> {
    return apiGet<CodeData>(`/admin/problems/${id}/code`, { signal })
  },

  async getCases(id: string, signal?: AbortSignal): Promise<CasesData> {
    return apiGet<CasesData>(`/admin/problems/${id}/cases`, { signal })
  },

  // ========== 保留原有 API ==========
  getProblems(params) { ... },
  getProblem(id) { ... },  // 编辑页面仍需要完整数据
  createProblem(data) { ... },
  updateProblem(id, data) { ... },
  // ...其他 CRUD 操作保持不变
}
```

## 错误处理

### 错误层级

1. **API 层** - 捕获网络错误，返回标准错误格式
2. **Store 层** - 提取错误信息，存入对应的 `xxxError` 状态（忽略 AbortError）
3. **视图层** - 根据 `xxxError` 显示错误 UI + 重试按钮

### 错误 UI 展示

**Header 错误**（整个页面无法继续）：
```
┌─────────────────────────────────────────────────────────┐
│ > ERROR: 题目不存在或已被删除                           │
│                                    [返回列表] [重试]    │
└─────────────────────────────────────────────────────────┘
```

**Tab 内容错误**（仅 Tab 区域显示错误，Header 正常）：
```
┌─────────────────────────────────────────────────────────┐
│ [Header 正常显示]                                       │
├─────────────────────────────────────────────────────────┤
│ > ERROR: 加载代码模板失败                               │
│                                               [重试]    │
└─────────────────────────────────────────────────────────┘
```

## 竞态条件处理

### 问题场景
用户快速切换 tab：Tab A 请求发出 → 切换到 Tab B → Tab A 响应返回 → 覆盖 Tab B 的数据

### 解决方案：AbortController

1. 每个 fetch 函数使用独立的 AbortController
2. 同一类型请求发出前，取消之前未完成的请求
3. 离开页面时取消所有请求
4. 忽略 AbortError，不更新错误状态

```typescript
// 请求取消流程
fetchCode(id) {
  // 1. 取消之前的 code 请求
  // 2. 创建新的 AbortController
  // 3. 发送请求
  // 4. 忽略 AbortError
}
```

## 改动文件清单

### 后端

| 文件 | 改动 |
|------|------|
| `backend/src/problem/problem.controller.ts` | 新增 4 个路由 |
| `backend/src/problem/problem.service.ts` | 新增 4 个查询方法 |
| `backend/src/problem/dto/problem-header.dto.ts` | 新增 Header 响应 DTO |
| `backend/src/problem/dto/problem-description.dto.ts` | 新增 Description 响应 DTO |
| `backend/src/problem/dto/problem-code.dto.ts` | 新增 Code 响应 DTO |
| `backend/src/problem/dto/problem-cases.dto.ts` | 新增 Cases 响应 DTO |

### 前端

| 文件 | 改动 |
|------|------|
| `management/src/api/admin/problems.ts` | 新增 4 个 API 函数 + 类型定义 |
| `management/src/stores/admin/problems.ts` | 重构状态结构、添加 AbortController |
| `management/src/views/problems/ProblemDetailView.vue` | 重构数据加载逻辑、错误处理 |
| `management/src/views/problems/components/DescriptionDisplay.vue` | 无改动（保持 props 接口）|
| `management/src/views/problems/components/CodeDisplay.vue` | 无改动（保持 props 接口）|
| `management/src/views/problems/components/CasesDisplay.vue` | 无改动（保持 props 接口）|

## 测试计划

### 1. 后端 API 测试
- 测试 4 个新 API 的响应格式
- 测试不存在的题目 ID 返回 404
- 测试 DTO 验证

### 2. 前端集成测试
- 测试页面加载时 Header + Tab 数据并行加载
- 测试 Tab 切换时数据正确刷新
- 测试题目切换时旧数据清除
- 测试错误状态正确显示
- 测试重试功能
- 测试快速切换 tab 时的竞态条件处理

### 3. E2E 测试
- 完整的用户浏览题目流程
- 网络错误场景
