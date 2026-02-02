<script setup lang="ts">
import { ref, computed } from 'vue'
import { toast } from 'vue-sonner'
import { useI18n } from 'vue-i18n'
import { IconPlus, IconTrash } from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'

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

const canCreate = computed(() => authStore.hasPermission('CREATE', 'CONTEST'))
const canUpdate = computed(() => authStore.hasPermission('UPDATE', 'CONTEST'))
const canDelete = computed(() => authStore.hasPermission('DELETE', 'CONTEST'))

const toolbarFilters = computed<Filter[]>(() => [
  {
    modelValue: statusFilter.value,
    placeholder: t('contests.filters.allStatus'),
    width: 'w-[140px]',
    options: [
      { value: 'all', label: t('contests.filters.allStatus') },
      { value: 'NOT_STARTED', label: t('contests.filters.status.notStarted') },
      { value: 'ONGOING', label: t('contests.filters.status.ongoing') },
      { value: 'FINISHED', label: t('contests.filters.status.finished') },
    ],
  },
  {
    modelValue: typeFilter.value,
    placeholder: t('contests.filters.allTypes'),
    width: 'w-[140px]',
    options: [
      { value: 'all', label: t('contests.filters.allTypes') },
      { value: 'IOI', label: t('contests.filters.type.ioi') },
      { value: 'ICPC', label: t('contests.filters.type.icpc') },
      { value: 'CUSTOM', label: t('contests.filters.type.custom') },
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
  filters: {
    statusFilter: statusFilter.value,
    typeFilter: typeFilter.value,
  },
  transformParams: ({ search, filters, page, limit }) => ({
    search,
    status: filters.statusFilter === 'all' ? undefined : filters.statusFilter,
    type: filters.typeFilter === 'all' ? undefined : (filters.typeFilter as ContestType | undefined),
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
  <div class="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
    <div
      v-if="selectedRows.length > 0"
      class="flex items-center justify-between rounded-lg border border-primary/20 bg-primary/5 p-2 px-4 animate-in fade-in slide-in-from-top-2"
    >
      <div class="flex items-center gap-3">
        <span class="text-sm font-medium">{{
          t('contests.selected', { count: selectedRows.length })
        }}</span>
        <Separator orientation="vertical" class="h-4" />
        <div class="flex items-center gap-2">
          <Button
            v-if="canDelete"
            variant="destructive"
            size="sm"
            class="h-8 text-xs"
            @click="handleBulkDelete"
            :disabled="bulkActionLoading"
          >
            <IconTrash class="h-3.5 w-3.5 mr-1" />
            {{ t('contests.actions.bulkDelete') }}
          </Button>
        </div>
      </div>
      <Button variant="ghost" size="sm" class="h-8 text-xs" @click="selectedRows = []">
        {{ t('contests.clearSelection') }}
      </Button>
    </div>

    <DataTable
      :columns="columns"
      :data="data"
      :pagination="tablePagination"
      :row-count="total"
      :loading="loading"
      v-model:selected-rows="selectedRows"
      @update:pagination="tablePagination = $event"
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

      <template #extra-actions>
        <Button v-if="canCreate" variant="outline" size="sm" @click="wizardOpen = true">
          <IconPlus />
          <span class="hidden lg:inline">{{ t('contests.createContest') }}</span>
        </Button>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ error }}</span>
      <Button variant="outline" size="sm" @click="loadContests()">{{ t('common.retry') }}</Button>
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
