<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ColumnDef } from '@tanstack/vue-table'
import { IconPlus, IconTrash, IconDotsVertical, IconBell, IconPencil } from '@tabler/icons-vue'
import { format } from 'date-fns'

import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useNotificationsStore } from '@/stores/admin/notifications'
import {
  NOTIFICATION_TYPES,
  NOTIFICATION_CATEGORIES,
  type NotificationCategory,
  type NotificationType,
  type SystemAnnouncement,
  type AdminNotificationQueryParams,
} from '@/api/admin/notifications'
import NotificationCreateDialog from './NotificationCreateDialog.vue'
import { getNotificationCategoryLabel, getNotificationTypeLabel } from './notificationLabels'

import { badge, NOTIFICATION_TYPE_COLOR_MAP } from '@/components/ui/terminal'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { useDataTable } from '@/composables/useDataTable'

const { t } = useI18n()
const store = useNotificationsStore()

const typeFilter = ref<string>('all')
const categoryFilter = ref<string>('all')
const createDialogOpen = ref(false)
const deleteDialogOpen = ref(false)
const selectedNotificationId = ref<string | null>(null)
const selectedNotificationTitle = ref<string | null>(null)
const notificationToEdit = ref<SystemAnnouncement | null>(null)

const isLoaded = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

const {
  searchQuery,
  tablePagination,
  loading,
  data,
  total,
  error,
  loadEntities: loadNotifications,
} = useDataTable<
  SystemAnnouncement,
  { type: string; category: string },
  AdminNotificationQueryParams
>({
  store: {
    data: computed(() => store.announcements),
    total: computed(() => store.total),
    isLoading: computed(() => store.isLoading),
    error: computed(() => store.error),
    fetch: (params) => store.fetchAnnouncements(params),
  },
  filters: () => ({
    type: typeFilter.value,
    category: categoryFilter.value,
  }),
  transformParams: ({ search, filters, page, limit }) => ({
    keyword: search,
    type: filters.type === 'all' ? undefined : filters.type,
    category: filters.category === 'all' ? undefined : filters.category,
    page,
    limit,
  }),
  autoLoad: true,
})

const stats = computed(() => {
  const announcements = data.value
  return {
    total: total.value,
    system: announcements.filter((a) => a.type === 'SYSTEM').length,
    contest: announcements.filter((a) => a.type === 'CONTEST' || a.type === 'CONTEST_REMINDER')
      .length,
    submission: announcements.filter((a) => a.type === 'SUBMISSION').length,
    other: announcements.filter(
      (a) => a.type !== 'SYSTEM' && a.type !== 'CONTEST' && a.type !== 'SUBMISSION',
    ).length,
  }
})

const toolbarFilters = computed<Filter[]>(() => [
  {
    modelValue: typeFilter.value,
    placeholder: t('notifications.allTypes'),
    width: 'w-[160px]',
    options: [
      { value: 'all', label: t('notifications.allTypes') },
      ...NOTIFICATION_TYPES.map((type) => ({
        value: type,
        label: getNotificationTypeLabel(type, t),
      })),
    ],
  },
  {
    modelValue: categoryFilter.value,
    placeholder: t('notifications.allCategories'),
    width: 'w-[160px]',
    options: [
      { value: 'all', label: t('notifications.allCategories') },
      ...NOTIFICATION_CATEGORIES.map((category) => ({
        value: category,
        label: getNotificationCategoryLabel(category, t),
      })),
    ],
  },
])

function startDelete(notification: SystemAnnouncement) {
  selectedNotificationId.value = notification.id
  selectedNotificationTitle.value = notification.title
  deleteDialogOpen.value = true
}

function startEdit(notification: SystemAnnouncement) {
  notificationToEdit.value = notification
  createDialogOpen.value = true
}

async function handleDelete(id: string | number) {
  await store.deleteAnnouncement(String(id))
}

const columns: ColumnDef<SystemAnnouncement>[] = [
  {
    accessorKey: 'title',
    header: () => t('notifications.columns.title'),
    cell: ({ row }) =>
      h('div', { class: 'font-medium max-w-[300px] truncate' }, row.original.title),
  },
  {
    accessorKey: 'type',
    header: () => t('notifications.columns.type'),
    cell: ({ row }) =>
      badge({
        color: NOTIFICATION_TYPE_COLOR_MAP[row.original.type] ?? 'info',
        label: getNotificationTypeLabel(row.original.type as NotificationType, t),
      }),
  },
  {
    accessorKey: 'category',
    header: () => t('notifications.columns.category'),
    cell: ({ row }) =>
      h(
        'span',
        { class: 'font-data text-xs uppercase tracking-wider text-[var(--silver-400)]' },
        row.original.category
          ? getNotificationCategoryLabel(row.original.category as NotificationCategory, t)
          : '—',
      ),
  },
  {
    accessorKey: 'createdAt',
    header: () => t('notifications.sentAt'),
    cell: ({ row }) =>
      h(
        'span',
        { class: 'font-data text-sm text-[var(--silver-500)] tabular-nums' },
        format(new Date(row.original.createdAt), 'MMM d, yyyy HH:mm'),
      ),
  },
  {
    accessorKey: 'creator',
    header: () => t('notifications.sentBy'),
    cell: ({ row }) => {
      const creator = row.original.creator
      if (!creator) return h('span', { class: 'text-sm text-[var(--silver-400)]' }, '—')
      const initials = creator.username.slice(0, 2).toUpperCase()
      return h('div', { class: 'flex items-center gap-2' }, [
        h(
          Avatar,
          { class: 'h-7 w-7 ring-1 ring-[var(--silver-200)] dark:ring-[var(--silver-300)]' },
          {
            default: () => [
              h(AvatarImage, { src: creator.avatar ?? '' }),
              h(
                AvatarFallback,
                { class: 'text-xs font-data bg-[var(--surface-sunken)]' },
                () => initials,
              ),
            ],
          },
        ),
        h('span', { class: 'text-sm' }, creator.username),
      ])
    },
  },
  {
    id: 'actions',
    header: () => t('common.actions.label'),
    cell: ({ row }) => {
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
                        h('span', { class: 'sr-only' }, t('common.open')),
                        h(IconDotsVertical, { class: 'h-4 w-4' }),
                      ],
                    },
                  ),
              },
            ),
            h(
              DropdownMenuContent,
              { align: 'end', variant: 'terminal' },
              {
                default: () => [
                  h(
                    DropdownMenuItem,
                    {
                      variant: 'terminal',
                      class:
                        'text-[var(--accent-electric)] hover:bg-[color-mix(in_oklch,_var(--accent-electric)_10%,_var(--card))] focus:bg-[color-mix(in_oklch,_var(--accent-electric)_10%,_var(--card))] data-[highlighted]:bg-[color-mix(in_oklch,_var(--accent-electric)_10%,_var(--card))] dark:hover:bg-[color-mix(in_oklch,_var(--accent-electric)_14%,_var(--card))] dark:focus:bg-[color-mix(in_oklch,_var(--accent-electric)_14%,_var(--card))] dark:data-[highlighted]:bg-[color-mix(in_oklch,_var(--accent-electric)_14%,_var(--card))] focus:text-[var(--accent-electric)] data-[highlighted]:text-[var(--accent-electric)] [&_svg]:!text-[var(--accent-electric)]',
                      onClick: () => startEdit(row.original),
                    },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconPencil, { class: 'h-4 w-4' }),
                          t('common.edit'),
                        ]),
                    },
                  ),
                  h(
                    DropdownMenuItem,
                    {
                      variant: 'terminal_destructive',
                      onClick: () => startDelete(row.original),
                    },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconTrash, { class: 'h-4 w-4' }),
                          t('common.delete'),
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
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Terminal Header -->
    <div
      :class="[
        'border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
          {{ t('notifications.title') }}
        </h1>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)] transition-colors"
          @click="createDialogOpen = true"
        >
          <IconPlus class="h-4 w-4 mr-1.5" />
          <span class="uppercase tracking-wider">{{ t('notifications.newNotification') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('notifications.stats.total') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('notifications.stats.system') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums">{{
            stats.system
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('notifications.stats.contest') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.contest
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]"
            >{{ t('notifications.stats.submission') }}:</span
          >
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.submission
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconBell class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">{{
            t('notifications.stats.badge')
          }}</span>
        </div>
      </div>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1">
      <DataTable
        :columns="columns"
        :data="data"
        :pagination="tablePagination"
        :row-count="total"
        :loading="loading"
        @update:pagination="tablePagination = $event"
        class="terminal-table"
      >
        <template #toolbar-left>
          <DataTableToolbar
            :search-model-value="searchQuery"
            @update:search-model-value="searchQuery = $event"
            :search-placeholder="t('notifications.searchPlaceholder')"
            :filters="toolbarFilters"
            @update:filter="
              (index, value) => {
                if (index === 0) typeFilter = String(value)
                else categoryFilter = String(value)
              }
            "
            :loading="loading"
            :on-refresh="loadNotifications"
          />
        </template>
      </DataTable>

      <!-- Error state -->
      <div
        v-if="error"
        class="mt-4 flex items-center justify-between border border-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_8%,_transparent)] p-4"
      >
        <div class="flex items-center gap-3">
          <span class="font-data text-sm text-[var(--terminal-red)]">&gt; ERROR:</span>
          <span class="text-sm text-[var(--foreground)]">{{ error }}</span>
        </div>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]"
          @click="loadNotifications()"
        >
          {{ t('common.retry') }}
        </Button>
      </div>
    </div>
  </div>

  <NotificationCreateDialog
    v-model:open="createDialogOpen"
    :notification-to-edit="notificationToEdit"
    @success="loadNotifications()"
    @update:open="
      (open) => {
        if (!open) notificationToEdit = null
      }
    "
  />
  <EntityActionDialog
    v-model:open="deleteDialogOpen"
    :entity-id="selectedNotificationId"
    :entity-title="selectedNotificationTitle"
    action="delete"
    :title="t('notifications.delete.title')"
    :description="t('notifications.delete.description')"
    :confirm-label="t('common.deleteConfirm')"
    :cancel-label="t('common.cancel')"
    :success-label="t('notifications.deleteSuccess')"
    :error-label="t('notifications.deleteError')"
    :on-action="handleDelete"
    @success="loadNotifications()"
  />
</template>
