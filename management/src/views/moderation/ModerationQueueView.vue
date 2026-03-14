<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { toast } from 'vue-sonner'
import { useDebounceFn } from '@vueuse/core'
import type { PaginationState } from '@tanstack/vue-table'

import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
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
} from '@tabler/icons-vue'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import BaseDetailDrawer from '@/components/shared/BaseDetailDrawer.vue'

import { problemsApi, type Problem, Difficulty } from '@/api/admin/problems'
import { createColumns, type ModerationActions, type FlagStatus } from './columns'

const { t } = useI18n()
const router = useRouter()

const isLoaded = ref(false)
const flaggedProblems = ref<Problem[]>([])
const loading = ref(false)
const total = ref(0)
const totalPages = ref(0)

const pagination = ref<PaginationState>({ pageIndex: 0, pageSize: 20 })
const searchQuery = ref('')
const statusFilter = ref<FlagStatus | 'all'>('all')
const difficultyFilter = ref<Difficulty | 'all'>('all')
const selectedRows = ref<Problem[]>([])

const drawerOpen = ref(false)
const selectedProblem = ref<Problem | null>(null)
const drawerStatus = ref<FlagStatus>('REVIEWED')
const drawerNotes = ref('')
const saving = ref(false)

const batchDialogOpen = ref(false)
const batchStatus = ref<FlagStatus>('RESOLVED')
const batchNotes = ref('')
const batchSaving = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
  loadFlaggedProblems()
})

// Stats for terminal ticker
const stats = computed(() => ({
  total: total.value,
  pending: flaggedProblems.value.filter((p) => p.flag_status === 'PENDING').length,
  reviewed: flaggedProblems.value.filter((p) => p.flag_status === 'REVIEWED').length,
  resolved: flaggedProblems.value.filter((p) => p.flag_status === 'RESOLVED').length,
}))

// Table columns with actions
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

// Filter configuration for DataTableToolbar
const filters = computed<Filter[]>(() => [
  {
    modelValue: statusFilter.value,
    placeholder: t('moderation.filterStatus'),
    options: [
      { value: 'all', label: t('moderation.allStatuses') },
      { value: 'PENDING', label: t('moderation.statusPending') },
      { value: 'REVIEWED', label: t('moderation.statusReviewed') },
      { value: 'RESOLVED', label: t('moderation.statusResolved') },
      { value: 'DISMISSED', label: t('moderation.statusDismissed') },
    ],
    width: 'w-[160px]',
  },
  {
    modelValue: difficultyFilter.value,
    placeholder: t('common.difficulty.label'),
    options: [
      { value: 'all', label: t('common.all') },
      { value: 'EASY', label: t('common.difficulty.easy') },
      { value: 'MEDIUM', label: t('common.difficulty.medium') },
      { value: 'HARD', label: t('common.difficulty.hard') },
    ],
    width: 'w-[140px]',
  },
])

// Debounced search
const debouncedSearch = useDebounceFn(() => {
  pagination.value.pageIndex = 0
  loadFlaggedProblems()
}, 300)

// Watch pagination changes
watch(pagination, () => {
  loadFlaggedProblems()
}, { deep: true })

// Watch search query
watch(searchQuery, () => {
  debouncedSearch()
})

// Watch filters
watch([statusFilter, difficultyFilter], () => {
  pagination.value.pageIndex = 0
  loadFlaggedProblems()
})

async function loadFlaggedProblems() {
  loading.value = true
  try {
    const response = await problemsApi.getFlaggedProblems({
      page: pagination.value.pageIndex + 1,
      limit: pagination.value.pageSize,
      status: statusFilter.value === 'all' ? undefined : statusFilter.value,
    })
    flaggedProblems.value = response.data
    total.value = response.total
    totalPages.value = response.totalPages
  } catch (error) {
    console.error('Failed to load flagged problems:', error)
    toast.error(t('moderation.loadError'))
  } finally {
    loading.value = false
  }
}

async function handleQuickAction(id: string, status: FlagStatus) {
  try {
    await problemsApi.moderateProblem(id, { status })
    toast.success(t('moderation.success'))
    await loadFlaggedProblems()
  } catch (error) {
    console.error('Failed to moderate problem:', error)
    toast.error(t('moderation.error'))
  }
}

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
    selectedProblem.value = null
    drawerNotes.value = ''
    await loadFlaggedProblems()
  } catch (error) {
    console.error('Failed to moderate problem:', error)
    toast.error(t('moderation.error'))
  } finally {
    saving.value = false
  }
}

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

function handleFilterUpdate(index: number, value: string | number) {
  if (index === 0) {
    statusFilter.value = value as FlagStatus | 'all'
  } else if (index === 1) {
    difficultyFilter.value = value as Difficulty | 'all'
  }
}

function handleRefresh() {
  loadFlaggedProblems()
}

function clearSelection() {
  selectedRows.value = []
}
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
            {{ t('moderation.title') }}
          </h1>
        </div>
        <Button
          variant="terminal"
          size="sm"
          class="h-8 font-data text-xs border-[var(--silver-300)]"
          @click="handleRefresh"
          :disabled="loading"
        >
          <IconRefresh :class="['h-3.5 w-3.5', { 'animate-spin': loading }]" />
          <span class="uppercase tracking-wider hidden sm:inline">{{ t('common.refresh') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('moderation.terminal.total') }}:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('moderation.terminal.pending') }}:</span>
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.pending
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('moderation.terminal.reviewed') }}:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.reviewed
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('moderation.terminal.resolved') }}:</span>
          <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums">{{
            stats.resolved
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconShield class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{ t('moderation.terminal.contentModeration') }}</span>
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
            &gt; {{ t('moderation.terminal.selected') }}:{{ selectedRows.length }}
          </span>
        </div>
        <div class="h-4 w-px bg-[var(--silver-300)]" />
        <div class="flex items-center gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[oklch(0.7_0.15_145/0.1)]"
            @click="openBatchDialog('RESOLVED')"
          >
            <IconCheck class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('moderation.batchResolve') }}</span>
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[oklch(0.6_0.2_25/0.1)]"
            @click="openBatchDialog('DISMISSED')"
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
        :data="flaggedProblems"
        :pagination="pagination"
        :row-count="total"
        :loading="loading"
        v-model:selected-rows="selectedRows"
        @update:pagination="pagination = $event"
        :empty-title="t('moderation.emptyTitle')"
        :empty-description="t('moderation.emptyDescription')"
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
      :entity="selectedProblem"
      :loading="saving"
      :title="t('moderation.drawerTitle')"
      :description="t('moderation.drawerDescription')"
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
          <span class="uppercase tracking-wider">{{ t('common.save') }}</span>
        </Button>
      </template>

      <template #content="{ entity }">
        <!-- Problem Info -->
        <div class="space-y-4">
          <div class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-4 bg-[var(--surface-sunken)]">
            <p class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)] mb-2">
              {{ t('moderation.columns.problem') }}
            </p>
            <p class="font-medium text-sm">{{ entity.title }}</p>
            <p class="text-xs text-[var(--silver-500)] font-data mt-1">{{ entity.slug }}</p>
          </div>

          <!-- Status -->
          <div>
            <Label class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">
              {{ t('moderation.status') }}
            </Label>
            <Select v-model="drawerStatus" class="mt-2">
              <SelectTrigger
                class="h-9 font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] bg-transparent"
              >
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

          <!-- Current Flag Reason -->
          <div
            v-if="entity.flag_reason"
            class="border border-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.08)] p-4"
          >
            <div class="flex items-start gap-2">
              <IconAlertTriangle class="h-5 w-5 text-[var(--terminal-red)] flex-shrink-0 mt-0.5" />
              <div class="flex-1">
                <p class="text-xs font-data text-[var(--terminal-red)] uppercase tracking-wider mb-1">
                  &gt; {{ t('moderation.terminal.flagReasonLabel') }}
                </p>
                <p class="text-sm text-[var(--foreground)]">{{ entity.flag_reason }}</p>
              </div>
            </div>
          </div>

          <!-- Notes -->
          <div>
            <Label
              for="drawer-notes"
              class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]"
            >
              {{ t('moderation.notes') }}
            </Label>
            <Textarea
              id="drawer-notes"
              v-model="drawerNotes"
              :placeholder="t('moderation.notesPlaceholder')"
              rows="4"
              class="mt-2 font-data text-sm border-[var(--silver-300)] hover:border-[var(--accent-electric)] bg-transparent placeholder:text-[var(--silver-400)]"
            />
          </div>

          <!-- Reporter Info -->
          <div class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-4 bg-[var(--surface-sunken)]">
            <p class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)] mb-3">
              {{ t('moderation.reportInfo') }}
            </p>
            <div class="space-y-2 text-sm">
              <div class="flex justify-between">
                <span class="text-[var(--silver-500)]">{{ t('common.reportedBy') }}:</span>
                <span class="font-data">{{ entity.flag_reported_by || t('moderation.unknownReporter') }}</span>
              </div>
              <div class="flex justify-between">
                <span class="text-[var(--silver-500)]">{{ t('common.reportedAt') }}:</span>
                <span class="font-data">{{ entity.flag_reported_at ? new Date(entity.flag_reported_at).toLocaleDateString() : '—' }}</span>
              </div>
            </div>
          </div>
        </div>
      </template>
    </BaseDetailDrawer>

    <!-- Batch Moderation Dialog -->
    <Dialog v-model:open="batchDialogOpen">
      <DialogContent class="terminal-card border-[var(--silver-300)]">
        <DialogHeader
          class="terminal-card-header border-b border-[var(--silver-300)] bg-[var(--surface-sunken)] px-4 py-3 -mx-6 -mt-6"
        >
          <DialogTitle
            class="flex items-center gap-2 font-data text-sm uppercase tracking-wider text-[var(--terminal-amber)]"
          >
            <IconChecks class="h-4 w-4" />
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
            <Label
              for="batch-notes"
              class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]"
            >
              {{ t('moderation.notes') }}
            </Label>
            <Textarea
              id="batch-notes"
              v-model="batchNotes"
              :placeholder="t('moderation.batchNotesPlaceholder')"
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
            <IconChecks v-else class="h-3.5 w-3.5 mr-1.5" />
            {{ batchSaving ? t('common.saving') : t('moderation.apply') }}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </div>
</template>
