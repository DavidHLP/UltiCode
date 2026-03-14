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
│           └── SimilarProblemSearch.vue # 相似题目搜索输入
├── api/
│   └── recommendation.ts                # 推荐相关 API 调用
├── stores/
│   └── recommendation.ts                # 推荐状态管理
├── types/
│   └── recommendation.ts                # 推荐相关类型定义
├── features/sider/
│   └── sidebar.data.ts                  # 新增 recommendationSidebarData
└── i18n/locales/
    ├── zh-CN.ts                         # 中文翻译
    └── en-US.ts                         # 英文翻译
```

---

## 四、组件设计

### 4.1 RecommendationsView.vue

主页面组件，包含：
- 左侧推荐类型导航（RecommendationNav）
- 右侧内容区（根据当前类型显示对应推荐列表）
- 顶部筛选栏（TagFilter）

```vue
<script setup lang="ts">
// 状态管理
const currentType = ref<'daily' | 'weak-points' | 'challenge' | 'similar'>('daily')
const selectedTags = ref<string[]>([])

// 根据 currentType 加载推荐数据
// 监听 selectedTags 变化重新加载
</script>

<template>
  <div class="flex gap-6">
    <RecommendationNav v-model="currentType" />
    <div class="flex-1">
      <TagFilter v-model="selectedTags" @refresh="loadRecommendations" />
      <!-- 推荐列表 -->
      <div class="grid gap-4">
        <ProblemCard v-for="item in recommendations" :key="item.problemId" :item="item" />
      </div>
    </div>
  </div>
</template>
```

### 4.2 RecommendationNav.vue

左侧导航组件，展示 4 种推荐类型。

```vue
<script setup lang="ts">
const navItems = [
  { key: 'daily', label: '每日推荐', icon: Sparkles },
  { key: 'weak-points', label: '薄弱点强化', icon: Target },
  { key: 'challenge', label: '挑战模式', icon: Flame },
  { key: 'similar', label: '相似题目', icon: GitBranch },
]
</script>

<template>
  <nav class="w-48 shrink-0">
    <div class="sticky top-4 space-y-1">
      <button
        v-for="item in navItems"
        :key="item.key"
        :class="[{ 'bg-accent': modelValue === item.key }]"
        @click="$emit('update:modelValue', item.key)"
      >
        <component :is="item.icon" />
        <span>{{ item(item.label) }}</span>
      </button>
    </div>
  </nav>
</template>
```

### 4.3 ProblemCard.vue

推荐题目卡片，显示题目信息和推荐理由。

```vue
<script setup lang="ts">
defineProps<{
  item: RecommendItem
}>()
</script>

<template>
  <Card class="hover:border-primary transition-colors">
    <CardHeader class="pb-2">
      <div class="flex items-start justify-between">
        <div>
          <CardTitle class="text-lg">
            <RouterLink :to="`/problems/${item.slug}`">
              {{ item.title }}
            </RouterLink>
          </CardTitle>
          <div class="mt-1 flex gap-2">
            <Badge :variant="difficultyVariant">{{ item.difficulty }}</Badge>
            <Badge v-for="tag in item.tags.slice(0, 3)" :key="tag" variant="outline">
              {{ tag }}
            </Badge>
          </div>
        </div>
        <div class="text-right">
          <span class="text-sm text-muted-foreground">推荐指数</span>
          <div class="text-lg font-semibold text-primary">{{ item.score.toFixed(2) }}</div>
        </div>
      </div>
    </CardHeader>
    <CardContent>
      <p class="text-sm text-muted-foreground">{{ item.reason }}</p>
    </CardContent>
  </Card>
</template>
```

### 4.4 TagFilter.vue

标签筛选下拉组件。

```vue
<script setup lang="ts">
// 从 API 获取可用标签列表
const availableTags = ref<string[]>([])

// 多选标签
const selectedTags = defineModel<string[]>('modelValue', { default: [] })
</script>

<template>
  <div class="mb-4 flex items-center gap-4">
    <Combobox v-model="selectedTags" multiple>
      <ComboboxTrigger>标签筛选</ComboboxTrigger>
      <ComboboxContent>
        <ComboboxItem v-for="tag in availableTags" :key="tag" :value="tag">
          {{ tag }}
        </ComboboxItem>
      </ComboboxContent>
    </Combobox>
    <Button variant="outline" size="sm" @click="$emit('refresh')">
      <RefreshCw class="mr-2 h-4 w-4" />
      刷新
    </Button>
  </div>
</template>
```

### 4.5 SimilarProblemSearch.vue

相似题目搜索组件（仅 challenge 类型显示）。

```vue
<script setup lang="ts">
const searchQuery = ref('')
const searchResults = ref<Problem[]>([])
const selectedProblem = ref<Problem | null>(null)

// 搜索题目
async function handleSearch(query: string) {
  if (query.length < 2) return
  searchResults.value = await searchProblems(query)
}
</script>

<template>
  <div class="mb-4">
    <Combobox v-model="selectedProblem">
      <ComboboxInput placeholder="搜索题目..." @input="handleSearch" />
      <ComboboxContent>
        <ComboboxItem v-for="problem in searchResults" :key="problem.id" :value="problem">
          {{ problem.title }}
        </ComboboxItem>
      </ComboboxContent>
    </Combobox>
  </div>
</template>
```

---

## 五、API 设计

### 5.1 recommendation.ts

```typescript
// api/recommendation.ts
import { http } from '@/lib/http'
import type { RecommendItem, RecommendResult, RecommendScenario } from '@/types/recommendation'

export const recommendationApi = {
  // 每日推荐
  getDaily: (size = 10, includeSolved = false) =>
    http.get<RecommendResult>('/recommendations/daily', {
      params: { size, includeSolved }
    }),

  // 薄弱点推荐
  getWeakPoints: (size = 10, tags?: string[]) =>
    http.get<RecommendResult>('/recommendations/weak-points', {
      params: { size, tags: tags?.join(',') }
    }),

  // 挑战模式
  getChallenge: (size = 5) =>
    http.get<RecommendResult>('/recommendations/challenge', {
      params: { size }
    }),

  // 相似题目
  getSimilar: (problemId: number, size = 5) =>
    http.get<RecommendResult>(`/recommendations/similar/${problemId}`, {
      params: { size }
    }),

  // 健康检查
  healthCheck: () =>
    http.get('/recommendations/health'),
}
```

---

## 六、Store 设计

### 6.1 recommendation.ts

```typescript
// stores/recommendation.ts
import { defineStore } from 'pinia'
import { recommendationApi } from '@/api/recommendation'
import type { RecommendItem, RecommendScenario } from '@/types/recommendation'

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
    async loadDaily(size = 10, includeSolved = false) {
      this.loading = true
      try {
        const res = await recommendationApi.getDaily(size, includeSolved)
        if (res.data.success) {
          this.daily = res.data.data?.items || []
        }
      } finally {
        this.loading = false
      }
    },

    async loadWeakPoints(size = 10, tags?: string[]) {
      this.loading = true
      try {
        const res = await recommendationApi.getWeakPoints(size, tags)
        if (res.data.success) {
          this.weakPoints = res.data.data?.items || []
        }
      } finally {
        this.loading = false
      }
    },

    async loadChallenge(size = 5) {
      this.loading = true
      try {
        const res = await recommendationApi.getChallenge(size)
        if (res.data.success) {
          this.challenge = res.data.data?.items || []
        }
      } finally {
        this.loading = false
      }
    },

    async loadSimilar(problemId: number, size = 5) {
      this.loading = true
      try {
        const res = await recommendationApi.getSimilar(problemId, size)
        if (res.data.success) {
          this.similar = res.data.data?.items || []
        }
      } finally {
        this.loading = false
      }
    },
  },
})
```

---

## 七、路由配置

### 7.1 路由定义

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
      props: { type: 'daily' },
    },
    {
      path: 'weak-points',
      name: 'recommendations-weak-points',
      component: () => import('@/views/recommendations/RecommendationsView.vue'),
      props: { type: 'weak-points' },
    },
    {
      path: 'challenge',
      name: 'recommendations-challenge',
      component: () => import('@/views/recommendations/RecommendationsView.vue'),
      props: { type: 'challenge' },
    },
    {
      path: 'similar',
      name: 'recommendations-similar',
      component: () => import('@/views/recommendations/RecommendationsView.vue'),
      props: { type: 'similar' },
    },
  ],
}
```

---

## 八、侧边栏数据

### 8.1 sidebar.data.ts 新增

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

---

## 九、国际化

### 9.1 中文 (zh-CN.ts)

```typescript
sidebar: {
  recommendation: {
    types: '推荐类型',
    daily: '每日推荐',
    weakPoints: '薄弱点强化',
    challenge: '挑战模式',
    similar: '相似题目',
  },
}

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
    weakPoints: '暂无薄弱点数据，继续练习以获取更精准的推荐',
    challenge: '暂无挑战题目，请先完成更多中等难度题目',
    similar: '请搜索题目以查找相似题目',
  },
  search: {
    placeholder: '搜索题目...',
    noResults: '未找到相关题目',
  },
}
```

### 9.2 英文 (en-US.ts)

```typescript
sidebar: {
  recommendation: {
    types: 'Recommendation Types',
    daily: 'Daily Practice',
    weakPoints: 'Weak Points',
    challenge: 'Challenge Mode',
    similar: 'Similar Problems',
  },
}

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
    weakPoints: 'No weak point data yet. Keep practicing for better recommendations.',
    challenge: 'No challenge problems available. Complete more medium problems first.',
    similar: 'Search for a problem to find similar ones.',
  },
  search: {
    placeholder: 'Search problems...',
    noResults: 'No problems found',
  },
}
```

---

## 十、实施检查清单

- [ ] 创建类型定义 `types/recommendation.ts`
- [ ] 创建 API 模块 `api/recommendation.ts`
- [ ] 创建 Store `stores/recommendation.ts`
- [ ] 创建主页面 `views/recommendations/RecommendationsView.vue`
- [ ] 创建子组件:
  - [ ] `RecommendationNav.vue`
  - [ ] `ProblemCard.vue`
  - [ ] `TagFilter.vue`
  - [ ] `SimilarProblemSearch.vue`
- [ ] 更新路由配置
- [ ] 更新侧边栏数据 `sidebar.data.ts`
- [ ] 添加国际化翻译
- [ ] 测试 4 种推荐场景
- [ ] 测试标签筛选功能
- [ ] 测试相似题目搜索

---

*文档版本: 1.0*
*创建日期: 2026-03-14*
