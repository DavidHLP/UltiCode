# 推荐页面 UI 实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 console 前端创建独立的推荐页面，展示个性化题目推荐（每日推荐、薄弱点强化、挑战模式、相似题目）

**Architecture:** 采用 Vue 3 + Pinia + TypeScript 架构，遵循项目现有的文件组织模式。API 层负责与后端通信，Store 管理状态，视图组件展示 UI。使用项目已有的 shadcn-vue UI 组件。

**Tech Stack:** Vue 3 Composition API, Pinia, TypeScript, Vue Router, vue-i18n, Vitest

---

## File Structure

```
console/src/
├── types/
│   └── recommendation.ts              # 类型定义 (NEW)
├── api/
│   └── recommendation.ts              # API 调用 (NEW)
├── stores/
│   ├── recommendation.ts              # 状态管理 (NEW)
│   └── __tests__/
│       └── recommendation.spec.ts     # Store 测试 (NEW)
├── views/
│   └── recommendations/
│       ├── RecommendationsView.vue    # 主页面 (NEW)
│       └── components/
│           ├── RecommendationNav.vue  # 左侧导航 (NEW)
│           ├── ProblemCard.vue        # 题目卡片 (NEW)
│           ├── TagFilter.vue          # 标签筛选 (NEW)
│           └── SimilarProblemSearch.vue # 相似题目搜索 (NEW)
├── router/
│   └── index.ts                       # 路由配置 (MODIFY)
├── features/sider/
│   ├── sidebar.data.ts                # 侧边栏数据 (MODIFY)
│   └── AppSidebar.vue                 # 侧边栏组件 (MODIFY)
└── i18n/locales/
    ├── zh-CN/
    │   ├── index.ts                   # 中文入口 (MODIFY)
    │   └── recommendation.ts          # 中文翻译 (NEW)
    └── en-US/
        ├── index.ts                   # 英文入口 (MODIFY)
        └── recommendation.ts          # 英文翻译 (NEW)
```

---

## Chunk 1: Foundation (Types + API)

### Task 1: 类型定义

**Files:**
- Create: `console/src/types/recommendation.ts`

- [ ] **Step 1: 创建类型定义文件**

```typescript
// console/src/types/recommendation.ts

/**
 * 推荐场景类型（与后端 RecommendScenario 枚举对应）
 */
export type RecommendScenario = 'DAILY' | 'SIMILAR' | 'WEAK_POINT' | 'CHALLENGE'

/**
 * 前端路由使用的推荐类型
 */
export type RecommendType = 'daily' | 'weak-points' | 'challenge' | 'similar'

/**
 * 单个推荐题目项（与后端 RecommendItem DTO 对应）
 */
export interface RecommendItem {
  /** 题目 ID */
  problemId: number
  /** URL 友好的标识 */
  slug: string
  /** 题目标题 */
  title: string
  /** 难度 (Easy/Medium/Hard) */
  difficulty: string
  /** 推荐分数 (0.0 - 1.0) */
  score: number
  /** 标签列表 */
  tags: string[]
  /** 推荐理由 */
  reason: string
}

/**
 * 推荐结果（与后端 RecommendResult DTO 对应）
 */
export interface RecommendResult {
  /** 推荐题目列表 */
  items: RecommendItem[]
  /** 总数 */
  totalCount: number
  /** 使用的推荐场景 */
  scenario: RecommendScenario
  /** 生成时间 */
  generatedAt: string
}

/**
 * API 响应包装（与后端 RecommendResponse DTO 对应）
 */
export interface RecommendResponse {
  success: boolean
  code: number
  message: string
  data: RecommendResult | null
}
```

- [ ] **Step 2: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 3: 提交**

```bash
git add console/src/types/recommendation.ts
git commit -m "feat(console): add recommendation types"
```

---

### Task 2: API 模块

**Files:**
- Create: `console/src/api/recommendation.ts`

- [ ] **Step 1: 创建 API 模块**

```typescript
// console/src/api/recommendation.ts
import { apiGet } from '@/utils/request'
import type { RecommendResponse, RecommendResult } from '@/types/recommendation'

/**
 * 推荐服务返回的数据结构（unwrap 后）
 * 注意：apiGet 已经自动 unwrap response.data，所以返回的是 data 部分
 */
type ApiResult = RecommendResponse

/**
 * 解析推荐响应
 * 后端返回格式：{ success, code, message, data: { items, totalCount, scenario, generatedAt } }
 */
function parseRecommendResponse(response: ApiResult): RecommendResult | null {
  if (!response.success || !response.data) {
    return null
  }
  return response.data
}

export const recommendationApi = {
  /**
   * 获取每日推荐
   */
  async getDaily(size = 10, includeSolved = false): Promise<RecommendResult | null> {
    const params = new URLSearchParams()
    params.append('size', String(size))
    if (includeSolved) params.append('includeSolved', 'true')

    const response = await apiGet<ApiResult>(`/recommendations/daily?${params}`)
    return parseRecommendResponse(response)
  },

  /**
   * 获取薄弱点推荐
   */
  async getWeakPoints(size = 10, tags?: string[]): Promise<RecommendResult | null> {
    const params = new URLSearchParams()
    params.append('size', String(size))
    if (tags && tags.length > 0) {
      params.append('tags', tags.join(','))
    }

    const response = await apiGet<ApiResult>(`/recommendations/weak-points?${params}`)
    return parseRecommendResponse(response)
  },

  /**
   * 获取挑战模式推荐
   */
  async getChallenge(size = 5): Promise<RecommendResult | null> {
    const params = new URLSearchParams()
    params.append('size', String(size))

    const response = await apiGet<ApiResult>(`/recommendations/challenge?${params}`)
    return parseRecommendResponse(response)
  },

  /**
   * 获取相似题目推荐
   */
  async getSimilar(problemId: number, size = 5): Promise<RecommendResult | null> {
    const params = new URLSearchParams()
    params.append('size', String(size))

    const response = await apiGet<ApiResult>(`/recommendations/similar/${problemId}?${params}`)
    return parseRecommendResponse(response)
  },

  /**
   * 健康检查
   */
  async healthCheck(): Promise<{ status: string }> {
    return apiGet<{ status: string }>('/recommendations/health')
  },
}
```

- [ ] **Step 2: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 3: 提交**

```bash
git add console/src/api/recommendation.ts
git commit -m "feat(console): add recommendation API module"
```

---

## Chunk 2: State Management (Store + Tests)

### Task 3: Pinia Store

**Files:**
- Create: `console/src/stores/recommendation.ts`
- Create: `console/src/stores/__tests__/recommendation.spec.ts`

- [ ] **Step 1: 创建 Store 文件**

```typescript
// console/src/stores/recommendation.ts
import { defineStore } from 'pinia'
import { recommendationApi } from '@/api/recommendation'
import type { RecommendItem } from '@/types/recommendation'

export const useRecommendationStore = defineStore('recommendation', {
  state: () => ({
    daily: [] as RecommendItem[],
    weakPoints: [] as RecommendItem[],
    challenge: [] as RecommendItem[],
    similar: [] as RecommendItem[],
    loading: false,
    error: null as string | null,
  }),

  actions: {
    _handleError(e: unknown, defaultMessage: string) {
      if (e instanceof Error) {
        this.error = e.message
      } else if (typeof e === 'string') {
        this.error = e
      } else {
        this.error = defaultMessage
      }
      console.error('Recommendation store error:', e)
    },

    async loadDaily(size = 10, includeSolved = false) {
      this.loading = true
      this.error = null
      try {
        const result = await recommendationApi.getDaily(size, includeSolved)
        this.daily = result?.items || []
      } catch (e) {
        this._handleError(e, 'Failed to load daily recommendations')
        this.daily = []
      } finally {
        this.loading = false
      }
    },

    async loadWeakPoints(size = 10, tags?: string[]) {
      this.loading = true
      this.error = null
      try {
        const result = await recommendationApi.getWeakPoints(size, tags)
        this.weakPoints = result?.items || []
      } catch (e) {
        this._handleError(e, 'Failed to load weak point recommendations')
        this.weakPoints = []
      } finally {
        this.loading = false
      }
    },

    async loadChallenge(size = 5) {
      this.loading = true
      this.error = null
      try {
        const result = await recommendationApi.getChallenge(size)
        this.challenge = result?.items || []
      } catch (e) {
        this._handleError(e, 'Failed to load challenge recommendations')
        this.challenge = []
      } finally {
        this.loading = false
      }
    },

    async loadSimilar(problemId: number, size = 5) {
      this.loading = true
      this.error = null
      try {
        const result = await recommendationApi.getSimilar(problemId, size)
        this.similar = result?.items || []
      } catch (e) {
        this._handleError(e, 'Failed to load similar problems')
        this.similar = []
      } finally {
        this.loading = false
      }
    },

    clearError() {
      this.error = null
    },
  },
})
```

- [ ] **Step 2: 创建 Store 测试文件**

```typescript
// console/src/stores/__tests__/recommendation.spec.ts
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useRecommendationStore } from '../recommendation'
import * as recommendationApi from '@/api/recommendation'

// Mock API module
vi.mock('@/api/recommendation', () => ({
  recommendationApi: {
    getDaily: vi.fn(),
    getWeakPoints: vi.fn(),
    getChallenge: vi.fn(),
    getSimilar: vi.fn(),
  },
}))

const mockRecommendItem = {
  problemId: 1,
  slug: 'two-sum',
  title: 'Two Sum',
  difficulty: 'Easy',
  score: 0.85,
  tags: ['Array', 'Hash Table'],
  reason: 'Based on your history',
}

const mockRecommendResult = {
  items: [mockRecommendItem],
  totalCount: 1,
  scenario: 'DAILY' as const,
  generatedAt: '2026-03-14T10:00:00Z',
}

describe('useRecommendationStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  describe('initial state', () => {
    it('should have empty arrays and null error by default', () => {
      const store = useRecommendationStore()

      expect(store.daily).toEqual([])
      expect(store.weakPoints).toEqual([])
      expect(store.challenge).toEqual([])
      expect(store.similar).toEqual([])
      expect(store.loading).toBe(false)
      expect(store.error).toBeNull()
    })
  })

  describe('loadDaily', () => {
    it('should load daily recommendations successfully', async () => {
      vi.mocked(recommendationApi.recommendationApi.getDaily).mockResolvedValue(mockRecommendResult)

      const store = useRecommendationStore()
      await store.loadDaily(10, false)

      expect(store.daily).toEqual([mockRecommendItem])
      expect(store.loading).toBe(false)
      expect(store.error).toBeNull()
    })

    it('should handle API error', async () => {
      vi.mocked(recommendationApi.recommendationApi.getDaily).mockRejectedValue(new Error('Network error'))

      const store = useRecommendationStore()
      await store.loadDaily(10, false)

      expect(store.daily).toEqual([])
      expect(store.loading).toBe(false)
      expect(store.error).toBe('Network error')
    })

    it('should handle null result', async () => {
      vi.mocked(recommendationApi.recommendationApi.getDaily).mockResolvedValue(null)

      const store = useRecommendationStore()
      await store.loadDaily(10, false)

      expect(store.daily).toEqual([])
    })
  })

  describe('loadWeakPoints', () => {
    it('should load weak points recommendations with tags', async () => {
      vi.mocked(recommendationApi.recommendationApi.getWeakPoints).mockResolvedValue({
        ...mockRecommendResult,
        scenario: 'WEAK_POINT',
      })

      const store = useRecommendationStore()
      await store.loadWeakPoints(10, ['Array'])

      expect(store.weakPoints).toEqual([mockRecommendItem])
      expect(recommendationApi.recommendationApi.getWeakPoints).toHaveBeenCalledWith(10, ['Array'])
    })
  })

  describe('loadChallenge', () => {
    it('should load challenge recommendations', async () => {
      vi.mocked(recommendationApi.recommendationApi.getChallenge).mockResolvedValue({
        ...mockRecommendResult,
        scenario: 'CHALLENGE',
      })

      const store = useRecommendationStore()
      await store.loadChallenge(5)

      expect(store.challenge).toEqual([mockRecommendItem])
    })
  })

  describe('loadSimilar', () => {
    it('should load similar problems', async () => {
      vi.mocked(recommendationApi.recommendationApi.getSimilar).mockResolvedValue({
        ...mockRecommendResult,
        scenario: 'SIMILAR',
      })

      const store = useRecommendationStore()
      await store.loadSimilar(1, 5)

      expect(store.similar).toEqual([mockRecommendItem])
      expect(recommendationApi.recommendationApi.getSimilar).toHaveBeenCalledWith(1, 5)
    })
  })

  describe('clearError', () => {
    it('should clear error', () => {
      const store = useRecommendationStore()
      store.error = 'Some error'
      store.clearError()

      expect(store.error).toBeNull()
    })
  })
})
```

- [ ] **Step 3: 运行测试**

Run: `cd console && pnpm vitest run stores/__tests__/recommendation.spec.ts`
Expected: All tests pass

- [ ] **Step 4: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 5: 提交**

```bash
git add console/src/stores/recommendation.ts console/src/stores/__tests__/recommendation.spec.ts
git commit -m "feat(console): add recommendation store with tests"
```

---

## Chunk 3: i18n (Internationalization)

### Task 4: 国际化文件

**Files:**
- Create: `console/src/i18n/locales/zh-CN/recommendation.ts`
- Create: `console/src/i18n/locales/en-US/recommendation.ts`
- Modify: `console/src/i18n/locales/zh-CN/index.ts`
- Modify: `console/src/i18n/locales/en-US/index.ts`

- [ ] **Step 1: 创建中文翻译文件**

```typescript
// console/src/i18n/locales/zh-CN/recommendation.ts
export default {
  sidebar: {
    recommendation: {
      types: '推荐类型',
      daily: '每日推荐',
      weakPoints: '薄弱点强化',
      challenge: '挑战模式',
      similar: '相似题目',
    },
  },
  recommendation: {
    title: '题目推荐',
    filter: {
      tags: '标签筛选',
      allTags: '全部标签',
      refresh: '刷新',
    },
    card: {
      score: '推荐指数',
      reason: '推荐理由',
    },
    empty: {
      daily: '暂无每日推荐，快去做几道题吧！',
      'weak-points': '暂无薄弱点数据，继续练习以获取更精准的推荐',
      challenge: '暂无挑战题目，请先完成更多中等难度题目',
      similar: '请搜索题目以查找相似题目',
    },
    search: {
      placeholder: '搜索题目...',
      noResults: '未找到相关题目',
    },
  },
}
```

- [ ] **Step 2: 创建英文翻译文件**

```typescript
// console/src/i18n/locales/en-US/recommendation.ts
export default {
  sidebar: {
    recommendation: {
      types: 'Recommendation Types',
      daily: 'Daily Practice',
      weakPoints: 'Weak Points',
      challenge: 'Challenge Mode',
      similar: 'Similar Problems',
    },
  },
  recommendation: {
    title: 'Problem Recommendations',
    filter: {
      tags: 'Filter by Tags',
      allTags: 'All Tags',
      refresh: 'Refresh',
    },
    card: {
      score: 'Score',
      reason: 'Reason',
    },
    empty: {
      daily: 'No daily recommendations yet. Start solving problems!',
      'weak-points': 'No weak point data yet. Keep practicing for better recommendations.',
      challenge: 'No challenge problems available. Complete more medium problems first.',
      similar: 'Search for a problem to find similar ones.',
    },
    search: {
      placeholder: 'Search problems...',
      noResults: 'No problems found',
    },
  },
}
```

- [ ] **Step 3: 更新中文 index.ts**

在 `console/src/i18n/locales/zh-CN/index.ts` 中添加导入：

```typescript
// 在文件顶部添加导入
import recommendation from "./recommendation";

// 在 export default 对象中添加 recommendation
export default {
  common,
  auth,
  sidebar,
  problem,
  contest,
  forum,
  personal,
  submission,
  errors,
  bookmark,
  markdown,
  solution,
  shortcuts,
  achievement,
  recommendation,  // 添加这行
} as const;
```

- [ ] **Step 4: 更新英文 index.ts**

在 `console/src/i18n/locales/en-US/index.ts` 中添加导入：

```typescript
// 在文件顶部添加导入
import recommendation from "./recommendation";

// 在 export default 对象中添加 recommendation
export default {
  // ... existing imports
  recommendation,  // 添加这行
} as const;
```

- [ ] **Step 5: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 6: 提交**

```bash
git add console/src/i18n/locales/zh-CN/recommendation.ts \
        console/src/i18n/locales/en-US/recommendation.ts \
        console/src/i18n/locales/zh-CN/index.ts \
        console/src/i18n/locales/en-US/index.ts
git commit -m "feat(console): add recommendation i18n translations"
```

---

## Chunk 4: UI Components

### Task 5: ProblemCard 组件

**Files:**
- Create: `console/src/views/recommendations/components/ProblemCard.vue`

- [ ] **Step 1: 创建 ProblemCard 组件**

```vue
<!-- console/src/views/recommendations/components/ProblemCard.vue -->
<script setup lang="ts">
import { computed } from 'vue'
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import type { RecommendItem } from '@/types/recommendation'

const props = defineProps<{
  item: RecommendItem
}>()

const difficultyVariant = computed(() => {
  switch (props.item.difficulty.toLowerCase()) {
    case 'easy': return 'default'
    case 'medium': return 'secondary'
    case 'hard': return 'destructive'
    default: return 'outline'
  }
})

const difficultyClass = computed(() => {
  switch (props.item.difficulty.toLowerCase()) {
    case 'easy': return 'text-green-600'
    case 'medium': return 'text-yellow-600'
    case 'hard': return 'text-red-600'
    default: return ''
  }
})
</script>

<template>
  <Card class="hover:border-primary/50 transition-colors">
    <CardHeader class="pb-2">
      <div class="flex items-start justify-between gap-4">
        <div class="flex-1">
          <CardTitle class="text-lg">
            <RouterLink
              :to="`/problems/${item.slug}`"
              class="hover:text-primary transition-colors"
            >
              {{ item.title }}
            </RouterLink>
          </CardTitle>
          <div class="mt-2 flex flex-wrap gap-2">
            <Badge :variant="difficultyVariant" :class="difficultyClass">
              {{ item.difficulty }}
            </Badge>
            <Badge
              v-for="tag in item.tags.slice(0, 3)"
              :key="tag"
              variant="outline"
            >
              {{ tag }}
            </Badge>
            <Badge v-if="item.tags.length > 3" variant="outline">
              +{{ item.tags.length - 3 }}
            </Badge>
          </div>
        </div>
        <div class="text-right shrink-0">
          <span class="text-xs text-muted-foreground">推荐指数</span>
          <div class="text-lg font-semibold text-primary">
            {{ item.score.toFixed(2) }}
          </div>
        </div>
      </div>
    </CardHeader>
    <CardContent>
      <p class="text-sm text-muted-foreground">{{ item.reason }}</p>
    </CardContent>
  </Card>
</template>
```

- [ ] **Step 2: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 3: 提交**

```bash
git add console/src/views/recommendations/components/ProblemCard.vue
git commit -m "feat(console): add ProblemCard component for recommendations"
```

---

### Task 6: RecommendationNav 组件

**Files:**
- Create: `console/src/views/recommendations/components/RecommendationNav.vue`

- [ ] **Step 1: 创建 RecommendationNav 组件**

```vue
<!-- console/src/views/recommendations/components/RecommendationNav.vue -->
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Sparkles, Target, Flame, GitBranch } from 'lucide-vue-next'
import { cn } from '@/lib/utils'
import type { RecommendType } from '@/types/recommendation'

const props = defineProps<{
  modelValue: RecommendType
}>()

const emit = defineEmits<{
  'update:modelValue': [value: RecommendType]
}>()

const router = useRouter()
const { t } = useI18n()

const navItems: { key: RecommendType; label: string; icon: typeof Sparkles }[] = [
  { key: 'daily', label: 'sidebar.recommendation.daily', icon: Sparkles },
  { key: 'weak-points', label: 'sidebar.recommendation.weakPoints', icon: Target },
  { key: 'challenge', label: 'sidebar.recommendation.challenge', icon: Flame },
  { key: 'similar', label: 'sidebar.recommendation.similar', icon: GitBranch },
]

function navigate(key: RecommendType) {
  // 导航由路由处理，不需要 emit（父组件使用 computed 属性）
  router.push({ name: `recommendations-${key}` })
}
</script>

<template>
  <nav class="w-48 shrink-0">
    <div class="sticky top-4 space-y-1">
      <button
        v-for="item in navItems"
        :key="item.key"
        :class="cn(
          'flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors',
          modelValue === item.key
            ? 'bg-primary text-primary-foreground'
            : 'hover:bg-muted'
        )"
        @click="navigate(item.key)"
      >
        <component :is="item.icon" class="h-4 w-4" />
        <span>{{ t(item.label) }}</span>
      </button>
    </div>
  </nav>
</template>
```

- [ ] **Step 2: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 3: 提交**

```bash
git add console/src/views/recommendations/components/RecommendationNav.vue
git commit -m "feat(console): add RecommendationNav component"
```

---

### Task 7: TagFilter 组件

**Files:**
- Create: `console/src/views/recommendations/components/TagFilter.vue`

- [ ] **Step 1: 创建 TagFilter 组件**

```vue
<!-- console/src/views/recommendations/components/TagFilter.vue -->
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { RefreshCw } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import {
  Combobox,
  ComboboxAnchor,
  ComboboxTrigger,
  ComboboxContent,
  ComboboxItem,
  ComboboxEmpty,
} from '@/components/ui/combobox'

const selectedTags = defineModel<string[]>({ default: () => [] })

const emit = defineEmits<{
  refresh: []
}>()

const { t } = useI18n()

// 可用标签列表（可从 API 获取或使用预定义列表）
const availableTags = ref<string[]>([
  '数组', '字符串', '链表', '树', '图', '动态规划',
  '贪心', '二分查找', '深度优先搜索', '广度优先搜索',
  '哈希表', '栈', '队列', '堆', '排序', '回溯',
])
</script>

<template>
  <div class="mb-6 flex items-center gap-4">
    <Combobox v-model="selectedTags" multiple>
      <ComboboxAnchor>
        <ComboboxTrigger class="w-[200px]">
          {{ selectedTags.length > 0 ? selectedTags.join(', ') : t('recommendation.filter.tags') }}
        </ComboboxTrigger>
      </ComboboxAnchor>
      <ComboboxContent>
        <ComboboxEmpty>
          {{ t('recommendation.search.noResults') }}
        </ComboboxEmpty>
        <ComboboxItem
          v-for="tag in availableTags"
          :key="tag"
          :value="tag"
        >
          {{ tag }}
        </ComboboxItem>
      </ComboboxContent>
    </Combobox>

    <Button variant="outline" size="sm" @click="emit('refresh')">
      <RefreshCw class="mr-2 h-4 w-4" />
      {{ t('recommendation.filter.refresh') }}
    </Button>
  </div>
</template>
```

- [ ] **Step 2: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 3: 提交**

```bash
git add console/src/views/recommendations/components/TagFilter.vue
git commit -m "feat(console): add TagFilter component"
```

---

### Task 8: SimilarProblemSearch 组件

**Files:**
- Create: `console/src/views/recommendations/components/SimilarProblemSearch.vue`

- [ ] **Step 1: 创建 SimilarProblemSearch 组件**

```vue
<!-- console/src/views/recommendations/components/SimilarProblemSearch.vue -->
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDebounceFn } from '@vueuse/core'
import { searchProblems } from '@/api/problem'
import type { Problem } from '@/types/problem'
import {
  Combobox,
  ComboboxAnchor,
  ComboboxInput,
  ComboboxContent,
  ComboboxItem,
  ComboboxEmpty,
} from '@/components/ui/combobox'

const emit = defineEmits<{
  select: [problemId: number]
}>()

const { t } = useI18n()

const searchQuery = ref('')
const searchResults = ref<Problem[]>([])
const selectedProblem = ref<Problem | null>(null)
const isSearching = ref(false)

// 搜索题目（带防抖）
const debouncedSearch = useDebounceFn(async (query: string) => {
  if (query.length < 2) {
    searchResults.value = []
    return
  }

  isSearching.value = true
  try {
    searchResults.value = await searchProblems(query)
  } finally {
    isSearching.value = false
  }
}, 300)

// 监听搜索输入变化
watch(searchQuery, debouncedSearch)

// 监听选择变化
watch(selectedProblem, (problem) => {
  if (problem) {
    emit('select', problem.id)
  }
})
</script>

<template>
  <div class="mb-6">
    <Combobox v-model="selectedProblem">
      <ComboboxAnchor>
        <ComboboxInput
          v-model="searchQuery"
          :placeholder="t('recommendation.search.placeholder')"
        />
      </ComboboxAnchor>
      <ComboboxContent>
        <ComboboxEmpty>
          {{ isSearching ? '...' : t('recommendation.search.noResults') }}
        </ComboboxEmpty>
        <ComboboxItem
          v-for="problem in searchResults"
          :key="problem.id"
          :value="problem"
        >
          {{ problem.title }}
        </ComboboxItem>
      </ComboboxContent>
    </Combobox>
  </div>
</template>
```

- [ ] **Step 2: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 3: 提交**

```bash
git add console/src/views/recommendations/components/SimilarProblemSearch.vue
git commit -m "feat(console): add SimilarProblemSearch component"
```

---

### Task 9: RecommendationsView 主页面

**Files:**
- Create: `console/src/views/recommendations/RecommendationsView.vue`

- [ ] **Step 1: 创建主页面组件**

```vue
<!-- console/src/views/recommendations/RecommendationsView.vue -->
<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useRecommendationStore } from '@/stores/recommendation'
import RecommendationNav from './components/RecommendationNav.vue'
import ProblemCard from './components/ProblemCard.vue'
import TagFilter from './components/TagFilter.vue'
import SimilarProblemSearch from './components/SimilarProblemSearch.vue'
import { Skeleton } from '@/components/ui/skeleton'
import type { RecommendType, RecommendItem } from '@/types/recommendation'

const route = useRoute()
const { t } = useI18n()
const store = useRecommendationStore()

// 从路由推断当前类型
const currentType = computed<RecommendType>(() => {
  const path = route.path
  if (path.includes('weak-points')) return 'weak-points'
  if (path.includes('challenge')) return 'challenge'
  if (path.includes('similar')) return 'similar'
  return 'daily'
})

// 选中的标签
const selectedTags = ref<string[]>([])

// 相似题目搜索选中的题目 ID
const selectedProblemId = ref<number | null>(null)

// 当前类型的推荐数据
const recommendations = computed<RecommendItem[]>(() => {
  switch (currentType.value) {
    case 'daily': return store.daily
    case 'weak-points': return store.weakPoints
    case 'challenge': return store.challenge
    case 'similar': return store.similar
    default: return []
  }
})

// 加载数据
async function loadRecommendations() {
  switch (currentType.value) {
    case 'daily':
      await store.loadDaily(10, false)
      break
    case 'weak-points':
      await store.loadWeakPoints(10, selectedTags.value.length > 0 ? selectedTags.value : undefined)
      break
    case 'challenge':
      await store.loadChallenge(5)
      break
    case 'similar':
      if (selectedProblemId.value) {
        await store.loadSimilar(selectedProblemId.value, 5)
      }
      break
  }
}

// 监听类型变化
watch(currentType, () => {
  selectedTags.value = []
  selectedProblemId.value = null
  loadRecommendations()
}, { immediate: true })

// 处理相似题目选择
function handleProblemSelect(problemId: number) {
  selectedProblemId.value = problemId
  loadRecommendations()
}
</script>

<template>
  <div class="flex gap-6">
    <!-- 左侧导航 (currentType 是 computed，所以只用 :model-value，不用 v-model) -->
    <RecommendationNav :model-value="currentType" />

    <!-- 右侧内容区 -->
    <div class="flex-1">
      <h1 class="mb-6 text-2xl font-bold">{{ t('recommendation.title') }}</h1>

      <!-- 筛选栏 -->
      <SimilarProblemSearch
        v-if="currentType === 'similar'"
        @select="handleProblemSelect"
      />
      <TagFilter
        v-else
        v-model="selectedTags"
        @refresh="loadRecommendations"
      />

      <!-- 加载状态 -->
      <div v-if="store.loading" class="grid gap-4">
        <Skeleton v-for="i in 3" :key="i" class="h-32 rounded-lg" />
      </div>

      <!-- 错误状态 -->
      <div v-else-if="store.error" class="rounded-lg border border-destructive/50 bg-destructive/10 p-4 text-destructive">
        {{ store.error }}
      </div>

      <!-- 空状态 -->
      <div v-else-if="recommendations.length === 0" class="rounded-lg border bg-muted/50 p-8 text-center text-muted-foreground">
        <p v-if="currentType === 'similar' && !selectedProblemId">
          {{ t('recommendation.empty.similar') }}
        </p>
        <p v-else>
          {{ t(`recommendation.empty.${currentType}`) }}
        </p>
      </div>

      <!-- 推荐列表 -->
      <div v-else class="grid gap-4">
        <ProblemCard
          v-for="item in recommendations"
          :key="item.problemId"
          :item="item"
        />
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 2: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 3: 提交**

```bash
git add console/src/views/recommendations/RecommendationsView.vue
git commit -m "feat(console): add RecommendationsView main page"
```

---

## Chunk 5: Routing & Sidebar

### Task 10: 路由配置

**Files:**
- Modify: `console/src/router/index.ts`

- [ ] **Step 1: 添加推荐路由**

在 `console/src/router/index.ts` 中添加推荐路由配置：

1. 在文件中找到 `const router = createRouter({...})` 之前的位置
2. 添加 recommendationRoutes 定义：

```typescript
// 在 personalRoutes 定义之后添加
const recommendationRoutes: RouteRecordRaw = {
  path: '/recommendations',
  component: () => import('@/features/sider/AppLayout.vue'),
  meta: { requiresAuth: true },
  children: [
    {
      path: '',
      redirect: { name: 'recommendations-daily' },
    },
    {
      path: 'daily',
      name: 'recommendations-daily',
      component: () => import('@/views/recommendations/RecommendationsView.vue'),
    },
    {
      path: 'weak-points',
      name: 'recommendations-weak-points',
      component: () => import('@/views/recommendations/RecommendationsView.vue'),
    },
    {
      path: 'challenge',
      name: 'recommendations-challenge',
      component: () => import('@/views/recommendations/RecommendationsView.vue'),
    },
    {
      path: 'similar',
      name: 'recommendations-similar',
      component: () => import('@/views/recommendations/RecommendationsView.vue'),
    },
  ],
}
```

3. 在 routes 数组中添加 recommendationRoutes：

```typescript
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // ... existing routes
    recommendationRoutes,  // 添加这行
  ],
})
```

- [ ] **Step 2: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 3: 提交**

```bash
git add console/src/router/index.ts
git commit -m "feat(console): add recommendation routes"
```

---

### Task 11: 侧边栏数据

**Files:**
- Modify: `console/src/features/sider/sidebar.data.ts`
- Modify: `console/src/features/sider/AppSidebar.vue`

- [ ] **Step 1: 添加推荐侧边栏数据**

在 `console/src/features/sider/sidebar.data.ts` 中：

1. 在 lucide-vue-next 导入中添加新图标：

```typescript
import {
  // ... existing imports
  Sparkles,  // 添加
  Target,    // 添加
  Flame,     // 添加
  GitBranch, // 添加
} from "lucide-vue-next";
```

2. 在文件末尾添加 recommendationSidebarData：

```typescript
export const recommendationSidebarData: SidebarSection[] = [
  {
    name: 'sidebar.recommendation.types',
    items: [
      {
        title: 'sidebar.recommendation.daily',
        url: '/recommendations/daily',
        icon: Sparkles,
      },
      {
        title: 'sidebar.recommendation.weakPoints',
        url: '/recommendations/weak-points',
        icon: Target,
      },
      {
        title: 'sidebar.recommendation.challenge',
        url: '/recommendations/challenge',
        icon: Flame,
      },
      {
        title: 'sidebar.recommendation.similar',
        url: '/recommendations/similar',
        icon: GitBranch,
      },
    ],
  },
]
```

- [ ] **Step 2: 更新 AppSidebar.vue**

在 `console/src/features/sider/AppSidebar.vue` 中：

1. 添加 recommendationSidebarData 导入：

```typescript
import {
  forumSidebarData,
  problemSidebarData,
  contestSidebarData,
  personalSidebarData,
  recommendationSidebarData,  // 添加这行
} from "@/features/sider/sidebar.data";
```

2. 添加推荐路由检测：

```typescript
// 在现有 computed 定义之后添加
const isRecommendationContext = computed(() => route.path.startsWith('/recommendations'))
```

3. 更新 currentSidebarData：

```typescript
const currentSidebarData = computed(() => {
  if (isRecommendationContext.value) {  // 添加这个条件
    return recommendationSidebarData
  }
  if (isProblemContext.value) {
    return problemSidebarData
  }
  // ... rest of existing conditions
})
```

- [ ] **Step 3: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 4: 提交**

```bash
git add console/src/features/sider/sidebar.data.ts console/src/features/sider/AppSidebar.vue
git commit -m "feat(console): add recommendation sidebar navigation"
```

---

## Chunk 6: Integration Testing

### Task 12: 端到端验证

- [ ] **Step 1: 启动开发服务器**

Run: `cd console && pnpm dev`

- [ ] **Step 2: 验证路由访问**

在浏览器中访问以下路由，确认页面正常加载：
- `http://localhost:9002/recommendations` → 应重定向到 `/recommendations/daily`
- `http://localhost:9002/recommendations/daily`
- `http://localhost:9002/recommendations/weak-points`
- `http://localhost:9002/recommendations/challenge`
- `http://localhost:9002/recommendations/similar`

- [ ] **Step 3: 验证侧边栏导航**

确认：
- 访问 `/recommendations/*` 路由时，侧边栏显示推荐类型导航
- 点击侧边栏项可以正常切换路由
- 当前选中项有高亮样式

- [ ] **Step 4: 验证 API 调用**

打开浏览器开发者工具 Network 面板，确认：
- 每日推荐页面调用 `GET /recommendations/daily`
- 薄弱点页面调用 `GET /recommendations/weak-points`
- 挑战模式页面调用 `GET /recommendations/challenge`
- 相似题目页面搜索后调用 `GET /recommendations/similar/{problemId}`

- [ ] **Step 5: 验证加载状态**

确认：
- 数据加载时显示 Skeleton 加载状态
- 加载完成后显示推荐列表
- 无数据时显示空状态提示

- [ ] **Step 6: 运行完整测试套件**

Run: `cd console && pnpm test`
Expected: All tests pass

- [ ] **Step 7: 运行 lint 检查**

Run: `cd console && pnpm lint`
Expected: No errors

- [ ] **Step 8: 运行类型检查**

Run: `cd console && pnpm type-check`
Expected: No errors

- [ ] **Step 9: 最终提交**

```bash
git add -A
git commit -m "feat(console): complete recommendation page implementation

- Add recommendation types, API module, and store
- Add RecommendationsView with 4 recommendation scenarios
- Add ProblemCard, TagFilter, SimilarProblemSearch components
- Add recommendation routes and sidebar navigation
- Add i18n translations (zh-CN, en-US)
- Add store unit tests"
```

---

## Summary

| Task | Description | Files |
|------|-------------|-------|
| 1 | 类型定义 | `types/recommendation.ts` |
| 2 | API 模块 | `api/recommendation.ts` |
| 3 | Store + 测试 | `stores/recommendation.ts`, `stores/__tests__/recommendation.spec.ts` |
| 4 | 国际化 | `i18n/locales/*/recommendation.ts`, `i18n/locales/*/index.ts` |
| 5 | ProblemCard | `views/recommendations/components/ProblemCard.vue` |
| 6 | RecommendationNav | `views/recommendations/components/RecommendationNav.vue` |
| 7 | TagFilter | `views/recommendations/components/TagFilter.vue` |
| 8 | SimilarProblemSearch | `views/recommendations/components/SimilarProblemSearch.vue` |
| 9 | 主页面 | `views/recommendations/RecommendationsView.vue` |
| 10 | 路由配置 | `router/index.ts` |
| 11 | 侧边栏 | `features/sider/sidebar.data.ts`, `features/sider/AppSidebar.vue` |
| 12 | 集成测试 | Manual verification |

---

*文档版本: 1.0*
*创建日期: 2026-03-14*
