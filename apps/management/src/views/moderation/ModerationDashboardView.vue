<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  IconRefresh,
  IconAlertTriangle,
  IconClock,
  IconCheck,
  IconX,
  IconScale,
  IconChartBar,
  IconFileText,
  IconCode,
  IconMessage,
  IconMessages,
  IconTrendingUp,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'

import { useModerationStore } from '@/stores/admin/moderation'
import { ReportCategory } from '@/api/admin/moderation'

const { t } = useI18n()
const router = useRouter()
const store = useModerationStore()

const isLoaded = ref(false)

onMounted(async () => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
  await store.fetchStats(true)
})

const stats = computed(() => store.stats)

// Status cards data
const statusCards = computed(() => [
  {
    key: 'pending',
    label: t('moderation.stats.totalPending'),
    value: stats.value?.pendingCount ?? 0,
    icon: IconAlertTriangle,
    color: 'text-foreground-strong',
    bgColor: 'bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)]',
    borderColor: 'border-[color-mix(in_oklch,_var(--status-warning-mark)_40%,_transparent)]',
  },
  {
    key: 'under_review',
    label: t('moderation.stats.totalUnderReview'),
    value: stats.value?.underReviewCount ?? 0,
    icon: IconClock,
    color: 'text-foreground-strong',
    bgColor: 'bg-[color-mix(in_oklch,_var(--status-info-mark)_15%,_transparent)]',
    borderColor: 'border-[color-mix(in_oklch,_var(--status-info-mark)_40%,_transparent)]',
  },
  {
    key: 'resolved',
    label: t('moderation.stats.totalResolved'),
    value: stats.value?.resolvedCount ?? 0,
    icon: IconCheck,
    color: 'text-foreground-strong',
    bgColor: 'bg-[color-mix(in_oklch,_var(--status-success-mark)_15%,_transparent)]',
    borderColor: 'border-[color-mix(in_oklch,_var(--status-success-mark)_40%,_transparent)]',
  },
  {
    key: 'dismissed',
    label: t('moderation.stats.totalDismissed'),
    value: stats.value?.dismissedCount ?? 0,
    icon: IconX,
    color: 'text-foreground-strong',
    bgColor: 'bg-[color-mix(in_oklch,_var(--status-error-mark)_15%,_transparent)]',
    borderColor: 'border-[color-mix(in_oklch,_var(--status-error-mark)_40%,_transparent)]',
  },
  {
    key: 'appeal_pending',
    label: t('moderation.stats.totalAppealPending'),
    value: stats.value?.pendingAppealsCount ?? 0,
    icon: IconScale,
    color: 'text-foreground-strong',
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
  forum_post: 'text-foreground-strong',
  forum_comment: 'text-foreground-strong',
  solution: 'text-foreground-strong',
  solution_comment: 'text-foreground-strong',
  problem: 'text-foreground-strong',
}

const categoryColors: Record<ReportCategory, string> = {
  SPAM: 'bg-[var(--status-warning-mark)]',
  HARASSMENT: 'bg-[var(--status-error-mark)]',
  HATE_SPEECH: 'bg-[var(--status-error-mark)]',
  VIOLENCE: 'bg-[var(--status-error-mark)]',
  SEXUAL_CONTENT: 'bg-[var(--status-error-mark)]',
  MISINFORMATION: 'bg-[var(--status-warning-mark)]',
  WRONG_ANSWER: 'bg-[var(--status-warning-mark)]',
  COPYRIGHT: 'bg-[var(--status-special-mark)]',
  OTHER: 'bg-[var(--foreground-muted)]',
}

const totalItems = computed(() => {
  return statusCards.value.reduce((sum, card) => sum + card.value, 0)
})

function handleRefresh() {
  store.fetchStats(true)
}

function navigateToQueue() {
  router.push('/moderation')
}

function navigateToAppeals() {
  router.push('/moderation/appeals')
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
      <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
        {{ t('moderation.stats.title') }}
      </h1>
      <Button
        variant="terminal"
        size="sm"
        class="h-8 font-data text-xs border-[var(--border-subtle)]"
        @click="handleRefresh"
        :disabled="store.statsLoading"
      >
        <IconRefresh :class="['h-3.5 w-3.5', { 'animate-spin': store.statsLoading }]" />
        <span class="uppercase tracking-wider hidden sm:inline">{{ t('common.refresh') }}</span>
      </Button>
    </div>

    <!-- Status Cards -->
    <div
      :class="[
        'grid grid-cols-2 md:grid-cols-5 gap-4',
        'transition-all duration-500 delay-100',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <Card
        v-for="card in statusCards"
        :key="card.key"
        :class="[
          'border cursor-pointer hover:shadow-md transition-shadow',
          card.borderColor,
          card.bgColor,
        ]"
        @click="navigateToQueue"
      >
        <CardHeader class="pb-2">
          <CardTitle class="flex items-center gap-2 text-sm font-data">
            <component :is="card.icon" :class="['h-4 w-4', card.color]" />
            <span class="text-[var(--foreground-muted)] uppercase tracking-wider text-2xs">
              {{ card.label }}
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div class="flex items-end justify-between">
            <span :class="['text-3xl font-data tabular-nums', card.color]">
              {{ card.value }}
            </span>
            <span v-if="totalItems > 0" class="text-xs text-[var(--foreground-muted)] font-data">
              {{ Math.round((card.value / totalItems) * 100) }}%
            </span>
          </div>
        </CardContent>
      </Card>
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
      <Card class="border-[var(--border-subtle)] dark:border-[var(--border-subtle)]">
        <CardHeader>
          <CardTitle class="flex items-center gap-2 text-sm font-data">
            <IconChartBar class="h-4 w-4 text-[var(--foreground-muted)]" />
            <span class="text-[var(--foreground-muted)] uppercase tracking-wider text-2xs">
              {{ t('moderation.stats.byCategory') }}
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="categoryData.length === 0" class="py-8 text-center">
            <p class="text-xs font-data text-[var(--foreground-muted)]">
              {{ t('common.noDataAvailable') }}
            </p>
          </div>
          <div v-else class="space-y-3">
            <div v-for="item in categoryData" :key="item.category" class="space-y-1">
              <div class="flex items-center justify-between text-xs">
                <span class="font-data">{{ t(`moderation.categories.${item.category}`) }}</span>
                <span class="font-data tabular-nums text-[var(--foreground-muted)]">
                  {{ item.count }} ({{ item.percentage }}%)
                </span>
              </div>
              <Progress
                :model-value="item.percentage"
                :class="['h-2', categoryColors[item.category]]"
              />
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- By Entity Type -->
      <Card class="border-[var(--border-subtle)] dark:border-[var(--border-subtle)]">
        <CardHeader>
          <CardTitle class="flex items-center gap-2 text-sm font-data">
            <IconTrendingUp class="h-4 w-4 text-[var(--foreground-muted)]" />
            <span class="text-[var(--foreground-muted)] uppercase tracking-wider text-2xs">
              {{ t('moderation.stats.byEntityType') }}
            </span>
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div v-if="entityTypeData.length === 0" class="py-8 text-center">
            <p class="text-xs font-data text-[var(--foreground-muted)]">
              {{ t('common.noDataAvailable') }}
            </p>
          </div>
          <div v-else class="space-y-3">
            <div
              v-for="[entityType, count] in entityTypeData"
              :key="entityType"
              class="flex items-center justify-between p-2 border border-[var(--border-subtle)] dark:border-[var(--border-subtle)]"
            >
              <div class="flex items-center gap-2">
                <component
                  :is="entityTypeIcons[entityType] || IconFileText"
                  :class="['h-4 w-4', entityTypeColors[entityType] || 'text-[var(--foreground-muted)]']"
                />
                <span class="text-sm">{{ t(`moderation.entityTypes.${entityType}`) }}</span>
              </div>
              <span class="font-data text-sm tabular-nums">{{ count }}</span>
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
      <Card
        class="border-[var(--status-warning-mark)] bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] cursor-pointer hover:shadow-md transition-shadow"
        @click="navigateToQueue"
      >
        <CardContent class="py-6">
          <div class="flex items-center gap-4">
            <div
              class="p-3 rounded-full bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)]"
            >
              <IconAlertTriangle class="h-6 w-6 text-[var(--status-warning-mark)]" />
            </div>
            <div>
              <h3 class="font-data text-sm uppercase tracking-wider text-[var(--foreground-strong)]">
                {{ t('moderation.queue.title') }}
              </h3>
              <p class="text-xs text-[var(--foreground-muted)] mt-1">
                {{ t('moderation.queue.description') }}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card
        class="border-[var(--status-special-mark)] bg-[color-mix(in_oklch,_var(--status-special-mark)_8%,_transparent)] cursor-pointer hover:shadow-md transition-shadow"
        @click="navigateToAppeals"
      >
        <CardContent class="py-6">
          <div class="flex items-center gap-4">
            <div
              class="p-3 rounded-full bg-[color-mix(in_oklch,_var(--status-special-mark)_15%,_transparent)]"
            >
              <IconScale class="h-6 w-6 text-[var(--status-special-mark)]" />
            </div>
            <div>
              <h3 class="font-data text-sm uppercase tracking-wider text-[var(--foreground-strong)]">
                {{ t('moderation.appeals.title') }}
              </h3>
              <p class="text-xs text-[var(--foreground-muted)] mt-1">
                {{ t('moderation.appeals.description') }}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- Average Resolution Time -->
    <Card
      v-if="stats?.avgResolutionTimeHours"
      :class="[
        'border-[var(--border-subtle)] dark:border-[var(--border-subtle)]',
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
              <IconClock class="h-6 w-6 text-[var(--status-success-mark)]" />
            </div>
            <div>
              <h3 class="font-data text-sm uppercase tracking-wider text-[var(--foreground-muted)]">
                {{ t('moderation.stats.avgResolutionTime') }}
              </h3>
              <p class="text-2xl font-data tabular-nums text-[var(--foreground-strong)] mt-1">
                {{ Math.round(stats.avgResolutionTimeHours) }}
                <span class="text-sm text-[var(--foreground-muted)]">{{
                  t('moderation.stats.hours')
                }}</span>
              </p>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
