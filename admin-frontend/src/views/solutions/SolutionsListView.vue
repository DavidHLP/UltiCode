<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import { useRouter } from 'vue-router'
import { watchDebounced } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import { useI18n } from 'vue-i18n'
import {
  IconCheck,
  IconDotsVertical,
  IconEyeOff,
  IconFile,
  IconFlag,
  IconRefresh,
  IconTrash,
  IconX,
  IconUser,
  IconCode,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useSolutionsStore } from '@/stores/admin/solutions'
import { useAuthStore } from '@/stores/admin/auth'
import type { Solution } from '@/api/admin/solutions'

import DataTable from '@/components/table/DataTable.vue'
import SolutionDeleteDialog from './SolutionDeleteDialog.vue'
import SolutionFlagDialog from './SolutionFlagDialog.vue'

const router = useRouter()
const solutionsStore = useSolutionsStore()
const authStore = useAuthStore()
const { t } = useI18n()

const searchQuery = ref('')
const flaggedFilter = ref<string>('all')
const publishedFilter = ref<string>('all')
const tablePagination = ref({ pageIndex: 0, pageSize: 10 })

const selectedSolutionId = ref<string | null>(null)
const selectedSolutionTitle = ref<string | null>(null)
const deleteDialogOpen = ref(false)
const flagDialogOpen = ref(false)

const canUpdateSolution = computed(() => authStore.hasPermission('MODERATE', 'SOLUTION'))
const canDeleteSolution = computed(() => authStore.hasPermission('DELETE', 'SOLUTION'))

onMounted(() => loadSolutions())

async function loadSolutions() {
  await solutionsStore.fetchSolutions({
    search: searchQuery.value || undefined,
    is_flagged: flaggedFilter.value === 'all' ? undefined : flaggedFilter.value === 'flagged',
    is_published:
      publishedFilter.value === 'all'
        ? undefined
        : publishedFilter.value === 'published'
          ? true
          : false,
    page: tablePagination.value.pageIndex + 1,
    limit: tablePagination.value.pageSize,
  })
}

// Watchers
watchDebounced(
  searchQuery,
  () => {
    tablePagination.value.pageIndex = 0
    loadSolutions()
  },
  { debounce: 500 },
)

watch([flaggedFilter, publishedFilter], () => {
  tablePagination.value.pageIndex = 0
  loadSolutions()
})

watch(
  () => tablePagination.value,
  () => loadSolutions(),
  { deep: true },
)

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
    // We update local state in store, so no need to reload unless desired
  } catch {
    toast.error(t('solutions.toast.failedToUnflag'))
  }
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
      :data="solutionsStore.solutions"
      :pagination="tablePagination"
      :row-count="solutionsStore.total"
      :loading="solutionsStore.loading"
      @update:pagination="tablePagination = $event"
    >
      <template #toolbar-left>
        <div class="flex flex-wrap items-center gap-2 w-full lg:w-auto">
          <Input
            v-model="searchQuery"
            :placeholder="t('solutions.searchPlaceholder')"
            class="h-8 min-w-[150px] w-full lg:w-[250px]"
          >
            <template #trailing>
              <button
                v-if="searchQuery"
                @click="searchQuery = ''"
                class="rounded-sm opacity-70 hover:opacity-100"
              >
                <IconX class="h-3 w-3" />
              </button>
            </template>
          </Input>

          <div class="flex items-center gap-2 overflow-x-auto pb-1 lg:pb-0">
            <Select v-model="flaggedFilter">
              <SelectTrigger class="h-8 w-[130px]">
                <SelectValue :placeholder="t('solutions.filters.flagStatus')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{{ t('solutions.filters.all') }}</SelectItem>
                <SelectItem value="flagged">{{ t('solutions.filters.flagged') }}</SelectItem>
                <SelectItem value="clean">{{ t('solutions.filters.clean') }}</SelectItem>
              </SelectContent>
            </Select>

            <Select v-model="publishedFilter">
              <SelectTrigger class="h-8 w-[130px]">
                <SelectValue :placeholder="t('solutions.filters.visibility')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{{ t('solutions.filters.all') }}</SelectItem>
                <SelectItem value="published">{{ t('solutions.filters.published') }}</SelectItem>
                <SelectItem value="unpublished">{{
                  t('solutions.filters.unpublished')
                }}</SelectItem>
              </SelectContent>
            </Select>

            <Button
              variant="ghost"
              size="icon"
              class="h-8 w-8"
              @click="loadSolutions()"
              :title="t('common.refresh')"
            >
              <IconRefresh
                class="h-3.5 w-3.5"
                :class="{ 'animate-spin': solutionsStore.loading }"
              />
            </Button>
          </div>
        </div>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="solutionsStore.error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ solutionsStore.error }}</span>
      <Button variant="outline" size="sm" @click="loadSolutions()">{{ t('common.retry') }}</Button>
    </div>
  </div>

  <SolutionDeleteDialog
    v-model:open="deleteDialogOpen"
    :solution-id="selectedSolutionId"
    :solution-title="selectedSolutionTitle"
    @success="loadSolutions"
  />

  <SolutionFlagDialog
    v-model:open="flagDialogOpen"
    :solution-id="selectedSolutionId"
    :solution-title="selectedSolutionTitle"
    @success="loadSolutions"
  />
</template>
