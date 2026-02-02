<script setup lang="ts">
import { ref, computed, h } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import {
  IconCheck,
  IconDotsVertical,
  IconEyeOff,
  IconFile,
  IconFlag,
  IconTrash,
  IconUser,
  IconCode,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useSolutionsStore } from '@/stores/admin/solutions'
import { useAuthStore } from '@/stores/auth'
import type { Solution } from '@/api/admin/solutions'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
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

function viewSolution(id: string) {
  router.push({ name: 'solution-view-description', params: { id } })
}

function confirmDelete(solution: Solution) {
  selectedSolutionId.value = solution.id
  selectedSolutionTitle.value = solution.title
  deleteDialogOpen.value = true
}

function openFlagDialog(solution: Solution) {
  selectedSolutionId.value = solution.id
  selectedSolutionTitle.value = solution.title
  flagDialogOpen.value = true
}

async function unflagSolution(id: string) {
  try {
    await solutionsStore.unflagSolution(id)
    toast.success(t('solutions.toast.unflaggedSuccessfully'))
  } catch {
    toast.error(t('solutions.toast.failedToUnflag'))
  }
}

async function handleDeleteSolution(id: string | number) {
  await solutionsStore.deleteSolution(String(id))
}

async function handleFlagSolution(id: string | number, reason?: string) {
  await solutionsStore.flagSolution(String(id), { reason: reason || '' })
}

const columns: ColumnDef<Solution>[] = [
  {
    id: 'select',
    header: ({ table }) =>
      h(Checkbox, {
        modelValue:
          table.getIsAllPageRowsSelected() ||
          (table.getIsSomePageRowsSelected() && 'indeterminate'),
        'onUpdate:modelValue': (value: boolean | 'indeterminate') =>
          table.toggleAllPageRowsSelected(!!value),
        'aria-label': t('table.selectAll'),
      }),
    cell: ({ row }) =>
      h(Checkbox, {
        modelValue: row.getIsSelected(),
        'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
        'aria-label': t('common.select'),
      }),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'id',
    header: () => t('solutions.columns.id'),
    cell: ({ row }) => {
      const id = row.getValue('id') as string
      return h('span', { class: 'text-muted-foreground text-xs font-mono' }, id.slice(0, 8))
    },
  },
  {
    accessorKey: 'title',
    header: () => t('solutions.columns.solution'),
    cell: ({ row }) => {
      const solution = row.original
      return h('div', { class: 'flex flex-col' }, [
        h('span', { class: 'font-medium text-sm' }, solution.title),
        h('div', { class: 'flex items-center gap-1 text-xs text-muted-foreground' }, [
          h(IconCode, { class: 'h-3 w-3' }),
          h('span', {}, solution.problem?.title || t('common.noData')),
        ]),
      ])
    },
  },
  {
    accessorKey: 'author',
    header: () => t('solutions.columns.author'),
    cell: ({ row }) => {
      const author = row.original.author
      return h('div', { class: 'flex items-center gap-2' }, [
        h(IconUser, { class: 'h-3 w-3 text-muted-foreground' }),
        h('span', { class: 'text-sm' }, author?.username || t('common.noData')),
      ])
    },
  },
  {
    accessorKey: 'is_flagged',
    header: () => t('solutions.columns.status'),
    cell: ({ row }) => {
      const isFlagged = row.getValue('is_flagged') as boolean
      const isPublished = row.original.is_published
      const isDeleted = row.original.is_deleted

      if (isDeleted) {
        return h(Badge, { variant: 'destructive' }, () => [
          h(IconTrash, { class: 'mr-1 h-3 w-3' }),
          t('solutions.status.deleted'),
        ])
      }

      if (isFlagged) {
        return h(Badge, { variant: 'destructive' }, () => [
          h(IconFlag, { class: 'mr-1 h-3 w-3' }),
          t('solutions.status.flagged'),
        ])
      }

      return h(Badge, { variant: isPublished ? 'default' : 'secondary' }, () => [
        isPublished
          ? h(IconCheck, { class: 'mr-1 h-3 w-3' })
          : h(IconEyeOff, { class: 'mr-1 h-3 w-3' }),
        isPublished ? t('solutions.status.published') : t('solutions.status.unpublished'),
      ])
    },
  },
  {
    accessorKey: 'views',
    header: () => t('solutions.columns.views'),
    cell: ({ row }) => {
      const views = row.getValue('views') as number
      return h(
        'span',
        { class: 'text-muted-foreground text-sm tabular-nums' },
        views.toLocaleString(),
      )
    },
  },
  {
    accessorKey: 'created_at',
    header: () => t('solutions.columns.created'),
    cell: ({ row }) => {
      const date = new Date(row.getValue('created_at') as Date)
      return h('span', { class: 'text-muted-foreground text-sm' }, date.toLocaleDateString())
    },
  },
  {
    id: 'actions',
    header: () => t('solutions.columns.actions'),
    cell: ({ row }) => {
      const solution = row.original
      return h(
        DropdownMenu,
        {},
        {
          default: () => [
            h(
              DropdownMenuTrigger,
              { asChild: true },
              {
                default: () =>
                  h(
                    Button,
                    { variant: 'ghost', size: 'icon', class: 'h-8 w-8 p-0' },
                    {
                      default: () => [
                        h('span', { class: 'sr-only' }, t('common.open')),
                        h(IconDotsVertical, { class: 'h-4 w-4' }),
                      ],
                    },
                  ),
              },
            ),
            h(
              DropdownMenuContent,
              { align: 'end' },
              {
                default: () => [
                  h(
                    DropdownMenuItem,
                    { onClick: () => viewSolution(solution.id) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconFile, { class: 'h-4 w-4' }),
                          t('solutions.actions.viewDetails'),
                        ]),
                    },
                  ),
                  canUpdateSolution.value
                    ? solution.is_flagged
                      ? h(
                          DropdownMenuItem,
                          { onClick: () => unflagSolution(solution.id) },
                          {
                            default: () =>
                              h('div', { class: 'flex items-center gap-2 text-emerald-600' }, [
                                h(IconCheck, { class: 'h-4 w-4' }),
                                t('solutions.actions.unflag'),
                              ]),
                          },
                        )
                      : h(
                          DropdownMenuItem,
                          { onClick: () => openFlagDialog(solution) },
                          {
                            default: () =>
                              h('div', { class: 'flex items-center gap-2 text-amber-600' }, [
                                h(IconFlag, { class: 'h-4 w-4' }),
                                t('solutions.actions.flag'),
                              ]),
                          },
                        )
                    : null,
                  h(DropdownMenuSeparator, {}),
                  canDeleteSolution.value
                    ? h(
                        DropdownMenuItem,
                        { onClick: () => confirmDelete(solution) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                              h(IconTrash, { class: 'h-4 w-4' }),
                              t('solutions.actions.delete'),
                            ]),
                        },
                      )
                    : null,
                ],
              },
            ),
          ],
        },
      )
    },
  },
]
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
