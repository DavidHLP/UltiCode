# Moderation Queue Redesign Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor ModerationQueueView from card layout to DataTable with enhanced filtering and detail drawer.

**Architecture:** Use existing DataTable + DataTableToolbar components with tanstack-table. Create columns.ts for column definitions and use BaseDetailDrawer for the moderation drawer. Add quick action buttons inline in the actions column.

**Tech Stack:** Vue 3, TypeScript, @tanstack/vue-table, Tailwind CSS

---

## File Structure

| File | Action | Description |
|------|--------|-------------|
| `management/src/views/moderation/columns.ts` | CREATE | DataTable column definitions |
| `management/src/views/moderation/ModerationQueueView.vue` | MODIFY | Main view with DataTable |
| `management/src/i18n/locales/zh-CN.ts` | MODIFY | Add new i18n keys |
| `management/src/i18n/locales/en-US.ts` | MODIFY | Add new i18n keys |

---

## Chunk 1: Column Definitions

### Task 1: Create columns.ts

**Files:**
- Create: `management/src/views/moderation/columns.ts`

- [ ] **Step 1: Create columns.ts with column definitions**

```typescript
// management/src/views/moderation/columns.ts
import { h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconAlertTriangle,
  IconCheck,
  IconClock,
  IconEye,
  IconX,
} from '@tabler/icons-vue'

import { Checkbox } from '@/components/ui/checkbox'
import { Button } from '@/components/ui/button'
import { Difficulty, type Problem } from '@/api/admin/problems'
import { formatDate } from '@/lib/format/date'

export type FlagStatus = 'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED'

export interface ModerationActions {
  viewProblem: (id: string) => void
  openDrawer: (problem: Problem) => void
  quickResolve: (id: string) => void
  quickDismiss: (id: string) => void
}

// Status badge styles
const STATUS_STYLES: Record<FlagStatus, { bg: string; border: string; text: string; icon: typeof IconAlertTriangle }> = {
  PENDING: {
    bg: 'bg-[oklch(0.75_0.15_85/0.15)]',
    border: 'border-[oklch(0.75_0.15_85/0.4)]',
    text: 'text-[var(--terminal-amber)]',
    icon: IconAlertTriangle,
  },
  REVIEWED: {
    bg: 'bg-[oklch(0.7_0.12_200/0.15)]',
    border: 'border-[oklch(0.7_0.12_200/0.4)]',
    text: 'text-[var(--terminal-cyan)]',
    icon: IconClock,
  },
  RESOLVED: {
    bg: 'bg-[oklch(0.7_0.15_145/0.15)]',
    border: 'border-[oklch(0.7_0.15_145/0.4)]',
    text: 'text-[var(--terminal-green)]',
    icon: IconCheck,
  },
  DISMISSED: {
    bg: 'bg-[oklch(0.6_0.2_25/0.15)]',
    border: 'border-[oklch(0.6_0.2_25/0.4)]',
    text: 'text-[var(--terminal-red)]',
    icon: IconX,
  },
}

// Difficulty badge styles (matching ProblemsListView)
const DIFFICULTY_STYLES: Record<Difficulty, { bg: string; border: string; text: string }> = {
  EASY: {
    bg: 'bg-[oklch(0.7_0.15_145/0.15)]',
    border: 'border-[oklch(0.7_0.15_145/0.4)]',
    text: 'text-[var(--terminal-green)]',
  },
  MEDIUM: {
    bg: 'bg-[oklch(0.75_0.15_85/0.15)]',
    border: 'border-[oklch(0.75_0.15_85/0.4)]',
    text: 'text-[var(--terminal-amber)]',
  },
  HARD: {
    bg: 'bg-[oklch(0.6_0.2_25/0.15)]',
    border: 'border-[oklch(0.6_0.2_25/0.4)]',
    text: 'text-[var(--terminal-red)]',
  },
}

function renderStatusBadge(status: FlagStatus, t: (key: string) => string) {
  const style = STATUS_STYLES[status]
  const Icon = style.icon
  const label = t(`moderation.status${status.charAt(0) + status.slice(1).toLowerCase()}`)

  return h('div', { class: 'flex items-center gap-2' }, [
    h('span', {
      class: ['w-1.5 h-1.5 rounded-full', style.text.replace('text-', 'bg-')].join(' '),
    }),
    h(
      'span',
      {
        class: [
          'font-data text-[11px] font-medium uppercase tracking-[0.05em]',
          'px-2 py-0.5 border',
          style.bg,
          style.border,
          style.text,
        ].join(' '),
      },
      label,
    ),
  ])
}

function renderDifficultyBadge(difficulty: Difficulty, t: (key: string) => string) {
  const style = DIFFICULTY_STYLES[difficulty]
  const label = t(`common.difficulty.${difficulty.toLowerCase()}`)

  return h(
    'span',
    {
      class: [
        'font-data text-[10px] uppercase px-1.5 py-0.5 border',
        style.bg,
        style.border,
        style.text,
      ].join(' '),
    },
    label,
  )
}

function truncateText(text: string, maxLength: number = 60): string {
  if (!text) return '—'
  if (text.length <= maxLength) return text
  return text.slice(0, maxLength) + '...'
}

export function createColumns(
  t: (key: string) => string,
  actions: ModerationActions,
): ColumnDef<Problem>[] {
  return [
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
          class:
            'border-[var(--silver-300)] data-[state=checked]:bg-[var(--accent-electric)] data-[state=checked]:border-[var(--accent-electric)]',
        }),
      cell: ({ row }) =>
        h(Checkbox, {
          modelValue: row.getIsSelected(),
          'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
          'aria-label': 'Select row',
          class:
            'border-[var(--silver-300)] data-[state=checked]:bg-[var(--accent-electric)] data-[state=checked]:border-[var(--accent-electric)]',
        }),
      enableSorting: false,
      enableHiding: false,
    },
    {
      id: 'problem',
      accessorKey: 'title',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('moderation.columns.problem'),
        ),
      cell: ({ row }) => {
        const problem = row.original
        return h('div', { class: 'flex flex-col gap-1' }, [
          h('span', { class: 'font-medium text-sm' }, problem.title),
          h('div', { class: 'flex items-center gap-2' }, [
            h('span', { class: 'text-xs text-[var(--silver-400)] font-data' }, problem.slug),
            renderDifficultyBadge(problem.difficulty, t),
          ]),
        ])
      },
    },
    {
      accessorKey: 'flag_status',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('common.status'),
        ),
      cell: ({ row }) => {
        const status = (row.getValue('flag_status') as FlagStatus) || 'PENDING'
        return renderStatusBadge(status, t)
      },
    },
    {
      accessorKey: 'flag_reason',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('moderation.flagReason'),
        ),
      cell: ({ row }) => {
        const reason = row.original.flag_reason
        return h(
          'span',
          {
            class: 'text-sm text-[var(--silver-600)] dark:text-[var(--silver-400)]',
            title: reason || '',
          },
          truncateText(reason || ''),
        )
      },
    },
    {
      accessorKey: 'flag_reported_by',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('common.reportedBy'),
        ),
      cell: ({ row }) => {
        const reporter = row.original.flag_reported_by
        return h(
          'span',
          { class: 'text-sm text-[var(--silver-600)] dark:text-[var(--silver-400)]' },
          reporter || t('moderation.unknownReporter'),
        )
      },
    },
    {
      accessorKey: 'flag_reported_at',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('common.reportedAt'),
        ),
      cell: ({ row }) => {
        const date = row.original.flag_reported_at
        return h(
          'span',
          { class: 'text-sm text-[var(--silver-500)] font-data' },
          date ? formatDate(date) : '—',
        )
      },
    },
    {
      id: 'actions',
      header: () =>
        h(
          'span',
          {
            class: 'font-data text-[10px] uppercase tracking-[0.15em] text-[var(--silver-500)]',
          },
          t('common.actions'),
        ),
      cell: ({ row }) => {
        const problem = row.original
        return h('div', { class: 'flex items-center gap-1' }, [
          h(
            Button,
            {
              variant: 'terminal',
              size: 'icon',
              class:
                'h-7 w-7 border-[var(--silver-300)] hover:border-[var(--terminal-green)] hover:text-[var(--terminal-green)]',
              onClick: () => actions.quickResolve(problem.id),
              title: t('moderation.quickResolve'),
            },
            () => h(IconCheck, { class: 'h-3.5 w-3.5' }),
          ),
          h(
            Button,
            {
              variant: 'terminal',
              size: 'icon',
              class:
                'h-7 w-7 border-[var(--silver-300)] hover:border-[var(--terminal-red)] hover:text-[var(--terminal-red)]',
              onClick: () => actions.quickDismiss(problem.id),
              title: t('moderation.quickDismiss'),
            },
            () => h(IconX, { class: 'h-3.5 w-3.5' }),
          ),
          h(
            Button,
            {
              variant: 'terminal',
              size: 'icon',
              class:
                'h-7 w-7 border-[var(--silver-300)] hover:border-[var(--terminal-cyan)] hover:text-[var(--terminal-cyan)]',
              onClick: () => actions.openDrawer(problem),
              title: t('common.view'),
            },
            () => h(IconEye, { class: 'h-3.5 w-3.5' }),
          ),
        ])
      },
      enableSorting: false,
      enableHiding: false,
    },
  ]
}
```

- [ ] **Step 2: Commit columns.ts**

```bash
git add management/src/views/moderation/columns.ts
git commit -m "feat(moderation): add DataTable column definitions"
```

---

## Chunk 2: i18n Keys

### Task 2: Add i18n keys

**Files:**
- Modify: `management/src/i18n/locales/zh-CN.ts`
- Modify: `management/src/i18n/locales/en-US.ts`

- [ ] **Step 1: Add Chinese i18n keys to zh-CN.ts**

Find the `moderation` object (around line 99) and add new keys:

```typescript
// In moderation object, add these keys:
moderation: {
  // ... existing keys ...
  // Add new keys:
  columns: {
    problem: '题目',
  },
  quickResolve: '快速解决',
  quickDismiss: '快速驳回',
  unknownReporter: '未知用户',
  drawerTitle: '审核详情',
  drawerDescription: '查看举报信息并进行审核',
  problemDetails: '题目详情',
  flagInfo: '举报信息',
  moderationActions: '审核操作',
  newStatus: '新状态',
  searchPlaceholder: '搜索题目...',
  allDifficulties: '全部难度',
},
```

- [ ] **Step 2: Add English i18n keys to en-US.ts**

Find the `moderation` object and add matching keys:

```typescript
moderation: {
  // ... existing keys ...
  // Add new keys:
  columns: {
    problem: 'Problem',
  },
  quickResolve: 'Quick Resolve',
  quickDismiss: 'Quick Dismiss',
  unknownReporter: 'Unknown',
  drawerTitle: 'Moderation Details',
  drawerDescription: 'View flag information and moderate',
  problemDetails: 'Problem Details',
  flagInfo: 'Flag Information',
  moderationActions: 'Moderation Actions',
  newStatus: 'New Status',
  searchPlaceholder: 'Search problems...',
  allDifficulties: 'All Difficulties',
},
```

- [ ] **Step 3: Commit i18n changes**

```bash
git add management/src/i18n/locales/zh-CN.ts management/src/i18n/locales/en-US.ts
git commit -m "feat(i18n): add moderation queue redesign keys"
```

---

## Chunk 3: Main View Refactor

### Task 3: Refactor ModerationQueueView.vue

**Files:**
- Modify: `management/src/views/moderation/ModerationQueueView.vue`

- [ ] **Step 1: Update imports and remove unused ones**

Replace the imports section with:

```typescript
<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { useDebounceFn } from '@vueuse/core'
import type { PaginationState } from '@tanstack/vue-table'

import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  IconRefresh,
  IconSearch,
  IconFlagOff,
  IconShield,
  IconLoader2,
  IconCheck,
  IconX,
  IconAlertTriangle,
} from '@tabler/icons-vue'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import BaseDetailDrawer from '@/components/shared/BaseDetailDrawer.vue'
import { TerminalBadge } from '@/components/ui/terminal'

import { problemsApi, type Problem, Difficulty } from '@/api/admin/problems'
import { createColumns, type ModerationActions, type FlagStatus } from './columns'
```

- [ ] **Step 2: Update state variables**

Replace the state section with:

```typescript
const { t } = useI18n()
const router = useRouter()

// Animation state
const isLoaded = ref(false)

// Data
const flaggedProblems = ref<Problem[]>([])
const loading = ref(false)
const total = ref(0)
const totalPages = ref(0)

// Pagination
const pagination = ref<PaginationState>({
  pageIndex: 0,
  pageSize: 20,
})

// Filters
const searchQuery = ref('')
const statusFilter = ref<FlagStatus | 'all'>('all')
const difficultyFilter = ref<Difficulty | 'all'>('all')

// Selection
const selectedRows = ref<Problem[]>([])

// Detail drawer
const drawerOpen = ref(false)
const selectedProblem = ref<Problem | null>(null)
const drawerStatus = ref<FlagStatus>('REVIEWED')
const drawerNotes = ref('')
const saving = ref(false)

// Batch dialog
const batchDialogOpen = ref(false)
const batchStatus = ref<FlagStatus>('RESOLVED')
const batchNotes = ref('')
const batchSaving = ref(false)
```

- [ ] **Step 3: Update computed properties and methods**

```typescript
// Stats for terminal ticker (computed from current page)
const stats = computed(() => {
  const problems = flaggedProblems.value
  return {
    total: total.value,
    pending: problems.filter((p) => p.flag_status === 'PENDING').length,
    reviewed: problems.filter((p) => p.flag_status === 'REVIEWED').length,
    resolved: problems.filter((p) => p.flag_status === 'RESOLVED').length,
  }
})

// Column definitions
const columns = computed(() => {
  const actions: ModerationActions = {
    viewProblem: (id) => router.push(`/admin/problems/${id}`),
    openDrawer: (problem) => {
      selectedProblem.value = problem
      drawerStatus.value = (problem.flag_status as FlagStatus) || 'REVIEWED'
      drawerNotes.value = problem.flag_notes || ''
      drawerOpen.value = true
    },
    quickResolve: (id) => handleQuickAction(id, 'RESOLVED'),
    quickDismiss: (id) => handleQuickAction(id, 'DISMISSED'),
  }
  return createColumns(t, actions)
})

// Filter options
const statusFilterOptions: Filter = {
  modelValue: statusFilter.value,
  placeholder: t('moderation.filterStatus'),
  options: [
    { value: 'all', label: t('moderation.allStatuses') },
    { value: 'PENDING', label: t('moderation.statusPending') },
    { value: 'REVIEWED', label: t('moderation.statusReviewed') },
    { value: 'RESOLVED', label: t('moderation.statusResolved') },
    { value: 'DISMISSED', label: t('moderation.statusDismissed') },
  ],
  width: 'w-[140px]',
}

const difficultyFilterOptions: Filter = {
  modelValue: difficultyFilter.value,
  placeholder: t('common.difficulty'),
  options: [
    { value: 'all', label: t('moderation.allDifficulties') },
    { value: 'EASY', label: t('common.difficulty.easy') },
    { value: 'MEDIUM', label: t('common.difficulty.medium') },
    { value: 'HARD', label: t('common.difficulty.hard') },
  ],
  width: 'w-[120px]',
}

// Debounced search
const debouncedLoad = useDebounceFn(loadFlaggedProblems, 300)

// Load flagged problems
async function loadFlaggedProblems() {
  loading.value = true
  selectedRows.value = []
  try {
    const response = await problemsApi.getFlaggedProblems({
      page: pagination.value.pageIndex + 1,
      limit: pagination.value.pageSize,
      status: statusFilter.value === 'all' ? undefined : statusFilter.value,
    })

    // Filter by difficulty on client side (API doesn't support it)
    let data = response.data
    if (difficultyFilter.value !== 'all') {
      data = data.filter((p) => p.difficulty === difficultyFilter.value)
    }

    // Filter by search query on client side
    if (searchQuery.value) {
      const query = searchQuery.value.toLowerCase()
      data = data.filter(
        (p) =>
          p.title.toLowerCase().includes(query) ||
          p.slug.toLowerCase().includes(query),
      )
    }

    flaggedProblems.value = data
    total.value = response.total
    totalPages.value = response.totalPages
  } catch (error) {
    console.error('Failed to load flagged problems:', error)
    toast.error(t('moderation.loadError'))
  } finally {
    loading.value = false
  }
}

// Quick action handler
async function handleQuickAction(id: string, status: FlagStatus) {
  try {
    await problemsApi.moderateProblem(id, { status })
    toast.success(t('moderation.success'))
    await loadFlaggedProblems()
  } catch (error) {
    console.error('Failed to moderate:', error)
    toast.error(t('moderation.error'))
  }
}

// Drawer save
async function handleDrawerSave() {
  if (!selectedProblem.value) return
  saving.value = true
  try {
    await problemsApi.moderateProblem(selectedProblem.value.id, {
      status: drawerStatus.value,
      notes: drawerNotes.value || undefined,
    })
    toast.success(t('moderation.success'))
    drawerOpen.value = false
    await loadFlaggedProblems()
  } catch (error) {
    console.error('Failed to moderate:', error)
    toast.error(t('moderation.error'))
  } finally {
    saving.value = false
  }
}

// Batch moderation
function openBatchDialog(status: FlagStatus) {
  batchStatus.value = status
  batchNotes.value = ''
  batchDialogOpen.value = true
}

async function handleBatchModerate() {
  if (selectedRows.value.length === 0) return
  batchSaving.value = true
  try {
    const result = await problemsApi.batchModerateProblems({
      ids: selectedRows.value.map((p) => p.id),
      status: batchStatus.value,
      notes: batchNotes.value || undefined,
    })

    const successCount = result.results.filter((r) => r.success).length
    const failCount = result.results.filter((r) => !r.success).length

    if (failCount === 0) {
      toast.success(t('moderation.batchSuccess', { count: successCount }))
    } else {
      toast.warning(t('moderation.batchPartial', { success: successCount, failed: failCount }))
    }

    batchDialogOpen.value = false
    selectedRows.value = []
    await loadFlaggedProblems()
  } catch (error) {
    console.error('Failed to batch moderate:', error)
    toast.error(t('moderation.batchError'))
  } finally {
    batchSaving.value = false
  }
}

// Filter handlers
function handleFilterUpdate(index: number, value: string) {
  if (index === 0) {
    statusFilter.value = value as FlagStatus | 'all'
  } else if (index === 1) {
    difficultyFilter.value = value as Difficulty | 'all'
  }
  pagination.value.pageIndex = 0
  loadFlaggedProblems()
}

// Pagination handler
watch(pagination, () => {
  loadFlaggedProblems()
}, { deep: true })

// Lifecycle
onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
  loadFlaggedProblems()
})
```

- [ ] **Step 4: Update template**

Replace the entire template with:

```vue
<template>
  <div class="relative flex flex-col gap-0 overflow-auto">
    <!-- Terminal Header -->
    <div
      :class="[
        'border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="py-4 px-4 lg:px-6 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <span class="terminal-prompt text-base">moderation</span>
            <span class="terminal-cursor" />
          </div>
          <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
            {{ t('moderation.title') }}
          </h1>
        </div>
      </div>

      <!-- Stats Ticker -->
      <div
        class="py-2.5 px-4 lg:px-6 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">total:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">pending:</span>
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.pending
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">reviewed:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.reviewed
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">resolved:</span>
          <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums">{{
            stats.resolved
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconShield class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">content moderation</span>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div
      :class="[
        'flex-1 py-4 px-4 lg:px-6',
        'transition-all duration-500 delay-100',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <!-- Toolbar with Filters -->
      <div class="mb-4 flex items-center justify-between gap-4 flex-wrap">
        <DataTableToolbar
          v-model:search-model-value="searchQuery"
          :search-placeholder="t('moderation.searchPlaceholder')"
          :filters="[statusFilterOptions, difficultyFilterOptions]"
          :loading="loading"
          @update:search-model-value="debouncedLoad"
          @update:filter="handleFilterUpdate"
          @refresh="loadFlaggedProblems"
        />

        <!-- Batch Actions -->
        <div v-if="selectedRows.length > 0" class="flex items-center gap-2">
          <span class="font-data text-sm text-[var(--terminal-amber)]">
            {{ t('moderation.selectedCount', { count: selectedRows.length }) }}
          </span>
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[oklch(0.7_0.15_145/0.1)]"
            @click="openBatchDialog('RESOLVED')"
          >
            <IconCheck class="h-3.5 w-3.5 mr-1.5" />
            {{ t('moderation.batchResolve') }}
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[oklch(0.6_0.2_25/0.1)]"
            @click="openBatchDialog('DISMISSED')"
          >
            <IconX class="h-3.5 w-3.5 mr-1.5" />
            {{ t('moderation.batchDismiss') }}
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs text-[var(--silver-500)]"
            @click="selectedRows = []"
          >
            [ESC] {{ t('common.clearSelection') }}
          </Button>
        </div>
      </div>

      <!-- DataTable -->
      <DataTable
        :columns="columns"
        :data="flaggedProblems"
        :pagination="pagination"
        :row-count="total"
        :loading="loading"
        :empty-title="t('moderation.noFlagged')"
        :empty-description="t('moderation.noFlaggedDescription')"
        v-model:selected-rows="selectedRows"
        v-model:pagination="pagination"
      />
    </div>

    <!-- Detail Drawer -->
    <BaseDetailDrawer
      v-model:open="drawerOpen"
      :entity="selectedProblem"
      :loading="false"
      :title="t('moderation.drawerTitle')"
      :description="t('moderation.drawerDescription')"
      width="w-[450px] sm:w-[540px]"
    >
      <template #content="{ entity }">
        <!-- Problem Details Section -->
        <div class="space-y-4">
          <div class="border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] pb-4">
            <h3 class="font-data text-xs uppercase tracking-wider text-[var(--silver-500)] mb-3">
              &gt; {{ t('moderation.problemDetails') }}
            </h3>
            <div class="space-y-2">
              <div>
                <span class="text-sm text-[var(--silver-500)]">{{ t('common.title') }}:</span>
                <span class="ml-2 font-medium">{{ entity.title }}</span>
              </div>
              <div>
                <span class="text-sm text-[var(--silver-500)]">Slug:</span>
                <span class="ml-2 font-data text-xs">{{ entity.slug }}</span>
              </div>
              <div>
                <span class="text-sm text-[var(--silver-500)]">{{ t('common.difficulty') }}:</span>
                <span class="ml-2">{{ entity.difficulty }}</span>
              </div>
            </div>
          </div>

          <!-- Flag Info Section -->
          <div class="border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] pb-4">
            <h3 class="font-data text-xs uppercase tracking-wider text-[var(--silver-500)] mb-3">
              &gt; {{ t('moderation.flagInfo') }}
            </h3>
            <div class="space-y-3">
              <div>
                <span class="text-sm text-[var(--silver-500)] block mb-1">
                  {{ t('moderation.flagReason') }}:
                </span>
                <div
                  class="border border-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.08)] p-3 text-sm"
                >
                  <div class="flex items-start gap-2">
                    <IconAlertTriangle class="h-4 w-4 text-[var(--terminal-red)] flex-shrink-0 mt-0.5" />
                    <p>{{ entity.flag_reason || '—' }}</p>
                  </div>
                </div>
              </div>
              <div class="flex gap-4 text-sm">
                <div>
                  <span class="text-[var(--silver-500)]">{{ t('common.reportedBy') }}:</span>
                  <span class="ml-1">{{ entity.flag_reported_by || t('moderation.unknownReporter') }}</span>
                </div>
                <div>
                  <span class="text-[var(--silver-500)]">{{ t('common.reportedAt') }}:</span>
                  <span class="ml-1">{{ entity.flag_reported_at ? new Date(entity.flag_reported_at).toLocaleDateString() : '—' }}</span>
                </div>
              </div>
              <div v-if="entity.flag_notes">
                <span class="text-sm text-[var(--silver-500)] block mb-1">
                  {{ t('moderation.moderationNotes') }}:
                </span>
                <p class="text-sm bg-[var(--surface-sunken)] border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-2">
                  {{ entity.flag_notes }}
                </p>
              </div>
            </div>
          </div>

          <!-- Moderation Actions Section -->
          <div>
            <h3 class="font-data text-xs uppercase tracking-wider text-[var(--silver-500)] mb-3">
              &gt; {{ t('moderation.moderationActions') }}
            </h3>
            <div class="space-y-3">
              <div>
                <Label class="terminal-label text-[var(--silver-500)]">
                  {{ t('moderation.newStatus') }}
                </Label>
                <Select v-model="drawerStatus" class="mt-1">
                  <SelectTrigger class="h-9 terminal-input">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="PENDING">{{ t('moderation.statusPending') }}</SelectItem>
                    <SelectItem value="REVIEWED">{{ t('moderation.statusReviewed') }}</SelectItem>
                    <SelectItem value="RESOLVED">{{ t('moderation.statusResolved') }}</SelectItem>
                    <SelectItem value="DISMISSED">{{ t('moderation.statusDismissed') }}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div>
                <Label class="terminal-label text-[var(--silver-500)]">
                  {{ t('moderation.notes') }}
                </Label>
                <textarea
                  v-model="drawerNotes"
                  :placeholder="t('moderation.notesPlaceholder')"
                  rows="3"
                  class="mt-1 w-full terminal-input p-2 text-sm"
                />
              </div>
              <div class="flex justify-end gap-2 pt-2">
                <Button
                  variant="terminal"
                  size="sm"
                  class="font-data text-xs border-[var(--silver-300)]"
                  @click="drawerOpen = false"
                >
                  {{ t('common.cancel') }}
                </Button>
                <Button
                  variant="terminal"
                  size="sm"
                  class="font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[oklch(0.7_0.15_145/0.1)]"
                  :disabled="saving"
                  @click="handleDrawerSave"
                >
                  <IconLoader2 v-if="saving" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
                  <IconCheck v-else class="h-3.5 w-3.5 mr-1.5" />
                  {{ saving ? t('common.saving') : t('common.save') }}
                </Button>
              </div>
            </div>
          </div>
        </div>
      </template>
    </BaseDetailDrawer>

    <!-- Batch Dialog -->
    <Dialog v-model:open="batchDialogOpen">
      <DialogContent class="terminal-card border-[var(--silver-300)]">
        <DialogHeader
          class="terminal-card-header border-b border-[var(--silver-300)] bg-[var(--surface-sunken)] px-4 py-3 -mx-6 -mt-6"
        >
          <DialogTitle
            class="font-data text-sm uppercase tracking-wider text-[var(--terminal-amber)]"
          >
            &gt; {{ t('moderation.batchModerateTitle') }}
          </DialogTitle>
          <DialogDescription class="font-data text-xs text-[var(--silver-400)]">
            {{ t('moderation.batchModerateDescription', { count: selectedRows.length }) }}
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-4 pt-4">
          <div>
            <Label class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">
              {{ t('moderation.newStatus') }}
            </Label>
            <div class="mt-2 flex gap-2">
              <Button
                :variant="batchStatus === 'RESOLVED' ? 'default' : 'terminal'"
                :class="[
                  'h-9 font-data text-xs',
                  batchStatus === 'RESOLVED'
                    ? 'border-[var(--terminal-green)] text-[var(--terminal-green)] bg-[oklch(0.7_0.15_145/0.1)]'
                    : 'border-[var(--silver-300)] hover:border-[var(--terminal-green)] hover:text-[var(--terminal-green)]',
                ]"
                size="sm"
                @click="batchStatus = 'RESOLVED'"
              >
                <IconCheck class="h-3.5 w-3.5 mr-1.5" />
                {{ t('moderation.statusResolved') }}
              </Button>
              <Button
                :variant="batchStatus === 'DISMISSED' ? 'default' : 'terminal'"
                :class="[
                  'h-9 font-data text-xs',
                  batchStatus === 'DISMISSED'
                    ? 'border-[var(--terminal-red)] text-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.1)]'
                    : 'border-[var(--silver-300)] hover:border-[var(--terminal-red)] hover:text-[var(--terminal-red)]',
                ]"
                size="sm"
                @click="batchStatus = 'DISMISSED'"
              >
                <IconX class="h-3.5 w-3.5 mr-1.5" />
                {{ t('moderation.statusDismissed') }}
              </Button>
            </div>
          </div>
          <div>
            <Label class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">
              {{ t('moderation.notes') }}
            </Label>
            <textarea
              v-model="batchNotes"
              :placeholder="t('moderation.batchNotesPlaceholder')"
              rows="3"
              class="mt-2 w-full terminal-input p-2 text-sm"
            />
          </div>
        </div>
        <DialogFooter class="gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)]"
            @click="batchDialogOpen = false"
          >
            {{ t('common.cancel') }}
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[oklch(0.7_0.15_145/0.1)]"
            :disabled="batchSaving"
            @click="handleBatchModerate"
          >
            <IconLoader2 v-if="batchSaving" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
            {{ batchSaving ? t('common.saving') : t('moderation.apply') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>

<style scoped>
.terminal-input {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 13px;
  border-radius: 0;
  border: 1px solid var(--silver-200);
  background: var(--surface-sunken);
}

.dark .terminal-input {
  border-color: var(--silver-300);
}

.terminal-input:focus {
  outline: none;
  border-color: var(--accent-electric);
  box-shadow: 0 0 0 2px var(--accent-electric-glow);
}
</style>
```

- [ ] **Step 5: Add missing Dialog imports**

Add these imports at the top:

```typescript
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
```

- [ ] **Step 6: Run type check**

```bash
cd /home/davidhlp/project/UltiCode-Public-Next/management && pnpm type-check
```

Expected: No errors

- [ ] **Step 7: Commit main view refactor**

```bash
git add management/src/views/moderation/ModerationQueueView.vue
git commit -m "feat(moderation): refactor to DataTable layout with detail drawer"
```

---

## Chunk 4: Testing & Verification

### Task 4: Verify Implementation

**Files:**
- None (verification only)

- [ ] **Step 1: Run development server**

```bash
cd /home/davidhlp/project/UltiCode-Public-Next && pnpm dev:admin
```

- [ ] **Step 2: Verify functionality**

1. Navigate to `/admin/moderation`
2. Verify DataTable displays flagged problems
3. Verify status badges show correct colors
4. Verify search filters by title
5. Verify status filter works
6. Verify difficulty filter works
7. Verify quick resolve/dismiss buttons work
8. Verify detail drawer opens and saves
9. Verify batch operations work
10. Verify pagination works

- [ ] **Step 3: Run lint check**

```bash
cd /home/davidhlp/project/UltiCode-Public-Next/management && pnpm lint
```

Expected: No errors

- [ ] **Step 4: Final commit**

```bash
git add -A
git commit -m "feat(moderation): complete moderation queue redesign"
```

---

## Summary

| Task | Files | Status |
|------|-------|--------|
| 1. Create columns.ts | `columns.ts` (NEW) | Pending |
| 2. Add i18n keys | `zh-CN.ts`, `en-US.ts` | Pending |
| 3. Refactor main view | `ModerationQueueView.vue` | Pending |
| 4. Verify implementation | - | Pending |
