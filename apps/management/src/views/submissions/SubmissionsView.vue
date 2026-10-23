<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { Button } from '@ulticode/design-system/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@ulticode/design-system/ui/dialog'
import { Label } from '@ulticode/design-system/ui/label'
import {
  RefreshCw as IconRefresh,
  Play as IconPlayerPlay,
  Loader2 as IconLoader2,
  Database as IconDatabase,
} from '@lucide/vue'

import { useSubmissionsStore } from '@/stores/admin/submissions'
import type { SubmissionListItem, SubmissionDetail } from '@/api/admin/submissions'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import { useRemoteTable } from '@/composables/useRemoteTable'
import { createColumns, formatRuntime, formatMemory, getStatusLabel } from './columns'

const { t, te } = useI18n()
const store = useSubmissionsStore()

// Filters

// Detail dialog
const detailDialogOpen = ref(false)
const selectedSubmission = ref<SubmissionDetail | null>(null)
const detailLoading = ref(false)
const currentRejudgeId = ref<string | null>(null)

// Rejudge dialog
const rejudgeDialogOpen = ref(false)
const rejudging = ref(false)

// Batch rejudge dialog
const batchRejudgeDialogOpen = ref(false)
const batchRejudging = ref(false)
const selectedRows = ref<SubmissionListItem[]>([])

// Animation state for staggered reveal
const isLoaded = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
  // Load statistics and filters separately
  store.fetchStatistics()
  store.fetchFilters()
})
const {
  query,
  searchQuery,
  tablePagination,
  loading,
  data,
  total,
  error,
  refresh: loadSubmissions,
  setFilters,
} = useRemoteTable<
  SubmissionListItem,
  { status: string; language: string },
  Parameters<typeof store.fetchSubmissions>[0]
>({
  store,
  initialQuery: { filters: { status: 'all', language: 'all' } },
  toParams: ({ search, filters, page, limit }) => ({
    search,
    status: filters.status === 'all' ? undefined : filters.status,
    language: filters.language === 'all' ? undefined : filters.language,
    page,
    limit,
  }),
  autoLoad: true,
})

const statusFilter = computed({
  get: () => query.value.filters.status,
  set: (status: string) => setFilters({ ...query.value.filters, status }),
})
const languageFilter = computed({
  get: () => query.value.filters.language,
  set: (language: string) => setFilters({ ...query.value.filters, language }),
})

// Stats for terminal ticker
const stats = computed(() => store.stats)

// Toolbar filters for DataTableToolbar
const toolbarFilters = computed<Filter[]>(() => [
  {
    modelValue: statusFilter.value,
    placeholder: t('submissions.allStatuses'),
    width: 'w-[140px]',
    options: [
      { value: 'all', label: t('submissions.allStatuses') },
      ...store.statuses.map((s) => ({
        value: s.key,
        label: te(`submissions.statusLabels.${s.code}`)
          ? t(`submissions.statusLabels.${s.code}`)
          : s.label,
      })),
    ],
  },
  {
    modelValue: languageFilter.value,
    placeholder: t('submissions.allLanguages'),
    width: 'w-[140px]',
    options: [
      { value: 'all', label: t('submissions.allLanguages') },
      ...store.languages.map((lang) => ({ value: lang.key, label: lang.label })),
    ],
  },
])

async function viewSubmission(id: string) {
  detailLoading.value = true
  detailDialogOpen.value = true
  try {
    selectedSubmission.value = await store.getSubmissionDetail(id)
  } catch (error) {
    console.error('Failed to load submission:', error)
    toast.error(t('submissions.loadDetailError'))
    detailDialogOpen.value = false
  } finally {
    detailLoading.value = false
  }
}

function openRejudgeDialog(id: string) {
  currentRejudgeId.value = id
  rejudgeDialogOpen.value = true
}

async function rejudgeSubmission() {
  if (!currentRejudgeId.value) return
  rejudging.value = true
  try {
    const result = await store.rejudgeSubmission(currentRejudgeId.value)
    if (result.success) {
      toast.success(t('submissions.rejudgeSuccess'))
      await loadSubmissions()
    } else {
      toast.error(t('submissions.rejudgeError', { error: result.error }))
    }
  } catch (error) {
    console.error('Failed to rejudge:', error)
    toast.error(t('submissions.rejudgeError', { error: 'Unknown error' }))
  } finally {
    rejudging.value = false
    rejudgeDialogOpen.value = false
    currentRejudgeId.value = null
  }
}

async function batchRejudge() {
  if (selectedRows.value.length === 0) return

  batchRejudging.value = true
  try {
    const ids = selectedRows.value.map((r) => r.id)
    const result = await store.batchRejudge(ids)
    if (result.failed === 0) {
      toast.success(t('submissions.toast.batchRejudgeSuccess', { count: result.successful }))
    } else {
      toast.warning(
        t('submissions.toast.batchRejudgePartial', {
          success: result.successful,
          failed: result.failed,
        }),
      )
    }
    selectedRows.value = []
    await loadSubmissions()
  } catch (error) {
    console.error('Failed to batch rejudge:', error)
    toast.error(t('submissions.toast.batchRejudgeError'))
  } finally {
    batchRejudging.value = false
    batchRejudgeDialogOpen.value = false
  }
}

const columns = createColumns(t, {
  viewSubmission,
  openRejudgeDialog,
})
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
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-foreground">
          {{ t('submissions.title') }}
        </h1>
        <Button variant="outline" size="sm" @click="loadSubmissions">
          <IconRefresh class="h-4 w-4 mr-1.5" />
          <span class="uppercase tracking-wider">{{ t('common.refresh') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex flex-wrap items-center gap-x-6 gap-y-2 whitespace-nowrap border-t border-border dark:border-border bg-muted"
      >
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('submissions.stats.total') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{
            stats?.total.toLocaleString() ?? '—'
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('submissions.stats.pending') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{
            stats?.pending ?? '—'
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('submissions.stats.topLanguage') }}:</span
          >
          <span class="font-mono text-sm text-foreground">{{ stats?.topLanguage ?? '—' }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('submissions.stats.acceptedRate') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{
            stats ? `${stats.acceptedRate}%` : '—'
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-muted-foreground">
          <IconDatabase class="h-4 w-4" />
          <span class="text-xs font-mono uppercase tracking-wider">{{
            t('submissions.stats.submissionManagement')
          }}</span>
        </div>
      </div>
      <div
        v-if="store.statsError"
        role="alert"
        class="flex flex-wrap items-center justify-between gap-3 border-t border-border p-4"
      >
        <p class="min-w-0 text-sm text-destructive [overflow-wrap:anywhere]">
          {{ t('submissions.loadStatisticsError') }}
        </p>
        <Button variant="outline" size="sm" @click="store.fetchStatistics()">
          {{ t('common.retry') }}
        </Button>
      </div>
    </div>

    <!-- Bulk Action Bar - Terminal Style -->
    <div
      v-if="!error && selectedRows.length > 0"
      :class="[
        'mt-4 flex flex-wrap items-center justify-between gap-3 border border-status-warning-mark bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)] p-3',
        'animate-in fade-in slide-in-from-top-2 duration-200',
      ]"
    >
      <div class="flex flex-wrap items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="whitespace-nowrap font-mono text-sm text-foreground">
            {{ t('submissions.table.selected', { count: selectedRows.length }) }}
          </span>
        </div>
        <div class="h-4 w-px bg-border" />
        <div class="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            @click="batchRejudgeDialogOpen = true"
            :disabled="batchRejudging"
          >
            <IconPlayerPlay class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('submissions.batchRejudge') }}</span>
          </Button>
        </div>
      </div>
      <Button variant="outline" size="sm" @click="selectedRows = []">
        {{ t('common.clearSelection') }}
      </Button>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1 min-h-0">
      <DataTable
        :columns="columns"
        :data="error ? [] : data"
        :pagination="tablePagination"
        :row-count="error ? 0 : total"
        :hide-pagination="!!error"
        :loading="loading"
        v-model:selected-rows="selectedRows"
        @update:pagination="tablePagination = $event"
        class="w-full text-sm"
      >
        <template #toolbar-left>
          <DataTableToolbar
            :search-model-value="searchQuery"
            @update:search-model-value="searchQuery = $event"
            :search-placeholder="t('submissions.searchPlaceholder')"
            :filters="toolbarFilters"
            @update:filter="
              (index, value) =>
                index === 0 ? (statusFilter = String(value)) : (languageFilter = String(value))
            "
            :loading="loading"
            :on-refresh="loadSubmissions"
          />
        </template>
        <template v-if="error" #empty>
          <div
            role="alert"
            class="flex flex-wrap items-center justify-between gap-3 border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] p-4"
          >
            <div class="flex min-w-0 flex-1 flex-wrap items-center gap-3 [overflow-wrap:anywhere]">
              <span class="font-mono text-sm text-foreground">&gt; ERROR:</span>
              <span class="text-sm text-foreground">{{ error }}</span>
            </div>
            <Button variant="outline" size="sm" @click="loadSubmissions()">
              {{ t('common.retry') }}
            </Button>
          </div>
        </template>
      </DataTable>
    </div>
  </div>

  <!-- Detail Dialog - Terminal Style -->
  <Dialog v-model:open="detailDialogOpen">
    <DialogContent class="sm:max-w-4xl">
      <DialogHeader class="pr-6">
        <DialogTitle class="flex items-center gap-2">
          <span class="text-lg font-medium">{{ t('submissions.detail') }}</span>
        </DialogTitle>
        <DialogDescription v-if="selectedSubmission" class="break-words">
          {{ selectedSubmission.problemTitle }} - {{ selectedSubmission.username }}
        </DialogDescription>
        <DialogDescription v-else>
          {{ t('submissions.detail') }}
        </DialogDescription>
      </DialogHeader>
      <div v-if="detailLoading" class="flex items-center justify-center py-12">
        <IconLoader2 class="h-6 w-6 animate-spin text-muted-foreground" />
      </div>
      <div v-else-if="selectedSubmission" class="min-w-0 space-y-4 py-4">
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div>
            <Label>{{ t('submissions.id') }}</Label>
            <p class="break-all font-mono text-sm text-foreground mt-1">
              {{ selectedSubmission.id }}
            </p>
          </div>
          <div>
            <Label>{{ t('submissions.status') }}</Label>
            <p class="font-mono text-sm mt-1">
              {{ getStatusLabel(t, selectedSubmission.status) }}
            </p>
          </div>
          <div>
            <Label>{{ t('submissions.runtime') }}</Label>
            <p class="font-mono text-sm mt-1">{{ formatRuntime(selectedSubmission.runtime) }}</p>
          </div>
          <div>
            <Label>{{ t('submissions.memory') }}</Label>
            <p class="font-mono text-sm mt-1">{{ formatMemory(selectedSubmission.memory) }}</p>
          </div>
        </div>
        <div>
          <Label>{{ t('submissions.code') }}</Label>
          <pre
            class="mt-2 max-w-full overflow-x-auto rounded-md border border-border bg-muted p-4 text-sm font-mono"
          ><code>{{ selectedSubmission.code }}</code></pre>
        </div>
        <div v-if="selectedSubmission.notes">
          <Label>{{ t('submissions.notes') }}</Label>
          <p class="mt-1 break-words text-sm text-foreground">{{ selectedSubmission.notes }}</p>
        </div>
      </div>
      <DialogFooter>
        <Button variant="outline" size="sm" @click="detailDialogOpen = false">
          {{ t('common.close') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>

  <!-- Rejudge Dialog - Terminal Style -->
  <Dialog v-model:open="rejudgeDialogOpen">
    <DialogContent>
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2">
          <span class="text-lg font-medium">{{ t('submissions.rejudgeTitle') }}</span>
        </DialogTitle>
        <DialogDescription>
          {{ t('submissions.rejudgeDescription') }}
        </DialogDescription>
      </DialogHeader>
      <DialogFooter class="pt-4">
        <Button variant="outline" size="sm" @click="rejudgeDialogOpen = false">
          {{ t('common.cancel') }}
        </Button>
        <Button variant="outline" size="sm" :disabled="rejudging" @click="rejudgeSubmission">
          <IconLoader2 v-if="rejudging" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
          {{ t('submissions.rejudge') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>

  <!-- Batch Rejudge Dialog - Terminal Style -->
  <Dialog v-model:open="batchRejudgeDialogOpen">
    <DialogContent>
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2">
          <span class="text-lg font-medium">{{ t('submissions.batchRejudgeTitle') }}</span>
        </DialogTitle>
        <DialogDescription>
          {{ t('submissions.batchRejudgeDescription', { count: selectedRows.length }) }}
        </DialogDescription>
      </DialogHeader>
      <DialogFooter class="pt-4">
        <Button variant="outline" size="sm" @click="batchRejudgeDialogOpen = false">
          {{ t('common.cancel') }}
        </Button>
        <Button variant="outline" size="sm" :disabled="batchRejudging" @click="batchRejudge">
          <IconLoader2 v-if="batchRejudging" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
          {{ t('submissions.batchRejudge') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
