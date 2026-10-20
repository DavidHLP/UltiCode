<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { toast } from 'vue-sonner'

import { Button } from '@ulticode/design-system/ui/button'
import { Label } from '@ulticode/design-system/ui/label'
import { Textarea } from '@ulticode/design-system/ui/textarea'
import { Input } from '@ulticode/design-system/ui/input'
import {
  RefreshCw as IconRefresh,
  Shield as IconShield,
  Loader2 as IconLoader2,
  AlertTriangle as IconAlertTriangle,
  User as IconUser,
  Check as IconCheck,
  X as IconX,
} from '@lucide/vue'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar from '@/components/table/DataTableToolbar.vue'
import BaseDetailDrawer from '@/components/shared/BaseDetailDrawer.vue'

import { useModerationStore } from '@/stores/admin/moderation'
import {
  type ModerationQueueItem,
  moderationQueueApi,
  ModerationActionType,
  ModerationStatus,
  ReportCategory,
  type QueryModerationQueueParams,
  type ModeratableEntityType,
} from '@/api/admin/moderation'
import { useRemoteTable } from '@/composables/useRemoteTable'
import { createColumns, type ModerationActions } from './columns'
import { useModerationFilters } from './composables/useModerationFilters'
import { ACTION_CATALOG, actionColorVar, entityRoute } from './presentation/moderationPresentation'
import BatchActionDialog from './components/BatchActionDialog.vue'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const store = useModerationStore()

const isLoaded = ref(false)

// Detail drawer state
const drawerOpen = ref(false)
const drawerReturnFocus = ref<HTMLElement | null>(null)
const selectedQueueItem = ref<ModerationQueueItem | null>(null)
const drawerAction = ref<ModerationActionType>(ModerationActionType.RESOLVED)
const drawerNote = ref('')
const drawerDurationDays = ref<number | undefined>(undefined)
const saving = ref(false)

// Batch dialog state
const batchDialogOpen = ref(false)
const selectedRows = ref<ModerationQueueItem[]>([])

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
  store.fetchStats()
})
const {
  query,
  searchQuery,
  tablePagination,
  loading,
  data,
  total,
  refresh: loadQueue,
  setFilters,
} = useRemoteTable<
  ModerationQueueItem,
  {
    status: ModerationStatus | 'all'
    category: ReportCategory | 'all'
    entityType: ModeratableEntityType | 'all'
  },
  QueryModerationQueueParams
>({
  store: store.queue,
  initialQuery: { filters: { status: 'all', category: 'all', entityType: 'all' } },
  toParams: ({ filters, page, limit }) => ({
    page,
    limit,
    status: filters.status === 'all' ? undefined : filters.status,
    primaryCategory: filters.category === 'all' ? undefined : filters.category,
    entityType: filters.entityType === 'all' ? undefined : filters.entityType,
  }),
  debounceMs: 300,
  autoLoad: true,
})

const { buildFilters, handleFilterUpdate } = useModerationFilters({ query, setFilters })
const filters = buildFilters(t)

// Stats
const availableStats = computed(() => (store.statsLoading || store.statsError ? null : store.stats))
const stats = computed(() => ({
  total: loading.value || store.queueError ? '—' : store.queueTotal,
  pending: availableStats.value?.pendingCount ?? '—',
  underReview: availableStats.value?.underReviewCount ?? '—',
  resolved: availableStats.value?.resolvedCount ?? '—',
}))

// Table columns
const columns = computed(() => {
  const actions: ModerationActions = {
    viewEntity: (item) => {
      router.push(entityRoute(item.entityType, item.entityId))
    },
    openDrawer: (item) => openDrawer(item, getActiveMenuTrigger()),
    quickAction: (id, action) => handleQuickAction(id, action),
    claimItem: (id) => handleClaim(id),
  }
  return createColumns(t, actions)
})

function getActiveMenuTrigger(): HTMLElement | null {
  const active = document.activeElement
  if (!(active instanceof HTMLElement) || active.getAttribute('role') !== 'menuitem') return null
  const triggerId = active.closest('[role="menu"]')?.getAttribute('aria-labelledby')
  return triggerId ? document.getElementById(triggerId) : null
}

function openDrawer(item: ModerationQueueItem, returnFocus: HTMLElement | null = null) {
  drawerReturnFocus.value = returnFocus
  selectedQueueItem.value = item
  drawerAction.value = ModerationActionType.RESOLVED
  drawerNote.value = ''
  drawerDurationDays.value = undefined
  drawerOpen.value = true
}

function restoreDrawerFocus(event: Event) {
  const target = drawerReturnFocus.value
  drawerReturnFocus.value = null
  if (!target?.isConnected) return
  event.preventDefault()
  target.focus({ preventScroll: true })
}

watch(
  () => route.query.queueId,
  async (id, _previous, onCleanup) => {
    drawerOpen.value = false
    selectedQueueItem.value = null
    if (typeof id !== 'string' || !id.trim()) return
    const controller = new AbortController()
    onCleanup(() => controller.abort())
    try {
      const item = await moderationQueueApi.getQueueItem(encodeURIComponent(id), controller.signal)
      if (!controller.signal.aborted) openDrawer(item)
    } catch {
      if (!controller.signal.aborted) toast.error(t('moderation.toast.error'))
    }
  },
  { immediate: true },
)

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

// Action options for drawer — sourced from the presentation module so adding
// a new ModerationActionType is a one-line change there.
const actionOptions = computed(() =>
  ACTION_CATALOG.map((a) => ({
    value: a.value,
    label: t(a.labelKey),
    icon: a.icon,
    color: actionColorVar(a.color),
    requiresDuration: a.requiresDuration,
  })),
)

const selectedActionOption = computed(() =>
  actionOptions.value.find((opt) => opt.value === drawerAction.value),
)
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Page header -->
    <div
      :class="[
        'border border-border dark:border-border bg-card',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-foreground">
          {{ t('nav.moderation') }}
        </h1>
        <Button
          variant="outline"
          size="sm"
          :aria-label="t('common.refresh')"
          @click="handleRefresh"
          :disabled="loading || store.statsLoading"
        >
          <IconRefresh :class="['h-3.5 w-3.5', { 'animate-spin': loading }]" />
          <span class="uppercase tracking-wider hidden sm:inline">{{ t('common.refresh') }}</span>
        </Button>
      </div>

      <div
        class="px-4 lg:px-6 py-2.5 flex flex-wrap items-center gap-x-6 gap-y-2 whitespace-nowrap border-t border-border dark:border-border bg-muted"
      >
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('moderation.terminal.filteredTotal') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.total }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('moderation.terminal.globalPending') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.pending }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('moderation.terminal.globalUnderReview') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{
            stats.underReview
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('moderation.terminal.globalResolved') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.resolved }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-muted-foreground">
          <IconShield class="h-4 w-4" />
          <span class="text-xs font-mono uppercase tracking-wider">{{ t('nav.moderation') }}</span>
        </div>
      </div>
    </div>

    <p
      v-if="store.statsError"
      role="alert"
      class="mt-4 text-sm text-destructive [overflow-wrap:anywhere]"
    >
      {{ t('moderation.stats.loadError') }}
    </p>

    <!-- Batch Actions Bar -->
    <div
      v-if="selectedRows.length > 0"
      :class="[
        'mt-4 flex items-center justify-between border border-status-warning-mark bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)] p-3',
        'animate-in fade-in slide-in-from-top-2 duration-200',
      ]"
    >
      <div class="flex items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="font-mono text-sm text-foreground">
            &gt; {{ t('moderation.queue.selectedCount', { count: selectedRows.length }) }}
          </span>
        </div>
        <div class="h-4 w-px bg-border" />
        <div class="flex items-center gap-2">
          <Button variant="outline" size="sm" @click="batchDialogOpen = true">
            <IconCheck class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('moderation.batchResolve') }}</span>
          </Button>
          <Button variant="outline" size="sm" @click="batchDialogOpen = true">
            <IconX class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('moderation.batchDismiss') }}</span>
          </Button>
        </div>
      </div>
      <Button variant="outline" size="sm" @click="clearSelection">
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
        class="w-full text-sm"
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
      @close-auto-focus="restoreDrawerFocus"
      :entity="selectedQueueItem"
      :loading="saving"
      :title="t('moderation.drawerTitle')"
      :description="t('moderation.queue.description')"
      :loading-text="t('common.saving')"
      :not-found-text="t('moderation.notFound')"
    >
      <template #headerActions>
        <Button variant="outline" size="sm" :disabled="saving" @click="handleDrawerSave">
          <IconLoader2 v-if="saving" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
          <IconCheck v-else class="h-3.5 w-3.5 mr-1.5" />
          <span class="uppercase tracking-wider">{{
            t('moderation.actionPanel.confirmAction')
          }}</span>
        </Button>
      </template>

      <template #content="{ entity }">
        <div class="space-y-4">
          <div class="border border-border dark:border-border p-4 bg-muted">
            <p class="text-xs font-mono uppercase tracking-wider text-muted-foreground mb-2">
              {{ t('moderation.detail.entityInfo') }}
            </p>
            <div class="space-y-2 text-sm">
              <div class="flex justify-between">
                <span class="text-muted-foreground">{{ t('moderation.columns.entityType') }}:</span>
                <span class="font-mono">{{
                  t(`moderation.entityTypes.${entity.entityType}`)
                }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">{{ t('moderation.columns.entity') }}:</span>
                <span class="font-mono text-xs truncate max-w-[200px]">{{ entity.entityId }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">{{ t('moderation.queue.priority') }}:</span>
                <span class="font-mono">{{ entity.priority }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-muted-foreground">{{ t('moderation.queue.reportCount') }}:</span>
                <span class="font-mono">{{ entity.reportCount }}</span>
              </div>
            </div>
          </div>

          <div>
            <Label>{{ t('moderation.actionPanel.selectAction') }}</Label>
            <div class="mt-2 grid grid-cols-2 gap-2">
              <Button
                v-for="option in actionOptions"
                :key="option.value"
                :variant="drawerAction === option.value ? 'default' : 'outline'"
                :class="[
                  'h-9 font-mono text-xs justify-start',
                  drawerAction === option.value
                    ? `border-current ${option.color} bg-current/10`
                    : 'border-border hover:border-current',
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
            <Label for="duration-days">{{ t('moderation.actionPanel.durationLabel') }}</Label>
            <Input
              id="duration-days"
              v-model.number="drawerDurationDays"
              type="number"
              min="1"
              max="365"
              :placeholder="t('moderation.actionPanel.durationPlaceholder')"
              class="mt-2"
            />
          </div>

          <div>
            <Label for="drawer-note">{{ t('moderation.actionPanel.addNote') }}</Label>
            <Textarea
              id="drawer-note"
              v-model="drawerNote"
              :placeholder="t('moderation.actionPanel.notePlaceholder')"
              rows="4"
              class="mt-2 placeholder:text-muted-foreground"
            />
          </div>

          <div
            class="border border-status-warning-mark bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] p-3"
          >
            <div class="flex items-start gap-2">
              <IconAlertTriangle class="h-4 w-4 text-status-warning-mark flex-shrink-0 mt-0.5" />
              <p class="text-xs text-foreground">
                {{ t('moderation.actionPanel.warning') }}
              </p>
            </div>
          </div>

          <div
            v-if="entity.assignedToId"
            class="border border-border dark:border-border p-4 bg-muted"
          >
            <p class="text-xs font-mono uppercase tracking-wider text-muted-foreground mb-2">
              {{ t('moderation.queue.assignedTo') }}
            </p>
            <div class="flex items-center gap-2">
              <IconUser class="h-4 w-4 text-muted-foreground" />
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
