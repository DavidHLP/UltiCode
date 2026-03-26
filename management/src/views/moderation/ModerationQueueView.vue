<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { useDebounceFn } from '@vueuse/core'
import type { PaginationState } from '@tanstack/vue-table'

import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  IconRefresh,
  IconShield,
  IconLoader2,
  IconCheck,
  IconX,
  IconChecks,
  IconAlertTriangle,
  IconUser,
  IconTrash,
  IconEyeOff,
  IconAlertCircle,
  IconClock,
  IconBan,
} from '@tabler/icons-vue'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import BaseDetailDrawer from '@/components/shared/BaseDetailDrawer.vue'

import { useModerationStore } from '@/stores/admin/moderation'
import {
  type ModerationQueueItem,
  ModerationStatus,
  ReportCategory,
  ModerationActionType,
  type ModeratableEntityType,
} from '@/api/admin/moderation'
import { createColumns, type ModerationActions } from './columns'

const { t } = useI18n()
const router = useRouter()
const store = useModerationStore()

const isLoaded = ref(false)
const pagination = ref<PaginationState>({ pageIndex: 0, pageSize: 20 })
const searchQuery = ref('')

// Filters
const statusFilter = ref<ModerationStatus | 'all'>('all')
const categoryFilter = ref<ReportCategory | 'all'>('all')
const entityTypeFilter = ref<ModeratableEntityType | 'all'>('all')

const selectedRows = ref<ModerationQueueItem[]>([])

// Detail drawer state
const drawerOpen = ref(false)
const selectedQueueItem = ref<ModerationQueueItem | null>(null)
const drawerAction = ref<ModerationActionType>(ModerationActionType.RESOLVED)
const drawerNote = ref('')
const drawerDurationDays = ref<number | undefined>(undefined)
const saving = ref(false)

// Batch dialog state
const batchDialogOpen = ref(false)
const batchAction = ref<ModerationActionType>(ModerationActionType.RESOLVED)
const batchNote = ref('')
const batchSaving = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
  loadData()
  store.fetchStats()
})

// Stats for terminal ticker
const stats = computed(() => ({
  total: store.queueTotal,
  pending: store.stats?.pendingCount ?? 0,
  underReview: store.stats?.underReviewCount ?? 0,
  resolved: store.stats?.resolvedCount ?? 0,
}))

// Table columns with actions
const columns = computed(() => {
  const actions: ModerationActions = {
    viewEntity: (item) => {
      // Navigate to the appropriate entity detail page
      const routes: Record<ModeratableEntityType, string> = {
        forum_post: `/admin/forum/posts/${item.entityId}`,
        forum_comment: `/admin/forum/comments/${item.entityId}`,
        solution: `/admin/solutions/${item.entityId}`,
        solution_comment: `/admin/solutions/${item.entityId}`,
        problem: `/admin/problems/${item.entityId}`,
      }
      router.push(routes[item.entityType])
    },
    openDrawer: (item) => {
      selectedQueueItem.value = item
      drawerAction.value = ModerationActionType.RESOLVED
      drawerNote.value = ''
      drawerDurationDays.value = undefined
      drawerOpen.value = true
    },
    quickAction: (id, action) => handleQuickAction(id, action),
    claimItem: (id) => handleClaim(id),
  }
  return createColumns(t, actions)
})

// Filter configuration for DataTableToolbar
const filters = computed<Filter[]>(() => [
  {
    modelValue: statusFilter.value,
    placeholder: t('moderation.status.title'),
    options: [
      { value: 'all', label: t('moderation.status.all') },
      { value: ModerationStatus.PENDING, label: t('moderation.status.PENDING') },
      { value: ModerationStatus.UNDER_REVIEW, label: t('moderation.status.UNDER_REVIEW') },
      { value: ModerationStatus.RESOLVED, label: t('moderation.status.RESOLVED') },
      { value: ModerationStatus.DISMISSED, label: t('moderation.status.DISMISSED') },
      { value: ModerationStatus.APPEAL_PENDING, label: t('moderation.status.APPEAL_PENDING') },
    ],
    width: 'w-[160px]',
  },
  {
    modelValue: categoryFilter.value,
    placeholder: t('moderation.categories.title'),
    options: [
      { value: 'all', label: t('moderation.categories.all') },
      { value: ReportCategory.SPAM, label: t('moderation.categories.SPAM') },
      { value: ReportCategory.HARASSMENT, label: t('moderation.categories.HARASSMENT') },
      { value: ReportCategory.HATE_SPEECH, label: t('moderation.categories.HATE_SPEECH') },
      { value: ReportCategory.VIOLENCE, label: t('moderation.categories.VIOLENCE') },
      { value: ReportCategory.SEXUAL_CONTENT, label: t('moderation.categories.SEXUAL_CONTENT') },
      { value: ReportCategory.MISINFORMATION, label: t('moderation.categories.MISINFORMATION') },
      { value: ReportCategory.WRONG_ANSWER, label: t('moderation.categories.WRONG_ANSWER') },
      { value: ReportCategory.COPYRIGHT, label: t('moderation.categories.COPYRIGHT') },
      { value: ReportCategory.OTHER, label: t('moderation.categories.OTHER') },
    ],
    width: 'w-[160px]',
  },
  {
    modelValue: entityTypeFilter.value,
    placeholder: t('moderation.entityTypes.title'),
    options: [
      { value: 'all', label: t('moderation.entityTypes.all') },
      { value: 'forum_post', label: t('moderation.entityTypes.forum_post') },
      { value: 'forum_comment', label: t('moderation.entityTypes.forum_comment') },
      { value: 'solution', label: t('moderation.entityTypes.solution') },
      { value: 'solution_comment', label: t('moderation.entityTypes.solution_comment') },
      { value: 'problem', label: t('moderation.entityTypes.problem') },
    ],
    width: 'w-[140px]',
  },
])

// Debounced search
const debouncedSearch = useDebounceFn(() => {
  pagination.value.pageIndex = 0
  loadData()
}, 300)

// Watch pagination changes
watch(
  pagination,
  () => {
    loadData()
  },
  { deep: true },
)

// Watch search query
watch(searchQuery, () => {
  debouncedSearch()
})

// Watch filters
watch([statusFilter, categoryFilter, entityTypeFilter], () => {
  pagination.value.pageIndex = 0
  loadData()
})

async function loadData() {
  await store.fetchQueue({
    page: pagination.value.pageIndex + 1,
    limit: pagination.value.pageSize,
    status: statusFilter.value === 'all' ? undefined : statusFilter.value,
    primaryCategory: categoryFilter.value === 'all' ? undefined : categoryFilter.value,
    entityType: entityTypeFilter.value === 'all' ? undefined : entityTypeFilter.value,
  })
}

async function handleQuickAction(id: string, action: ModerationActionType) {
  try {
    await store.performAction(id, { action })
    toast.success(t('moderation.toast.actionCompleted'))
  } catch (error) {
    console.error('Failed to perform action:', error)
    toast.error(t('moderation.toast.error'))
  }
}

async function handleClaim(id: string) {
  try {
    await store.claimItem(id)
    toast.success(t('moderation.toast.claimed'))
  } catch (error) {
    console.error('Failed to claim item:', error)
    toast.error(t('moderation.toast.error'))
  }
}

async function handleDrawerSave() {
  if (!selectedQueueItem.value) return

  saving.value = true
  try {
    await store.performAction(selectedQueueItem.value.id, {
      action: drawerAction.value,
      note: drawerNote.value || undefined,
      durationDays: drawerDurationDays.value,
    })

    toast.success(t('moderation.toast.success'))
    drawerOpen.value = false
    selectedQueueItem.value = null
    drawerNote.value = ''
  } catch (error) {
    console.error('Failed to perform action:', error)
    toast.error(t('moderation.toast.error'))
  } finally {
    saving.value = false
  }
}

function openBatchDialog(action: ModerationActionType) {
  batchAction.value = action
  batchNote.value = ''
  batchDialogOpen.value = true
}

async function handleBatchAction() {
  if (selectedRows.value.length === 0) return

  batchSaving.value = true
  try {
    const result = await store.batchAction({
      queueIds: selectedRows.value.map((item) => item.id),
      action: batchAction.value,
      note: batchNote.value || undefined,
    })

    const successCount = result.results.filter((r) => r.success).length
    const failCount = result.results.filter((r) => !r.success).length

    if (failCount === 0) {
      toast.success(t('moderation.toast.batchCompleted'))
    } else {
      toast.warning(`${successCount} succeeded, ${failCount} failed`)
    }

    batchDialogOpen.value = false
    selectedRows.value = []
  } catch (error) {
    console.error('Failed to perform batch action:', error)
    toast.error(t('moderation.toast.error'))
  } finally {
    batchSaving.value = false
  }
}

function handleFilterUpdate(index: number, value: string | number) {
  if (index === 0) {
    statusFilter.value = value as ModerationStatus | 'all'
  } else if (index === 1) {
    categoryFilter.value = value as ReportCategory | 'all'
  } else if (index === 2) {
    entityTypeFilter.value = value as ModeratableEntityType | 'all'
  }
}

function handleRefresh() {
  loadData()
  store.fetchStats(true)
}

function clearSelection() {
  selectedRows.value = []
}

// Action type options for the drawer
const actionOptions = computed(() => [
  {
    value: ModerationActionType.DISMISSED,
    label: t('moderation.actions.DISMISSED'),
    icon: IconX,
    color: 'text-[var(--terminal-red)]',
    requiresDuration: false,
  },
  {
    value: ModerationActionType.RESOLVED,
    label: t('moderation.actions.RESOLVED'),
    icon: IconCheck,
    color: 'text-[var(--terminal-green)]',
    requiresDuration: false,
  },
  {
    value: ModerationActionType.DELETED,
    label: t('moderation.actions.DELETED'),
    icon: IconTrash,
    color: 'text-[var(--terminal-red)]',
    requiresDuration: false,
  },
  {
    value: ModerationActionType.HIDDEN,
    label: t('moderation.actions.HIDDEN'),
    icon: IconEyeOff,
    color: 'text-[var(--terminal-amber)]',
    requiresDuration: false,
  },
  {
    value: ModerationActionType.WARNED,
    label: t('moderation.actions.WARNED'),
    icon: IconAlertCircle,
    color: 'text-[var(--terminal-amber)]',
    requiresDuration: false,
  },
  {
    value: ModerationActionType.TEMP_BANNED,
    label: t('moderation.actions.TEMP_BANNED'),
    icon: IconClock,
    color: 'text-[var(--terminal-amber)]',
    requiresDuration: true,
  },
  {
    value: ModerationActionType.PERM_BANNED,
    label: t('moderation.actions.PERM_BANNED'),
    icon: IconBan,
    color: 'text-[var(--terminal-red)]',
    requiresDuration: false,
  },
])

const selectedActionOption = computed(() =>
  actionOptions.value.find((opt) => opt.value === drawerAction.value),
)
</script>

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
      <div class="py-4 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <span class="terminal-prompt text-base">moderation</span>
            <span class="terminal-cursor" />
          </div>
          <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
            {{ t('moderation.queue.title') }}
          </h1>
        </div>
        <Button
          variant="terminal"
          size="sm"
          class="h-8 font-data text-xs border-[var(--silver-300)]"
          @click="handleRefresh"
          :disabled="store.queueLoading"
        >
          <IconRefresh :class="['h-3.5 w-3.5', { 'animate-spin': store.queueLoading }]" />
          <span class="uppercase tracking-wider hidden sm:inline">{{ t('common.refresh') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('moderation.terminal.total') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('moderation.terminal.pending') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.pending
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('moderation.terminal.underReview') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.underReview
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('moderation.terminal.resolved') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums">{{
            stats.resolved
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconShield class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">CONTENT MODERATION</span>
        </div>
      </div>
    </div>

    <!-- Batch Actions Bar - Terminal Style -->
    <div
      v-if="selectedRows.length > 0"
      :class="[
        'mt-4 flex items-center justify-between border border-[var(--terminal-amber)] bg-[oklch(0.75_0.15_85/0.08)] dark:bg-[oklch(0.75_0.15_85/0.15)] p-3',
        'animate-in fade-in slide-in-from-top-2 duration-200',
      ]"
    >
      <div class="flex items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="font-data text-sm text-[var(--terminal-amber)]">
            &gt; {{ t('moderation.queue.selectedCount', { count: selectedRows.length }) }}
          </span>
        </div>
        <div class="h-4 w-px bg-[var(--silver-300)]" />
        <div class="flex items-center gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[oklch(0.7_0.15_145/0.1)]"
            @click="openBatchDialog(ModerationActionType.RESOLVED)"
          >
            <IconCheck class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('moderation.batchResolve') }}</span>
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[oklch(0.6_0.2_25/0.1)]"
            @click="openBatchDialog(ModerationActionType.DISMISSED)"
          >
            <IconX class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('moderation.batchDismiss') }}</span>
          </Button>
        </div>
      </div>
      <Button
        variant="terminal"
        size="sm"
        class="h-8 font-data text-xs text-[var(--silver-500)] hover:text-[var(--foreground)]"
        @click="clearSelection"
      >
        [ESC] {{ t('common.clearSelection') }}
      </Button>
    </div>

    <!-- Main Content - DataTable -->
    <div
      :class="[
        'flex-1 py-4',
        'transition-all duration-500 delay-200',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <DataTable
        :columns="columns"
        :data="store.queueItems"
        :pagination="pagination"
        :row-count="store.queueTotal"
        :loading="store.queueLoading"
        v-model:selected-rows="selectedRows"
        @update:pagination="pagination = $event"
        :empty-title="t('moderation.queue.emptyTitle')"
        :empty-description="t('moderation.queue.emptyDescription')"
        class="terminal-table"
      >
        <template #toolbar-left>
          <DataTableToolbar
            v-model:search-model-value="searchQuery"
            :search-placeholder="t('moderation.searchPlaceholder')"
            :filters="filters"
            :loading="store.queueLoading"
            :on-refresh="handleRefresh"
            @update:filter="handleFilterUpdate"
          />
        </template>
      </DataTable>
    </div>

    <!-- Detail Drawer -->
    <BaseDetailDrawer
      v-model:open="drawerOpen"
      :entity="selectedQueueItem"
      :loading="saving"
      :title="t('moderation.drawerTitle')"
      :description="t('moderation.queue.description')"
      :loading-text="t('common.saving')"
      :not-found-text="t('moderation.notFound')"
      width="w-[400px] sm:w-[540px]"
    >
      <template #headerActions>
        <Button
          variant="terminal"
          size="sm"
          class="h-8 font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[oklch(0.7_0.15_145/0.1)]"
          :disabled="saving"
          @click="handleDrawerSave"
        >
          <IconLoader2 v-if="saving" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
          <IconCheck v-else class="h-3.5 w-3.5 mr-1.5" />
          <span class="uppercase tracking-wider">{{
            t('moderation.actionPanel.confirmAction')
          }}</span>
        </Button>
      </template>

      <template #content="{ entity }">
        <div class="space-y-4">
          <!-- Entity Info -->
          <div
            class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-4 bg-[var(--surface-sunken)]"
          >
            <p class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)] mb-2">
              {{ t('moderation.detail.entityInfo') }}
            </p>
            <div class="space-y-2 text-sm">
              <div class="flex justify-between">
                <span class="text-[var(--silver-500)]"
                  >{{ t('moderation.columns.entityType') }}:</span
                >
                <span class="font-data">{{ entity.entityType }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-[var(--silver-500)]">{{ t('moderation.columns.entity') }}:</span>
                <span class="font-data text-xs truncate max-w-[200px]">{{ entity.entityId }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-[var(--silver-500)]">{{ t('moderation.queue.priority') }}:</span>
                <span class="font-data">{{ entity.priority }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-[var(--silver-500)]"
                  >{{ t('moderation.queue.reportCount') }}:</span
                >
                <span class="font-data">{{ entity.reportCount }}</span>
              </div>
            </div>
          </div>

          <!-- Action Selection -->
          <div>
            <Label class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">
              {{ t('moderation.actionPanel.selectAction') }}
            </Label>
            <div class="mt-2 grid grid-cols-2 gap-2">
              <Button
                v-for="option in actionOptions"
                :key="option.value"
                :variant="drawerAction === option.value ? 'default' : 'terminal'"
                :class="[
                  'h-9 font-data text-xs justify-start',
                  drawerAction === option.value
                    ? `border-current ${option.color} bg-current/10`
                    : 'border-[var(--silver-300)] hover:border-current',
                ]"
                size="sm"
                @click="drawerAction = option.value"
              >
                <component :is="option.icon" :class="['h-3.5 w-3.5 mr-2', option.color]" />
                {{ option.label }}
              </Button>
            </div>
          </div>

          <!-- Duration (for temporary ban) -->
          <div v-if="selectedActionOption?.requiresDuration">
            <Label
              for="duration-days"
              class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]"
            >
              {{ t('moderation.actionPanel.durationLabel') }}
            </Label>
            <Input
              id="duration-days"
              v-model.number="drawerDurationDays"
              type="number"
              min="1"
              max="365"
              :placeholder="t('moderation.actionPanel.durationPlaceholder')"
              class="mt-2 font-data text-sm border-[var(--silver-300)] hover:border-[var(--accent-electric)] bg-transparent"
            />
          </div>

          <!-- Note -->
          <div>
            <Label
              for="drawer-note"
              class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]"
            >
              {{ t('moderation.actionPanel.addNote') }}
            </Label>
            <Textarea
              id="drawer-note"
              v-model="drawerNote"
              :placeholder="t('moderation.actionPanel.notePlaceholder')"
              rows="4"
              class="mt-2 font-data text-sm border-[var(--silver-300)] hover:border-[var(--accent-electric)] bg-transparent placeholder:text-[var(--silver-400)]"
            />
          </div>

          <!-- Warning -->
          <div class="border border-[var(--terminal-amber)] bg-[oklch(0.75_0.15_85/0.08)] p-3">
            <div class="flex items-start gap-2">
              <IconAlertTriangle
                class="h-4 w-4 text-[var(--terminal-amber)] flex-shrink-0 mt-0.5"
              />
              <p class="text-xs text-[var(--terminal-amber)]">
                {{ t('moderation.actionPanel.warning') }}
              </p>
            </div>
          </div>

          <!-- Assigned To -->
          <div
            v-if="entity.assignedToId"
            class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-4 bg-[var(--surface-sunken)]"
          >
            <p class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)] mb-2">
              {{ t('moderation.queue.assignedTo') }}
            </p>
            <div class="flex items-center gap-2">
              <IconUser class="h-4 w-4 text-[var(--silver-500)]" />
              <span class="text-sm">
                {{ entity.assignedToName || entity.assignedToUsername }}
              </span>
            </div>
          </div>
        </div>
      </template>
    </BaseDetailDrawer>

    <!-- Batch Action Dialog -->
    <Dialog v-model:open="batchDialogOpen">
      <DialogContent class="terminal-card border-[var(--silver-300)]">
        <DialogHeader
          class="terminal-card-header border-b border-[var(--silver-300)] bg-[var(--surface-sunken)] px-4 py-3 -mx-6 -mt-6"
        >
          <DialogTitle
            class="flex items-center gap-2 font-data text-sm uppercase tracking-wider text-[var(--terminal-amber)]"
          >
            <IconChecks class="h-4 w-4" />
            &gt; {{ t('moderation.dialogs.confirmBatchTitle') }}
          </DialogTitle>
          <DialogDescription class="font-data text-xs text-[var(--silver-400)]">
            {{
              t('moderation.dialogs.confirmBatchMessage', {
                count: selectedRows.length,
                action:
                  batchAction === ModerationActionType.RESOLVED
                    ? t('moderation.actions.RESOLVED').toLowerCase()
                    : t('moderation.actions.DISMISSED').toLowerCase(),
              })
            }}
          </DialogDescription>
        </DialogHeader>
        <div class="space-y-4 pt-4">
          <div>
            <Label class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">
              {{ t('moderation.actionPanel.selectAction') }}
            </Label>
            <div class="mt-2 flex gap-2">
              <Button
                :variant="batchAction === ModerationActionType.RESOLVED ? 'default' : 'terminal'"
                :class="[
                  'h-9 font-data text-xs',
                  batchAction === ModerationActionType.RESOLVED
                    ? 'border-[var(--terminal-green)] text-[var(--terminal-green)] bg-[oklch(0.7_0.15_145/0.1)]'
                    : 'border-[var(--silver-300)] hover:border-[var(--terminal-green)] hover:text-[var(--terminal-green)]',
                ]"
                size="sm"
                @click="batchAction = ModerationActionType.RESOLVED"
              >
                <IconCheck class="h-3.5 w-3.5 mr-1.5" />
                {{ t('moderation.actions.RESOLVED') }}
              </Button>
              <Button
                :variant="batchAction === ModerationActionType.DISMISSED ? 'default' : 'terminal'"
                :class="[
                  'h-9 font-data text-xs',
                  batchAction === ModerationActionType.DISMISSED
                    ? 'border-[var(--terminal-red)] text-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.1)]'
                    : 'border-[var(--silver-300)] hover:border-[var(--terminal-red)] hover:text-[var(--terminal-red)]',
                ]"
                size="sm"
                @click="batchAction = ModerationActionType.DISMISSED"
              >
                <IconX class="h-3.5 w-3.5 mr-1.5" />
                {{ t('moderation.actions.DISMISSED') }}
              </Button>
            </div>
          </div>
          <div>
            <Label
              for="batch-notes"
              class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]"
            >
              {{ t('moderation.actionPanel.addNote') }}
            </Label>
            <Textarea
              id="batch-notes"
              v-model="batchNote"
              :placeholder="t('moderation.actionPanel.notePlaceholder')"
              rows="3"
              class="mt-2 font-data text-sm border-[var(--silver-300)] hover:border-[var(--accent-electric)] bg-transparent placeholder:text-[var(--silver-400)]"
            />
          </div>
        </div>
        <DialogFooter class="gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--silver-500)]"
            @click="batchDialogOpen = false"
          >
            {{ t('moderation.dialogs.cancel') }}
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[oklch(0.7_0.15_145/0.1)]"
            :disabled="batchSaving"
            @click="handleBatchAction"
          >
            <IconLoader2 v-if="batchSaving" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
            <IconChecks v-else class="h-3.5 w-3.5 mr-1.5" />
            {{ batchSaving ? t('common.saving') : t('moderation.dialogs.confirm') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
