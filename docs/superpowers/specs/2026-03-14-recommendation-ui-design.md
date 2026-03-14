# 推荐页面 UI 设计文档

> Console 前端推荐题目展示功能

## 一、概述

### 1.1 需求背景

后端已实现完整的推荐 API（每日推荐、相似题目、薄弱点强化、挑战模式），但前端缺少展示入口。本设计为 console 前端新增独立推荐页面，展示个性化推荐题目。

### 1.2 设计决策

| 决策项 | 选择 | 理由 |
|--------|------|------|
| 页面位置 | 独立页面 `/recommendations` | 用户明确需求，便于后续扩展 |
| 布局方式 | 左侧导航 + 内容区 | 符合项目现有 UI 风格，导航清晰 |
| 筛选功能 | 标签筛选 | 满足基础筛选需求 |

### 1.3 前置条件

在开始实现前，需要确认以下文件/组件已存在：

- [x] `console/src/utils/request.ts` - HTTP 请求工具 (apiGet, apiPost 等)
- [x] `console/src/components/ui/combobox/` - Combobox 组件系列
- [x] `console/src/components/ui/card/` - Card 组件系列
- [x] `console/src/components/ui/badge/` - Badge 组件
- [x] `console/src/components/ui/skeleton/` - Skeleton 组件

需要新建的类型文件：
- [ ] `console/src/types/recommendation.ts` - 推荐相关类型定义

---

## 二、页面设计

### 2.1 页面布局

```
┌─────────────────────────────────────────────────────────────────────┐
│ [≡] UltiCode  [题目集] [论坛] [竞赛]           [搜索] [通知] [头像]   │
├────────────┬────────────────────────────────────────────────────────┤
│  用户头像   │                                                        │
│  ────────  │  📚 题目推荐                                           │
│            │  ─────────────────────────────────────────────────────│
│  推荐类型   │  [标签筛选: ▼ 全部标签]    [刷新]                      │
│  ────────  │  ─────────────────────────────────────────────────────│
│  ◉ 每日推荐 │                                                        │
│  ○ 薄弱点   │  ┌──────────────────────────────────────────────────┐│
│  ○ 挑战模式 │  │  📝 两数之和                    Easy    评分 0.85  ││
│  ○ 相似题目 │  │  标签: 数组, 哈希表                                ││
│            │  │  推荐理由: 基于您的做题历史，推荐练习哈希表技巧        ││
│            │  └──────────────────────────────────────────────────┘│
├────────────┴────────────────────────────────────────────────────────┤
│  Sidebar (根据路由自动切换)                                          │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 推荐场景

| 场景 | 路由 | API | 说明 |
|------|------|-----|------|
| 每日推荐 | `/recommendations/daily` | `GET /recommendations/daily` | 个性化每日练习推荐 |
| 薄弱点强化 | `/recommendations/weak-points` | `GET /recommendations/weak-points` | 针对薄弱标签的强化练习 |
| 挑战模式 | `/recommendations/challenge` | `GET /recommendations/challenge` | 高于当前能力的进阶题目 |
| 相似题目 | `/recommendations/similar` | `GET /recommendations/similar/:problemId` | 与指定题目相似的题目 |

---

## 三、文件结构

```
console/src/
├── views/
│   └── recommendations/
│       ├── RecommendationsView.vue      # 主页面 (左侧导航 + 右侧内容)
│       └── components/
│           ├── RecommendationNav.vue    # 左侧推荐类型导航
│           ├── ProblemCard.vue          # 推荐题目卡片
│           ├── TagFilter.vue            # 标签筛选下拉
│           └── SimilarProblemSearch.vue # 相似题目搜索输入（仅 similar 类型）
├── api/
│   └── recommendation.ts                # 推荐相关 API 调用
├── stores/
│   └── recommendation.ts                # 推荐状态管理
├── types/
│   └── recommendation.ts                # 推荐相关类型定义（需新建）
├── features/sider/
│   └── sidebar.data.ts                  # 新增 recommendationSidebarData
└── i18n/locales/
    ├── zh-CN/
    │   ├── index.ts
    │   └── recommendation.ts            # 中文翻译（需新建）
    └── en-US/
        ├── index.ts
        └── recommendation.ts            # 英文翻译（需新建）
```

---

## 四、类型定义

### 4.1 recommendation.ts (需新建)

```typescript
// types/recommendation.ts

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

---

## 五、组件设计

### 5.1 RecommendationsView.vue

主页面组件，包含：
- 左侧推荐类型导航（RecommendationNav）
- 右侧内容区（根据当前类型显示对应推荐列表）
- 顶部筛选栏（TagFilter 或 SimilarProblemSearch）
- 加载状态、错误状态、空状态处理

```vue
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
    <!-- 左侧导航 -->
    <RecommendationNav v-model="currentType" />

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

### 5.2 RecommendationNav.vue

左侧导航组件，展示 4 种推荐类型。

```vue
<script setup lang="ts">
import { computed } from 'vue'
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
  emit('update:modelValue', key)
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

### 5.3 ProblemCard.vue

推荐题目卡片，显示题目信息和推荐理由。

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
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

### 5.4 TagFilter.vue

标签筛选下拉组件。

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { RefreshCw } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import {
  Combobox,
  ComboboxAnchor,
  ComboboxTrigger,
  ComboboxInput,
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

// TODO: 从 API 获取标签列表
// onMounted(async () => {
//   availableTags.value = await fetchTags()
// })
</script>

<template>
  <div class="mb-6 flex items-center gap-4">
    <Combobox v-model="selectedTags" multiple>
      <ComboboxAnchor>
        <ComboboxTrigger class="w-[200px]">
          {{ t('recommendation.filter.tags') }}
        </ComboboxTrigger>
      </ComboboxAnchor>
      <ComboboxContent>
        <ComboboxInput :placeholder="t('recommendation.search.placeholder')" />
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

### 5.5 SimilarProblemSearch.vue

相似题目搜索组件（仅 similar 类型显示）。

```vue
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
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

// 搜索题目
async function handleSearch(query: string) {
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
}

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
          @update:model-value="handleSearch"
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

---

## 六、API 设计

### 6.1 recommendation.ts

```typescript
// api/recommendation.ts
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

---

## 七、Store 设计

### 7.1 recommendation.ts

```typescript
// stores/recommendation.ts
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

---

## 八、路由配置

### 8.1 路由定义

```typescript
// router/index.ts 新增
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

// 在 routes 数组中添加
routes: [
  // ... existing routes
  recommendationRoutes,
]
```

---

## 九、侧边栏数据

### 9.1 sidebar.data.ts 新增

```typescript
import { Sparkles, Target, Flame, GitBranch } from 'lucide-vue-next'

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

### 9.2 AppSidebar.vue 更新

```typescript
// 在 AppSidebar.vue 中添加推荐路由检测
const isRecommendationContext = computed(() => route.path.startsWith('/recommendations'))

const currentSidebarData = computed(() => {
  if (isRecommendationContext.value) {
    return recommendationSidebarData
  }
  // ... existing conditions
})
```

---

## 十、国际化

### 10.1 中文 (zh-CN/recommendation.ts)

```typescript
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

### 10.2 英文 (en-US/recommendation.ts)

```typescript
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

### 10.3 导入到 index.ts

```typescript
// zh-CN/index.ts
import recommendation from './recommendation'

export default {
  // ... existing translations
  ...recommendation,
}

// en-US/index.ts
import recommendation from './recommendation'

export default {
  // ... existing translations
  ...recommendation,
}
```

---

## 十一、实施检查清单

### 11.1 类型定义
- [ ] 创建 `types/recommendation.ts`

### 11.2 API 层
- [ ] 创建 `api/recommendation.ts`

### 11.3 Store 层
- [ ] 创建 `stores/recommendation.ts`

### 11.4 视图组件
- [ ] 创建 `views/recommendations/RecommendationsView.vue`
- [ ] 创建子组件:
  - [ ] `components/RecommendationNav.vue`
  - [ ] `components/ProblemCard.vue`
  - [ ] `components/TagFilter.vue`
  - [ ] `components/SimilarProblemSearch.vue`

### 11.5 路由配置
- [ ] 更新 `router/index.ts` 添加推荐路由

### 11.6 侧边栏
- [ ] 更新 `sidebar.data.ts` 添加 `recommendationSidebarData`
- [ ] 更新 `AppSidebar.vue` 添加推荐路由检测

### 11.7 国际化
- [ ] 创建 `i18n/locales/zh-CN/recommendation.ts`
- [ ] 创建 `i18n/locales/en-US/recommendation.ts`
- [ ] 更新各 `index.ts` 导入翻译

### 11.8 测试
- [ ] 测试每日推荐场景
- [ ] 测试薄弱点推荐场景
- [ ] 测试挑战模式场景
- [ ] 测试相似题目搜索场景
- [ ] 测试标签筛选功能
- [ ] 测试加载/错误/空状态

---

*文档版本: 1.1*
*创建日期: 2026-03-14*
*更新日期: 2026-03-14*
*更新内容: 修复审查问题 (API 路径、类型定义、错误处理、加载状态)*
