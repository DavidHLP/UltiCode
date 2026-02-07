<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ColumnDef } from '@tanstack/vue-table'
import {
  IconPlus,
  IconTrash,
  IconDotsVertical,
  IconRefresh,
  IconCircleXFilled,
} from '@tabler/icons-vue'
import { format } from 'date-fns'

import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
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
import { useNotificationsStore } from '@/stores/admin/notifications'
import { NotificationType, type SystemAnnouncement } from '@/api/admin/notifications'

import DataTable from '@/components/table/DataTable.vue'
import NotificationCreateDialog from './NotificationCreateDialog.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import { getNotificationTypeBadgeVariant } from '@/lib/ui/status'

const { t } = useI18n()
const store = useNotificationsStore()

const searchQuery = ref('')
const typeFilter = ref<string>('all')
const createDialogOpen = ref(false)
const deleteDialogOpen = ref(false)
const selectedNotificationId = ref<string | null>(null)
const selectedNotificationTitle = ref<string | null>(null)

function startDelete(notification: SystemAnnouncement) {
  selectedNotificationId.value = notification.id
  selectedNotificationTitle.value = notification.title
  deleteDialogOpen.value = true
}

async function handleDelete(id: string | number) {
  await store.deleteAnnouncement(String(id))
}

function getTypeBadgeVariant(type: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  return getNotificationTypeBadgeVariant(type)
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
      h(Badge, { variant: getTypeBadgeVariant(row.original.type) }, () => row.original.type),
  },
  {
    accessorKey: 'created_at',
    header: () => t('notifications.sentAt'),
    cell: ({ row }) =>
      h(
        'span',
        { class: 'text-muted-foreground text-sm' },
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
          { class: 'h-7 w-7' },
          {
            default: () => [
              h(AvatarImage, { src: creator.avatar ?? '' }),
              h(AvatarFallback, { class: 'text-xs' }, () => initials),
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
                      class: 'text-destructive',
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

onMounted(() => {
  store.fetchAnnouncements()
})
</script>

<template>
  <div class="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
    <DataTable :columns="columns" :data="store.announcements" :loading="store.isLoading">
      <template #toolbar-left>
        <Input
          v-model="searchQuery"
          :placeholder="t('notifications.searchPlaceholder')"
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
        <Select v-model="typeFilter">
          <SelectTrigger class="w-[160px]">
            <SelectValue :placeholder="t('notifications.allTypes')" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{{ t('notifications.allTypes') }}</SelectItem>
            <SelectItem v-for="type in NotificationType" :key="type" :value="type">
              {{ type }}
            </SelectItem>
          </SelectContent>
        </Select>
        <Button
          variant="outline"
          size="icon"
          @click="store.fetchAnnouncements()"
          :title="t('notifications.refresh')"
        >
          <IconRefresh class="h-4 w-4" :class="{ 'animate-spin': store.isLoading }" />
        </Button>
      </template>

      <template #extra-actions>
        <Button variant="outline" size="sm" @click="createDialogOpen = true">
          <IconPlus />
          <span class="hidden lg:inline">{{ t('notifications.newNotification') }}</span>
        </Button>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="store.error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ store.error }}</span>
      <Button variant="outline" size="sm" @click="store.fetchAnnouncements()">{{
        t('common.retry')
      }}</Button>
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
