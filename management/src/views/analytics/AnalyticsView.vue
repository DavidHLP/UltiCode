<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { IconRefresh } from '@tabler/icons-vue'
import { AnalyticsNav } from '@/components/analytics'

import { useAnalyticsReports } from './composables/useAnalyticsReports'
import UserActivityReport from './components/UserActivityReport.vue'
import ProblemCompletionReport from './components/ProblemCompletionReport.vue'
import ContestParticipationReport from './components/ContestParticipationReport.vue'
import RevenueReport from './components/RevenueReport.vue'
import PerformanceReport from './components/PerformanceReport.vue'

const { t } = useI18n()

const {
  activeTab,
  loading,
  days,
  showRefreshSession,
  formattedTime,
  formattedDate,
  formatNumber,
  formatPercent,
  formatCurrency,
  userActivityReport,
  problemCompletionReport,
  contestParticipationReport,
  revenueReport,
  performanceReport,
  loadReport,
  refreshSession,
} = useAnalyticsReports()
</script>

<template>
  <div class="flex flex-col gap-6 py-6 px-4 lg:px-8 min-h-full bg-background">
    <!-- Precision Header -->
    <header
      class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between pb-4 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]"
    >
      <div class="space-y-1">
        <h1 class="text-2xl font-medium tracking-tight text-foreground">
          {{ t('analytics.title') }}
        </h1>
        <p class="text-sm text-[var(--silver-500)]">
          {{ t('analytics.description') }}
        </p>
      </div>

      <div class="flex items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="text-[var(--silver-400)]">{{ formattedDate }}</span>
          <span class="text-lg font-data tabular-nums text-foreground">{{ formattedTime }}</span>
        </div>

        <Select v-model="days">
          <SelectTrigger
            class="w-[130px] h-8 text-xs border-[var(--silver-200)] dark:border-[var(--silver-300)]"
          >
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="7">{{ t('analytics.periods.7days') }}</SelectItem>
            <SelectItem value="30">{{ t('analytics.periods.30days') }}</SelectItem>
            <SelectItem value="90">{{ t('analytics.periods.90days') }}</SelectItem>
            <SelectItem value="365">{{ t('analytics.periods.1year') }}</SelectItem>
          </SelectContent>
        </Select>

        <Button
          variant="outline"
          size="sm"
          @click="loadReport"
          :disabled="loading"
          class="h-8 border-[var(--silver-200)] dark:border-[var(--silver-300)]"
        >
          <IconRefresh class="h-3.5 w-3.5 mr-1" :class="{ 'animate-spin': loading }" />
          {{ t('common.refresh') }}
        </Button>
      </div>
    </header>

    <!-- Main Layout: Sidebar + Content -->
    <div class="flex flex-col lg:flex-row gap-6">
      <aside class="lg:w-56 shrink-0">
        <div class="sticky top-6">
          <AnalyticsNav v-model:active-item="activeTab" />
        </div>
      </aside>

      <main class="flex-1 min-w-0">
        <!-- Loading State -->
        <div v-if="loading" class="flex items-center justify-center py-12">
          <div class="flex items-center gap-3 text-[var(--silver-400)]">
            <div
              class="h-4 w-4 border-2 border-[var(--silver-300)] border-t-foreground rounded-full animate-spin"
            />
            <span>{{ t('common.loading') }}</span>
          </div>
        </div>

        <!-- Permission Denied -->
        <div v-else-if="showRefreshSession" class="flex items-center justify-center py-12">
          <div class="text-center space-y-4">
            <p class="text-[var(--silver-400)]">{{ t('analytics.permissionDenied') }}</p>
            <Button variant="outline" size="sm" @click="refreshSession" :disabled="loading">
              <IconRefresh class="h-4 w-4 mr-2" :class="{ 'animate-spin': loading }" />
              {{ t('analytics.refreshSession') }}
            </Button>
          </div>
        </div>

        <UserActivityReport
          v-else-if="activeTab === 'user_activity' && userActivityReport"
          :report="userActivityReport"
          :format-number="formatNumber"
          :format-percent="formatPercent"
        />

        <ProblemCompletionReport
          v-else-if="activeTab === 'problem_completion' && problemCompletionReport"
          :report="problemCompletionReport"
          :format-number="formatNumber"
          :format-percent="formatPercent"
        />

        <ContestParticipationReport
          v-else-if="activeTab === 'contest_participation' && contestParticipationReport"
          :report="contestParticipationReport"
          :format-number="formatNumber"
        />

        <RevenueReport
          v-else-if="activeTab === 'revenue' && revenueReport"
          :report="revenueReport"
          :format-number="formatNumber"
          :format-percent="formatPercent"
          :format-currency="formatCurrency"
        />

        <PerformanceReport
          v-else-if="activeTab === 'performance' && performanceReport"
          :report="performanceReport"
          :format-number="formatNumber"
          :format-percent="formatPercent"
        />

        <!-- No Data State -->
        <div v-else-if="!loading" class="flex items-center justify-center py-12">
          <div class="text-center">
            <p class="text-[var(--silver-400)]">{{ t('analytics.noData') }}</p>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>
