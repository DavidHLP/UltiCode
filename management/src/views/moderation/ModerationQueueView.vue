<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'

import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Input } from '@/components/ui/input'
import {
  IconRefresh,
  IconShield,
  IconLoader2,
  IconCheck,
  IconX,
  IconAlertTriangle,
  IconUser,
  IconTrash,
  IconEyeOff,
  IconAlertCircle,
  IconClock,
  IconBan,
} from '@tabler/icons-vue'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar from '@/components/table/DataTableToolbar.vue'
import BaseDetailDrawer from '@/components/shared/BaseDetailDrawer.vue'

import { useModerationStore } from '@/stores/admin/moderation'
import {
  type ModerationQueueItem,
  ModerationActionType,
  type ModeratableEntityType,
  ModerationStatus,
  ReportCategory,
  type QueryModerationQueueParams,
} from '@/api/admin/moderation'
import { useDataTable } from '@/composables/useDataTable'
import { createColumns, type ModerationActions } from './columns'
import { useModerationFilters } from './composables/useModerationFilters'
import BatchActionDialog from './components/BatchActionDialog.vue'

const { t } = useI18n()
const router = useRouter()
const store = useModerationStore()

const isLoaded = ref(false)

const { statusFilter, categoryFilter, entityTypeFilter, buildFilters, handleFilterUpdate } =
  useModerationFilters()

// Detail drawer state
const drawerOpen = ref(false)
const selectedQueueItem = ref<ModerationQueueItem | null>(null)
const drawerAction = ref<ModerationActionType>(ModerationActionType.RESOLVED)
const drawerNote = ref('')
const drawerDurationDays = ref<number | undefined>(undefined)
const saving = ref(false)

// Batch dialog state
const batchDialogOpen = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
  store.fetchStats()
})

// Stats
const stats = computed(() => ({
  total: store.queueTotal,
  pending: store.stats?.pendingCount ?? 0,
  underReview: store.stats?.underReviewCount ?? 0,
  resolved: store.stats?.resolvedCount ?? 0,
}))

// Table columns
const columns = computed(() => {
  const actions: ModerationActions = {
    viewEntity: (item) => {
      const routes: Record<ModeratableEntityType, string> = {
        forum_post: `/forum/posts/${item.entityId}`,
        forum_comment: `/comments/forum/${item.entityId}`,
        solution: `/solutions/${item.entityId}`,
        solution_comment: `/comments/solution/${item.entityId}`,
        problem: `/problems/${item.entityId}`,
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

const filters = buildFilters(t)

const {
  searchQuery,
  tablePagination,
  selectedRows,
  loading,
  data,
  total,
  loadEntities: loadQueue,
} = useDataTable<
  ModerationQueueItem,
  {
    status: ModerationStatus | 'all'
    primaryCategory: ReportCategory | 'all'
    entityType: ModeratableEntityType | 'all'
  },
  QueryModerationQueueParams
>({
  store: {
    data: computed(() => store.queueItems),
    total: computed(() => store.queueTotal),
    isLoading: computed(() => store.queueLoading),
    error: computed(() => store.queueError),
    fetch: (params) => store.fetchQueue(params),
  },
  filters: () => ({
    status: statusFilter.value,
    primaryCategory: categoryFilter.value,
    entityType: entityTypeFilter.value,
  }),
  transformParams: ({ filters, page, limit }) => ({
    page,
    limit,
    status: filters.status === 'all' ? undefined : filters.status,
    primaryCategory: filters.primaryCategory === 'all' ? undefined : filters.primaryCategory,
    entityType: filters.entityType === 'all' ? undefined : filters.entityType,
  }),
  debounceMs: 300,
  autoLoad: true,
})

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

function handleRefresh() {
  loadQueue()
  store.fetchStats(true)
}

function clearSelection() {
  selectedRows.value = []
}

function handleBatchComplete() {
  selectedRows.value = []
  loadQueue()
}

// Action options for drawer
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
          {{ t('nav.moderation') }}
        </h1>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)] transition-colors"
          @click="handleRefresh"
          :disabled="loading"
        >
          <IconRefresh :class="['h-3.5 w-3.5', { 'animate-spin': loading }]" />
          <span class="uppercase tracking-wider hidden sm:inline">{{ t('common.refresh') }}</span>
        </Button>
      </div>

      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
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
          <span class="text-xs font-data uppercase tracking-wider">{{ t('nav.moderation') }}</span>
        </div>
      </div>
    </div>

    <!-- Batch Actions Bar -->
    <div
      v-if="selectedRows.length > 0"
      :class="[
        'mt-4 flex items-center justify-between border border-[var(--terminal-amber)] bg-[color-mix(in_oklch,_var(--terminal-amber)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)] p-3',
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
            class="h-8 font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[color-mix(in_oklch,_var(--terminal-green)_10%,_transparent)]"
            @click="batchDialogOpen = true"
          >
            <IconCheck class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('moderation.batchResolve') }}</span>
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]"
            @click="batchDialogOpen = true"
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

    <!-- Main Content -->
    <div
      :class="[
        'flex-1 py-4',
        'transition-all duration-500 delay-200',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 translate-y-2',
      ]"
    >
      <DataTable
        :columns="columns"
        :data="data"
        :pagination="tablePagination"
        :row-count="total"
        :loading="loading"
        v-model:selected-rows="selectedRows"
        @update:pagination="tablePagination = $event"
        :empty-title="t('moderation.queue.emptyTitle')"
        :empty-description="t('moderation.queue.emptyDescription')"
        class="terminal-table"
      >
        <template #toolbar-left>
          <DataTableToolbar
            v-model:search-model-value="searchQuery"
            :search-placeholder="t('moderation.searchPlaceholder')"
            :filters="filters"
            :loading="loading"
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
          class="h-8 font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[color-mix(in_oklch,_var(--terminal-green)_10%,_transparent)]"
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

          <div>
            <Label class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">{{
              t('moderation.actionPanel.selectAction')
            }}</Label>
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

          <div v-if="selectedActionOption?.requiresDuration">
            <Label
              for="duration-days"
              class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]"
              >{{ t('moderation.actionPanel.durationLabel') }}</Label
            >
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

          <div>
            <Label
              for="drawer-note"
              class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]"
              >{{ t('moderation.actionPanel.addNote') }}</Label
            >
            <Textarea
              id="drawer-note"
              v-model="drawerNote"
              :placeholder="t('moderation.actionPanel.notePlaceholder')"
              rows="4"
              class="mt-2 font-data text-sm border-[var(--silver-300)] hover:border-[var(--accent-electric)] bg-transparent placeholder:text-[var(--silver-400)]"
            />
          </div>

          <div
            class="border border-[var(--terminal-amber)] bg-[color-mix(in_oklch,_var(--terminal-amber)_8%,_transparent)] p-3"
          >
            <div class="flex items-start gap-2">
              <IconAlertTriangle
                class="h-4 w-4 text-[var(--terminal-amber)] flex-shrink-0 mt-0.5"
              />
              <p class="text-xs text-[var(--terminal-amber)]">
                {{ t('moderation.actionPanel.warning') }}
              </p>
            </div>
          </div>

          <div
            v-if="entity.assignedToId"
            class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-4 bg-[var(--surface-sunken)]"
          >
            <p class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)] mb-2">
              {{ t('moderation.queue.assignedTo') }}
            </p>
            <div class="flex items-center gap-2">
              <IconUser class="h-4 w-4 text-[var(--silver-500)]" />
              <span class="text-sm">{{ entity.assignedToName || entity.assignedToUsername }}</span>
            </div>
          </div>
        </div>
      </template>
    </BaseDetailDrawer>

    <BatchActionDialog
      v-model:open="batchDialogOpen"
      :selected-items="selectedRows"
      @complete="handleBatchComplete"
    />
  </div>
</template>
