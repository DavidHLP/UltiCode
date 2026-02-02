<script setup lang="ts">
import { ref, computed, onMounted, h, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useDebounceFn } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import {
  IconAlertTriangle,
  IconCheck,
  IconCircleCheckFilled,
  IconDotsVertical,
  IconEye,
  IconEyeOff,
  IconFile,
  IconFlag,
  IconFlagOff,
  IconFlask,
  IconBrackets,
  IconLoader,
  IconPencil,
  IconPlus,
  IconRefresh,
  IconSparkles,
  IconTrophy,
  IconTrash,
  IconX,
  IconDownload,
  IconUpload,
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
  DropdownMenuSub,
  DropdownMenuSubTrigger,
  DropdownMenuSubContent,
} from '@/components/ui/dropdown-menu'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useProblemsStore } from '@/stores/admin/problems'
import { useAuthStore } from '@/stores/auth'
import { Difficulty, type Problem, problemsApi } from '@/api/admin/problems'
import { ApiError } from '@/utils/request'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import ProblemImportDialog from '@/components/problems/ProblemImportDialog.vue'
import BulkActionDialog from '@/components/problems/BulkActionDialog.vue'
import BulkEditDialog from '@/components/problems/BulkEditDialog.vue'
import { useDataTable } from '@/composables/useDataTable'
import { getDifficultyBadgeVariant, getDifficultyColor } from '@/lib/entities/problem'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const problemsStore = useProblemsStore()
const authStore = useAuthStore()

// Initialize filters from URL query params
const searchQuery = ref((route.query.search as string) || '')
const difficultyFilter = ref((route.query.difficulty as string) || 'all')
const statusFilter = ref((route.query.status as string) || 'all')
const publishedFilter = ref((route.query.published as string) || 'all')

// Convert page from query (1-based) to pageIndex (0-based)
const initialPage = Number(route.query.page) || 1
const sortBy = ref((route.query.sortBy as string) || 'default')
const sortOrder = ref<'asc' | 'desc'>((route.query.sortOrder as 'asc' | 'desc') || 'desc')

const selectedProblemId = ref<string | null>(null)
const selectedProblemTitle = ref<string | null>(null)
const deleteDialogOpen = ref(false)
const importing = ref(false)
const importDialogOpen = ref(false)
const selectedRows = ref<Problem[]>([])
const bulkActionDialogOpen = ref(false)
const bulkActionType = ref<'publish' | 'unpublish' | 'delete' | 'restore'>('publish')
const bulkActionLoading = ref(false)
const bulkEditDialogOpen = ref(false)

const canCreateProblem = computed(() => authStore.hasPermission('CREATE', 'PROBLEM'))
const canUpdateProblem = computed(() => authStore.hasPermission('UPDATE', 'PROBLEM'))
const canDeleteProblem = computed(() => authStore.hasPermission('DELETE', 'PROBLEM'))

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
  {
    sortBy: string
    sortOrder: 'asc' | 'desc'
  },
  Parameters<typeof problemsStore.fetchProblems>[0]
>({
  store: {
    data: computed(() => problemsStore.problems),
    total: computed(() => problemsStore.total),
    isLoading: computed(() => problemsStore.loading),
    error: computed(() => problemsStore.error),
    fetch: (params) => problemsStore.fetchProblems(params),
  },
  filters: {
    sortBy: sortBy.value,
    sortOrder: sortOrder.value,
  },
  transformParams: ({ search, filters, page, limit }) => ({
    search,
    difficulty:
      difficultyFilter.value === 'all' ? undefined : (difficultyFilter.value as Difficulty),
    status: statusFilter.value === 'all' ? undefined : (statusFilter.value as Problem['status']),
    is_published:
      publishedFilter.value === 'all'
        ? undefined
        : publishedFilter.value === 'published'
          ? true
          : false,
    sortBy: filters.sortBy === 'default' ? undefined : filters.sortBy,
    sortOrder: filters.sortOrder || undefined,
    page,
    limit,
  }),
  autoLoad: false,
})

// Initialize pageIndex from URL
tablePagination.value.pageIndex = Math.max(0, initialPage - 1)

// Sync external searchQuery with internal one and trigger reload
watch(
  () => searchQuery.value,
  (newValue) => {
    internalSearchQuery.value = newValue
  },
  { immediate: true },
)

// Load on mount
onMounted(() => loadProblems())

// URL synchronization - debounced to avoid excessive updates
const debouncedUpdateUrl = useDebounceFn(() => {
  router.push({
    query: {
      ...(searchQuery.value && { search: searchQuery.value }),
      ...(difficultyFilter.value !== 'all' && { difficulty: difficultyFilter.value }),
      ...(statusFilter.value !== 'all' && { status: statusFilter.value }),
      ...(publishedFilter.value !== 'all' && { published: publishedFilter.value }),
      ...(sortBy.value !== 'default' && { sortBy: sortBy.value }),
      ...(sortOrder.value && { sortOrder: sortOrder.value }),
      page: (tablePagination.value.pageIndex + 1).toString(),
    },
  })
}, 300)

// Watch all filter state changes and update URL
watch(
  [
    searchQuery,
    difficultyFilter,
    statusFilter,
    publishedFilter,
    sortBy,
    sortOrder,
    tablePagination,
  ],
  debouncedUpdateUrl,
  { deep: true },
)

// Watch filters for data reload (debouncing search separately)
watch([difficultyFilter, statusFilter, publishedFilter, sortBy, sortOrder], () => {
  loadProblems()
})

// Watch pagination for data reload
watch(
  () => tablePagination.value,
  () => loadProblems(),
  { deep: true },
)

// Handle browser back/forward navigation
watch(
  () => route.query,
  (newQuery) => {
    searchQuery.value = (newQuery.search as string) || ''
    difficultyFilter.value = (newQuery.difficulty as string) || 'all'
    statusFilter.value = (newQuery.status as string) || 'all'
    publishedFilter.value = (newQuery.published as string) || 'all'
    sortBy.value = (newQuery.sortBy as string) || 'default'
    sortOrder.value = (newQuery.sortOrder as 'asc' | 'desc') || 'desc'

    const page = Number(newQuery.page) || 1
    tablePagination.value.pageIndex = Math.max(0, page - 1)

    loadProblems()
  },
  { deep: true },
)

function viewProblem(id: string) {
  router.push({ name: 'problem-view-description', params: { id } })
}

function viewProblemCode(id: string) {
  router.push({ name: 'problem-view-code', params: { id } })
}

function viewProblemCases(id: string) {
  router.push({ name: 'problem-view-cases', params: { id } })
}

function editProblem(id: string) {
  router.push({ name: 'problem-edit-description', params: { id } })
}

function editProblemCode(id: string) {
  router.push({ name: 'problem-edit-code', params: { id } })
}

function editProblemCases(id: string) {
  router.push({ name: 'problem-edit-cases', params: { id } })
}

function confirmDelete(problem: Problem) {
  selectedProblemId.value = problem.id
  selectedProblemTitle.value = problem.title
  deleteDialogOpen.value = true
}

async function handleDeleteProblem(id: string | number) {
  await problemsStore.deleteProblem(String(id))
}

// Error context handler for detailed error messages
interface ErrorContext {
  title: string
  message: string
  suggestion?: string
  canRetry: boolean
}

function getErrorContext(error: unknown, action: string): ErrorContext {
  const apiError = error instanceof ApiError ? error : null
  const statusCode = apiError?.code || 0
  const errorMessage = apiError?.response?.data?.message || apiError?.message || 'Unknown error'

  switch (statusCode) {
    case 400:
      return {
        title: t('errors.validation.title'),
        message: errorMessage || t('errors.validation.default'),
        suggestion: t('errors.validation.suggestion'),
        canRetry: false,
      }
    case 401:
      return {
        title: t('errors.unauthorized.title'),
        message: t('errors.unauthorized.message'),
        suggestion: t('errors.unauthorized.suggestion'),
        canRetry: false,
      }
    case 403:
      return {
        title: t('errors.forbidden.title'),
        message: t('errors.forbidden.message'),
        suggestion: t('errors.forbidden.suggestion'),
        canRetry: false,
      }
    case 404:
      return {
        title: t('errors.notFound.title'),
        message: `${action} ${t('errors.notFound.message')}`,
        suggestion: t('errors.notFound.suggestion'),
        canRetry: false,
      }
    case 500:
    case 502:
    case 503:
      return {
        title: t('errors.serverError.title'),
        message: t('errors.serverError.message'),
        suggestion: t('errors.serverError.suggestion'),
        canRetry: true,
      }
    default:
      return {
        title: t('errors.network.title'),
        message: errorMessage,
        suggestion: t('errors.network.suggestion'),
        canRetry: true,
      }
  }
}

async function publishProblem(id: string) {
  try {
    await problemsStore.publishProblem(id)
    toast.success(t('problems.toast.publishSuccess'))
    await loadProblems()
  } catch (error) {
    const ctx = getErrorContext(error, t('problems.actions.publish'))
    toast.error(ctx.message, {
      description: ctx.suggestion,
      action: ctx.canRetry
        ? {
            label: t('common.retry'),
            onClick: () => publishProblem(id),
          }
        : undefined,
    })
  }
}

async function unpublishProblem(id: string) {
  try {
    await problemsStore.unpublishProblem(id)
    toast.success(t('problems.toast.unpublishSuccess'))
    await loadProblems()
  } catch (error) {
    const ctx = getErrorContext(error, t('problems.actions.unpublish'))
    toast.error(ctx.message, {
      description: ctx.suggestion,
      action: ctx.canRetry
        ? {
            label: t('common.retry'),
            onClick: () => unpublishProblem(id),
          }
        : undefined,
    })
  }
}

async function flagProblem(id: string) {
  try {
    const reason = prompt(t('moderation.reasonPrompt'))
    if (!reason) return
    await problemsApi.flagProblem(id, reason)
    toast.success(t('moderation.flagSuccess'))
    await loadProblems()
  } catch (error) {
    const ctx = getErrorContext(error, t('problems.actions.flag'))
    toast.error(ctx.message, { description: ctx.suggestion })
  }
}

async function unflagProblem(id: string) {
  try {
    await problemsApi.moderateProblem(id, { status: 'DISMISSED' })
    toast.success(t('moderation.unflagSuccess'))
    await loadProblems()
  } catch (error) {
    const ctx = getErrorContext(error, t('problems.actions.unflag'))
    toast.error(ctx.message, { description: ctx.suggestion })
  }
}

async function exportProblems(format: 'json' | 'csv') {
  try {
    importing.value = true
    await problemsApi.exportProblems(
      {
        search: searchQuery.value || undefined,
        difficulty:
          difficultyFilter.value === 'all' ? undefined : (difficultyFilter.value as Difficulty),
        status:
          statusFilter.value === 'all' ? undefined : (statusFilter.value as Problem['status']),
        is_published:
          publishedFilter.value === 'all'
            ? undefined
            : publishedFilter.value === 'published'
              ? true
              : false,
      },
      format,
    )
    toast.success(t('problems.export.success'))
  } catch (error) {
    console.error('Failed to export problems:', error)
    const ctx = getErrorContext(error, t('problems.actions.export'))
    toast.error(ctx.message, {
      description: ctx.suggestion,
    })
  } finally {
    importing.value = false
  }
}

async function handleImported() {
  await loadProblems()
}

async function handleBulkAction(action: 'publish' | 'unpublish' | 'delete' | 'restore') {
  if (selectedRows.value.length === 0) {
    toast.error(t('problems.bulk.noSelection'))
    return
  }
  bulkActionType.value = action
  bulkActionDialogOpen.value = true
}

async function confirmBulkAction() {
  if (selectedRows.value.length === 0) return

  bulkActionLoading.value = true
  try {
    const response = await problemsApi.bulkAction({
      ids: selectedRows.value.map((p) => p.id),
      action: bulkActionType.value,
    })

    const successCount = response.results.filter((r) => r.success).length
    const failedCount = response.results.filter((r) => !r.success).length

    if (failedCount === 0) {
      toast.success(
        t('problems.bulk.success', {
          count: successCount,
          action: t(`problems.bulk.${bulkActionType.value}`),
        }),
      )
    } else if (successCount === 0) {
      toast.error(
        t('problems.bulk.failed', {
          count: failedCount,
          action: t(`problems.bulk.${bulkActionType.value}`),
        }),
      )
    } else {
      toast.warning(
        t('problems.bulk.partial', {
          success: successCount,
          failed: failedCount,
          action: t(`problems.bulk.${bulkActionType.value}`),
        }),
      )
    }

    selectedRows.value = []
    bulkActionDialogOpen.value = false
    await loadProblems()
  } catch (error) {
    console.error('Failed to perform bulk action:', error)
    const ctx = getErrorContext(error, t('problems.bulk.action'))
    toast.error(ctx.message, {
      description: ctx.suggestion,
    })
  } finally {
    bulkActionLoading.value = false
  }
}

async function handleBulkEdited() {
  selectedRows.value = []
  bulkEditDialogOpen.value = false
  await loadProblems()
}

function getDifficultyIcon(difficulty: Difficulty) {
  switch (difficulty) {
    case 'EASY':
      return IconCheck
    case 'MEDIUM':
      return IconSparkles
    case 'HARD':
      return IconTrophy
    default:
      return IconFile
  }
}

const columns: ColumnDef<Problem>[] = [
  {
    id: 'select',
    header: ({ table }) =>
      h(Checkbox, {
        modelValue:
          table.getIsAllPageRowsSelected() ||
          (table.getIsSomePageRowsSelected() && 'indeterminate'),
        'onUpdate:modelValue': (value: boolean | 'indeterminate') =>
          table.toggleAllPageRowsSelected(!!value),
        'aria-label': 'Select all',
      }),
    cell: ({ row }) =>
      h(Checkbox, {
        modelValue: row.getIsSelected(),
        'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
        'aria-label': 'Select row',
      }),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'id',
    header: () => t('problems.columns.id'),
    cell: ({ row }) => {
      const id = row.getValue('id') as string
      return h('span', { class: 'text-muted-foreground text-xs font-mono' }, id.slice(0, 8))
    },
  },
  {
    accessorKey: 'title',
    header: () => t('problems.columns.problem'),
    cell: ({ row }) => {
      const problem = row.original
      return h('div', { class: 'flex flex-col' }, [
        h('span', { class: 'font-medium text-sm' }, problem.title),
        h('span', { class: 'text-muted-foreground text-xs' }, problem.slug),
      ])
    },
  },
  {
    accessorKey: 'difficulty',
    header: () => t('problems.columns.difficulty'),
    cell: ({ row }) => {
      const difficulty = row.getValue('difficulty') as Difficulty
      const icon = getDifficultyIcon(difficulty)
      const color = getDifficultyColor(difficulty)
      return h('div', { class: 'flex items-center gap-2' }, [
        h(icon, { class: `h-4 w-4 ${color}` }),
        h(Badge, { variant: getDifficultyBadgeVariant(difficulty) }, () =>
          t(`problems.difficulty.${difficulty}`),
        ),
      ])
    },
  },
  {
    accessorKey: 'status',
    header: () => t('common.status'),
    cell: ({ row }) => {
      const status = row.getValue('status') as string
      const isSolved = status === 'solved'
      const isAttempted = status === 'attempted'
      const icon = isSolved ? IconCircleCheckFilled : undefined
      const variant = isSolved
        ? ('default' as const)
        : isAttempted
          ? ('secondary' as const)
          : ('outline' as const)
      const label = t(`problems.status.${isSolved ? 'solved' : isAttempted ? 'attempted' : 'todo'}`)
      return h('div', { class: 'flex items-center gap-2' }, [
        icon
          ? h(icon, { class: 'h-4 w-4 text-emerald-500' })
          : h(IconLoader, { class: 'h-4 w-4 animate-spin text-muted-foreground' }),
        h(Badge, { variant }, () => label),
      ])
    },
  },
  {
    accessorKey: 'is_published',
    header: () => t('problems.columns.published'),
    cell: ({ row }) => {
      const isPublished = row.getValue('is_published') as boolean
      const isDeleted = row.original.is_deleted
      if (isDeleted) {
        return h(
          Badge,
          { variant: 'destructive' },
          {
            default: () => [h(IconX, { class: 'mr-1 h-3 w-3' }), t('problems.published.deleted')],
          },
        )
      }
      return h(
        Badge,
        { variant: isPublished ? 'default' : 'secondary' },
        {
          default: () => [
            isPublished
              ? h(IconCheck, { class: 'mr-1 h-3 w-3' })
              : h(IconEyeOff, { class: 'mr-1 h-3 w-3' }),
            isPublished ? t('problems.published.published') : t('problems.published.draft'),
          ],
        },
      )
    },
  },
  {
    accessorKey: 'is_flagged',
    header: () => t('problems.columns.flagged'),
    cell: ({ row }) => {
      const isFlagged = row.original.is_flagged
      if (!isFlagged) {
        return h('span', { class: 'text-muted-foreground text-sm' }, '—')
      }
      return h(
        Badge,
        { variant: 'destructive', class: 'gap-1' },
        {
          default: () => [
            h(IconAlertTriangle, { class: 'h-3 w-3' }),
            t('moderation.statusPending'),
          ],
        },
      )
    },
  },
  {
    accessorKey: 'submission_count',
    header: () => t('problems.columns.submissions'),
    cell: ({ row }) => {
      const count = row.original.submission_count || 0
      return h(
        'span',
        { class: 'text-muted-foreground text-sm tabular-nums' },
        count.toLocaleString(),
      )
    },
  },
  {
    accessorKey: 'tags',
    header: () => t('problems.columns.tags'),
    cell: ({ row }) => {
      const tags = row.original.tags || []
      if (tags.length === 0) {
        return h('span', { class: 'text-muted-foreground text-sm' }, '—')
      }
      return h(
        'div',
        { class: 'flex items-center gap-1 flex-wrap' },
        tags.slice(0, 3).map((tag) =>
          h(
            Badge,
            { variant: 'outline', class: 'text-xs' },
            {
              default: () => tag.label,
            },
          ),
        ),
      )
    },
  },
  {
    accessorKey: 'created_at',
    header: () => t('common.created'),
    cell: ({ row }) => {
      const date = new Date(row.getValue('created_at') as Date)
      return h('span', { class: 'text-muted-foreground text-sm' }, date.toLocaleDateString())
    },
  },
  {
    id: 'actions',
    header: () => t('common.actions'),
    cell: ({ row }) => {
      const problem = row.original
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
                        h('span', { class: 'sr-only' }, 'Open menu'),
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
                  // View Sub-menu
                  h(
                    DropdownMenuSub,
                    {},
                    {
                      default: () => [
                        h(
                          DropdownMenuSubTrigger,
                          { class: 'gap-2' },
                          {
                            default: () => [h(IconEye, { class: 'h-4 w-4' }), t('common.view')],
                          },
                        ),
                        h(
                          DropdownMenuSubContent,
                          {},
                          {
                            default: () => [
                              h(
                                DropdownMenuItem,
                                { onClick: () => viewProblem(problem.id) },
                                {
                                  default: () =>
                                    h('div', { class: 'flex items-center gap-2' }, [
                                      h(IconFile, { class: 'h-4 w-4' }),
                                      t('problems.tabs.description'),
                                    ]),
                                },
                              ),
                              h(
                                DropdownMenuItem,
                                { onClick: () => viewProblemCode(problem.id) },
                                {
                                  default: () =>
                                    h('div', { class: 'flex items-center gap-2' }, [
                                      h(IconBrackets, { class: 'h-4 w-4' }),
                                      t('problems.tabs.code'),
                                    ]),
                                },
                              ),
                              h(
                                DropdownMenuItem,
                                { onClick: () => viewProblemCases(problem.id) },
                                {
                                  default: () =>
                                    h('div', { class: 'flex items-center gap-2' }, [
                                      h(IconFlask, { class: 'h-4 w-4' }),
                                      t('problems.tabs.testCases'),
                                    ]),
                                },
                              ),
                            ],
                          },
                        ),
                      ],
                    },
                  ),
                  // Edit Sub-menu
                  canUpdateProblem.value
                    ? h(
                        DropdownMenuSub,
                        {},
                        {
                          default: () => [
                            h(
                              DropdownMenuSubTrigger,
                              { class: 'gap-2' },
                              {
                                default: () => [
                                  h(IconPencil, { class: 'h-4 w-4' }),
                                  t('common.edit'),
                                ],
                              },
                            ),
                            h(
                              DropdownMenuSubContent,
                              {},
                              {
                                default: () => [
                                  h(
                                    DropdownMenuItem,
                                    { onClick: () => editProblem(problem.id) },
                                    {
                                      default: () =>
                                        h('div', { class: 'flex items-center gap-2' }, [
                                          h(IconFile, { class: 'h-4 w-4' }),
                                          t('problems.tabs.description'),
                                        ]),
                                    },
                                  ),
                                  h(
                                    DropdownMenuItem,
                                    { onClick: () => editProblemCode(problem.id) },
                                    {
                                      default: () =>
                                        h('div', { class: 'flex items-center gap-2' }, [
                                          h(IconBrackets, { class: 'h-4 w-4' }),
                                          t('problems.tabs.code'),
                                        ]),
                                    },
                                  ),
                                  h(
                                    DropdownMenuItem,
                                    { onClick: () => editProblemCases(problem.id) },
                                    {
                                      default: () =>
                                        h('div', { class: 'flex items-center gap-2' }, [
                                          h(IconFlask, { class: 'h-4 w-4' }),
                                          t('problems.tabs.testCases'),
                                        ]),
                                    },
                                  ),
                                ],
                              },
                            ),
                          ],
                        },
                      )
                    : null,
                  h(DropdownMenuSeparator, {}),
                  // Flag/Unflag action
                  canUpdateProblem.value
                    ? h(
                        DropdownMenuItem,
                        {
                          onClick: () =>
                            problem.is_flagged
                              ? unflagProblem(problem.id)
                              : flagProblem(problem.id),
                        },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              problem.is_flagged
                                ? h(IconFlagOff, { class: 'h-4 w-4 text-emerald-600' })
                                : h(IconFlag, { class: 'h-4 w-4 text-amber-600' }),
                              problem.is_flagged ? t('moderation.unflag') : t('moderation.flag'),
                            ]),
                        },
                      )
                    : null,
                  h(DropdownMenuSeparator, {}),
                  canUpdateProblem.value
                    ? problem.is_published
                      ? h(
                          DropdownMenuItem,
                          { onClick: () => unpublishProblem(problem.id) },
                          {
                            default: () =>
                              h('div', { class: 'flex items-center gap-2 text-amber-600' }, [
                                h(IconEyeOff, { class: 'h-4 w-4' }),
                                t('problems.actions.unpublish'),
                              ]),
                          },
                        )
                      : h(
                          DropdownMenuItem,
                          { onClick: () => publishProblem(problem.id) },
                          {
                            default: () =>
                              h('div', { class: 'flex items-center gap-2 text-emerald-600' }, [
                                h(IconEye, { class: 'h-4 w-4' }),
                                t('problems.actions.publish'),
                              ]),
                          },
                        )
                    : null,
                  canDeleteProblem.value
                    ? h(
                        DropdownMenuItem,
                        { onClick: () => confirmDelete(problem) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                              h(IconTrash, { class: 'h-4 w-4' }),
                              t('common.delete'),
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
              <SelectTrigger class="h-8 w-[150px]">
                <SelectValue :placeholder="t('problems.sort.title')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="default">{{ t('problems.sort.default') }}</SelectItem>
                <SelectItem value="title">{{ t('problems.sort.titleAsc') }}</SelectItem>
                <SelectItem value="difficulty">{{ t('problems.sort.difficultyAsc') }}</SelectItem>
                <SelectItem value="created_at">{{ t('problems.sort.createdDesc') }}</SelectItem>
                <SelectItem value="updated_at">{{ t('problems.sort.updatedDesc') }}</SelectItem>
                <SelectItem value="submission_count">{{
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
            >
              <IconTrophy class="h-3.5 w-3.5" :class="{ 'rotate-180': sortOrder === 'asc' }" />
            </Button>

            <DropdownMenu v-if="selectedRows.length > 0">
              <DropdownMenuTrigger as-child>
                <Button variant="outline" size="sm" class="h-8 gap-1.5">
                  <span>{{ t('problems.bulk.selected', { count: selectedRows.length }) }}</span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem @click="handleBulkAction('publish')">
                  <IconEye class="mr-2 h-4 w-4 text-emerald-600" />
                  <span>{{ t('problems.bulk.publish') }}</span>
                </DropdownMenuItem>
                <DropdownMenuItem @click="handleBulkAction('unpublish')">
                  <IconEyeOff class="mr-2 h-4 w-4 text-amber-600" />
                  <span>{{ t('problems.bulk.unpublish') }}</span>
                </DropdownMenuItem>
                <DropdownMenuItem @click="handleBulkAction('delete')">
                  <IconTrash class="mr-2 h-4 w-4 text-destructive" />
                  <span>{{ t('problems.bulk.delete') }}</span>
                </DropdownMenuItem>
                <DropdownMenuItem @click="handleBulkAction('restore')">
                  <IconRefresh class="mr-2 h-4 w-4 text-blue-600" />
                  <span>{{ t('problems.bulk.restore') }}</span>
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem @click="bulkEditDialogOpen = true">
                  <IconPencil class="mr-2 h-4 w-4" />
                  <span>{{ t('problems.bulkEdit.edit') }}</span>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </template>
        </DataTableToolbar>
      </template>

      <template #extra-actions>
        <div class="flex items-center gap-2">
          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <Button variant="outline" size="sm" class="h-8 gap-1.5">
                <IconDownload class="h-4 w-4" />
                <span class="hidden sm:inline">{{ t('problems.export.title') }}</span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem @click="exportProblems('json')">
                {{ t('problems.export.json') }}
              </DropdownMenuItem>
              <DropdownMenuItem @click="exportProblems('csv')">
                {{ t('problems.export.csv') }}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>

          <Button variant="outline" size="sm" class="h-8 gap-1.5" @click="importDialogOpen = true">
            <IconUpload class="h-4 w-4" />
            <span class="hidden sm:inline">{{ t('problems.import.title') }}</span>
          </Button>

          <Button
            v-if="canCreateProblem"
            size="sm"
            class="h-8"
            @click="router.push({ name: 'problem-create' })"
          >
            <IconPlus class="mr-2 h-4 w-4" />
            <span>{{ t('problems.addProblem') }}</span>
          </Button>
        </div>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ error }}</span>
      <Button variant="outline" size="sm" @click="loadProblems()">{{ t('common.retry') }}</Button>
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
</template>
