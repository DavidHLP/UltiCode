<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'

import { Button } from '@/components/ui/button'

import { useSolutionsStore } from '@/stores/admin/solutions'
import { useAuthStore } from '@/stores/auth'
import type { Solution } from '@/api/admin/solutions'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { createColumns } from './columns'
import { useDataTable } from '@/composables/useDataTable'

const router = useRouter()
const solutionsStore = useSolutionsStore()
const authStore = useAuthStore()
const { t } = useI18n()

const flaggedFilter = ref<string>('all')
const publishedFilter = ref<string>('all')

const selectedSolutionId = ref<string | null>(null)
const selectedSolutionTitle = ref<string | null>(null)
const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)

const canUpdateSolution = computed(() => authStore.hasPermission('MODERATE', 'SOLUTION'))
const canDeleteSolution = computed(() => authStore.hasPermission('DELETE', 'SOLUTION'))

const toolbarFilters = computed<Filter[]>(() => [
  {
    modelValue: flaggedFilter.value,
    placeholder: t('solutions.filters.flagStatus'),
    width: 'w-[130px]',
    options: [
      { value: 'all', label: t('solutions.filters.all') },
      { value: 'flagged', label: t('solutions.filters.flagged') },
      { value: 'clean', label: t('solutions.filters.clean') },
    ],
  },
  {
    modelValue: publishedFilter.value,
    placeholder: t('solutions.filters.visibility'),
    width: 'w-[130px]',
    options: [
      { value: 'all', label: t('solutions.filters.all') },
      { value: 'published', label: t('solutions.filters.published') },
      { value: 'unpublished', label: t('solutions.filters.unpublished') },
    ],
  },
])

const {
  searchQuery,
  tablePagination,
  loading,
  data,
  total,
  error,
  loadEntities: loadSolutions,
} = useDataTable<
  Solution,
  { flaggedFilter: string; publishedFilter: string },
  Parameters<typeof solutionsStore.fetchSolutions>[0]
>({
  store: {
    data: computed(() => solutionsStore.solutions),
    total: computed(() => solutionsStore.total),
    isLoading: computed(() => solutionsStore.loading),
    error: computed(() => solutionsStore.error),
    fetch: (params) => solutionsStore.fetchSolutions(params),
  },
  filters: {
    flaggedFilter: flaggedFilter.value,
    publishedFilter: publishedFilter.value,
  },
  transformParams: ({ search, filters, page, limit }) => ({
    search,
    is_flagged: filters.flaggedFilter === 'all' ? undefined : filters.flaggedFilter === 'flagged',
    is_published:
      filters.publishedFilter === 'all'
        ? undefined
        : filters.publishedFilter === 'published'
          ? true
          : false,
    page,
    limit,
  }),
  autoLoad: true,
})

const columns = createColumns(
  t,
  {
    viewSolution: (id: string) => {
      router.push({ name: 'solution-view-description', params: { id } })
    },
    openFlagDialog: (solution: Solution) => {
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
    confirmDelete: (solution: Solution) => {
      selectedSolutionId.value = solution.id
      selectedSolutionTitle.value = solution.title
      deleteDialogOpen.value = true
    },
  },
  () => canUpdateSolution.value,
  () => canDeleteSolution.value,
)

async function handleDeleteSolution(id: string | number) {
  await solutionsStore.deleteSolution(String(id))
}

async function handleFlagSolution(id: string | number, reason?: string) {
  await solutionsStore.flagSolution(String(id), { reason: reason || '' })
}
</script>

<template>
  <div class="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
    <DataTable
      :columns="columns"
      :data="data"
      :pagination="tablePagination"
      :row-count="total"
      :loading="loading"
      @update:pagination="tablePagination = $event"
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
    </DataTable>

    <!-- Error state -->
    <div
      v-if="error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ error }}</span>
      <Button variant="outline" size="sm" @click="loadSolutions()">{{ t('common.retry') }}</Button>
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
