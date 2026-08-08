<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { IconPlus, IconTrophy, IconDownload, IconUpload, IconDatabase } from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useProblemsStore } from '@/stores/admin/problems'
import { useAuthStore } from '@/stores/auth'
import { type Problem } from '@/api/admin/problems'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import ProblemImportDialog from '@/components/problems/ProblemImportDialog.vue'
import BulkActionDialog from '@/components/problems/BulkActionDialog.vue'
import BulkEditDialog from '@/components/problems/BulkEditDialog.vue'
import FlagInfoDialog from '@/components/problems/FlagInfoDialog.vue'
import ProblemBulkActions from './components/ProblemBulkActions.vue'
import ProblemAuditDrawer from './components/ProblemAuditDrawer.vue'
import { useDataTable } from '@/composables/useDataTable'
import { useProblemFilters } from './composables/useProblemFilters'
import { useProblemActions } from './composables/useProblemActions'
import { useProblemColumns } from './composables/useProblemColumns'

const { t } = useI18n()
const router = useRouter()
const problemsStore = useProblemsStore()
const authStore = useAuthStore()

const {
  searchQuery,
  difficultyFilter,
  statusFilter,
  publishedFilter,
  sortBy,
  sortOrder,
  buildFilterParams,
  buildExportParams,
} = useProblemFilters()

const {
  searchQuery: internalSearchQuery,
  tablePagination,
  loading,
  data,
  total,
  error,
  loadEntities: loadProblems,
} = useDataTable<
  Problem,
  { sortBy: string; sortOrder: 'asc' | 'desc' },
  Parameters<typeof problemsStore.fetchProblems>[0]
>({
  store: {
    data: computed(() => problemsStore.problems),
    total: computed(() => problemsStore.total),
    isLoading: computed(() => problemsStore.loading),
    error: computed(() => problemsStore.error),
    fetch: (params) => problemsStore.fetchProblems(params),
  },
  filters: () => ({
    sortBy: sortBy.value,
    sortOrder: sortOrder.value,
  }),
  transformParams: ({ search, page, limit }) => ({
    search,
    ...buildFilterParams({ pageIndex: page - 1, pageSize: limit }),
  }),
  autoLoad: false,
})

// Initialize pageIndex from URL
tablePagination.value.pageIndex = Math.max(
  0,
  (Number(router.currentRoute.value.query.page) || 1) - 1,
)

const {
  selectedProblemId,
  selectedProblemTitle,
  deleteDialogOpen,
  flagDialogOpen,
  selectedProblemForFlag,
  selectedProblemForFlagTitle,
  flagInfoDialogOpen,
  selectedProblemForFlagInfo,
  auditDrawerOpen,
  auditDrawerProblemId,
  importDialogOpen,
  selectedRows,
  bulkActionDialogOpen,
  bulkActionType,
  bulkActionLoading,
  bulkEditDialogOpen,
  viewProblem,
  viewProblemCode,
  viewProblemCases,
  confirmDelete,
  handleDeleteProblem,
  publishProblem,
  unpublishProblem,
  openFlagDialog,
  viewFlagInfo,
  openAuditDrawer,
  handleFlagProblem,
  unflagProblem,
  exportProblems,
  handleImported,
  handleBulkAction,
  confirmBulkAction,
  handleBulkEdited,
} = useProblemActions(() => loadProblems())

// Permissions
const canCreateProblem = computed(() => authStore.hasPermission('CREATE', 'PROBLEM'))
const canUpdateProblem = computed(() => authStore.hasPermission('UPDATE', 'PROBLEM'))
const canDeleteProblem = computed(() => authStore.hasPermission('DELETE', 'PROBLEM'))

// Animation state
const isLoaded = ref(false)

// Stats
const stats = computed(() => {
  const problems = problemsStore.problems
  const total = problemsStore.total
  const published = problems.filter((p) => p.isPublished ?? p.is_published).length
  const draft = problems.filter((p) => !(p.isPublished ?? p.is_published)).length
  const flagged = problems.filter((p) => p.isFlagged ?? p.is_flagged).length
  return { total, published, draft, flagged }
})

// Toolbar filters
const toolbarFilters = computed<Filter[]>(() => [
  {
    modelValue: difficultyFilter.value,
    placeholder: t('problems.filters.allDifficulty'),
    width: 'w-[140px]',
    options: [
      { value: 'all', label: t('problems.filters.allDifficulty') },
      { value: 'EASY', label: t('problems.difficulty.EASY') },
      { value: 'MEDIUM', label: t('problems.difficulty.MEDIUM') },
      { value: 'HARD', label: t('problems.difficulty.HARD') },
    ],
  },
  {
    modelValue: statusFilter.value,
    placeholder: t('problems.filters.allStatus'),
    width: 'w-[140px]',
    options: [
      { value: 'all', label: t('problems.filters.allStatus') },
      { value: 'DRAFT', label: t('problems.status.DRAFT') },
      { value: 'PUBLISHED', label: t('problems.status.PUBLISHED') },
      { value: 'ARCHIVED', label: t('problems.status.ARCHIVED') },
    ],
  },
  {
    modelValue: publishedFilter.value,
    placeholder: t('problems.filters.allPublished'),
    width: 'w-[160px]',
    options: [
      { value: 'all', label: t('problems.filters.allPublished') },
      { value: 'published', label: t('problems.filters.published') },
      { value: 'unpublished', label: t('problems.filters.unpublished') },
    ],
  },
])

// Table columns (delegated to composable)
const columns = useProblemColumns(canUpdateProblem, canDeleteProblem, {
  viewProblem,
  viewProblemCode,
  viewProblemCases,
  viewFlagInfo,
  openFlagDialog,
  openAuditDrawer,
  unflagProblem,
  publishProblem,
  unpublishProblem,
  confirmDelete,
})

// Sync external searchQuery with useDataTable internal
watch(
  searchQuery,
  (newValue) => {
    internalSearchQuery.value = newValue
  },
  { immediate: true },
)

// Load on mount
onMounted(() => {
  loadProblems()
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

// Reload when the Problem-specific toolbar filters change. sortBy/sortOrder and
// pagination are intentionally NOT watched here: useDataTable already owns
// reload for its `filters` ({sortBy, sortOrder}) and `tablePagination`, so
// adding them here would fire a second concurrent fetch on every sort/page
// change. Keeping this watcher scoped to difficulty/status/published makes
// useDataTable the single owner of sort + pagination reload.
watch([difficultyFilter, statusFilter, publishedFilter], () => {
  loadProblems()
})
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
          {{ t('problems.title') }}
        </h1>
        <Button
          v-if="canCreateProblem"
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)] transition-colors"
          @click="router.push({ name: 'problem-create' })"
        >
          <IconPlus class="h-4 w-4 mr-1.5" />
          <span class="uppercase tracking-wider">{{ t('problems.addProblem') }}</span>
        </Button>
      </div>

      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('problems.stats.total') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('problems.stats.published') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums">{{
            stats.published
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('problems.stats.draft') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.draft
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('problems.stats.flagged') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-red)] tabular-nums">{{
            stats.flagged
          }}</span>
        </div>
        <div class="ml-auto hidden sm:flex items-center gap-2 text-[var(--silver-400)]">
          <IconDatabase class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{
            t('problems.stats.problemManagement')
          }}</span>
        </div>
      </div>
    </div>

    <ProblemBulkActions
      :selected-count="selectedRows.length"
      :loading="bulkActionLoading"
      @bulk-publish="handleBulkAction('publish')"
      @bulk-unpublish="handleBulkAction('unpublish')"
      @bulk-delete="handleBulkAction('delete')"
      @bulk-edit="bulkEditDialogOpen = true"
      @clear-selection="selectedRows = []"
    />

    <div class="flex-1">
      <DataTable
        class="terminal-table"
        :columns="columns"
        :data="data"
        :pagination="tablePagination"
        :row-count="total"
        :loading="loading"
        :selected-rows="selectedRows"
        @update:pagination="tablePagination = $event"
        @update:selected-rows="selectedRows = $event"
      >
        <template #toolbar-left>
          <DataTableToolbar
            :search-model-value="searchQuery"
            @update:search-model-value="searchQuery = $event"
            :search-placeholder="t('problems.searchPlaceholder')"
            search-width="min-w-[150px] w-full lg:w-[250px]"
            :filters="toolbarFilters"
            @update:filter="
              (index, value) => {
                if (index === 0) difficultyFilter = String(value)
                else if (index === 1) statusFilter = String(value)
                else publishedFilter = String(value)
              }
            "
            :loading="loading"
            :on-refresh="loadProblems"
          >
            <template #extra-actions>
              <Select v-model="sortBy">
                <SelectTrigger
                  variant="terminal"
                  size="sm"
                  class="h-8 w-[150px] bg-[var(--surface-sunken)] border-[var(--silver-300)] dark:border-[var(--silver-300)] focus:border-[var(--accent-electric)]"
                >
                  <SelectValue :placeholder="t('problems.sort.title')" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="default">{{ t('problems.sort.default') }}</SelectItem>
                  <SelectItem value="title">{{ t('problems.sort.titleAsc') }}</SelectItem>
                  <SelectItem value="difficulty">{{ t('problems.sort.difficultyAsc') }}</SelectItem>
                  <SelectItem value="createdAt">{{ t('problems.sort.createdDesc') }}</SelectItem>
                  <SelectItem value="updatedAt">{{ t('problems.sort.updatedDesc') }}</SelectItem>
                  <SelectItem value="submissionCount">{{
                    t('problems.sort.submissionsDesc')
                  }}</SelectItem>
                </SelectContent>
              </Select>
              <Button
                variant="ghost"
                size="icon"
                class="h-8 w-8"
                @click="sortOrder = sortOrder === 'asc' ? 'desc' : 'asc'"
                :title="t('common.sort')"
                :aria-label="t('common.sort')"
              >
                <IconTrophy class="h-3.5 w-3.5" :class="{ 'rotate-180': sortOrder === 'asc' }" />
              </Button>
            </template>
          </DataTableToolbar>
        </template>

        <template #extra-actions>
          <div class="flex items-center gap-2">
            <DropdownMenu>
              <DropdownMenuTrigger as-child>
                <Button
                  variant="terminal"
                  size="sm"
                  class="h-8 font-data text-xs border-[var(--silver-300)]"
                >
                  <IconDownload class="h-4 w-4 mr-1.5" />
                  <span class="hidden sm:inline uppercase tracking-wider">{{
                    t('problems.export.title')
                  }}</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem @click="exportProblems(buildExportParams(), 'json')">{{
                  t('problems.export.json')
                }}</DropdownMenuItem>
                <DropdownMenuItem @click="exportProblems(buildExportParams(), 'csv')">{{
                  t('problems.export.csv')
                }}</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
            <Button
              variant="terminal"
              size="sm"
              class="h-8 font-data text-xs border-[var(--silver-300)]"
              @click="importDialogOpen = true"
            >
              <IconUpload class="h-4 w-4 mr-1.5" />
              <span class="hidden sm:inline uppercase tracking-wider">{{
                t('problems.import.title')
              }}</span>
            </Button>
          </div>
        </template>
      </DataTable>

      <div
        v-if="error"
        class="mt-4 flex items-center justify-between border border-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--terminal-red)_15%,_transparent)] p-4"
      >
        <div class="flex items-center gap-3">
          <span class="font-data text-sm text-[var(--terminal-red)]">&gt; ERROR:</span>
          <span class="text-sm text-[var(--foreground)]">{{ error }}</span>
        </div>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_15%,_transparent)]"
          @click="loadProblems()"
        >
          {{ t('common.retry') }}
        </Button>
      </div>
    </div>
  </div>

  <EntityActionDialog
    v-model:open="deleteDialogOpen"
    :entity-id="selectedProblemId"
    :entity-title="selectedProblemTitle"
    action="delete"
    :title="t('problems.dialog.delete.title')"
    :description="
      t('problems.dialog.delete.description', {
        title: selectedProblemTitle || t('problems.dialog.delete.thisProblem'),
      })
    "
    :confirm-label="t('problems.dialog.delete.confirm')"
    :cancel-label="t('common.cancel')"
    :success-label="t('problems.toast.deleteSuccess')"
    :error-label="t('problems.toast.deleteFailed')"
    :on-action="handleDeleteProblem"
    @success="loadProblems"
  />
  <EntityActionDialog
    v-model:open="flagDialogOpen"
    :entity-id="selectedProblemForFlag"
    :entity-title="selectedProblemForFlagTitle"
    action="flag"
    :title="t('moderation.flagProblem')"
    :description="
      t('moderation.flagDescription', {
        title: selectedProblemForFlagTitle || t('problems.dialog.delete.thisProblem'),
      })
    "
    :confirm-label="t('moderation.flag')"
    :cancel-label="t('common.cancel')"
    :success-label="t('moderation.flagSuccess')"
    :error-label="t('moderation.flagError')"
    :on-action="handleFlagProblem"
    @success="loadProblems"
  />
  <ProblemImportDialog v-model:open="importDialogOpen" @imported="handleImported" />
  <BulkActionDialog
    v-model:open="bulkActionDialogOpen"
    :action="bulkActionType"
    :count="selectedRows.length"
    @confirm="confirmBulkAction"
  />
  <BulkEditDialog
    v-model:open="bulkEditDialogOpen"
    :problems="selectedRows"
    @edited="handleBulkEdited"
  />
  <FlagInfoDialog v-model:open="flagInfoDialogOpen" :problem="selectedProblemForFlagInfo" />
  <ProblemAuditDrawer v-model:open="auditDrawerOpen" :problem-id="auditDrawerProblemId" />
</template>
