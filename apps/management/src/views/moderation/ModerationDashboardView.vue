<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink } from 'vue-router'
import {
  RefreshCw as IconRefresh,
  AlertTriangle as IconAlertTriangle,
  Clock as IconClock,
  Check as IconCheck,
  X as IconX,
  Scale as IconScale,
  ChartBar as IconChartBar,
  FileText as IconFileText,
  Code as IconCode,
  MessageSquare as IconMessage,
  MessagesSquare as IconMessages,
  TrendingUp as IconTrendingUp,
} from '@lucide/vue'

import { Button } from '@ulticode/design-system/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@ulticode/design-system/ui/card'
import { Progress } from '@ulticode/design-system/ui/progress'

import { useModerationStore } from '@/stores/admin/moderation'
import { ReportCategory } from '@/api/admin/moderation'

const { t } = useI18n()
const store = useModerationStore()

const isLoaded = ref(false)

onMounted(async () => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
  await store.fetchStats(true)
})

const stats = computed(() => (store.statsLoading || store.statsError ? null : store.stats))

// Status cards data
const statusCards = computed(() => [
  {
    key: 'pending',
    label: t('moderation.stats.totalPending'),
    value: stats.value?.pendingCount ?? 0,
    icon: IconAlertTriangle,
    color: 'text-foreground',
    bgColor: 'bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)]',
    borderColor: 'border-[color-mix(in_oklch,_var(--status-warning-mark)_40%,_transparent)]',
  },
  {
    key: 'under_review',
    label: t('moderation.stats.totalUnderReview'),
    value: stats.value?.underReviewCount ?? 0,
    icon: IconClock,
    color: 'text-foreground',
    bgColor: 'bg-[color-mix(in_oklch,_var(--status-info-mark)_15%,_transparent)]',
    borderColor: 'border-[color-mix(in_oklch,_var(--status-info-mark)_40%,_transparent)]',
  },
  {
    key: 'resolved',
    label: t('moderation.stats.totalResolved'),
    value: stats.value?.resolvedCount ?? 0,
    icon: IconCheck,
    color: 'text-foreground',
    bgColor: 'bg-[color-mix(in_oklch,_var(--status-success-mark)_15%,_transparent)]',
    borderColor: 'border-[color-mix(in_oklch,_var(--status-success-mark)_40%,_transparent)]',
  },
  {
    key: 'dismissed',
    label: t('moderation.stats.totalDismissed'),
    value: stats.value?.dismissedCount ?? 0,
    icon: IconX,
    color: 'text-foreground',
    bgColor: 'bg-[color-mix(in_oklch,_var(--status-error-mark)_15%,_transparent)]',
    borderColor: 'border-[color-mix(in_oklch,_var(--status-error-mark)_40%,_transparent)]',
  },
  {
    key: 'appeal_pending',
    label: t('moderation.stats.totalAppealPending'),
    value: stats.value?.pendingAppealsCount ?? 0,
    icon: IconScale,
    color: 'text-foreground',
    bgColor: 'bg-[color-mix(in_oklch,_var(--status-special-mark)_15%,_transparent)]',
    borderColor: 'border-[color-mix(in_oklch,_var(--status-special-mark)_40%,_transparent)]',
  },
])

// Category distribution
const categoryData = computed(() => {
  if (!stats.value?.byCategory) return []
  const categories = Object.entries(stats.value.byCategory)
    .filter(([, count]) => count > 0)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6)
  const total = categories.reduce((sum, [, count]) => sum + count, 0)
  return categories.map(([category, count]) => ({
    category: category as ReportCategory,
    count,
    percentage: total > 0 ? Math.round((count / total) * 100) : 0,
  }))
})

// Entity type distribution
const entityTypeData = computed(() => {
  if (!stats.value?.byEntityType) return []
  return Object.entries(stats.value.byEntityType)
    .filter(([, count]) => count > 0)
    .sort((a, b) => b[1] - a[1])
})

const entityTypeIcons: Record<string, typeof IconFileText> = {
  forum_post: IconMessages,
  forum_comment: IconMessage,
  solution: IconCode,
  solution_comment: IconMessage,
  problem: IconFileText,
}

const entityTypeColors: Record<string, string> = {
  forum_post: 'text-foreground',
  forum_comment: 'text-foreground',
  solution: 'text-foreground',
  solution_comment: 'text-foreground',
  problem: 'text-foreground',
}

const categoryColors: Record<ReportCategory, string> = {
  SPAM: 'bg-status-warning-mark',
  HARASSMENT: 'bg-status-error-mark',
  HATE_SPEECH: 'bg-status-error-mark',
  VIOLENCE: 'bg-status-error-mark',
  SEXUAL_CONTENT: 'bg-status-error-mark',
  MISINFORMATION: 'bg-status-warning-mark',
  WRONG_ANSWER: 'bg-status-warning-mark',
  COPYRIGHT: 'bg-status-special-mark',
  OTHER: 'bg-muted-foreground',
}

const totalItems = computed(() => {
  return statusCards.value.reduce((sum, card) => sum + card.value, 0)
})

function handleRefresh() {
  store.fetchStats(true)
}
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0 p-6">
    <!-- Header -->
    <div
      :class="[
        'flex items-center justify-between',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <h1 class="text-xl font-medium tracking-tight text-foreground">
        {{ t('moderation.stats.title') }}
      </h1>
      <Button
        variant="outline"
        size="sm"
        :aria-label="t('common.refresh')"
        @click="handleRefresh"
        :disabled="store.statsLoading"
      >
        <IconRefresh :class="['h-3.5 w-3.5', { 'animate-spin': store.statsLoading }]" />
        <span class="uppercase tracking-wider hidden sm:inline">{{ t('common.refresh') }}</span>
      </Button>
    </div>

    <p
      v-if="store.statsError"
      role="alert"
      class="text-sm text-destructive [overflow-wrap:anywhere]"
    >
      {{ t('moderation.stats.loadError') }}
    </p>

    <!-- Status Cards -->
    <div
      :class="[
        'grid grid-cols-[repeat(auto-fit,minmax(min(100%,10rem),1fr))] gap-4',
        'transition-all duration-500 delay-100',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <RouterLink
        v-for="card in statusCards"
        :key="card.key"
        to="/moderation"
        class="min-w-0 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <Card
          :class="[
            'h-full border hover:shadow-md transition-shadow',
            card.borderColor,
            card.bgColor,
          ]"
        >
          <CardHeader class="pb-2">
            <CardTitle class="flex items-center gap-2">
              <component :is="card.icon" :class="['h-4 w-4 shrink-0', card.color]" />
              <span
                class="min-w-0 [overflow-wrap:anywhere] text-muted-foreground uppercase tracking-wider text-xs"
              >
                {{ card.label }}
              </span>
            </CardTitle>
          </CardHeader>
          <CardContent class="mt-auto">
            <div class="flex items-end justify-between">
              <span :class="['text-3xl font-mono tabular-nums', card.color]">
                {{ stats ? card.value : '—' }}
              </span>
              <span v-if="stats && totalItems > 0" class="text-xs text-muted-foreground font-mono">
                {{ Math.round((card.value / totalItems) * 100) }}%
              </span>
            </div>
          </CardContent>
        </Card>
      </RouterLink>
    </div>

    <!-- Charts Row -->
    <div
      :class="[
        'grid grid-cols-1 md:grid-cols-2 gap-4',
        'transition-all duration-500 delay-200',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <!-- By Category -->
      <Card>
        <CardHeader>
          <CardTitle class="flex items-center gap-2">
            <IconChartBar class="h-4 w-4 text-muted-foreground" />
            <span class="text-muted-foreground uppercase tracking-wider text-xs">
              {{ t('moderation.stats.byCategory') }}
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="!stats" class="py-8 text-center text-muted-foreground">—</div>
          <div v-else-if="categoryData.length === 0" class="py-8 text-center">
            <p class="text-xs font-mono text-muted-foreground">
              {{ t('common.noDataAvailable') }}
            </p>
          </div>
          <div v-else class="space-y-3">
            <div v-for="item in categoryData" :key="item.category" class="space-y-1">
              <div class="flex items-center justify-between text-xs">
                <span class="font-mono">{{ t(`moderation.categories.${item.category}`) }}</span>
                <span class="font-mono tabular-nums text-muted-foreground">
                  {{ item.count }} ({{ item.percentage }}%)
                </span>
              </div>
              <Progress
                :model-value="item.percentage"
                class="h-2"
                :indicator-class="categoryColors[item.category]"
              />
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- By Entity Type -->
      <Card>
        <CardHeader>
          <CardTitle class="flex items-center gap-2">
            <IconTrendingUp class="h-4 w-4 text-muted-foreground" />
            <span class="text-muted-foreground uppercase tracking-wider text-xs">
              {{ t('moderation.stats.byEntityType') }}
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="!stats" class="py-8 text-center text-muted-foreground">—</div>
          <div v-else-if="entityTypeData.length === 0" class="py-8 text-center">
            <p class="text-xs font-mono text-muted-foreground">
              {{ t('common.noDataAvailable') }}
            </p>
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="[entityType, count] in entityTypeData"
              :key="entityType"
              class="flex items-center justify-between p-2 border border-border dark:border-border"
            >
              <div class="flex items-center gap-2">
                <component
                  :is="entityTypeIcons[entityType] || IconFileText"
                  :class="['h-4 w-4', entityTypeColors[entityType] || 'text-muted-foreground']"
                />
                <span class="text-sm">{{ t(`moderation.entityTypes.${entityType}`) }}</span>
              </div>
              <span class="font-mono text-sm tabular-nums">{{ count }}</span>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- Quick Actions -->
    <div
      :class="[
        'grid grid-cols-1 md:grid-cols-2 gap-4',
        'transition-all duration-500 delay-300',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <RouterLink
        to="/moderation"
        class="min-w-0 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <Card class="h-full">
          <CardContent class="py-6">
            <div class="flex items-center gap-4">
              <div
                class="p-3 rounded-full bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)]"
              >
                <IconAlertTriangle class="h-6 w-6 text-status-warning-mark" />
              </div>
              <div>
                <h3 class="font-mono text-sm uppercase tracking-wider text-foreground">
                  {{ t('moderation.queue.title') }}
                </h3>
                <p class="text-xs text-muted-foreground mt-1">
                  {{ t('moderation.queue.description') }}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      </RouterLink>

      <RouterLink
        to="/moderation/appeals"
        class="min-w-0 rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <Card class="h-full">
          <CardContent class="py-6">
            <div class="flex items-center gap-4">
              <div
                class="p-3 rounded-full bg-[color-mix(in_oklch,_var(--status-special-mark)_15%,_transparent)]"
              >
                <IconScale class="h-6 w-6 text-status-special-mark" />
              </div>
              <div>
                <h3 class="font-mono text-sm uppercase tracking-wider text-foreground">
                  {{ t('moderation.appeals.title') }}
                </h3>
                <p class="text-xs text-muted-foreground mt-1">
                  {{ t('moderation.appeals.description') }}
                </p>
              </div>
            </div>
          </CardContent>
        </Card>
      </RouterLink>
    </div>

    <!-- Average Resolution Time -->
    <Card
      v-if="stats?.avgResolutionTimeHours"
      :class="[
        'border-border dark:border-border',
        'transition-all duration-500 delay-400',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <CardContent class="py-6">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-4">
            <div
              class="p-3 rounded-full bg-[color-mix(in_oklch,_var(--status-success-mark)_15%,_transparent)]"
            >
              <IconClock class="h-6 w-6 text-status-success-mark" />
            </div>
            <div>
              <h3 class="font-mono text-sm uppercase tracking-wider text-muted-foreground">
                {{ t('moderation.stats.avgResolutionTime') }}
              </h3>
              <p class="text-2xl font-mono tabular-nums text-foreground mt-1">
                {{ Math.round(stats.avgResolutionTimeHours) }}
                <span class="text-sm text-muted-foreground">{{ t('moderation.stats.hours') }}</span>
              </p>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
