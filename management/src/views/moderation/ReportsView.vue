<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'

import { Button } from '@/components/ui/button'
import { IconRefresh, IconAlertTriangle } from '@tabler/icons-vue'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'

import { useModerationStore } from '@/stores/admin/moderation'
import {
  type Report,
  ReportStatus,
  ReportCategory,
  type ModeratableEntityType,
  type QueryReportsParams,
} from '@/api/admin/moderation'
import { useDataTable } from '@/composables/useDataTable'
import { createReportsColumns, type ReportActions } from './reports-columns'
import { entityRoute } from './workflow/moderationWorkflow'

const { t } = useI18n()
const router = useRouter()
const store = useModerationStore()

const isLoaded = ref(false)

// Filters
const statusFilter = ref<ReportStatus | 'all'>('all')
const categoryFilter = ref<ReportCategory | 'all'>('all')
const entityTypeFilter = ref<ModeratableEntityType | 'all'>('all')

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

// Stats
const stats = computed(() => ({
  total: store.reportsTotal,
  pending: store.reports.filter((r) => r.status === ReportStatus.PENDING).length,
}))

// Table columns
const columns = computed(() => {
  const actions: ReportActions = {
    viewEntity: (report) => {
      router.push(entityRoute(report.entityType, report.entityId))
    },
    viewInQueue: (report) => {
      if (report.queueId) {
        router.push({ path: '/moderation', query: { queueId: report.queueId } })
      } else {
        toast.warning('No queue item associated with this report')
      }
    },
  }
  return createReportsColumns(t, actions)
})

// Filter configuration
const filters = computed<Filter[]>(() => [
  {
    modelValue: statusFilter.value,
    placeholder: t('moderation.reportStatus.title'),
    options: [
      { value: 'all', label: t('moderation.reportStatus.all') },
      { value: ReportStatus.PENDING, label: t('moderation.reportStatus.PENDING') },
      { value: ReportStatus.REVIEWED, label: t('moderation.reportStatus.REVIEWED') },
      { value: ReportStatus.RESOLVED, label: t('moderation.reportStatus.RESOLVED') },
      { value: ReportStatus.DISMISSED, label: t('moderation.reportStatus.DISMISSED') },
    ],
    width: 'w-[140px]',
  },
  {
    modelValue: categoryFilter.value,
    placeholder: t('moderation.categories.title'),
    options: [
      { value: 'all', label: t('moderation.categories.all') },
      { value: ReportCategory.SPAM, label: t('moderation.categories.SPAM') },
      { value: ReportCategory.HARASSMENT, label: t('moderation.categories.HARASSMENT') },
      { value: ReportCategory.HATE_SPEECH, label: t('moderation.categories.HATE_SPEECH') },
      { value: ReportCategory.VIOLENCE, label: t('moderation.categories.VIOLENCE') },
      { value: ReportCategory.SEXUAL_CONTENT, label: t('moderation.categories.SEXUAL_CONTENT') },
      { value: ReportCategory.MISINFORMATION, label: t('moderation.categories.MISINFORMATION') },
      { value: ReportCategory.WRONG_ANSWER, label: t('moderation.categories.WRONG_ANSWER') },
      { value: ReportCategory.COPYRIGHT, label: t('moderation.categories.COPYRIGHT') },
      { value: ReportCategory.OTHER, label: t('moderation.categories.OTHER') },
    ],
    width: 'w-[160px]',
  },
  {
    modelValue: entityTypeFilter.value,
    placeholder: t('moderation.entityTypes.title'),
    options: [
      { value: 'all', label: t('moderation.entityTypes.all') },
      { value: 'forum_post', label: t('moderation.entityTypes.forum_post') },
      { value: 'forum_comment', label: t('moderation.entityTypes.forum_comment') },
      { value: 'solution', label: t('moderation.entityTypes.solution') },
      { value: 'solution_comment', label: t('moderation.entityTypes.solution_comment') },
      { value: 'problem', label: t('moderation.entityTypes.problem') },
    ],
    width: 'w-[140px]',
  },
])

const {
  searchQuery,
  tablePagination,
  selectedRows,
  loading,
  data,
  total,
  loadEntities: loadReports,
} = useDataTable<
  Report,
  {
    status: ReportStatus | 'all'
    category: ReportCategory | 'all'
    entityType: ModeratableEntityType | 'all'
  },
  QueryReportsParams
>({
  store: {
    data: computed(() => store.reports),
    total: computed(() => store.reportsTotal),
    isLoading: computed(() => store.reportsLoading),
    error: computed(() => store.reportsError),
    fetch: (params) => store.fetchReports(params),
  },
  filters: () => ({
    status: statusFilter.value,
    category: categoryFilter.value,
    entityType: entityTypeFilter.value,
  }),
  transformParams: ({ filters, page, limit }) => ({
    page,
    limit,
    status: filters.status === 'all' ? undefined : filters.status,
    category: filters.category === 'all' ? undefined : filters.category,
    entityType: filters.entityType === 'all' ? undefined : filters.entityType,
  }),
  debounceMs: 300,
  autoLoad: true,
})

function handleFilterUpdate(index: number, value: string | number) {
  if (index === 0) statusFilter.value = value as ReportStatus | 'all'
  else if (index === 1) categoryFilter.value = value as ReportCategory | 'all'
  else if (index === 2) entityTypeFilter.value = value as ModeratableEntityType | 'all'
}
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Terminal Header -->
    <div
      :class="[
        'border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
          {{ t('moderation.reports.title') }}
        </h1>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)] transition-colors"
          @click="loadReports"
          :disabled="loading"
        >
          <IconRefresh :class="['h-3.5 w-3.5', { 'animate-spin': loading }]" />
          <span class="uppercase tracking-wider hidden sm:inline">{{ t('common.refresh') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('moderation.terminal.total') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('moderation.terminal.pending') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.pending
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconAlertTriangle class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{
            t('moderation.reports.pageTitle')
          }}</span>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div
      :class="[
        'flex-1 py-4',
        'transition-all duration-500 delay-200',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <DataTable
        :columns="columns"
        :data="data"
        :pagination="tablePagination"
        :row-count="total"
        :loading="loading"
        v-model:selected-rows="selectedRows"
        @update:pagination="tablePagination = $event"
        :empty-title="t('moderation.reports.emptyTitle')"
        :empty-description="t('moderation.reports.emptyDescription')"
        class="terminal-table"
      >
        <template #toolbar-left>
          <DataTableToolbar
            v-model:search-model-value="searchQuery"
            :search-placeholder="t('moderation.searchPlaceholder')"
            :filters="filters"
            :loading="loading"
            :on-refresh="loadReports"
            @update:filter="handleFilterUpdate"
          />
        </template>
      </DataTable>
    </div>
  </div>
</template>
