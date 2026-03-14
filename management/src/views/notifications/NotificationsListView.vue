<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ColumnDef } from '@tanstack/vue-table'
import { IconPlus, IconTrash, IconDotsVertical, IconBell } from '@tabler/icons-vue'
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
import { NotificationType, type SystemAnnouncement } from '@/api/admin/notifications'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import NotificationCreateDialog from './NotificationCreateDialog.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'

const { t } = useI18n()
const store = useNotificationsStore()

const searchQuery = ref('')
const typeFilter = ref<string>('all')
const createDialogOpen = ref(false)
const deleteDialogOpen = ref(false)
const selectedNotificationId = ref<string | null>(null)
const selectedNotificationTitle = ref<string | null>(null)

// Animation state for staggered reveal
const isLoaded = ref(false)

onMounted(() => {
  store.fetchAnnouncements()
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

// Stats for terminal ticker
const stats = computed(() => {
  const announcements = store.announcements
  return {
    total: announcements.length,
    system: announcements.filter((a) => a.type === NotificationType.SYSTEM).length,
    contest: announcements.filter((a) => a.type === NotificationType.CONTEST).length,
    submission: announcements.filter((a) => a.type === NotificationType.SUBMISSION).length,
    other: announcements.filter(
      (a) =>
        a.type !== NotificationType.SYSTEM &&
        a.type !== NotificationType.CONTEST &&
        a.type !== NotificationType.SUBMISSION,
    ).length,
  }
})

// Toolbar filters for DataTableToolbar
const toolbarFilters = computed<Filter[]>(() => [
  {
    modelValue: typeFilter.value,
    placeholder: t('notifications.allTypes'),
    width: 'w-[160px]',
    options: [
      { value: 'all', label: t('notifications.allTypes') },
      ...Object.values(NotificationType).map((type) => ({ value: type, label: type })),
    ],
  },
])

// Filtered data based on search and type filter
const filteredData = computed(() => {
  let result = store.announcements

  if (searchQuery.value) {
    const query = searchQuery.value.toLowerCase()
    result = result.filter(
      (a) =>
        a.title.toLowerCase().includes(query) || a.creator.username.toLowerCase().includes(query),
    )
  }

  if (typeFilter.value !== 'all') {
    result = result.filter((a) => a.type === typeFilter.value)
  }

  return result
})

// Type badge class mapping for terminal style
function getTypeBadgeClass(type: string): string {
  switch (type) {
    case NotificationType.SYSTEM:
      return 'terminal-badge-info' // cyan
    case NotificationType.CONTEST:
      return 'terminal-badge-success' // green
    case NotificationType.SUBMISSION:
      return 'terminal-badge-warning' // amber
    case NotificationType.COMMENT:
    case NotificationType.REPLY:
    case NotificationType.MENTION:
      return 'terminal-badge-info' // cyan
    default:
      return 'terminal-badge-info'
  }
}

function startDelete(notification: SystemAnnouncement) {
  selectedNotificationId.value = notification.id
  selectedNotificationTitle.value = notification.title
  deleteDialogOpen.value = true
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
      h(
        'span',
        {
          class: `terminal-badge ${getTypeBadgeClass(row.original.type)}`,
        },
        row.original.type,
      ),
  },
  {
    accessorKey: 'created_at',
    header: () => t('notifications.sentAt'),
    cell: ({ row }) =>
      h(
        'span',
        { class: 'font-data text-sm text-[var(--silver-500)] tabular-nums' },
        format(new Date(row.original.created_at), 'MMM d, yyyy HH:mm'),
      ),
  },
  {
    accessorKey: 'creator',
    header: () => t('notifications.sentBy'),
    cell: ({ row }) => {
      const creator = row.original.creator
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
    header: () => t('common.actions'),
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
              { align: 'end' },
              {
                default: () => [
                  h(
                    DropdownMenuItem,
                    {
                      class: 'text-[var(--terminal-red)] focus:text-[var(--terminal-red)]',
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
            <span class="terminal-prompt text-base">notifications</span>
            <span class="terminal-cursor" />
          </div>
          <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
            {{ t('notifications.title') }}
          </h1>
        </div>
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
          <span class="terminal-label text-[var(--silver-500)]">total:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">system:</span>
          <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums">{{
            stats.system
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">contest:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.contest
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">submission:</span>
          <span class="font-data text-sm text-[var(--terminal-amber)] tabular-nums">{{
            stats.submission
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconBell class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">system announcements</span>
        </div>
      </div>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1 py-4">
      <DataTable
        :columns="columns"
        :data="filteredData"
        :loading="store.isLoading"
        class="terminal-table"
      >
        <template #toolbar-left>
          <DataTableToolbar
            :search-model-value="searchQuery"
            @update:search-model-value="searchQuery = $event"
            :search-placeholder="t('notifications.searchPlaceholder')"
            :filters="toolbarFilters"
            @update:filter="(index, value) => (typeFilter = String(value))"
            :loading="store.isLoading"
            :on-refresh="() => store.fetchAnnouncements()"
          />
        </template>
      </DataTable>

      <!-- Error state - Terminal Style -->
      <div
        v-if="store.error"
        class="mt-4 flex items-center justify-between border border-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.08)] p-4"
      >
        <div class="flex items-center gap-3">
          <span class="font-data text-sm text-[var(--terminal-red)]">&gt; ERROR:</span>
          <span class="text-sm text-[var(--foreground)]">{{ store.error }}</span>
        </div>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[oklch(0.6_0.2_25/0.1)]"
          @click="store.fetchAnnouncements()"
        >
          {{ t('common.retry') }}
        </Button>
      </div>
    </div>
  </div>

  <NotificationCreateDialog v-model:open="createDialogOpen" @success="store.fetchAnnouncements()" />
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
    @success="store.fetchAnnouncements()"
  />
</template>
