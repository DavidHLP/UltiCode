<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import { watchDebounced } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import {
  IconBan,
  IconCheck,
  IconCircleCheckFilled,
  IconCircleXFilled,
  IconDotsVertical,
  IconLoader,
  IconPlus,
  IconRefresh,
  IconShield,
  IconUser,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Checkbox } from '@/components/ui/checkbox'
import { Separator } from '@/components/ui/separator'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { useUsersStore } from '@/stores/admin/users'
import { useAuthStore } from '@/stores/admin/auth'
import type { User } from '@/api/admin/users'

import DataTable from '@/components/table/DataTable.vue'
import UserEditDialog from './UserEditDialog.vue'
import UserCreateDialog from './UserCreateDialog.vue'
import UserDetailDrawer from './UserDetailDrawer.vue'

const usersStore = useUsersStore()
const authStore = useAuthStore()

const searchQuery = ref('')
const roleFilter = ref<string>('all')
const statusFilter = ref<string>('all')
const tablePagination = ref({ pageIndex: 0, pageSize: 10 })
const selectedUserId = ref<string | null>(null)
const editDialogOpen = ref(false)
const createDialogOpen = ref(false)
const detailDrawerOpen = ref(false)

const banDialogOpen = ref(false)
const banReason = ref('')
const userToBanId = ref<string | null>(null)

const bulkActionLoading = ref(false)
const selectedRows = ref<User[]>([])

const canCreateUser = computed(() => authStore.hasPermission('CREATE', 'USER'))
const canModerateUser = computed(() => authStore.hasPermission('MODERATE', 'USER'))
const canDeleteUser = computed(() => authStore.hasPermission('DELETE', 'USER'))

onMounted(() => loadUsers())

async function loadUsers() {
  await usersStore.fetchUsers({
    search: searchQuery.value || undefined,
    role: roleFilter.value === 'all' ? undefined : roleFilter.value,
    is_active:
      statusFilter.value === 'active'
        ? true
        : statusFilter.value === 'inactive'
          ? false
          : undefined,
    is_banned: statusFilter.value === 'banned' ? true : undefined,
    page: tablePagination.value.pageIndex + 1,
    limit: tablePagination.value.pageSize,
  })
}

// Watchers for automatic queries
watchDebounced(
  searchQuery,
  () => {
    tablePagination.value.pageIndex = 0
    loadUsers()
  },
  { debounce: 500 },
)

watch([roleFilter, statusFilter], () => {
  if (tablePagination.value.pageIndex === 0) {
    loadUsers()
  } else {
    tablePagination.value.pageIndex = 0
  }
})

watch(
  () => tablePagination.value,
  () => loadUsers(),
  { deep: true },
)

function viewUser(id: string) {
  selectedUserId.value = id
  detailDrawerOpen.value = true
}

function editUser(id: string) {
  selectedUserId.value = id
  editDialogOpen.value = true
}

function startBanUser(id: string) {
  userToBanId.value = id
  banReason.value = ''
  banDialogOpen.value = true
}

async function confirmBan() {
  if (!userToBanId.value || !banReason.value) return

  try {
    await usersStore.banUser(userToBanId.value, banReason.value)
    toast.success('User has been banned')
    banDialogOpen.value = false
    await loadUsers()
  } catch {
    toast.error('Failed to ban user')
  }
}

async function unbanUser(id: string) {
  try {
    await usersStore.unbanUser(id)
    toast.success('User has been unbanned')
    await loadUsers()
  } catch {
    toast.error('Failed to unban user')
  }
}

async function handleBulkBan() {
  if (selectedRows.value.length === 0) return
  const ids = selectedRows.value.map((r) => r.id)
  const reason = prompt('Enter reason for bulk ban:')
  if (reason === null) return

  bulkActionLoading.value = true
  try {
    await usersStore.bulkBan(ids, reason)
    toast.success(`Successfully banned ${ids.length} users`)
    await loadUsers()
    selectedRows.value = []
  } catch {
    toast.error('Failed to bulk ban users')
  } finally {
    bulkActionLoading.value = false
  }
}

async function handleBulkUnban() {
  if (selectedRows.value.length === 0) return
  const ids = selectedRows.value.map((r) => r.id)

  bulkActionLoading.value = true
  try {
    await usersStore.bulkUnban(ids)
    toast.success(`Successfully unbanned ${ids.length} users`)
    await loadUsers()
    selectedRows.value = []
  } catch {
    toast.error('Failed to bulk unban users')
  } finally {
    bulkActionLoading.value = false
  }
}

async function handleBulkDelete() {
  if (selectedRows.value.length === 0) return
  const ids = selectedRows.value.map((r) => r.id)
  if (!confirm(`Are you sure you want to delete ${ids.length} users? This action is IRREVERSIBLE.`))
    return

  bulkActionLoading.value = true
  try {
    await usersStore.bulkDelete(ids)
    toast.success(`Successfully deleted ${ids.length} users`)
    await loadUsers()
    selectedRows.value = []
  } catch {
    toast.error('Failed to bulk delete users')
  } finally {
    bulkActionLoading.value = false
  }
}

function getRoleBadgeVariant(role: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (role) {
    case 'SUPER_ADMIN':
      return 'destructive'
    case 'ADMIN':
      return 'default'
    case 'MODERATOR':
      return 'secondary'
    default:
      return 'outline'
  }
}

function getStatusIcon(user: User) {
  if (user.is_banned) {
    return h(IconCircleXFilled, { class: 'h-4 w-4 text-destructive' })
  }
  if (user.is_active) {
    return h(IconCircleCheckFilled, { class: 'h-4 w-4 text-emerald-500' })
  }
  return h(IconLoader, { class: 'h-4 w-4 animate-spin text-muted-foreground' })
}

function getStatusBadge(user: User) {
  if (user.is_banned) {
    return h(Badge, { variant: 'destructive' }, () => 'Banned')
  }
  if (user.is_active) {
    return h(Badge, { variant: 'default' }, () => 'Active')
  }
  return h(Badge, { variant: 'secondary' }, () => 'Inactive')
}

const columns: ColumnDef<User>[] = [
  {
    id: 'select',
    header: ({ table }) =>
      h(Checkbox, {
        modelValue:
          table.getIsAllPageRowsSelected() ||
          (table.getIsSomePageRowsSelected() && 'indeterminate'),
        'onUpdate:modelValue': (value: boolean | 'indeterminate') =>
          table.toggleAllPageRowsSelected(!!value),
        'aria-label': 'Select all',
      }),
    cell: ({ row }) =>
      h(Checkbox, {
        modelValue: row.getIsSelected(),
        'onUpdate:modelValue': (value: boolean | 'indeterminate') => row.toggleSelected(!!value),
        'aria-label': 'Select row',
      }),
    enableSorting: false,
    enableHiding: false,
  },
  {
    accessorKey: 'username',
    header: 'User',
    cell: ({ row }) => {
      const user = row.original
      const initials =
        user.name
          ?.split(' ')
          .map((n: string) => n[0])
          .join('')
          .toUpperCase()
          .slice(0, 2) || user.username.slice(0, 2).toUpperCase()

      const displayName = user.name || user.username
      const displayEmail = user.email ?? user.username

      return h('div', { class: 'flex items-center gap-3' }, [
        h(
          Avatar,
          { class: 'h-9 w-9' },
          {
            default: () => [
              h(AvatarImage, { src: user.avatar ?? '' }),
              h(AvatarFallback, {}, () => initials),
            ],
          },
        ),
        h('div', { class: 'flex flex-col' }, [
          h('span', { class: 'font-medium text-sm' }, displayName),
          h('span', { class: 'text-muted-foreground text-xs' }, displayEmail),
        ]),
      ])
    },
  },
  {
    accessorKey: 'role',
    header: 'Role',
    cell: ({ row }) => {
      const role = row.getValue('role') as string
      const icon = role === 'USER' ? IconUser : IconShield
      return h('div', { class: 'flex items-center gap-2' }, [
        h(icon, { class: 'h-4 w-4 text-muted-foreground' }),
        h(Badge, { variant: getRoleBadgeVariant(role) }, () => role.replace('_', ' ')),
      ])
    },
  },
  {
    accessorKey: 'status',
    header: 'Status',
    cell: ({ row }) => {
      const user = row.original
      return h('div', { class: 'flex items-center gap-2' }, [
        getStatusIcon(user),
        getStatusBadge(user),
      ])
    },
  },
  {
    accessorKey: 'joined_at',
    header: 'Joined',
    cell: ({ row }) => {
      const date = new Date(row.getValue('joined_at') as string)
      return h('span', { class: 'text-muted-foreground text-sm' }, date.toLocaleDateString())
    },
  },
  {
    accessorKey: 'last_login_at',
    header: 'Last Login',
    cell: ({ row }) => {
      const lastLogin = row.getValue('last_login_at') as string | undefined
      if (!lastLogin) {
        return h('span', { class: 'text-muted-foreground text-sm' }, 'Never')
      }
      const date = new Date(lastLogin)
      return h('span', { class: 'text-muted-foreground text-sm' }, date.toLocaleDateString())
    },
  },
  {
    id: 'actions',
    header: 'Actions',
    cell: ({ row }) => {
      const user = row.original
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
                    { onClick: () => viewUser(user.id) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconUser, { class: 'h-4 w-4' }),
                          'View Details',
                        ]),
                    },
                  ),
                  h(
                    DropdownMenuItem,
                    { onClick: () => editUser(user.id) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconShield, { class: 'h-4 w-4' }),
                          'Edit Profile',
                        ]),
                    },
                  ),
                  h(DropdownMenuSeparator, {}),
                  canModerateUser.value
                    ? user.is_banned
                      ? h(
                          DropdownMenuItem,
                          { onClick: () => unbanUser(user.id) },
                          {
                            default: () =>
                              h('div', { class: 'flex items-center gap-2 text-emerald-600' }, [
                                h(IconCheck, { class: 'h-4 w-4' }),
                                'Unban User',
                              ]),
                          },
                        )
                      : h(
                          DropdownMenuItem,
                          { onClick: () => startBanUser(user.id) },
                          {
                            default: () =>
                              h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                                h(IconBan, { class: 'h-4 w-4' }),
                                'Ban User',
                              ]),
                          },
                        )
                    : null,
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
    <div
      v-if="selectedRows.length > 0"
      class="flex items-center justify-between rounded-lg border border-primary/20 bg-primary/5 p-2 px-4 animate-in fade-in slide-in-from-top-2"
    >
      <div class="flex items-center gap-3">
        <span class="text-sm font-medium">{{ selectedRows.length }} users selected</span>
        <Separator orientation="vertical" class="h-4" />
        <div class="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            class="h-8 text-xs"
            @click="handleBulkBan"
            :disabled="bulkActionLoading"
          >
            <IconBan class="h-3.5 w-3.5 mr-1" />
            Bulk Ban
          </Button>
          <Button
            variant="outline"
            size="sm"
            class="h-8 text-xs"
            @click="handleBulkUnban"
            :disabled="bulkActionLoading"
          >
            <IconCheck class="h-3.5 w-3.5 mr-1" />
            Bulk Unban
          </Button>
          <Button
            v-if="canDeleteUser"
            variant="destructive"
            size="sm"
            class="h-8 text-xs"
            @click="handleBulkDelete"
            :disabled="bulkActionLoading"
          >
            <IconCircleXFilled class="h-3.5 w-3.5 mr-1" />
            Bulk Delete
          </Button>
        </div>
      </div>
      <Button variant="ghost" size="sm" class="h-8 text-xs" @click="selectedRows = []">
        Clear Selection
      </Button>
    </div>

    <DataTable
      :columns="columns"
      :data="usersStore.users"
      :pagination="tablePagination"
      :row-count="usersStore.total"
      :loading="usersStore.loading"
      v-model:selected-rows="selectedRows"
      @update:pagination="tablePagination = $event"
    >
      <template #toolbar-left>
        <Input
          v-model="searchQuery"
          placeholder="Search users..."
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
        <Select v-model="roleFilter">
          <SelectTrigger class="w-[160px]">
            <SelectValue placeholder="All Roles" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Roles</SelectItem>
            <SelectItem value="USER">User</SelectItem>
            <SelectItem value="MODERATOR">Moderator</SelectItem>
            <SelectItem value="ADMIN">Admin</SelectItem>
            <SelectItem value="SUPER_ADMIN">Super Admin</SelectItem>
          </SelectContent>
        </Select>
        <Select v-model="statusFilter">
          <SelectTrigger class="w-[140px]">
            <SelectValue placeholder="All Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Status</SelectItem>
            <SelectItem value="active">Active</SelectItem>
            <SelectItem value="inactive">Inactive</SelectItem>
            <SelectItem value="banned">Banned</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="outline" size="icon" @click="loadUsers()" title="Refresh">
          <IconRefresh class="h-4 w-4" :class="{ 'animate-spin': usersStore.loading }" />
        </Button>
      </template>

      <template #extra-actions>
        <Button v-if="canCreateUser" variant="outline" size="sm" @click="createDialogOpen = true">
          <IconPlus />
          <span class="hidden lg:inline">Add User</span>
        </Button>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="usersStore.error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ usersStore.error }}</span>
      <Button variant="outline" size="sm" @click="loadUsers()">Retry</Button>
    </div>
  </div>

  <UserEditDialog v-model:open="editDialogOpen" :user-id="selectedUserId" @success="loadUsers" />
  <UserCreateDialog v-model:open="createDialogOpen" @success="loadUsers" />
  <UserDetailDrawer
    v-model:open="detailDrawerOpen"
    :user-id="selectedUserId"
    @success="loadUsers"
  />

  <Dialog v-model:open="banDialogOpen">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>Ban User</DialogTitle>
        <DialogDescription> Please provide a reason for banning this user. </DialogDescription>
      </DialogHeader>
      <div class="grid gap-4 py-4">
        <div class="grid gap-2">
          <Label for="reason">Reason</Label>
          <Textarea id="reason" v-model="banReason" placeholder="Violation of terms..." />
        </div>
      </div>
      <DialogFooter>
        <Button variant="outline" @click="banDialogOpen = false">Cancel</Button>
        <Button variant="destructive" @click="confirmBan" :disabled="!banReason">
          Confirm Ban
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>