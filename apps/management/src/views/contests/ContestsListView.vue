<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { toast } from 'vue-sonner'
import { useI18n } from 'vue-i18n'
import { IconPlus, IconTrash, IconTrophy } from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'

import { useContestsStore } from '@/stores/admin/contests'
import { useAuthStore } from '@/stores/auth'
import type { Contest, ContestType } from '@/api/admin/contests'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import ContestWizard from './wizard/ContestWizard.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import ContestDetailDrawer from './ContestDetailDrawer.vue'
import { createColumns } from './columns'
import { useDataTable } from '@/composables/useDataTable'

const contestsStore = useContestsStore()
const authStore = useAuthStore()
const { t } = useI18n()

const searchQuery = ref('')
const statusFilter = ref<string>('all')
const typeFilter = ref<string>('all')
const selectedContestId = ref<string | null>(null)
const selectedContestTitle = ref<string | null>(null)

const wizardOpen = ref(false)
const deleteDialogOpen = ref(false)
const detailDrawerOpen = ref(false)

const bulkActionLoading = ref(false)
const selectedRows = ref<Contest[]>([])

// Animation state for staggered reveal
const isLoaded = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

const canCreate = computed(() => authStore.hasPermission('CREATE', 'CONTEST'))
const canUpdate = computed(() => authStore.hasPermission('UPDATE', 'CONTEST'))
const canDelete = computed(() => authStore.hasPermission('DELETE', 'CONTEST'))

// Stats for terminal ticker
const stats = computed(() => {
  const contests = contestsStore.contests
  const total = contestsStore.total
  const running = contests.filter((c) => c.status === 'RUNNING').length
  const upcoming = contests.filter((c) => c.status === 'UPCOMING').length
  const finished = contests.filter((c) => c.status === 'FINISHED').length
  return { total, running, upcoming, finished }
})

const toolbarFilters = computed<Filter[]>(() => [
  {
    modelValue: statusFilter.value,
    placeholder: t('contests.filters.allStatus'),
    width: 'w-[140px]',
    options: [
      { value: 'all', label: t('contests.filters.allStatus') },
      { value: 'upcoming', label: t('contests.filters.status.upcoming') },
      { value: 'running', label: t('contests.filters.status.running') },
      { value: 'finished', label: t('contests.filters.status.finished') },
    ],
  },
  {
    modelValue: typeFilter.value,
    placeholder: t('contests.filters.allTypes'),
    width: 'w-[140px]',
    options: [
      { value: 'all', label: t('contests.filters.allTypes') },
      { value: 'PUBLIC', label: t('contests.filters.type.public') },
      { value: 'PRIVATE', label: t('contests.filters.type.private') },
      { value: 'VIRTUAL', label: t('contests.filters.type.virtual') },
    ],
  },
])

const {
  tablePagination,
  loading,
  data,
  total,
  error,
  loadEntities: loadContests,
} = useDataTable<
  Contest,
  { statusFilter: string; typeFilter: string },
  Parameters<typeof contestsStore.fetchContests>[0]
>({
  store: {
    data: computed(() => contestsStore.contests),
    total: computed(() => contestsStore.total),
    isLoading: computed(() => contestsStore.loading),
    error: computed(() => contestsStore.error),
    fetch: (params) => contestsStore.fetchContests(params),
  },
  filters: () => ({
    statusFilter: statusFilter.value,
    typeFilter: typeFilter.value,
  }),
  transformParams: ({ search, filters, page, limit }) => ({
    search,
    status: filters.statusFilter === 'all' ? undefined : filters.statusFilter,
    type:
      filters.typeFilter === 'all' ? undefined : (filters.typeFilter as ContestType | undefined),
    page,
    limit,
  }),
  autoLoad: true,
})

const columns = createColumns(
  t,
  {
    viewContest: (contest: Contest) => {
      selectedContestId.value = contest.id
      detailDrawerOpen.value = true
    },
    startContest: async (contest: Contest) => {
      try {
        await contestsStore.startContest(contest.id)
        toast.success(t('contests.toast.startedSuccessfully'))
        await loadContests()
      } catch {
        toast.error(t('contests.toast.failedToStart'))
      }
    },
    endContest: async (contest: Contest) => {
      try {
        await contestsStore.endContest(contest.id)
        toast.success(t('contests.toast.endedSuccessfully'))
        await loadContests()
      } catch {
        toast.error(t('contests.toast.failedToEnd'))
      }
    },
    startDeleteContest: (contest: Contest) => {
      selectedContestId.value = contest.id
      selectedContestTitle.value = contest.title
      deleteDialogOpen.value = true
    },
  },
  () => canUpdate.value,
  () => canDelete.value,
)

async function handleBulkDelete() {
  if (selectedRows.value.length === 0) return
  const ids = selectedRows.value.map((r) => r.id)
  if (!confirm(t('contests.confirmation.bulkDelete', { count: ids.length }))) return

  bulkActionLoading.value = true
  try {
    for (const id of ids) {
      await contestsStore.deleteContest(id)
    }
    await loadContests()
    selectedRows.value = []
    toast.success(t('contests.toast.bulkDeleteSuccess', { count: ids.length }))
  } catch {
    toast.error(t('contests.toast.bulkDeleteFailed'))
  } finally {
    bulkActionLoading.value = false
  }
}

async function handleDeleteContest(id: string | number) {
  await contestsStore.deleteContest(String(id))
}
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Terminal Header -->
    <div
      :class="[
        'border border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--card)]',
        'transition-colors duration-200',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
          {{ t('contests.title') }}
        </h1>
        <Button
          v-if="canCreate"
          type="button"
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--border-subtle)] hover:border-[var(--primary)] hover:text-[var(--primary)] transition-colors duration-200"
          @click="wizardOpen = true"
        >
          <IconPlus class="h-4 w-4 mr-1.5" />
          <span class="uppercase tracking-wider">{{ t('contests.createContest') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--foreground-muted)]"
            >{{ t('contests.stats.total') }}:</span
          >
          <span class="font-data text-sm text-[var(--foreground-strong)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--foreground-muted)]"
            >{{ t('contests.stats.running') }}:</span
          >
          <span class="font-data text-sm text-[var(--foreground-strong)] tabular-nums">{{
            stats.running
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--foreground-muted)]"
            >{{ t('contests.stats.upcoming') }}:</span
          >
          <span class="font-data text-sm text-[var(--foreground-strong)] tabular-nums">{{
            stats.upcoming
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--foreground-muted)]"
            >{{ t('contests.stats.finished') }}:</span
          >
          <span class="font-data text-sm text-[var(--foreground-muted)] tabular-nums">{{
            stats.finished
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--foreground-muted)]">
          <IconTrophy class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{
            t('contests.stats.contestManagement')
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
            v-if="canDelete"
            type="button"
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--status-error-mark)] text-foreground-strong hover:bg-[color-mix(in_oklch,_var(--status-error-mark)_10%,_transparent)]"
            @click="handleBulkDelete"
            :disabled="bulkActionLoading"
          >
            <IconTrash class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('contests.actions.bulkDelete') }}</span>
          </Button>
        </div>
      </div>
      <Button
        type="button"
        variant="terminal"
        size="sm"
        class="h-8 font-data text-xs text-[var(--foreground-muted)] hover:text-[var(--foreground)]"
        @click="selectedRows = []"
      >
        [ESC] {{ t('contests.clearSelection') }}
      </Button>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1">
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
            :search-placeholder="t('contests.searchPlaceholder')"
            :filters="toolbarFilters"
            @update:filter="
              (index, value) =>
                index === 0 ? (statusFilter = String(value)) : (typeFilter = String(value))
            "
            :loading="loading"
            :on-refresh="loadContests"
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
          type="button"
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--status-error-mark)] text-foreground-strong hover:bg-[color-mix(in_oklch,_var(--status-error-mark)_10%,_transparent)]"
          @click="loadContests()"
        >
          {{ t('common.retry') }}
        </Button>
      </div>
    </div>
  </div>

  <ContestWizard v-model:open="wizardOpen" @success="loadContests" />

  <EntityActionDialog
    v-model:open="deleteDialogOpen"
    :entity-id="selectedContestId"
    :entity-title="selectedContestTitle"
    action="delete"
    :title="t('contests.delete.title')"
    :description="
      t('contests.delete.description', {
        title: selectedContestTitle || t('contests.delete.thisContest'),
      })
    "
    :confirm-label="t('contests.delete.confirm')"
    :cancel-label="t('contests.delete.cancel')"
    :success-label="t('contests.toast.deletedSuccessfully')"
    :error-label="t('contests.toast.failedToDelete')"
    :on-action="handleDeleteContest"
    @success="loadContests"
  />

  <ContestDetailDrawer
    v-model:open="detailDrawerOpen"
    :contest-id="selectedContestId"
    @success="loadContests"
  />
</template>
