<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'

import { Button } from '@ulticode/design-system/ui/button'
import { RefreshCw as IconRefresh, AlertTriangle as IconAlertTriangle } from '@lucide/vue'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar from '@/components/table/DataTableToolbar.vue'

import { useModerationStore } from '@/stores/admin/moderation'
import {
  type Report,
  ReportStatus,
  ReportCategory,
  type ModeratableEntityType,
  type QueryReportsParams,
} from '@/api/admin/moderation'
import { useRemoteTable } from '@/composables/useRemoteTable'
import { createReportsColumns, type ReportActions } from './reports-columns'
import { useModerationFilters } from './composables/useModerationFilters'
import { entityRoute } from './presentation/moderationPresentation'

const { t } = useI18n()
const router = useRouter()
const store = useModerationStore()

const isLoaded = ref(false)
const selectedRows = ref<Report[]>([])

// Filters

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})
const {
  query,
  searchQuery,
  tablePagination,
  loading,
  data,
  total,
  refresh: loadReports,
  setFilters,
} = useRemoteTable<
  Report,
  {
    status: ReportStatus | 'all'
    category: ReportCategory | 'all'
    entityType: ModeratableEntityType | 'all'
  },
  QueryReportsParams
>({
  store: store.reportsCollection,
  initialQuery: {
    filters: { status: 'all', category: 'all', entityType: 'all' },
  },
  toParams: ({ filters, page, limit }) => ({
    page,
    limit,
    status: filters.status === 'all' ? undefined : filters.status,
    category: filters.category === 'all' ? undefined : filters.category,
    entityType: filters.entityType === 'all' ? undefined : filters.entityType,
  }),
  debounceMs: 300,
  autoLoad: true,
})

const { buildFilters, handleFilterUpdate } = useModerationFilters(
  { query, setFilters },
  {
    statusValues: Object.values(ReportStatus),
    statusNamespace: 'moderation.reportStatus',
  },
)
const filters = buildFilters(t)

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
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Page header -->
    <div
      :class="[
        'border border-border dark:border-border bg-card',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-foreground">
          {{ t('moderation.reports.title') }}
        </h1>
        <Button variant="outline" size="sm" @click="loadReports" :disabled="loading">
          <IconRefresh :class="['h-3.5 w-3.5', { 'animate-spin': loading }]" />
          <span class="uppercase tracking-wider hidden sm:inline">{{ t('common.refresh') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-border dark:border-border bg-muted"
      >
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('moderation.terminal.total') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.total }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('moderation.terminal.pending') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.pending }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-muted-foreground">
          <IconAlertTriangle class="h-4 w-4" />
          <span class="text-xs font-mono uppercase tracking-wider">{{
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
        class="w-full text-sm"
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
