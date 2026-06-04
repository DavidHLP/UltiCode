# UltiCode Vue 3 前端项目规则

> 本文件补充全局 ECC Web/TypeScript 规则，定义 UltiCode 前端项目特有的架构约定和模式。
> 与项目现有风格冲突时，以此文件为准。

## 适用范围

`console/` 和 `management/` 目录下所有 TypeScript/Vue 代码。

---

## 项目结构

两个前端项目共享相似的目录结构：

```
src/
├── api/             # API 调用函数
├── assets/          # 静态资源
├── components/      # 可复用组件
│   └── ui/          # shadcn-vue 基础组件
├── composables/     # Vue Composables (useXxx)
├── i18n/            # 国际化
│   ├── locales/     # 语言文件
│   └── utils/       # i18n 工具
├── layouts/         # 布局组件
├── router/          # 路由配置
├── stores/          # Pinia Stores
├── types/           # TypeScript 类型定义
├── utils/           # 工具函数
└── views/           # 页面视图
```

---

## API 调用模式

### Console 前端

使用 `apiGet`/`apiPost`/`apiPatch`/`apiDelete` 直接调用：

```typescript
import { apiGet, apiPost } from '@/utils/request'

export async function fetchProblems(filters: ProblemFilters): Promise<PaginatedProblems> {
  return apiGet('/problems', { params: filters })
}
```

### Management 前端

使用带类型的 API 函数封装：

```typescript
// management/src/api/admin/problems.ts
import { apiGet, apiPost, apiPatch, apiDelete } from '@/utils/request'

export const problemsApi = {
  getAll: (params?: ProblemQueryParams) => apiGet<ProblemListResponse>('/admin/problems', { params }),
  getById: (id: string) => apiGet<ProblemDetail>(`/admin/problems/${id}`),
  create: (data: CreateProblemDto) => apiPost<ProblemDetail>('/admin/problems', data),
}
```

### 约定

- **Console**: 可使用直接 `apiGet/apiPost` 调用
- **Management**: 必须定义带类型的 API 函数封装
- 所有 API 调用必须通过 `@/utils/request.ts` 中的工具函数
- 禁止直接使用 `axios` 调用后端接口

---

## 状态管理 (Pinia)

### Store 结构

```typescript
// stores/auth.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  // State
  const user = ref<User | null>(null)
  const isAuthenticated = computed(() => !!user.value)

  // Actions
  async function login(credentials: LoginCredentials) { ... }
  function logout() { ... }

  return { user, isAuthenticated, login, logout }
})
```

### 约定

- 使用 Composition API 风格 (`setup store`)，不使用 Options API 风格
- Store 文件名：`camelCase.ts`
- 导出 Store：`useXxxStore`
- State 用 `ref`，Getters 用 `computed`，Actions 用普通函数
- 异步操作在 Store 内处理，组件只调用 action

---

## 组件模式

### 组件命名

- 页面组件：`XxxView.vue`（放在 `views/` 下）
- 可复用组件：`XxxComponent.vue` 或 `Xxx.vue`（放在 `components/` 下）
- UI 基础组件：`Button.vue`, `Input.vue` 等（放在 `components/ui/` 下）

### 组件结构

```vue
<script setup lang="ts">
// 1. 类型导入
import type { PropType } from 'vue'
// 2. 组件导入
import { Button } from '@/components/ui/button'
// 3. Composable 导入
import { useAuthStore } from '@/stores/auth'
// 4. Props & Emits
const props = defineProps<{ ... }>()
const emit = defineEmits<{ ... }>()
// 5. Composables
const authStore = useAuthStore()
// 6. 响应式数据
const loading = ref(false)
// 7. 计算属性
const displayName = computed(() => ...)
// 8. 方法
function handleSubmit() { ... }
// 9. 生命周期
onMounted(() => { ... })
</script>

<template>
  <!-- 模板内容 -->
</template>
```

### 约定

- 必须使用 `<script setup lang="ts">`
- Props 使用泛型定义：`defineProps<{ title: string }>()`
- Emits 使用泛型定义：`defineEmits<{ update: [value: string] }>()`
- 禁止使用 `defineComponent` + Options API

---

## 国际化 (i18n)

### 翻译文件结构

```
src/i18n/locales/
├── en/
│   ├── common.ts
│   └── modules/
│       ├── problem.ts
│       └── contest.ts
└── zh/
    ├── common.ts
    └── modules/
        ├── problem.ts
        └── contest.ts
```

### 使用方式

```vue
<template>
  <h1>{{ t('problem.list.title') }}</h1>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>
```

### 约定

- 所有用户可见文本必须通过 `t()` 函数
- 翻译 key 使用 `模块.功能.具体文本` 格式
- 新增翻译 key 时必须同时添加 en 和 zh 翻译
- Management 的 DataTable 列名 key：`table.columnNames.{columnId}`
- columnId 使用 camelCase，与 API 字段名对应

---

## CSS / Tailwind

- 使用 Tailwind CSS v4（`@tailwindcss/vite` 插件）
- UI 组件使用 shadcn-vue（基于 reka-ui / Radix Vue）
- 图标使用 Lucide (`lucide-vue-next`)
- 禁止在组件中使用 scoped CSS 除非有特殊需求
- Prettier 配置：no semicolons, single quotes, 100 char print width

---

## Composables

### 命名

- 文件名：`useXxx.ts`
- 导出函数：`useXxx`

### 模式

```typescript
// composables/useLoading.ts
export function useLoading(initialValue = false) {
  const loading = ref(initialValue)

  async function withLoading<T>(fn: () => Promise<T>): Promise<T> {
    loading.value = true
    try {
      return await fn()
    } finally {
      loading.value = false
    }
  }

  return { loading, withLoading }
}
```

### 约定

- Composable 必须以 `use` 开头
- 返回值使用 `toRefs` 解构以保持响应性
- 异步 Composable 可返回 `Promise`
- 测试放在 `composables/__tests__/` 目录下

---

## 测试

- 单元测试：Vitest + @vue/test-utils
- E2E 测试（Management）：Playwright
- 测试文件放在对应目录的 `__tests__/` 下
- 运行：`pnpm test` (单次) / `pnpm test:watch` (监听)
- 覆盖率：`pnpm test:coverage`

---

## 请求工具

`@/utils/request.ts` 提供统一的 Axios 实例：

- 自动 CSRF token 管理
- 自动重试（网络错误）
- 401 自动跳转登录
- 请求/响应拦截器

**禁止**绕过 `request.ts` 直接创建 Axios 实例调用后端 API。

---

## 共享认证模块

`shared/` 目录包含跨项目共享的认证逻辑（auth-core Vue composable），Console 和 Management 均依赖此模块。

修改 `shared/` 中的代码时，必须在两个前端项目中验证兼容性。
