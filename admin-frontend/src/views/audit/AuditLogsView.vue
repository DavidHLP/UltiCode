<script setup lang="ts">
import { ref, onMounted, computed, h } from 'vue'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconCircleCheckFilled,
  IconDatabase,
  IconDownload,
  IconDotsVertical,
  IconFileText,
  IconInfoCircle,
  IconLoader,
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
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useAuditStore } from '@/stores/admin/audit'
import { useAuthStore } from '@/stores/admin/auth'
import type { AuditLog } from '@/api/admin/audit'

import DataTable from '@/components/table/DataTable.vue'

const auditStore = useAuditStore()
const authStore = useAuthStore()

const searchQuery = ref('')
const actionFilter = ref<string>('all')
const entityTypeFilter = ref<string>('all')
const tablePagination = ref({ pageIndex: 0, pageSize: 50 })

const canExportLogs = computed(() => authStore.hasPermission('READ', 'SYSTEM'))

onMounted(() => loadLogs())

async function loadLogs() {
  await auditStore.fetchLogs({
    action: actionFilter.value === 'all' ? undefined : actionFilter.value,
    entityType: entityTypeFilter.value === 'all' ? undefined : entityTypeFilter.value,
    page: tablePagination.value.pageIndex + 1,
    limit: tablePagination.value.pageSize,
  })
}

async function exportLogs(format: 'csv' | 'json') {
  try {
    await auditStore.exportLogs({
      action: actionFilter.value === 'all' ? undefined : actionFilter.value,
      entityType: entityTypeFilter.value === 'all' ? undefined : entityTypeFilter.value,
      format,
    })
  } catch {
    alert(`Failed to export logs as ${format.toUpperCase()}`)
  }
}

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
  const details = `
Action: ${log.action}
Entity: ${log.entity_type || 'N/A'} (${log.entity_id || 'N/A'})
Performer: ${log.performer?.username || 'System'} (${log.performer?.role || 'N/A'})
Target User: ${log.user?.username || 'N/A'}
Date: ${new Date(log.created_at).toLocaleString()}
IP: ${log.ip_address || 'N/A'}

Old Values:
${formatJson(log.old_values)}

New Values:
${formatJson(log.new_values)}
  `
  alert(details)
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
  <Tabs default-value="all-logs" class="w-full flex-col justify-start gap-6">
    <div class="flex items-center justify-between px-4 lg:px-6">
      <Label for="view-selector" class="sr-only">View</Label>
      <Select default-value="all-logs">
        <SelectTrigger id="view-selector" class="flex w-fit @4xl/main:hidden" size="sm">
          <SelectValue placeholder="Select a view" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all-logs">All Logs</SelectItem>
          <SelectItem value="user-actions">User Actions</SelectItem>
          <SelectItem value="system-events">System Events</SelectItem>
          <SelectItem value="security">Security</SelectItem>
        </SelectContent>
      </Select>
      <div class="flex items-center gap-4">
        <TabsList
          class="**:data-[slot=badge]:bg-muted-foreground/30 hidden **:data-[slot=badge]:size-5 **:data-[slot=badge]:rounded-full **:data-[slot=badge]:px-1 @4xl/main:flex"
        >
          <TabsTrigger value="all-logs">
            All Logs <Badge variant="secondary">{{ auditStore.logs.length }}</Badge>
          </TabsTrigger>
          <TabsTrigger value="user-actions">User Actions</TabsTrigger>
          <TabsTrigger value="system-events">System Events</TabsTrigger>
          <TabsTrigger value="security">Security</TabsTrigger>
        </TabsList>
        <div v-if="canExportLogs" class="flex items-center gap-2">
          <DropdownMenu>
            <DropdownMenuTrigger as-child>
              <Button variant="outline" size="sm">
                <IconDownload class="mr-2 h-4 w-4" />
                Export
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem @click="exportLogs('csv')"> Export as CSV </DropdownMenuItem>
              <DropdownMenuItem @click="exportLogs('json')"> Export as JSON </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </div>

    <TabsContent value="all-logs" class="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
      <DataTable
        :columns="columns"
        :data="auditStore.logs"
        @update:pagination="tablePagination = $event"
      >
        <template #toolbar-left>
          <Input
            v-model="searchQuery"
            placeholder="Search logs..."
            class="min-w-[200px] w-[260px]"
            @keyup.enter="loadLogs()"
          >
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
          <Select v-model="actionFilter" @update:model-value="loadLogs()">
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
          <Select v-model="entityTypeFilter" @update:model-value="loadLogs()">
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
            <IconRefresh class="h-4 w-4" />
          </Button>
        </template>

        <template v-if="auditStore.loading" #extra-actions>
          <div class="flex items-center gap-2 text-sm text-muted-foreground">
            <IconLoader class="h-4 w-4 animate-spin" />
            Loading...
          </div>
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

      <!-- Empty state -->
      <div
        v-if="!auditStore.loading && auditStore.logs.length === 0"
        class="flex flex-col items-center justify-center py-12 text-center"
      >
        <div
          class="flex size-16 items-center justify-center rounded-full bg-muted text-muted-foreground"
        >
          <IconFileText class="h-8 w-8" />
        </div>
        <h3 class="mt-4 text-lg font-semibold">No audit logs found</h3>
        <p class="text-muted-foreground">
          {{
            searchQuery || actionFilter !== 'all' || entityTypeFilter !== 'all'
              ? 'Try adjusting your filters'
              : 'Audit logs will appear here when admin actions are performed'
          }}
        </p>
      </div>
    </TabsContent>

    <TabsContent value="user-actions" class="flex flex-col px-4 lg:px-6">
      <div class="aspect-video w-full flex-1 rounded-lg border border-dashed" />
    </TabsContent>

    <TabsContent value="system-events" class="flex flex-col px-4 lg:px-6">
      <div class="aspect-video w-full flex-1 rounded-lg border border-dashed" />
    </TabsContent>

    <TabsContent value="security" class="flex flex-col px-4 lg:px-6">
      <div class="aspect-video w-full flex-1 rounded-lg border border-dashed" />
    </TabsContent>
  </Tabs>
</template>
