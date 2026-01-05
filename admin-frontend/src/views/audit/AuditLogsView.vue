<script setup lang="ts">
import { ref, onMounted, h, watch } from 'vue'
import { watchDebounced } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconCircleCheckFilled,
  IconDatabase,
  IconDotsVertical,
  IconFileText,
  IconInfoCircle,
  IconRefresh,
  IconShield,
  IconTrash,
  IconUser,
  IconX,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
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
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { ScrollArea } from '@/components/ui/scroll-area'
import { useAuditStore } from '@/stores/admin/audit'
import type { AuditLog } from '@/api/admin/audit'

import DataTable from '@/components/table/DataTable.vue'

const auditStore = useAuditStore()

const searchQuery = ref('')
const actionFilter = ref<string>('all')
const entityTypeFilter = ref<string>('all')
const tablePagination = ref({ pageIndex: 0, pageSize: 50 })

const selectedLog = ref<AuditLog | null>(null)
const detailsDialogOpen = ref(false)

onMounted(() => loadLogs())

async function loadLogs() {
  await auditStore.fetchLogs({
    action: actionFilter.value === 'all' ? undefined : actionFilter.value,
    entityType: entityTypeFilter.value === 'all' ? undefined : entityTypeFilter.value,
    page: tablePagination.value.pageIndex + 1,
    limit: tablePagination.value.pageSize,
  })
}

// Watchers
watchDebounced(
  searchQuery,
  () => {
    tablePagination.value.pageIndex = 0
    loadLogs()
  },
  { debounce: 500 },
)

watch([actionFilter, entityTypeFilter], () => {
  if (tablePagination.value.pageIndex === 0) {
    loadLogs()
  } else {
    tablePagination.value.pageIndex = 0
  }
})

watch(
  () => tablePagination.value,
  () => loadLogs(),
  { deep: true },
)

function getActionBadgeVariant(
  action: string,
): 'default' | 'secondary' | 'destructive' | 'outline' {
  const actionUpper = action.toUpperCase()
  if (
    actionUpper.includes('CREATE') ||
    actionUpper.includes('GRANT') ||
    actionUpper.includes('PUBLISH')
  ) {
    return 'default'
  }
  if (actionUpper.includes('UPDATE') || actionUpper.includes('UNBAN')) {
    return 'secondary'
  }
  if (
    actionUpper.includes('DELETE') ||
    actionUpper.includes('BAN') ||
    actionUpper.includes('REVOKE')
  ) {
    return 'destructive'
  }
  return 'outline'
}

function getActionIcon(action: string) {
  const actionUpper = action.toUpperCase()
  if (actionUpper.includes('CREATE') || actionUpper.includes('GRANT')) {
    return IconCircleCheckFilled
  }
  if (actionUpper.includes('UPDATE') || actionUpper.includes('PUBLISH')) {
    return IconFileText
  }
  if (actionUpper.includes('DELETE') || actionUpper.includes('REVOKE')) {
    return IconTrash
  }
  if (actionUpper.includes('BAN')) {
    return IconX
  }
  if (actionUpper.includes('UNBAN')) {
    return IconShield
  }
  return IconInfoCircle
}

function getActionIconColor(action: string) {
  const actionUpper = action.toUpperCase()
  if (
    actionUpper.includes('CREATE') ||
    actionUpper.includes('GRANT') ||
    actionUpper.includes('PUBLISH')
  ) {
    return 'text-emerald-500'
  }
  if (actionUpper.includes('UPDATE')) {
    return 'text-blue-500'
  }
  if (
    actionUpper.includes('DELETE') ||
    actionUpper.includes('BAN') ||
    actionUpper.includes('REVOKE')
  ) {
    return 'text-red-500'
  }
  return 'text-muted-foreground'
}

function getEntityTypeIcon(entityType: string | undefined) {
  if (!entityType) return IconInfoCircle
  const upper = entityType.toUpperCase()
  if (upper.includes('USER')) return IconUser
  if (upper.includes('PROBLEM')) return IconFileText
  if (upper.includes('CONTEST')) return IconDatabase
  return IconInfoCircle
}

function formatJson(value: unknown): string {
  if (!value) return 'N/A'
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}

function showDetails(log: AuditLog) {
  selectedLog.value = log
  detailsDialogOpen.value = true
}

const columns: ColumnDef<AuditLog>[] = [
  {
    accessorKey: 'created_at',
    header: 'Timestamp',
    cell: ({ row }) => {
      const date = new Date(row.getValue('created_at') as Date)
      return h(
        'span',
        { class: 'text-muted-foreground text-sm tabular-nums' },
        date.toLocaleString(),
      )
    },
  },
  {
    accessorKey: 'action',
    header: 'Action',
    cell: ({ row }) => {
      const action = row.getValue('action') as string
      const icon = getActionIcon(action)
      const color = getActionIconColor(action)
      return h('div', { class: 'flex items-center gap-2' }, [
        h(icon, { class: `h-4 w-4 ${color}` }),
        h(Badge, { variant: getActionBadgeVariant(action) }, () => action),
      ])
    },
  },
  {
    accessorKey: 'entity_type',
    header: 'Entity',
    cell: ({ row }) => {
      const entityType = row.original.entity_type
      const entityId = row.original.entity_id
      const icon = getEntityTypeIcon(entityType)
      if (!entityType) {
        return h('span', { class: 'text-muted-foreground text-sm' }, '—')
      }
      return h('div', { class: 'flex items-center gap-2' }, [
        h(icon, { class: 'h-4 w-4 text-muted-foreground' }),
        h('div', { class: 'flex flex-col' }, [
          h('span', { class: 'text-sm font-medium' }, entityType),
          h(
            'span',
            { class: 'text-muted-foreground text-xs font-mono' },
            entityId?.slice(0, 8) || 'N/A',
          ),
        ]),
      ])
    },
  },
  {
    accessorKey: 'performer',
    header: 'Performer',
    cell: ({ row }) => {
      const performer = row.original.performer
      if (!performer) {
        return h('span', { class: 'text-muted-foreground text-sm' }, 'System')
      }
      return h('div', { class: 'flex flex-col' }, [
        h('span', { class: 'text-sm font-medium' }, performer.username),
        h(Badge, { variant: 'outline', class: 'w-fit text-xs' }, () => performer.role),
      ])
    },
  },
  {
    accessorKey: 'user',
    header: 'Target User',
    cell: ({ row }) => {
      const user = row.original.user
      if (!user) {
        return h('span', { class: 'text-muted-foreground text-sm' }, '—')
      }
      return h('span', { class: 'text-sm' }, user.username)
    },
  },
  {
    accessorKey: 'ip_address',
    header: 'IP Address',
    cell: ({ row }) => {
      const ip = row.original.ip_address
      if (!ip) {
        return h('span', { class: 'text-muted-foreground text-sm' }, '—')
      }
      return h('span', { class: 'text-muted-foreground text-sm font-mono' }, ip)
    },
  },
  {
    id: 'actions',
    header: 'Actions',
    cell: ({ row }) => {
      const log = row.original
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
                    { onClick: () => showDetails(log) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconInfoCircle, { class: 'h-4 w-4' }),
                          'View Details',
                        ]),
                    },
                  ),
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
  <div class="flex flex-col gap-4 px-4 lg:px-6">
    <DataTable
      :columns="columns"
      :data="auditStore.logs"
      :pagination="tablePagination"
      :row-count="auditStore.total"
      :loading="auditStore.loading"
      @update:pagination="tablePagination = $event"
    >
      <template #toolbar-left>
        <Input v-model="searchQuery" placeholder="Search logs..." class="min-w-[200px] w-[260px]">
          <template #trailing>
            <button
              v-if="searchQuery"
              @click="searchQuery = ''"
              class="rounded-sm opacity-70 hover:opacity-100"
            >
              <IconX class="h-4 w-4" />
            </button>
          </template>
        </Input>
        <Select v-model="actionFilter">
          <SelectTrigger class="w-[180px]">
            <SelectValue placeholder="All Actions" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Actions</SelectItem>
            <SelectItem value="CREATE_USER">Create User</SelectItem>
            <SelectItem value="UPDATE_USER">Update User</SelectItem>
            <SelectItem value="DELETE_USER">Delete User</SelectItem>
            <SelectItem value="BAN_USER">Ban User</SelectItem>
            <SelectItem value="UNBAN_USER">Unban User</SelectItem>
            <SelectItem value="GRANT_PERMISSION">Grant Permission</SelectItem>
            <SelectItem value="REVOKE_PERMISSION">Revoke Permission</SelectItem>
          </SelectContent>
        </Select>
        <Select v-model="entityTypeFilter">
          <SelectTrigger class="w-[160px]">
            <SelectValue placeholder="All Entities" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Entities</SelectItem>
            <SelectItem value="USER">User</SelectItem>
            <SelectItem value="PROBLEM">Problem</SelectItem>
            <SelectItem value="CONTEST">Contest</SelectItem>
            <SelectItem value="SOLUTION">Solution</SelectItem>
            <SelectItem value="FORUM_POST">Forum Post</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="outline" size="icon" @click="loadLogs()" title="Refresh">
          <IconRefresh class="h-4 w-4" :class="{ 'animate-spin': auditStore.loading }" />
        </Button>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="auditStore.error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ auditStore.error }}</span>
      <Button variant="outline" size="sm" @click="loadLogs()">Retry</Button>
    </div>

    <Dialog v-model:open="detailsDialogOpen">
      <DialogContent class="max-w-2xl">
        <DialogHeader>
          <DialogTitle>Audit Log Details</DialogTitle>
          <DialogDescription>
            Detailed information for the selected audit log entry.
          </DialogDescription>
        </DialogHeader>
        <ScrollArea class="max-h-[60vh] pr-4">
          <div v-if="selectedLog" class="space-y-4 py-4">
            <div class="grid grid-cols-2 gap-4">
              <div class="space-y-1">
                <span class="text-xs font-medium text-muted-foreground uppercase">Action</span>
                <p class="text-sm font-semibold">{{ selectedLog.action }}</p>
              </div>
              <div class="space-y-1">
                <span class="text-xs font-medium text-muted-foreground uppercase">Date</span>
                <p class="text-sm font-semibold">
                  {{ new Date(selectedLog.created_at).toLocaleString() }}
                </p>
              </div>
              <div class="space-y-1">
                <span class="text-xs font-medium text-muted-foreground uppercase">Entity</span>
                <p class="text-sm font-semibold">
                  {{ selectedLog.entity_type || 'N/A' }} ({{ selectedLog.entity_id || 'N/A' }})
                </p>
              </div>
              <div class="space-y-1">
                <span class="text-xs font-medium text-muted-foreground uppercase">Performer</span>
                <p class="text-sm font-semibold">
                  {{ selectedLog.performer?.username || 'System' }} ({{
                    selectedLog.performer?.role || 'N/A'
                  }})
                </p>
              </div>
            </div>

            <div class="space-y-1">
              <span class="text-xs font-medium text-muted-foreground uppercase">Old Values</span>
              <pre class="p-2 rounded-md bg-muted text-xs overflow-auto">{{
                formatJson(selectedLog.old_values)
              }}</pre>
            </div>

            <div class="space-y-1">
              <span class="text-xs font-medium text-muted-foreground uppercase">New Values</span>
              <pre class="p-2 rounded-md bg-muted text-xs overflow-auto">{{
                formatJson(selectedLog.new_values)
              }}</pre>
            </div>
          </div>
        </ScrollArea>
      </DialogContent>
    </Dialog>
  </div>
</template>
