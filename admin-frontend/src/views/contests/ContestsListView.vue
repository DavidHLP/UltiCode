<script setup lang="ts">
import { ref, onMounted, computed, h } from 'vue'
import { useRouter } from 'vue-router'
import { useContestsStore } from '@/stores/admin/contests'
import { useAuthStore } from '@/stores/admin/auth'
import DataTable from '@/components/ui/data-table/DataTable.vue'
import type { ColumnDef } from '@tanstack/vue-table'
import type { Contest } from '@/api/admin/contests'
import { ContestType } from '@/api/admin/contests'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
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
import {
  IconDots,
  IconPlus,
  IconEdit,
  IconTrash,
  IconEye,
} from '@tabler/icons-vue'
import { useDebounceFn } from '@vueuse/core'
import { toast } from 'vue-sonner'
import ContestWizard from './wizard/ContestWizard.vue'

const router = useRouter()
const contestsStore = useContestsStore()
const authStore = useAuthStore()

// State
const search = ref('')
const statusFilter = ref<string>('all')
const typeFilter = ref<string>('all')
const isWizardOpen = ref(false)

// Permissions
const canCreate = computed(() => authStore.hasPermission('CREATE', 'CONTEST'))
const canUpdate = computed(() => authStore.hasPermission('UPDATE', 'CONTEST'))
const canDelete = computed(() => authStore.hasPermission('DELETE', 'CONTEST'))

// Columns
const columns: ColumnDef<Contest>[] = [
  {
    accessorKey: 'title',
    header: 'Title',
    cell: ({ row }) => {
      const contest = row.original
      return h('div', { class: 'flex flex-col' }, [
        h(
          'span',
          { class: 'font-medium cursor-pointer hover:underline', onClick: () => navigateToDetail(contest.id) },
          contest.title
        ),
        h('span', { class: 'text-xs text-muted-foreground' }, contest.slug),
      ])
    },
  },
  {
    accessorKey: 'type',
    header: 'Type',
    cell: ({ row }) => {
      return h(Badge, { variant: 'outline' }, () => row.original.contest_type)
    },
  },
  {
    accessorKey: 'status',
    header: 'Status',
    cell: ({ row }) => {
      const status = row.original.status
      const variant =
        status === 'RUNNING'
          ? 'default'
          : status === 'FINISHED'
            ? 'secondary'
            : 'outline'
      return h(Badge, { variant, class: 'capitalize' }, () => status.toLowerCase())
    },
  },
  {
    accessorKey: 'start_time',
    header: 'Start Time',
    cell: ({ row }) => {
      return new Date(row.original.start_time).toLocaleString()
    },
  },
  {
    accessorKey: 'participant_count',
    header: 'Participants',
    cell: ({ row }) => {
      return row.original.participant_count || 0
    },
  },
  {
    id: 'actions',
    cell: ({ row }) => {
      const contest = row.original
      return h(
        DropdownMenu,
        {},
        {
          default: () =>
            h(
              DropdownMenuTrigger,
              { asChild: true },
              {
                default: () =>
                  h(
                    Button,
                    { variant: 'ghost', size: 'icon', class: 'h-8 w-8 p-0' },
                    { default: () => h(IconDots, { class: 'h-4 w-4' }) }
                  ),
              }
            ),
          content: () =>
            h(DropdownMenuContent, { align: 'end' }, {
              default: () => [
                h(DropdownMenuLabel, () => 'Actions'),
                h(
                  DropdownMenuItem,
                  { onClick: () => navigateToDetail(contest.id) },
                  { default: () => [h(IconEye, { class: 'mr-2 h-4 w-4' }), 'View Details'] }
                ),
                canUpdate.value &&
                  h(
                    DropdownMenuItem,
                    { onClick: () => navigateToDetail(contest.id) }, // Edit is in detail view for now
                    { default: () => [h(IconEdit, { class: 'mr-2 h-4 w-4' }), 'Edit'] }
                  ),
                h(DropdownMenuSeparator),
                canDelete.value &&
                  h(
                    DropdownMenuItem,
                    {
                      class: 'text-destructive',
                      onClick: () => handleDelete(contest.id),
                    },
                    { default: () => [h(IconTrash, { class: 'mr-2 h-4 w-4' }), 'Delete'] }
                  ),
              ],
            }),
        }
      )
    },
  },
]

// Actions
const fetchContests = async () => {
  await contestsStore.fetchContests({
    search: search.value,
    status: statusFilter.value === 'all' ? undefined : statusFilter.value,
    type: typeFilter.value === 'all' ? undefined : (typeFilter.value as ContestType),
    page: 1, // Reset to page 1 on filter change
    limit: 20,
  })
}

const debouncedSearch = useDebounceFn(() => {
  fetchContests()
}, 300)

function navigateToDetail(id: string) {
  router.push({ name: 'contest-detail', params: { id } })
}

async function handleDelete(id: string) {
  if (!confirm('Are you sure you want to delete this contest?')) return
  try {
    await contestsStore.deleteContest(id)
    toast.success('Contest deleted')
  } catch {
    toast.error('Failed to delete contest')
  }
}

function handleWizardSuccess() {
  fetchContests()
}

onMounted(() => {
  fetchContests()
})
</script>

<template>
  <div class="h-full flex-1 flex-col space-y-8 p-8 md:flex">
    <div class="flex items-center justify-between space-y-2">
      <div>
        <h2 class="text-2xl font-bold tracking-tight">Contests</h2>
        <p class="text-muted-foreground">Manage programming contests and events.</p>
      </div>
      <div class="flex items-center space-x-2">
        <Button v-if="canCreate" @click="isWizardOpen = true">
          <IconPlus class="mr-2 h-4 w-4" />
          Create Contest
        </Button>
      </div>
    </div>

    <div class="space-y-4">
      <div class="flex items-center gap-2">
        <Input
          placeholder="Search contests..."
          v-model="search"
          class="h-8 w-[150px] lg:w-[250px]"
          @input="debouncedSearch"
        />
        <Select
          v-model="statusFilter"
          @update:model-value="fetchContests"
        >
          <SelectTrigger class="h-8 w-[150px]">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Status</SelectItem>
            <SelectItem value="upcoming">Upcoming</SelectItem>
            <SelectItem value="running">Running</SelectItem>
            <SelectItem value="finished">Finished</SelectItem>
          </SelectContent>
        </Select>
        <Select
          v-model="typeFilter"
          @update:model-value="fetchContests"
        >
          <SelectTrigger class="h-8 w-[150px]">
            <SelectValue placeholder="Type" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Types</SelectItem>
            <SelectItem value="PUBLIC">Public</SelectItem>
            <SelectItem value="PRIVATE">Private</SelectItem>
            <SelectItem value="VIRTUAL">Virtual</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <DataTable
        :columns="columns"
        :data="contestsStore.contests"
        :loading="contestsStore.loading"
        :pagination="{
          pageIndex: (contestsStore.page || 1) - 1, // API 1-based, Table 0-based
          pageSize: contestsStore.limit || 20,
          total: contestsStore.total
        }"
        @update:pagination="(p) => contestsStore.fetchContests({ page: p.pageIndex + 1, limit: p.pageSize })"
      />
    </div>

    <ContestWizard
      v-model:open="isWizardOpen"
      @success="handleWizardSuccess"
    />
  </div>
</template>
