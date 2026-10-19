<script setup lang="ts">
import { solutionPermissionRules } from '@/composables/domainPermissionMaps'
import { usePermissionMap } from '@/composables/usePermissionMap'
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { FileText as IconFileText } from '@lucide/vue'

import { Button } from '@ulticode/design-system/ui/button'

import { useSolutionsStore } from '@/stores/admin/solutions'
import type { SolutionListItem } from '@/api/admin/solutions'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { createColumns } from './columns'
import { useRemoteTable } from '@/composables/useRemoteTable'

const router = useRouter()
const solutionsStore = useSolutionsStore()
const can = { solution: usePermissionMap(solutionPermissionRules) }
const { t } = useI18n()

const selectedSolutionId = ref<string | null>(null)
const selectedSolutionTitle = ref<string | null>(null)
const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)

// Animation state for staggered reveal
const isLoaded = ref(false)

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
  error,
  refresh: loadSolutions,
  setFilters,
} = useRemoteTable<
  SolutionListItem,
  { flaggedFilter: string; publishedFilter: string },
  Parameters<typeof solutionsStore.fetchSolutions>[0]
>({
  store: solutionsStore,
  initialQuery: { filters: { flaggedFilter: 'all', publishedFilter: 'all' } },
  toParams: ({ search, filters, page, limit }) => ({
    search,
    isFlagged: filters.flaggedFilter === 'all' ? undefined : filters.flaggedFilter === 'flagged',
    isPublished:
      filters.publishedFilter === 'all' ? undefined : filters.publishedFilter === 'published',
    page,
    limit,
  }),
  autoLoad: true,
})

const flaggedFilter = computed({
  get: () => query.value.filters.flaggedFilter,
  set: (flaggedFilter: string) => setFilters({ ...query.value.filters, flaggedFilter }),
})
const publishedFilter = computed({
  get: () => query.value.filters.publishedFilter,
  set: (publishedFilter: string) => setFilters({ ...query.value.filters, publishedFilter }),
})

// Stats for terminal ticker
const stats = computed(() => {
  if (loading.value || error.value) return { total: '—', flagged: '—', published: '—' }
  return {
    total: solutionsStore.totalCount,
    flagged: solutionsStore.flaggedCount,
    published: solutionsStore.publishedCount,
  }
})

const toolbarFilters = computed<Filter[]>(() => [
  {
    modelValue: flaggedFilter.value,
    placeholder: t('solutions.filters.flagStatus'),
    width: 'w-[130px]',
    options: [
      { value: 'all', label: t('solutions.filters.allFlags') },
      { value: 'flagged', label: t('solutions.filters.flagged') },
      { value: 'clean', label: t('solutions.filters.clean') },
    ],
  },
  {
    modelValue: publishedFilter.value,
    placeholder: t('solutions.filters.visibility'),
    width: 'w-[130px]',
    options: [
      { value: 'all', label: t('solutions.filters.allVisibility') },
      { value: 'published', label: t('solutions.filters.published') },
      { value: 'unpublished', label: t('solutions.filters.unpublished') },
    ],
  },
])

const columns = createColumns(
  t,
  {
    viewSolution: (id: string) => {
      router.push({ name: 'solution-detail', params: { id } })
    },
    openFlagDialog: (solution: SolutionListItem) => {
      selectedSolutionId.value = solution.id
      selectedSolutionTitle.value = solution.title
      flagDialogOpen.value = true
    },
    unflagSolution: async (id: string) => {
      try {
        await solutionsStore.unflagSolution(id)
        toast.success(t('solutions.toast.unflaggedSuccessfully'))
      } catch {
        toast.error(t('solutions.toast.failedToUnflag'))
      }
    },
    confirmDelete: (solution: SolutionListItem) => {
      selectedSolutionId.value = solution.id
      selectedSolutionTitle.value = solution.title
      deleteDialogOpen.value = true
    },
  },
  can.solution,
)

async function handleDeleteSolution(id: string | number) {
  await solutionsStore.deleteSolution(String(id))
}

async function handleFlagSolution(id: string | number, reason?: string) {
  await solutionsStore.flagSolution(String(id), { reason: reason || '' })
}
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
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-foreground">
          {{ t('solutions.title') }}
        </h1>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex flex-wrap items-center gap-x-6 gap-y-2 whitespace-nowrap border-t border-border dark:border-border bg-muted"
      >
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('solutions.terminal.total') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.total }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('solutions.terminal.flagged') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.flagged }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('solutions.terminal.published') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.published }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-muted-foreground">
          <IconFileText class="h-4 w-4" />
          <span class="text-xs font-mono uppercase tracking-wider">{{
            t('solutions.terminal.solutionManagement')
          }}</span>
        </div>
      </div>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1">
      <DataTable
        :columns="columns"
        :data="error ? [] : data"
        :pagination="tablePagination"
        :row-count="error ? 0 : total"
        :hide-pagination="!!error"
        :loading="loading"
        @update:pagination="tablePagination = $event"
        class="w-full text-sm"
      >
        <template #toolbar-left>
          <DataTableToolbar
            :search-model-value="searchQuery"
            @update:search-model-value="searchQuery = $event"
            :search-placeholder="t('solutions.searchPlaceholder')"
            search-width="min-w-[150px] w-full lg:w-[250px]"
            :filters="toolbarFilters"
            @update:filter="
              (index, value) =>
                index === 0 ? (flaggedFilter = String(value)) : (publishedFilter = String(value))
            "
            :loading="loading"
            :on-refresh="loadSolutions"
          />
        </template>
        <template v-if="error" #empty>
          <div
            role="alert"
            class="flex flex-wrap items-center justify-between gap-3 border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] p-4"
          >
            <div class="flex min-w-0 flex-1 flex-wrap items-center gap-3 [overflow-wrap:anywhere]">
              <span class="font-mono text-sm text-foreground">&gt; ERROR:</span>
              <span class="text-sm text-foreground">{{ error }}</span>
            </div>
            <Button variant="outline" size="sm" @click="loadSolutions()">
              {{ t('common.retry') }}
            </Button>
          </div>
        </template>
      </DataTable>
    </div>
  </div>

  <EntityActionDialog
    v-model:open="deleteDialogOpen"
    :entity-id="selectedSolutionId"
    :entity-title="selectedSolutionTitle"
    action="delete"
    :title="t('solutions.delete.title')"
    :description="t('solutions.delete.description')"
    :confirm-label="t('solutions.delete.confirm')"
    :cancel-label="t('solutions.delete.cancel')"
    :success-label="t('solutions.toast.deletedSuccessfully')"
    :error-label="t('solutions.toast.failedToDelete')"
    :on-action="handleDeleteSolution"
    @success="loadSolutions"
  />

  <EntityActionDialog
    v-model:open="flagDialogOpen"
    :entity-id="selectedSolutionId"
    action="flag"
    :title="t('solutions.flag.title')"
    :description="t('solutions.flag.description')"
    :confirm-label="t('solutions.flag.confirm')"
    :cancel-label="t('solutions.flag.cancel')"
    :success-label="t('solutions.toast.flaggedSuccessfully')"
    :error-label="t('solutions.toast.failedToFlag')"
    :reason-label="t('solutions.flag.reasonLabel')"
    :reason-placeholder="t('solutions.flag.reasonPlaceholder')"
    :reason-required-label="t('solutions.toast.reasonRequired')"
    :on-action="handleFlagSolution"
    @success="loadSolutions"
  />
</template>
