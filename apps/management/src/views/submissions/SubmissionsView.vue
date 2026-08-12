<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { IconRefresh, IconPlayerPlay, IconLoader2, IconDatabase } from '@tabler/icons-vue'
import { useSubmissionsStore } from '@/stores/admin/submissions'
import type { SubmissionListItem, SubmissionDetail } from '@/api/admin/submissions'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import { useDataTable } from '@/composables/useDataTable'
import { createColumns, formatRuntime, formatMemory } from './columns'

const { t, te } = useI18n()
const store = useSubmissionsStore()

// Filters
const statusFilter = ref<string>('all')
const languageFilter = ref<string>('all')

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
        label: te(`submissions.statusLabels.${s.key}`)
          ? t(`submissions.statusLabels.${s.key}`)
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

const {
  searchQuery,
  tablePagination,
  selectedRows,
  loading,
  data,
  total,
  error,
  loadEntities: loadSubmissions,
} = useDataTable<
  SubmissionListItem,
  { status: string; language: string },
  Parameters<typeof store.fetchSubmissions>[0]
>({
  store: {
    data: computed(() => store.submissions),
    total: computed(() => store.total),
    isLoading: computed(() => store.loading),
    error: computed(() => store.error),
    fetch: (params) => store.fetchSubmissions(params),
  },
  filters: () => ({
    status: statusFilter.value,
    language: languageFilter.value,
  }),
  transformParams: ({ search, filters, page, limit }) => ({
    search,
    status: filters.status === 'all' ? undefined : filters.status,
    language: filters.language === 'all' ? undefined : filters.language,
    page,
    limit,
  }),
  autoLoad: true,
})

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
    <!-- Terminal Header -->
    <div
      :class="[
        'border border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
          {{ t('submissions.title') }}
        </h1>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--border-subtle)] hover:border-[var(--primary)] hover:text-[var(--primary)] transition-colors"
          @click="loadSubmissions"
        >
          <IconRefresh class="h-4 w-4 mr-1.5" />
          <span class="uppercase tracking-wider">{{ t('common.refresh') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--foreground-muted)]"
            >{{ t('submissions.stats.total') }}:</span
          >
          <span class="font-data text-sm text-[var(--foreground-strong)] tabular-nums">{{
            stats.total.toLocaleString()
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--foreground-muted)]"
            >{{ t('submissions.stats.pending') }}:</span
          >
          <span class="font-data text-sm text-[var(--foreground-strong)] tabular-nums">{{
            stats.pending
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--foreground-muted)]"
            >{{ t('submissions.stats.topLanguage') }}:</span
          >
          <span class="font-data text-sm text-[var(--foreground-strong)]">{{
            stats.topLanguage
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--foreground-muted)]"
            >{{ t('submissions.stats.acceptedRate') }}:</span
          >
          <span class="font-data text-sm text-[var(--foreground-strong)] tabular-nums"
            >{{ stats.acceptedRate }}%</span
          >
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--foreground-muted)]">
          <IconDatabase class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{
            t('submissions.stats.submissionManagement')
          }}</span>
        </div>
      </div>
    </div>

    <!-- Bulk Action Bar - Terminal Style -->
    <div
      v-if="selectedRows.length > 0"
      :class="[
        'mt-4 flex items-center justify-between border border-[var(--status-warning-mark)] bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)] p-3',
        'animate-in fade-in slide-in-from-top-2 duration-200',
      ]"
    >
      <div class="flex items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="font-data text-sm text-[var(--foreground-strong)]">
            &gt; SELECTED:{{ selectedRows.length }}
          </span>
        </div>
        <div class="h-4 w-px bg-[var(--border-subtle)]" />
        <div class="flex items-center gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--border-subtle)] hover:border-[var(--status-warning-mark)] hover:text-foreground-strong"
            @click="batchRejudgeDialogOpen = true"
            :disabled="batchRejudging"
          >
            <IconPlayerPlay class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('submissions.batchRejudge') }}</span>
          </Button>
        </div>
      </div>
      <Button
        variant="terminal"
        size="sm"
        class="h-8 font-data text-xs text-[var(--foreground-muted)] hover:text-[var(--foreground)]"
        @click="selectedRows = []"
      >
        [ESC] {{ t('common.clearSelection') }}
      </Button>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1 min-h-0">
      <DataTable
        :columns="columns"
        :data="data"
        :pagination="tablePagination"
        :row-count="total"
        :loading="loading"
        v-model:selected-rows="selectedRows"
        @update:pagination="tablePagination = $event"
        class="terminal-table"
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
      </DataTable>

      <!-- Error state - Terminal Style -->
      <div
        v-if="error"
        class="mt-4 flex items-center justify-between border border-[var(--status-error-mark)] bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] p-4"
      >
        <div class="flex items-center gap-3">
          <span class="font-data text-sm text-[var(--foreground-strong)]">&gt; ERROR:</span>
          <span class="text-sm text-[var(--foreground)]">{{ error }}</span>
        </div>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--status-error-mark)] text-foreground-strong hover:bg-[color-mix(in_oklch,_var(--status-error-mark)_10%,_transparent)]"
          @click="loadSubmissions()"
        >
          {{ t('common.retry') }}
        </Button>
      </div>
    </div>
  </div>

  <!-- Detail Dialog - Terminal Style -->
  <Dialog v-model:open="detailDialogOpen">
    <DialogContent
      class="max-w-4xl max-h-[80vh] overflow-y-auto border-[var(--border-subtle)] dark:border-[var(--border-subtle)]"
    >
      <DialogHeader class="border border-[var(--border-subtle)] dark:border-[var(--border-subtle)] pb-4">
        <DialogTitle class="flex items-center gap-2">
          <span class="text-lg font-medium">{{ t('submissions.detail') }}</span>
        </DialogTitle>
        <DialogDescription
          v-if="selectedSubmission"
          class="font-data text-sm text-[var(--foreground-muted)]"
        >
          {{ selectedSubmission.problemTitle }} - {{ selectedSubmission.username }}
        </DialogDescription>
        <DialogDescription v-else class="font-data text-sm text-[var(--foreground-muted)]">
          {{ t('submissions.detail') }}
        </DialogDescription>
      </DialogHeader>
      <div v-if="detailLoading" class="flex items-center justify-center py-12">
        <IconLoader2 class="h-6 w-6 animate-spin text-[var(--foreground-muted)]" />
      </div>
      <div v-else-if="selectedSubmission" class="space-y-4 py-4">
        <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div>
            <Label class="terminal-label text-[var(--foreground-muted)]">{{ t('submissions.id') }}</Label>
            <p class="font-data text-sm text-[var(--foreground-strong)] mt-1">
              {{ selectedSubmission.id }}
            </p>
          </div>
          <div>
            <Label class="terminal-label text-[var(--foreground-muted)]">{{
              t('submissions.status')
            }}</Label>
            <p class="font-data text-sm mt-1">
              {{
                te(`submissions.statusLabels.${selectedSubmission.status.toUpperCase()}`)
                  ? t(`submissions.statusLabels.${selectedSubmission.status.toUpperCase()}`)
                  : selectedSubmission.status
              }}
            </p>
          </div>
          <div>
            <Label class="terminal-label text-[var(--foreground-muted)]">{{
              t('submissions.runtime')
            }}</Label>
            <p class="font-data text-sm mt-1">{{ formatRuntime(selectedSubmission.runtime) }}</p>
          </div>
          <div>
            <Label class="terminal-label text-[var(--foreground-muted)]">{{
              t('submissions.memory')
            }}</Label>
            <p class="font-data text-sm mt-1">{{ formatMemory(selectedSubmission.memory) }}</p>
          </div>
        </div>
        <div>
          <Label class="terminal-label text-[var(--foreground-muted)]">{{ t('submissions.code') }}</Label>
          <pre
            class="mt-2 p-4 bg-[var(--surface-sunken)] border border-[var(--border-subtle)] dark:border-[var(--border-subtle)] overflow-x-auto text-sm font-mono terminal-code-block"
          ><code>{{ selectedSubmission.code }}</code></pre>
        </div>
        <div v-if="selectedSubmission.notes">
          <Label class="terminal-label text-[var(--foreground-muted)]">{{
            t('submissions.notes')
          }}</Label>
          <p class="mt-1 text-sm text-[var(--foreground-strong)]">{{ selectedSubmission.notes }}</p>
        </div>
      </div>
      <DialogFooter
        class="border-t border-[var(--border-subtle)] dark:border-[var(--border-subtle)] pt-4"
      >
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--border-subtle)] hover:border-[var(--primary)] hover:text-[var(--primary)]"
          @click="detailDialogOpen = false"
        >
          {{ t('common.close') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>

  <!-- Rejudge Dialog - Terminal Style -->
  <Dialog v-model:open="rejudgeDialogOpen">
    <DialogContent class="border-[var(--border-subtle)] dark:border-[var(--border-subtle)]">
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2">
          <span class="text-lg font-medium">{{ t('submissions.rejudgeTitle') }}</span>
        </DialogTitle>
        <DialogDescription class="text-[var(--foreground-muted)]">
          {{ t('submissions.rejudgeDescription') }}
        </DialogDescription>
      </DialogHeader>
      <DialogFooter class="pt-4">
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--border-subtle)]"
          @click="rejudgeDialogOpen = false"
        >
          {{ t('common.cancel') }}
        </Button>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--status-warning-mark)] text-foreground-strong hover:bg-[color-mix(in_oklch,_var(--status-warning-mark)_10%,_transparent)]"
          :disabled="rejudging"
          @click="rejudgeSubmission"
        >
          <IconLoader2 v-if="rejudging" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
          {{ t('submissions.rejudge') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>

  <!-- Batch Rejudge Dialog - Terminal Style -->
  <Dialog v-model:open="batchRejudgeDialogOpen">
    <DialogContent class="border-[var(--border-subtle)] dark:border-[var(--border-subtle)]">
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2">
          <span class="text-lg font-medium">{{ t('submissions.batchRejudgeTitle') }}</span>
        </DialogTitle>
        <DialogDescription class="text-[var(--foreground-muted)]">
          {{ t('submissions.batchRejudgeDescription', { count: selectedRows.length }) }}
        </DialogDescription>
      </DialogHeader>
      <DialogFooter class="pt-4">
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--border-subtle)]"
          @click="batchRejudgeDialogOpen = false"
        >
          {{ t('common.cancel') }}
        </Button>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--status-warning-mark)] text-foreground-strong hover:bg-[color-mix(in_oklch,_var(--status-warning-mark)_10%,_transparent)]"
          :disabled="batchRejudging"
          @click="batchRejudge"
        >
          <IconLoader2 v-if="batchRejudging" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
          {{ t('submissions.batchRejudge') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<style scoped>
.terminal-code-block {
  color: var(--foreground);
}

.terminal-code-block code {
  color: inherit;
}
</style>
