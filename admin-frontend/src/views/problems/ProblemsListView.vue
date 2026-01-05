<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import { useRouter } from 'vue-router'
import { watchDebounced } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconCheck,
  IconCircleCheckFilled,
  IconDotsVertical,
  IconEye,
  IconEyeOff,
  IconFile,
  IconLoader,
  IconPencil,
  IconPlus,
  IconRefresh,
  IconSparkles,
  IconTrophy,
  IconX,
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
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useProblemsStore } from '@/stores/admin/problems'
import { useAuthStore } from '@/stores/admin/auth'
import { Difficulty, type Problem } from '@/api/admin/problems'

import DataTable from '@/components/table/DataTable.vue'

const router = useRouter()
const problemsStore = useProblemsStore()
const authStore = useAuthStore()

const searchQuery = ref('')
const difficultyFilter = ref<string>('all')
const statusFilter = ref<string>('all')
const publishedFilter = ref<string>('all')
const tablePagination = ref({ pageIndex: 0, pageSize: 20 })

const canCreateProblem = computed(() => authStore.hasPermission('CREATE', 'PROBLEM'))
const canUpdateProblem = computed(() => authStore.hasPermission('UPDATE', 'PROBLEM'))

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
  tablePagination.value.pageIndex = 0
  loadProblems()
})

watch(
  () => tablePagination.value,
  () => loadProblems(),
  { deep: true },
)

function viewProblem(id: string) {
  router.push({ name: 'problem-detail', params: { id } })
}

function editProblem(id: string) {
  router.push({ name: 'problem-edit', params: { id } })
}

async function publishProblem(id: string) {
  try {
    await problemsStore.publishProblem(id)
    await loadProblems()
  } catch {
    alert('Failed to publish problem')
  }
}

async function unpublishProblem(id: string) {
  try {
    await problemsStore.unpublishProblem(id)
    await loadProblems()
  } catch {
    alert('Failed to unpublish problem')
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
    header: 'ID',
    cell: ({ row }) => {
      const id = row.getValue('id') as string
      return h('span', { class: 'text-muted-foreground text-xs font-mono' }, id.slice(0, 8))
    },
  },
  {
    accessorKey: 'title',
    header: 'Problem',
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
    header: 'Difficulty',
    cell: ({ row }) => {
      const difficulty = row.getValue('difficulty') as Difficulty
      const icon = getDifficultyIcon(difficulty)
      const color = getDifficultyColor(difficulty)
      return h('div', { class: 'flex items-center gap-2' }, [
        h(icon, { class: `h-4 w-4 ${color}` }),
        h(Badge, { variant: getDifficultyBadgeVariant(difficulty) }, () => difficulty),
      ])
    },
  },
  {
    accessorKey: 'status',
    header: 'Status',
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
      const label = isSolved ? 'Solved' : isAttempted ? 'Attempted' : 'Todo'
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
    header: 'Published',
    cell: ({ row }) => {
      const isPublished = row.getValue('is_published') as boolean
      const isDeleted = row.original.is_deleted
      if (isDeleted) {
        return h(
          Badge,
          { variant: 'destructive' },
          {
            default: () => [h(IconX, { class: 'mr-1 h-3 w-3' }), 'Deleted'],
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
            isPublished ? 'Published' : 'Draft',
          ],
        },
      )
    },
  },
  {
    accessorKey: 'submission_count',
    header: 'Submissions',
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
    header: 'Tags',
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
    header: 'Created',
    cell: ({ row }) => {
      const date = new Date(row.getValue('created_at') as Date)
      return h('span', { class: 'text-muted-foreground text-sm' }, date.toLocaleDateString())
    },
  },
  {
    id: 'actions',
    header: 'Actions',
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
                  h(
                    DropdownMenuItem,
                    { onClick: () => viewProblem(problem.id) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconEye, { class: 'h-4 w-4' }),
                          'View Details',
                        ]),
                    },
                  ),
                  canUpdateProblem.value
                    ? h(
                        DropdownMenuItem,
                        { onClick: () => editProblem(problem.id) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2' }, [
                              h(IconPencil, { class: 'h-4 w-4' }),
                              'Edit',
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
                                'Unpublish',
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
                                'Publish',
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
  <Tabs default-value="all-problems" class="w-full flex-col justify-start gap-6">
    <div class="flex items-center justify-between px-4 lg:px-6">
      <Label for="view-selector" class="sr-only">View</Label>
      <Select default-value="all-problems">
        <SelectTrigger id="view-selector" class="flex w-fit @4xl/main:hidden" size="sm">
          <SelectValue placeholder="Select a view" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all-problems">All Problems</SelectItem>
          <SelectItem value="published">Published</SelectItem>
          <SelectItem value="draft">Draft</SelectItem>
          <SelectItem value="archived">Archived</SelectItem>
        </SelectContent>
      </Select>
      <TabsList
        class="**:data-[slot=badge]:bg-muted-foreground/30 hidden **:data-[slot=badge]:size-5 **:data-[slot=badge]:rounded-full **:data-[slot=badge]:px-1 @4xl/main:flex"
      >
        <TabsTrigger value="all-problems">
          All Problems <Badge variant="secondary">{{ problemsStore.problems.length }}</Badge>
        </TabsTrigger>
        <TabsTrigger value="published">Published</TabsTrigger>
        <TabsTrigger value="draft">Draft</TabsTrigger>
        <TabsTrigger value="archived">Archived</TabsTrigger>
      </TabsList>
    </div>

    <TabsContent
      value="all-problems"
      class="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6"
    >
      <DataTable
        :columns="columns"
        :data="problemsStore.problems"
        :pagination="tablePagination"
        :loading="problemsStore.loading"
        @update:pagination="tablePagination = $event"
      >
        <template #toolbar-left>
          <Input
            v-model="searchQuery"
            placeholder="Search problems..."
            class="min-w-[200px] w-[260px]"
          >
            <template #trailing>
              <button
                v-if="searchQuery"
                @click="searchQuery = ''"
                class="rounded-sm opacity-70 hover:opacity-100"
              >
                <IconX class="h-4 w-4" />
              </button>
            </template>
          </Input>
          <Select v-model="difficultyFilter">
            <SelectTrigger class="w-[160px]">
              <SelectValue placeholder="All Difficulties" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Difficulties</SelectItem>
              <SelectItem value="EASY">Easy</SelectItem>
              <SelectItem value="MEDIUM">Medium</SelectItem>
              <SelectItem value="HARD">Hard</SelectItem>
            </SelectContent>
          </Select>
          <Select v-model="statusFilter">
            <SelectTrigger class="w-[140px]">
              <SelectValue placeholder="All Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Status</SelectItem>
              <SelectItem value="todo">Todo</SelectItem>
              <SelectItem value="attempted">Attempted</SelectItem>
              <SelectItem value="solved">Solved</SelectItem>
            </SelectContent>
          </Select>
          <Select v-model="publishedFilter">
            <SelectTrigger class="w-[140px]">
              <SelectValue placeholder="All" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All</SelectItem>
              <SelectItem value="published">Published</SelectItem>
              <SelectItem value="unpublished">Draft</SelectItem>
            </SelectContent>
          </Select>
          <Button variant="outline" size="icon" @click="loadProblems()" title="Refresh">
            <IconRefresh class="h-4 w-4" :class="{ 'animate-spin': problemsStore.loading }" />
          </Button>
        </template>

        <template #extra-actions>
          <Button
            v-if="canCreateProblem"
            variant="outline"
            size="sm"
            @click="router.push({ name: 'problem-create' })"
          >
            <IconPlus />
            <span class="hidden lg:inline">Add Problem</span>
          </Button>
        </template>
      </DataTable>

      <!-- Error state -->
      <div
        v-if="problemsStore.error"
        class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
      >
        <span class="text-destructive">{{ problemsStore.error }}</span>
        <Button variant="outline" size="sm" @click="loadProblems()">Retry</Button>
      </div>
    </TabsContent>

    <TabsContent value="published" class="flex flex-col px-4 lg:px-6">
      <div class="aspect-video w-full flex-1 rounded-lg border border-dashed" />
    </TabsContent>

    <TabsContent value="draft" class="flex flex-col px-4 lg:px-6">
      <div class="aspect-video w-full flex-1 rounded-lg border border-dashed" />
    </TabsContent>

    <TabsContent value="archived" class="flex flex-col px-4 lg:px-6">
      <div class="aspect-video w-full flex-1 rounded-lg border border-dashed" />
    </TabsContent>
  </Tabs>
</template>
