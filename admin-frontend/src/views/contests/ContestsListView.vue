<script setup lang="ts">
import { ref, computed, h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import { useI18n } from 'vue-i18n'
import {
  IconCalendar,
  IconCircleCheckFilled,
  IconCircleXFilled,
  IconClock,
  IconDotsVertical,
  IconEye,
  IconLoader,
  IconPlayerPlay,
  IconPlayerStop,
  IconPlus,
  IconRefresh,
  IconTrash,
  IconTrophy,
  IconUsers,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { Separator } from '@/components/ui/separator'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useContestsStore } from '@/stores/admin/contests'
import { useAuthStore } from '@/stores/auth'
import type { Contest } from '@/api/admin/contests'
import { ContestType } from '@/api/admin/contests'

import DataTable from '@/components/table/DataTable.vue'
import ContestWizard from './wizard/ContestWizard.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import ContestDetailDrawer from './ContestDetailDrawer.vue'
import { getContestTypeBadgeVariant } from '@/lib/ui/status'
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
    type: filters.typeFilter === 'all' ? undefined : (filters.typeFilter as ContestType),
    page,
    limit,
  }),
  autoLoad: true,
})

function viewContest(contest: Contest) {
  selectedContestId.value = contest.id
  detailDrawerOpen.value = true
}

function startDeleteContest(contest: Contest) {
  selectedContestId.value = contest.id
  selectedContestTitle.value = contest.title
  deleteDialogOpen.value = true
}

async function handleStartContest(contest: Contest) {
  try {
    await contestsStore.startContest(contest.id)
    toast.success(t('contests.toast.startedSuccessfully'))
    await loadContests()
  } catch {
    toast.error(t('contests.toast.failedToStart'))
  }
}

async function handleEndContest(contest: Contest) {
  try {
    await contestsStore.endContest(contest.id)
    toast.success(t('contests.toast.endedSuccessfully'))
    await loadContests()
  } catch {
    toast.error(t('contests.toast.failedToEnd'))
  }
}

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

function getStatusIcon(status: string) {
  switch (status) {
    case 'RUNNING':
      return h(IconCircleCheckFilled, { class: 'h-4 w-4 text-emerald-500' })
    case 'FINISHED':
      return h(IconCircleXFilled, { class: 'h-4 w-4 text-muted-foreground' })
    default:
      return h(IconLoader, { class: 'h-4 w-4 animate-spin text-blue-500' })
  }
}

function getStatusBadge(status: string) {
  switch (status) {
    case 'RUNNING':
      return h(Badge, { variant: 'default' }, () => t('contests.status.running'))
    case 'FINISHED':
      return h(Badge, { variant: 'secondary' }, () => t('contests.status.finished'))
    default:
      return h(Badge, { variant: 'outline' }, () => t('contests.status.upcoming'))
  }
}

function getTypeBadgeVariant(type: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  return getContestTypeBadgeVariant(type)
}

async function handleDeleteContest(id: string | number) {
  await contestsStore.deleteContest(String(id))
}

const columns: ColumnDef<Contest>[] = [
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
    accessorKey: 'title',
    header: () => t('contests.columns.contest'),
    cell: ({ row }) => {
      const contest = row.original
      return h('div', { class: 'flex items-center gap-3' }, [
        h(
          'div',
          {
            class: 'h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center text-primary',
          },
          [h(IconTrophy, { class: 'h-4 w-4' })],
        ),
        h('div', { class: 'flex flex-col' }, [
          h(
            'span',
            {
              class: 'font-medium text-sm cursor-pointer hover:underline',
              onClick: () => viewContest(contest),
            },
            contest.title,
          ),
          h('span', { class: 'text-muted-foreground text-xs' }, contest.slug),
        ]),
      ])
    },
  },
  {
    accessorKey: 'contest_type',
    header: () => t('contests.columns.type'),
    cell: ({ row }) => {
      const type = row.original.contest_type
      return h('div', { class: 'flex items-center gap-2' }, [
        h(Badge, { variant: getTypeBadgeVariant(type) }, () => t(`contests.type.${type}`)),
      ])
    },
  },
  {
    accessorKey: 'status',
    header: () => t('contests.columns.status'),
    cell: ({ row }) => {
      const status = row.original.status
      return h('div', { class: 'flex items-center gap-2' }, [
        getStatusIcon(status),
        getStatusBadge(status),
      ])
    },
  },
  {
    accessorKey: 'start_time',
    header: () => t('contests.columns.schedule'),
    cell: ({ row }) => {
      const contest = row.original
      const startDate = new Date(contest.start_time)
      return h('div', { class: 'flex flex-col text-sm' }, [
        h('div', { class: 'flex items-center gap-1.5 text-muted-foreground' }, [
          h(IconCalendar, { class: 'h-3.5 w-3.5' }),
          h('span', {}, startDate.toLocaleDateString()),
        ]),
        h('div', { class: 'flex items-center gap-1.5 text-muted-foreground' }, [
          h(IconClock, { class: 'h-3.5 w-3.5' }),
          h('span', {}, t('contests.scheduleStep.minutes', { minutes: contest.duration_minutes })),
        ]),
      ])
    },
  },
  {
    accessorKey: 'participant_count',
    header: () => t('contests.columns.participants'),
    cell: ({ row }) => {
      return h('div', { class: 'flex items-center gap-2 text-muted-foreground text-sm' }, [
        h(IconUsers, { class: 'h-4 w-4' }),
        h('span', {}, row.original.participant_count || 0),
      ])
    },
  },
  {
    id: 'actions',
    header: () => t('contests.columns.actions'),
    cell: ({ row }) => {
      const contest = row.original
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
                    { onClick: () => viewContest(contest) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconEye, { class: 'h-4 w-4' }),
                          t('contests.actions.viewDetails'),
                        ]),
                    },
                  ),
                  canUpdate.value && contest.status === 'UPCOMING'
                    ? h(
                        DropdownMenuItem,
                        { onClick: () => handleStartContest(contest) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2 text-emerald-600' }, [
                              h(IconPlayerPlay, { class: 'h-4 w-4' }),
                              t('contests.actions.startContest'),
                            ]),
                        },
                      )
                    : null,
                  canUpdate.value && contest.status === 'RUNNING'
                    ? h(
                        DropdownMenuItem,
                        { onClick: () => handleEndContest(contest) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2 text-amber-600' }, [
                              h(IconPlayerStop, { class: 'h-4 w-4' }),
                              t('contests.actions.endContest'),
                            ]),
                        },
                      )
                    : null,
                  h(DropdownMenuSeparator, {}),
                  canDelete.value
                    ? h(
                        DropdownMenuItem,
                        { onClick: () => startDeleteContest(contest) },
                        {
                          default: () =>
                            h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                              h(IconTrash, { class: 'h-4 w-4' }),
                              t('contests.actions.delete'),
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
        <Input
          v-model="searchQuery"
          :placeholder="t('contests.searchPlaceholder')"
          class="min-w-[200px] w-[260px]"
        >
          <template #trailing>
            <button
              v-if="searchQuery"
              @click="searchQuery = ''"
              class="rounded-sm opacity-70 hover:opacity-100"
            >
              <IconCircleXFilled class="h-4 w-4" />
            </button>
          </template>
        </Input>
        <Select v-model="statusFilter">
          <SelectTrigger class="w-[140px]">
            <SelectValue :placeholder="t('contests.filters.allStatus')" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{{ t('contests.filters.allStatus') }}</SelectItem>
            <SelectItem value="UPCOMING">{{ t('contests.status.upcoming') }}</SelectItem>
            <SelectItem value="RUNNING">{{ t('contests.status.running') }}</SelectItem>
            <SelectItem value="FINISHED">{{ t('contests.status.finished') }}</SelectItem>
          </SelectContent>
        </Select>
        <Select v-model="typeFilter">
          <SelectTrigger class="w-[130px]">
            <SelectValue :placeholder="t('contests.filters.allTypes')" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{{ t('contests.filters.allTypes') }}</SelectItem>
            <SelectItem value="PUBLIC">{{ t('contests.type.PUBLIC') }}</SelectItem>
            <SelectItem value="PRIVATE">{{ t('contests.type.PRIVATE') }}</SelectItem>
            <SelectItem value="VIRTUAL">{{ t('contests.type.VIRTUAL') }}</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="outline" size="icon" @click="loadContests()" :title="t('common.refresh')">
          <IconRefresh class="h-4 w-4" :class="{ 'animate-spin': loading }" />
        </Button>
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
    :description="t('contests.delete.description', { title: selectedContestTitle || t('contests.delete.thisContest') })"
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
