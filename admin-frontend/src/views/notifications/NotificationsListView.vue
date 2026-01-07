<script setup lang="ts">
import { onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import { IconPlus, IconTrash, IconDotsVertical } from '@tabler/icons-vue'
import { format } from 'date-fns'

import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useNotificationsStore } from '@/stores/admin/notifications'
import type { SystemAnnouncement } from '@/api/admin/notifications'

import DataTable from '@/components/table/DataTable.vue'

const router = useRouter()
const store = useNotificationsStore()

const columns: ColumnDef<SystemAnnouncement>[] = [
  {
    accessorKey: 'title',
    header: 'Title',
    cell: ({ row }) => h('div', { class: 'font-medium' }, row.original.title),
  },
  {
    accessorKey: 'type',
    header: 'Type',
    cell: ({ row }) => h(Badge, { variant: 'outline' }, () => row.original.type),
  },
  {
    accessorKey: 'created_at',
    header: 'Sent At',
    cell: ({ row }) => format(new Date(row.original.created_at), 'MMM d, yyyy HH:mm'),
  },
  {
    accessorKey: 'creator',
    header: 'Sent By',
    cell: ({ row }) => h('div', { class: 'flex items-center gap-2' }, [
      h('span', row.original.creator.username)
    ]),
  },
  {
    id: 'actions',
    cell: ({ row }) => {
      return h(DropdownMenu, [
        h(DropdownMenuTrigger, { asChild: true }, [
          h(Button, { variant: 'ghost', class: 'h-8 w-8 p-0' }, [
            h(IconDotsVertical, { class: 'h-4 w-4' })
          ])
        ]),
        h(DropdownMenuContent, { align: 'end' }, [
          h(DropdownMenuItem, {
            class: 'text-destructive',
            onClick: () => handleDelete(row.original.id)
          }, [
            h(IconTrash, { class: 'mr-2 h-4 w-4' }),
            'Delete'
          ]),
        ]),
      ])
    },
  },
]

const handleDelete = async (id: string) => {
  if (!confirm('Are you sure you want to delete this announcement? It will be removed for all users.')) return
  try {
    await store.deleteAnnouncement(id)
    toast.success('Announcement deleted')
  } catch {
    toast.error('Failed to delete announcement')
  }
}

onMounted(() => {
  store.fetchAnnouncements()
})
</script>

<template>
  <div class="flex flex-col gap-4 p-4 lg:p-6 h-[calc(100vh-theme(spacing.16))]">
    <div class="flex items-center justify-between">
      <div class="space-y-1">
        <h2 class="text-2xl font-semibold tracking-tight">Notifications</h2>
        <p class="text-sm text-muted-foreground">
          Manage system-wide announcements and notifications.
        </p>
      </div>
      <Button @click="router.push('/notifications/create')">
        <IconPlus class="mr-2 h-4 w-4" />
        New Notification
      </Button>
    </div>

    <div class="flex-1 overflow-hidden rounded-md border bg-card">
      <DataTable
        :columns="columns"
        :data="store.announcements"
        :loading="store.isLoading"
      />
    </div>
  </div>
</template>
