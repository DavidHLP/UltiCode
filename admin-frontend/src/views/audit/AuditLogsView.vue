<script setup lang="ts">
import { ref, onMounted, h, watch } from 'vue'
import { watchDebounced } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
import { IconDotsVertical, IconInfoCircle, IconRefresh, IconX } from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'

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
import { useAuditStore } from '@/stores/admin/audit'
import type { AuditLog } from '@/api/admin/audit'

import DataTable from '@/components/table/DataTable.vue'
import AuditLogDetailDrawer from './AuditLogDetailDrawer.vue'
import {
  getActionBadgeVariant,
  getActionIcon,
  getActionIconColor,
  getEntityTypeIcon,
} from './utils'

const { t } = useI18n()
const auditStore = useAuditStore()

const searchQuery = ref('')
const actionFilter = ref<string>('all')
const entityTypeFilter = ref<string>('all')
const tablePagination = ref({ pageIndex: 0, pageSize: 50 })

const selectedLog = ref<AuditLog | null>(null)
const detailsDrawerOpen = ref(false)

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

function showDetails(log: AuditLog) {
  selectedLog.value = log
  detailsDrawerOpen.value = true
}

const columns: ColumnDef<AuditLog>[] = [
  {
    accessorKey: 'created_at',
    header: () => t('audit.columns.createdAt'),
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
    header: () => t('audit.columns.action'),
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
    header: () => t('audit.columns.entityType'),
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
    header: () => t('audit.columns.performer'),
    cell: ({ row }) => {
      const performer = row.original.performer
      if (!performer) {
        return h('span', { class: 'text-muted-foreground text-sm' }, 'System')
      }
      return h('div', { class: 'flex flex-col' }, [
        h('span', { class: 'text-sm font-medium' }, performer.username),
        h(Badge, { variant: 'outline', class: 'w-fit text-xs scale-90 origin-left' }, () =>
          performer.role.replace('_', ' '),
        ),
      ])
    },
  },
  {
    accessorKey: 'user',
    header: () => t('audit.columns.target'),
    cell: ({ row }) => {
      const user = row.original.user
      if (!user) {
        return h('span', { class: 'text-muted-foreground text-sm' }, '—')
      }
      return h('div', { class: 'flex items-center gap-2' }, [
        h('span', { class: 'text-sm' }, user.username),
      ])
    },
  },
  {
    accessorKey: 'ip_address',
    header: () => t('audit.columns.ip'),
    cell: ({ row }) => {
      const ip = row.original.ip_address
      if (!ip) {
        return h('span', { class: 'text-muted-foreground text-sm' }, '—')
      }
      return h('div', { class: 'flex flex-col max-w-[150px]' }, [
        h('span', { class: 'text-sm font-mono truncate' }, ip),
      ])
    },
  },
  {
    id: 'actions',
    header: () => t('common.actions'),
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
                          t('audit.actions.viewDetails'),
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
  <div class="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
    <DataTable
      :columns="columns"
      :data="auditStore.logs"
      :pagination="tablePagination"
      :row-count="auditStore.total"
      :loading="auditStore.loading"
      @update:pagination="tablePagination = $event"
    >
      <template #toolbar-left>
        <div class="flex flex-wrap items-center gap-2 w-full lg:w-auto">
          <Input
            v-model="searchQuery"
            :placeholder="t('audit.searchPlaceholder')"
            class="h-8 min-w-[150px] w-full lg:w-[250px]"
          >
            <template #trailing>
              <button
                v-if="searchQuery"
                @click="searchQuery = ''"
                class="rounded-sm opacity-70 hover:opacity-100"
              >
                <IconX class="h-3 w-3" />
              </button>
            </template>
          </Input>

          <div class="flex items-center gap-2 overflow-x-auto pb-1 lg:pb-0">
            <Select v-model="actionFilter">
              <SelectTrigger class="h-8 w-[150px]">
                <SelectValue :placeholder="t('audit.filters.allActions')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{{ t('audit.filters.allActions') }}</SelectItem>
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
              <SelectTrigger class="h-8 w-[140px]">
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

            <Button
              variant="ghost"
              size="icon"
              class="h-8 w-8"
              @click="loadLogs()"
              :title="t('common.refresh')"
            >
              <IconRefresh class="h-3.5 w-3.5" :class="{ 'animate-spin': auditStore.loading }" />
            </Button>
          </div>
        </div>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="auditStore.error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ auditStore.error }}</span>
      <Button variant="outline" size="sm" @click="loadLogs()">{{ t('common.retry') }}</Button>
    </div>

    <AuditLogDetailDrawer v-model:open="detailsDrawerOpen" :log="selectedLog" />
  </div>
</template>
