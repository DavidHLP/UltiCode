<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { watchDebounced, useDebounceFn } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import {
  IconCheck,
  IconCircleCheckFilled,
  IconDotsVertical,
  IconEye,
  IconEyeOff,
  IconFile,
  IconFlask,
  IconBrackets,
  IconLoader,
  IconPencil,
  IconPlus,
  IconRefresh,
  IconSparkles,
  IconTrash,
  IconTrophy,
  IconX,
  IconDownload,
  IconUpload,
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
import { useAuthStore } from '@/stores/admin/auth'
import { Difficulty, type Problem, problemsApi } from '@/api/admin/problems'
import { ApiError } from '@/api/client'

import DataTable from '@/components/table/DataTable.vue'
import ProblemDeleteDialog from './ProblemDeleteDialog.vue'
import ProblemImportDialog from '@/components/problems/ProblemImportDialog.vue'

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
const tablePagination = ref({ pageIndex: Math.max(0, initialPage - 1), pageSize: 10 })

const selectedProblemId = ref<string | null>(null)
const selectedProblemTitle = ref<string | null>(null)
const deleteDialogOpen = ref(false)
const importing = ref(false)
const importDialogOpen = ref(false)

const canCreateProblem = computed(() => authStore.hasPermission('CREATE', 'PROBLEM'))
const canUpdateProblem = computed(() => authStore.hasPermission('UPDATE', 'PROBLEM'))
const canDeleteProblem = computed(() => authStore.hasPermission('DELETE', 'PROBLEM'))

onMounted(() => loadProblems())

async function loadProblems() {
  await problemsStore.fetchProblems({
    search: searchQuery.value || undefined,
    difficulty:
      difficultyFilter.value === 'all' ? undefined : (difficultyFilter.value as Difficulty),
    status: statusFilter.value === 'all' ? undefined : (statusFilter.value as Problem['status']),
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
    loadProblems()
  },
  { debounce: 500 },
)

watch([difficultyFilter, statusFilter, publishedFilter], () => {
  if (tablePagination.value.pageIndex === 0) {
    loadProblems()
  } else {
    tablePagination.value.pageIndex = 0
  }
})

watch(
  () => tablePagination.value,
  () => loadProblems(),
  { deep: true },
)

// URL synchronization - debounced to avoid excessive updates
const debouncedUpdateUrl = useDebounceFn(() => {
  router.push({
    query: {
      ...(searchQuery.value && { search: searchQuery.value }),
      ...(difficultyFilter.value !== 'all' && { difficulty: difficultyFilter.value }),
      ...(statusFilter.value !== 'all' && { status: statusFilter.value }),
      ...(publishedFilter.value !== 'all' && { published: publishedFilter.value }),
      page: (tablePagination.value.pageIndex + 1).toString(),
    },
  })
}, 300)

// Watch all filter state changes and update URL
watch(
  [searchQuery, difficultyFilter, statusFilter, publishedFilter, tablePagination],
  debouncedUpdateUrl,
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

function getDifficultyBadgeVariant(
  difficulty: Difficulty,
): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (difficulty) {
    case 'EASY':
      return 'default'
    case 'MEDIUM':
      return 'secondary'
    case 'HARD':
      return 'destructive'
    default:
      return 'outline'
  }
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

function getDifficultyColor(difficulty: Difficulty) {
  switch (difficulty) {
    case 'EASY':
      return 'text-emerald-500'
    case 'MEDIUM':
      return 'text-amber-500'
    case 'HARD':
      return 'text-red-500'
    default:
      return 'text-muted-foreground'
  }
}

async function exportProblems(format: 'json' | 'csv') {
  try {
    importing.value = true
    await problemsApi.exportProblems({
      format,
      search: searchQuery.value || undefined,
      difficulty:
        difficultyFilter.value === 'all' ? undefined : (difficultyFilter.value as Difficulty),
      status: statusFilter.value === 'all' ? undefined : (statusFilter.value as Problem['status']),
      is_published:
        publishedFilter.value === 'all'
          ? undefined
          : publishedFilter.value === 'published'
            ? true
            : false,
    })
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
      :data="problemsStore.problems"
      :pagination="tablePagination"
      :row-count="problemsStore.total"
      :loading="problemsStore.loading"
      @update:pagination="tablePagination = $event"
    >
      <template #toolbar-left>
        <div class="flex flex-wrap items-center gap-2 w-full lg:w-auto">
          <Input
            v-model="searchQuery"
            :placeholder="t('problems.searchPlaceholder')"
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
            <Select v-model="difficultyFilter">
              <SelectTrigger class="h-8 w-[130px]">
                <SelectValue :placeholder="t('problems.filters.difficulty')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{{ t('problems.filters.allLevels') }}</SelectItem>
                <SelectItem value="EASY">{{ t('problems.difficulty.EASY') }}</SelectItem>
                <SelectItem value="MEDIUM">{{ t('problems.difficulty.MEDIUM') }}</SelectItem>
                <SelectItem value="HARD">{{ t('problems.difficulty.HARD') }}</SelectItem>
              </SelectContent>
            </Select>

            <Select v-model="statusFilter">
              <SelectTrigger class="h-8 w-[120px]">
                <SelectValue :placeholder="t('problems.filters.status')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{{ t('problems.filters.allStatus') }}</SelectItem>
                <SelectItem value="todo">{{ t('problems.status.todo') }}</SelectItem>
                <SelectItem value="attempted">{{ t('problems.status.attempted') }}</SelectItem>
                <SelectItem value="solved">{{ t('problems.status.solved') }}</SelectItem>
              </SelectContent>
            </Select>

            <Select v-model="publishedFilter">
              <SelectTrigger class="h-8 w-[120px]">
                <SelectValue :placeholder="t('problems.filters.visibility')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{{ t('problems.filters.any') }}</SelectItem>
                <SelectItem value="published">{{ t('problems.filters.published') }}</SelectItem>
                <SelectItem value="unpublished">{{ t('problems.filters.unpublished') }}</SelectItem>
              </SelectContent>
            </Select>

            <Button
              variant="ghost"
              size="icon"
              class="h-8 w-8"
              @click="loadProblems()"
              :title="t('common.refresh')"
            >
              <IconRefresh class="h-3.5 w-3.5" :class="{ 'animate-spin': problemsStore.loading }" />
            </Button>
          </div>
        </div>
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

          <Button
            variant="outline"
            size="sm"
            class="h-8 gap-1.5"
            @click="importDialogOpen = true"
          >
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
      v-if="problemsStore.error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ problemsStore.error }}</span>
      <Button variant="outline" size="sm" @click="loadProblems()">{{ t('common.retry') }}</Button>
    </div>
  </div>

  <ProblemDeleteDialog
    v-model:open="deleteDialogOpen"
    :problem-id="selectedProblemId"
    :problem-title="selectedProblemTitle"
    @success="loadProblems"
  />

  <ProblemImportDialog
    v-model:open="importDialogOpen"
    @imported="handleImported"
  />
</template>
