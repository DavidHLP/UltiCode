<script setup lang="ts">
import { ref, computed, onMounted, h, watch } from 'vue'
import { watchDebounced } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconDotsVertical,
  IconInfoCircle,
  IconRefresh,
  IconX,
  IconDatabase,
  IconChevronDown,
  IconChevronUp,
} from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useAuditStore } from '@/stores/admin/audit'
import type { AuditLog } from '@/api/admin/audit'

import DataTable from '@/components/table/DataTable.vue'
import AuditLogDetailDrawer from './AuditLogDetailDrawer.vue'
import {
  getActionIcon,
  getActionIconColor,
  getActionBadgeClass,
  getEntityTypeIcon,
  AUDIT_ACTIONS_BY_ENTITY,
  AUDIT_ACTION_GROUPS,
  AUDIT_ENTITY_TYPES,
  actionToI18nKey,
  entityTypeToI18nKey,
} from './utils'

const { t } = useI18n()
const auditStore = useAuditStore()

const searchQuery = ref('')
const actionFilter = ref<string>('all')
const entityTypeFilter = ref<string>('all')
const startDateFilter = ref('')
const endDateFilter = ref('')
const performerIdFilter = ref('')
const userIdFilter = ref('')
const showAdvancedFilters = ref(false)
const tablePagination = ref({ pageIndex: 0, pageSize: 50 })

const selectedLog = ref<AuditLog | null>(null)
const detailsDrawerOpen = ref(false)

// Animation state for staggered reveal
const isLoaded = ref(false)

onMounted(() => {
  loadLogs()
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

// Stats for terminal ticker — all values from server-side aggregation
const stats = computed(() => {
  const s = auditStore.stats
  if (!s) {
    return { total: auditStore.total, create: 0, update: 0, delete: 0, other: 0 }
  }
  const byType = Object.fromEntries(s.actionsByType?.map((i) => [i.actionType, i.count]) ?? [])
  return {
    total: s.totalActions,
    create: byType.CREATE ?? 0,
    update: byType.UPDATE ?? 0,
    delete: byType.DELETE ?? 0,
    other: s.totalActions - (byType.CREATE ?? 0) - (byType.UPDATE ?? 0) - (byType.DELETE ?? 0),
  }
})

async function loadLogs() {
  const params = {
    search: searchQuery.value || undefined,
    action: actionFilter.value === 'all' ? undefined : actionFilter.value,
    entityType: entityTypeFilter.value === 'all' ? undefined : entityTypeFilter.value,
    startDate: startDateFilter.value || undefined,
    endDate: endDateFilter.value || undefined,
    performerId: performerIdFilter.value || undefined,
    userId: userIdFilter.value || undefined,
    page: tablePagination.value.pageIndex + 1,
    limit: tablePagination.value.pageSize,
  }
  await auditStore.fetchLogs(params)
  await auditStore.fetchStats(params)
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

watch(
  [actionFilter, entityTypeFilter, startDateFilter, endDateFilter, performerIdFilter, userIdFilter],
  () => {
    if (tablePagination.value.pageIndex === 0) {
      loadLogs()
    } else {
      tablePagination.value.pageIndex = 0
    }
  },
)

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
    accessorKey: 'createdAt',
    id: 'created_at',
    header: () => t('audit.columns.createdAt'),
    cell: ({ row }) => {
      const date = new Date(row.getValue('createdAt') as string)
      return h(
        'span',
        { class: 'font-data text-sm text-[var(--silver-500)] tabular-nums' },
        date.toLocaleString(),
      )
    },
  },
  {
    accessorKey: 'action',
    id: 'action',
    header: () => t('audit.columns.action'),
    cell: ({ row }) => {
      const action = row.getValue('action') as string
      const icon = getActionIcon(action)
      const color = getActionIconColor(action)
      const badgeClass = getActionBadgeClass(action)
      return h('div', { class: 'flex items-center gap-2' }, [
        h(icon, { class: `h-4 w-4 ${color}` }),
        h('span', { class: `terminal-badge ${badgeClass}` }, action),
      ])
    },
  },
  {
    accessorKey: 'entityType',
    id: 'entity_type',
    header: () => t('audit.columns.entityType'),
    cell: ({ row }) => {
      const entityType = row.original.entityType
      const entityId = row.original.entityId
      const icon = getEntityTypeIcon(entityType)
      if (!entityType) {
        return h('span', { class: 'text-[var(--silver-500)] text-sm' }, '—')
      }
      return h('div', { class: 'flex items-center gap-2' }, [
        h(icon, { class: 'h-4 w-4 text-[var(--silver-500)]' }),
        h('div', { class: 'flex flex-col' }, [
          h('span', { class: 'text-sm font-medium' }, entityType),
          h(
            'span',
            { class: 'text-[var(--silver-500)] text-xs font-data' },
            entityId?.slice(0, 8) || 'N/A',
          ),
        ]),
      ])
    },
  },
  {
    accessorKey: 'performer',
    id: 'performer',
    header: () => t('audit.columns.performer'),
    cell: ({ row }) => {
      const performer = row.original.performer
      if (!performer) {
        return h('span', { class: 'text-[var(--silver-500)] text-sm' }, 'System')
      }
      return h('div', { class: 'flex flex-col' }, [
        h('span', { class: 'text-sm font-medium' }, performer.username),
        h(
          'span',
          { class: 'terminal-badge terminal-badge-info w-fit text-xs scale-90 origin-left' },
          performer.role.replace('_', ' '),
        ),
      ])
    },
  },
  {
    accessorKey: 'user',
    id: 'user',
    header: () => t('audit.columns.target'),
    cell: ({ row }) => {
      const user = row.original.user
      if (!user) {
        return h('span', { class: 'text-[var(--silver-500)] text-sm' }, '—')
      }
      return h('div', { class: 'flex items-center gap-2' }, [
        h('span', { class: 'text-sm' }, user.username),
      ])
    },
  },
  {
    accessorKey: 'ipAddress',
    id: 'ip_address',
    header: () => t('audit.columns.ip'),
    cell: ({ row }) => {
      const ip = row.original.ipAddress
      if (!ip) {
        return h('span', { class: 'text-[var(--silver-500)] text-sm' }, '—')
      }
      return h('div', { class: 'flex flex-col max-w-[150px]' }, [
        h('span', { class: 'text-sm font-data truncate' }, ip),
      ])
    },
  },
  {
    id: 'actions',
    header: () => t('common.actions.label'),
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
                        h('span', { class: 'sr-only' }, t('audit.actions.openMenu')),
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
  <div class="relative flex flex-col gap-0 overflow-auto">
    <!-- Terminal Header -->
    <div
      :class="[
        'border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <span class="terminal-prompt text-base">audit-logs</span>
            <span class="terminal-cursor" />
          </div>
          <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
            {{ t('audit.title') }}
          </h1>
        </div>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">{{ t('audit.stats.total') }}:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('audit.stats.create') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums">{{
            stats.create
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('audit.stats.update') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.update
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('audit.stats.delete') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-red)] tabular-nums">{{
            stats.delete
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconDatabase class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{
            t('audit.stats.systemAuditTrail')
          }}</span>
        </div>
      </div>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1 py-4">
      <DataTable
        :columns="columns"
        :data="auditStore.logs"
        :pagination="tablePagination"
        :row-count="auditStore.total"
        :loading="auditStore.loading"
        @update:pagination="tablePagination = $event"
        class="terminal-table"
      >
        <template #toolbar-left>
          <div class="flex items-center gap-3">
            <div class="relative">
              <Input
                v-model="searchQuery"
                :placeholder="t('audit.searchPlaceholder')"
                class="terminal-input min-w-[200px] w-[260px] font-data text-sm"
              />
              <button
                v-if="searchQuery"
                @click="searchQuery = ''"
                class="absolute right-2 top-1/2 -translate-y-1/2 rounded-sm opacity-70 hover:opacity-100 text-[var(--silver-500)]"
              >
                <IconX class="h-4 w-4" />
              </button>
            </div>
            <Select v-model="actionFilter">
              <SelectTrigger
                class="terminal-input w-[200px] font-data text-xs uppercase tracking-wider"
              >
                <SelectValue :placeholder="t('audit.filters.allActions')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{{ t('audit.filters.allActions') }}</SelectItem>
                <SelectGroup v-for="group in AUDIT_ACTION_GROUPS" :key="group">
                  <SelectLabel>{{ t(`audit.entityGroups.${group}`) }}</SelectLabel>
                  <SelectItem
                    v-for="action in AUDIT_ACTIONS_BY_ENTITY[group]"
                    :key="action"
                    :value="action"
                  >
                    {{ t(actionToI18nKey(action)) }}
                  </SelectItem>
                </SelectGroup>
              </SelectContent>
            </Select>
            <Select v-model="entityTypeFilter">
              <SelectTrigger
                class="terminal-input w-[180px] font-data text-xs uppercase tracking-wider"
              >
                <SelectValue :placeholder="t('audit.filters.allEntities')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{{ t('audit.filters.allEntities') }}</SelectItem>
                <SelectItem v-for="type in AUDIT_ENTITY_TYPES" :key="type" :value="type">
                  {{ t(entityTypeToI18nKey(type)) }}
                </SelectItem>
              </SelectContent>
            </Select>
            <Button
              variant="terminal"
              size="icon"
              class="h-9 w-9 border-[var(--silver-300)] hover:border-[var(--terminal-green)] hover:text-[var(--terminal-green)]"
              @click="loadLogs()"
              :title="t('common.refresh')"
            >
              <IconRefresh class="h-4 w-4" :class="{ 'animate-spin': auditStore.loading }" />
            </Button>
            <Button
              variant="terminal"
              size="sm"
              class="font-data text-xs border-[var(--silver-300)]"
              @click="showAdvancedFilters = !showAdvancedFilters"
            >
              {{ t('audit.filters.advancedFilters') }}
              <IconChevronDown v-if="!showAdvancedFilters" class="h-3 w-3 ml-1" />
              <IconChevronUp v-else class="h-3 w-3 ml-1" />
            </Button>
          </div>
          <div
            v-if="showAdvancedFilters"
            class="flex items-center gap-3 mt-2 pt-2 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)]"
          >
            <Input
              v-model="startDateFilter"
              type="date"
              :placeholder="t('audit.filters.startDate')"
              class="terminal-input w-[140px] font-data text-sm"
            />
            <Input
              v-model="endDateFilter"
              type="date"
              :placeholder="t('audit.filters.endDate')"
              class="terminal-input w-[140px] font-data text-sm"
            />
            <Input
              v-model="performerIdFilter"
              :placeholder="t('audit.filters.performerId')"
              class="terminal-input w-[140px] font-data text-sm"
            />
            <Input
              v-model="userIdFilter"
              :placeholder="t('audit.filters.userId')"
              class="terminal-input w-[140px] font-data text-sm"
            />
          </div>
        </template>
      </DataTable>

      <!-- Error state - Terminal Style -->
      <div
        v-if="auditStore.error"
        class="mt-4 flex items-center justify-between border border-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_8%,_transparent)] p-4"
      >
        <div class="flex items-center gap-3">
          <span class="font-data text-sm text-[var(--terminal-red)]">&gt; ERROR:</span>
          <span class="text-sm text-[var(--foreground)]">{{ auditStore.error }}</span>
        </div>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]"
          @click="loadLogs()"
        >
          {{ t('common.retry') }}
        </Button>
      </div>
    </div>

    <AuditLogDetailDrawer v-model:open="detailsDrawerOpen" :log="selectedLog" />
  </div>
</template>
