<script setup lang="ts">
import { ref, computed, onMounted, h, watch } from 'vue'
import { formatDateTimeByLocale } from '@/i18n/utils'
import { watchDebounced } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconInfoCircle,
  IconRefresh,
  IconX,
  IconDatabase,
  IconChevronDown,
  IconChevronUp,
  IconSearch,
} from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
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
import { normalizeDateParams, type AuditLog } from '@/api/admin/audit'

import DataTable from '@/components/table/DataTable.vue'
import AuditLogDetailDrawer from './AuditLogDetailDrawer.vue'
import {
  getActionBadge,
  getEntityTypeIcon,
  AUDIT_ACTIONS_BY_ENTITY,
  AUDIT_ACTION_GROUPS,
  AUDIT_ENTITY_TYPES,
  actionToI18nKey,
  actionTypeGroupToI18nKey,
  entityTypeToI18nKey,
} from './utils'
import { badge, USER_ROLE_COLOR_MAP } from '@/components/ui/terminal'

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

const actionTypeStats = computed(() => auditStore.stats?.actionsByType ?? [])
const statsTotal = computed(() => auditStore.stats?.totalActions ?? auditStore.total)

async function loadLogs() {
  const params = normalizeDateParams({
    search: searchQuery.value || undefined,
    action: actionFilter.value === 'all' ? undefined : actionFilter.value,
    entityType: entityTypeFilter.value === 'all' ? undefined : entityTypeFilter.value,
    startDate: startDateFilter.value || undefined,
    endDate: endDateFilter.value || undefined,
    performerId: performerIdFilter.value || undefined,
    userId: userIdFilter.value || undefined,
    page: tablePagination.value.pageIndex + 1,
    limit: tablePagination.value.pageSize,
  })
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
  // 主动移除触发按钮的焦点。抽屉是模态 reka Dialog,打开时会立即给背景(含侧边栏
  // sidebar-wrapper)标记 aria-hidden;若此时焦点仍停留在位于背景内的触发按钮上,会触发
  // "Blocked aria-hidden ... descendant retained focus" 警告(reka-ui #1280 的基础竞态,
  // 任何背景内 trigger → 模态 Dialog 都会复现,与是否 dropdown 无关)。先 blur 让焦点回到
  // body,无论 reka 的 aria-hide 与 openAutoFocus 谁先执行,aria-hide 时焦点都不在被隐藏
  // 的子树内;随后抽屉的 focus trap 会把焦点移入抽屉。
  ;(document.activeElement as HTMLElement | null)?.blur()
  selectedLog.value = log
  detailsDrawerOpen.value = true
}

const columns: ColumnDef<AuditLog>[] = [
  {
    accessorKey: 'createdAt',
    header: () => t('audit.columns.createdAt'),
    cell: ({ row }) => {
      const date = new Date(row.getValue('createdAt') as string)
      return h(
        'span',
        { class: 'font-data text-sm text-[var(--foreground-muted)] tabular-nums' },
        formatDateTimeByLocale(date),
      )
    },
  },
  {
    accessorKey: 'action',
    header: () => t('audit.columns.action'),
    cell: ({ row }) => {
      const action = row.getValue('action') as string
      const i18nKey = actionToI18nKey(action)
      const translated = t(i18nKey)
      const label = translated === i18nKey ? action : translated
      return getActionBadge(action, label)
    },
  },
  {
    accessorKey: 'entityType',
    header: () => t('audit.columns.entityType'),
    cell: ({ row }) => {
      const entityType = row.original.entityType
      const entityId = row.original.entityId
      const icon = getEntityTypeIcon(entityType)
      const notAvailable = t('audit.drawer.notAvailable')
      if (!entityType) {
        return h('span', { class: 'text-[var(--foreground-muted)] text-sm' }, '—')
      }
      return h('div', { class: 'flex items-center gap-2' }, [
        h(icon, { class: 'h-4 w-4 text-[var(--foreground-muted)]' }),
        h('div', { class: 'flex flex-col' }, [
          h(
            'span',
            { class: 'text-sm font-medium' },
            t(entityTypeToI18nKey(entityType), entityType),
          ),
          h(
            'span',
            { class: 'text-[var(--foreground-muted)] text-xs font-data' },
            entityId?.slice(0, 8) || notAvailable,
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
        return h('span', { class: 'text-[var(--foreground-muted)] text-sm' }, t('audit.systemAction'))
      }
      return h('div', { class: 'flex flex-col' }, [
        h('span', { class: 'text-sm font-medium' }, performer.username),
        badge({
          color: USER_ROLE_COLOR_MAP[performer.role] ?? 'neutral',
          label: t(`users.filters.role.${performer.role}`, performer.role),
          size: 'sm',
        }),
      ])
    },
  },
  {
    accessorKey: 'user',
    header: () => t('audit.columns.target'),
    cell: ({ row }) => {
      const user = row.original.user
      if (!user) {
        return h('span', { class: 'text-[var(--foreground-muted)] text-sm' }, '—')
      }
      return h('div', { class: 'flex items-center gap-2' }, [
        h('span', { class: 'text-sm' }, user.username),
      ])
    },
  },
  {
    accessorKey: 'ipAddress',
    header: () => t('audit.columns.ip'),
    cell: ({ row }) => {
      const ip = row.original.ipAddress
      if (!ip) {
        return h('span', { class: 'text-[var(--foreground-muted)] text-sm' }, '—')
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
        Button,
        {
          variant: 'ghost',
          size: 'icon',
          class: 'h-8 w-8 p-0',
          title: t('audit.actions.viewDetails'),
          onClick: () => showDetails(log),
        },
        {
          default: () => [
            h('span', { class: 'sr-only' }, t('audit.actions.viewDetails')),
            h(IconInfoCircle, { class: 'h-4 w-4' }),
          ],
        },
      )
    },
  },
]
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
          {{ t('audit.title') }}
        </h1>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--foreground-muted)]">{{ t('audit.stats.total') }}:</span>
          <span class="font-data text-sm text-[var(--foreground-strong)] tabular-nums">{{
            statsTotal
          }}</span>
        </div>
        <div v-for="item in actionTypeStats" :key="item.actionType" class="flex items-center gap-2">
          <span class="terminal-label text-[var(--foreground-muted)]"
            >{{ t(actionTypeGroupToI18nKey(item.actionType)) }}:</span
          >
          <span class="font-data text-sm text-[var(--foreground-strong)] tabular-nums">{{
            item.count
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--foreground-muted)]">
          <IconDatabase class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{
            t('audit.stats.systemAuditTrail')
          }}</span>
        </div>
      </div>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1">
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
          <div class="flex items-center gap-3 flex-wrap">
            <div class="relative flex items-center min-w-[200px] w-[260px]">
              <IconSearch
                class="absolute left-2.5 h-3.5 w-3.5 text-[var(--foreground-muted)] pointer-events-none"
              />
              <Input
                v-model="searchQuery"
                variant="terminal"
                :placeholder="t('audit.searchPlaceholder')"
                class="h-8 pl-8 pr-8 !text-xs w-full bg-[var(--surface-sunken)] border-[var(--border-subtle)] dark:border-[var(--border-subtle)] focus:border-[var(--primary)]"
              />
              <button
                v-if="searchQuery"
                @click="searchQuery = ''"
                class="absolute right-2.5 opacity-70 hover:opacity-100 text-[var(--foreground-muted)] focus:outline-none transition-opacity"
              >
                <IconX class="h-3.5 w-3.5" />
              </button>
            </div>
            <Select v-model="actionFilter">
              <SelectTrigger
                variant="terminal"
                size="sm"
                class="h-8 w-[200px] bg-[var(--surface-sunken)] border-[var(--border-subtle)] dark:border-[var(--border-subtle)] focus:border-[var(--primary)] font-data text-xs uppercase tracking-wider"
              >
                <SelectValue :placeholder="t('audit.filters.allActions')" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">{{ t('audit.filters.allActions') }}</SelectItem>
                <SelectGroup v-for="group in AUDIT_ACTION_GROUPS" :key="group">
                  <SelectLabel>{{ t(`audit.entityGroups.${group}`, group) }}</SelectLabel>
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
                variant="terminal"
                size="sm"
                class="h-8 w-[180px] bg-[var(--surface-sunken)] border-[var(--border-subtle)] dark:border-[var(--border-subtle)] focus:border-[var(--primary)] font-data text-xs uppercase tracking-wider"
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
              class="h-8 w-8 border-[var(--border-subtle)] hover:border-[var(--status-success-mark)] hover:text-foreground-strong"
              @click="loadLogs()"
              :title="t('common.refresh')"
            >
              <IconRefresh class="h-3.5 w-3.5" :class="{ 'animate-spin': auditStore.loading }" />
            </Button>
            <Button
              variant="terminal"
              size="sm"
              class="font-data text-xs border-[var(--border-subtle)]"
              @click="showAdvancedFilters = !showAdvancedFilters"
            >
              {{ t('audit.filters.advancedFilters') }}
              <IconChevronDown v-if="!showAdvancedFilters" class="h-3 w-3 ml-1" />
              <IconChevronUp v-else class="h-3 w-3 ml-1" />
            </Button>
          </div>
          <div
            v-if="showAdvancedFilters"
            class="flex items-center gap-3 mt-2 pt-2 border-t border-[var(--border-subtle)] dark:border-[var(--border-subtle)]"
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
        class="mt-4 flex items-center justify-between border border-[var(--status-error-mark)] bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] p-4"
      >
        <div class="flex items-center gap-3">
          <span class="font-data text-sm text-[var(--foreground-strong)]">&gt; ERROR:</span>
          <span class="text-sm text-[var(--foreground)]">{{ auditStore.error }}</span>
        </div>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--status-error-mark)] text-foreground-strong hover:bg-[color-mix(in_oklch,_var(--status-error-mark)_10%,_transparent)]"
          @click="loadLogs()"
        >
          {{ t('common.retry') }}
        </Button>
      </div>
    </div>

    <AuditLogDetailDrawer v-model:open="detailsDrawerOpen" :log="selectedLog" />
  </div>
</template>
