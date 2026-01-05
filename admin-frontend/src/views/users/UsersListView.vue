<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import { useRouter } from 'vue-router'
import { watchDebounced } from '@vueuse/core'
import type { ColumnDef } from '@tanstack/vue-table'
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
  IconUsers,
} from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Checkbox } from '@/components/ui/checkbox'
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useUsersStore } from '@/stores/admin/users'
import { useAuthStore } from '@/stores/admin/auth'
import type { User } from '@/api/admin/users'

import DataTable from '@/components/table/DataTable.vue'
import UserEditDialog from './UserEditDialog.vue'
import UserCreateDialog from './UserCreateDialog.vue'

const router = useRouter()
const usersStore = useUsersStore()
const authStore = useAuthStore()

const searchQuery = ref('')
const roleFilter = ref<string>('all')
const statusFilter = ref<string>('all')
const tablePagination = ref({ pageIndex: 0, pageSize: 20 })
const selectedUserId = ref<string | null>(null)
const editDialogOpen = ref(false)
const createDialogOpen = ref(false)

const canCreateUser = computed(() => authStore.hasPermission('CREATE', 'USER'))
const canModerateUser = computed(() => authStore.hasPermission('MODERATE', 'USER'))

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
  tablePagination.value.pageIndex = 0
  loadUsers()
})

watch(
  () => tablePagination.value,
  () => loadUsers(),
  { deep: true },
)

function viewUser(id: string) {
  router.push({ name: 'user-detail', params: { id } })
}

function editUser(id: string) {
  selectedUserId.value = id
  editDialogOpen.value = true
}

async function banUser(id: string) {
  const reason = prompt('Enter ban reason:')
  if (!reason) return

  try {
    await usersStore.banUser(id, reason)
    await loadUsers()
  } catch {
    alert('Failed to ban user')
  }
}

async function unbanUser(id: string) {
  try {
    await usersStore.unbanUser(id)
    await loadUsers()
  } catch {
    alert('Failed to unban user')
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
                          'Edit Permissions',
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
                          { onClick: () => banUser(user.id) },
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
  <Tabs default-value="all-users" class="w-full flex-col justify-start gap-6">
    <div class="flex items-center justify-between px-4 lg:px-6">
      <Label for="view-selector" class="sr-only">View</Label>
      <Select default-value="all-users">
        <SelectTrigger id="view-selector" class="flex w-fit @4xl/main:hidden" size="sm">
          <SelectValue placeholder="Select a view" />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value="all-users">All Users</SelectItem>
          <SelectItem value="active">Active</SelectItem>
          <SelectItem value="inactive">Inactive</SelectItem>
          <SelectItem value="banned">Banned</SelectItem>
        </SelectContent>
      </Select>
      <TabsList
        class="**:data-[slot=badge]:bg-muted-foreground/30 hidden **:data-[slot=badge]:size-5 **:data-[slot=badge]:rounded-full **:data-[slot=badge]:px-1 @4xl/main:flex"
      >
        <TabsTrigger value="all-users">
          All Users <Badge variant="secondary">{{ usersStore.users.length }}</Badge>
        </TabsTrigger>
        <TabsTrigger value="active">Active</TabsTrigger>
        <TabsTrigger value="inactive">Inactive</TabsTrigger>
        <TabsTrigger value="banned">Banned</TabsTrigger>
      </TabsList>
    </div>

    <TabsContent value="all-users" class="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
      <DataTable
        :columns="columns"
        :data="usersStore.users"
        :pagination="tablePagination"
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
            </SelectContent>
          </Select>
          <Button variant="outline" size="icon" @click="loadUsers()" title="Refresh">
            <IconRefresh class="h-4 w-4" :class="{ 'animate-spin': usersStore.loading }" />
          </Button>
        </template>

        <template #extra-actions>
          <Button
            v-if="canCreateUser"
            variant="outline"
            size="sm"
            @click="createDialogOpen = true"
          >
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

      <!-- Empty state -->
      <div
        v-if="!usersStore.loading && usersStore.users.length === 0"
        class="flex flex-col items-center justify-center py-12 text-center"
      >
        <div
          class="flex size-16 items-center justify-center rounded-full bg-muted text-muted-foreground"
        >
          <IconUsers class="h-8 w-8" />
        </div>
        <h3 class="mt-4 text-lg font-semibold">No users found</h3>
        <p class="text-muted-foreground">
          {{
            searchQuery || roleFilter !== 'all' || statusFilter !== 'all'
              ? 'Try adjusting your filters'
              : 'Get started by creating a new user'
          }}
        </p>
        <Button v-if="canCreateUser" class="mt-4" @click="createDialogOpen = true">
          <IconPlus class="mr-2 h-4 w-4" />
          Create User
        </Button>
      </div>
    </TabsContent>

    <TabsContent value="active" class="flex flex-col px-4 lg:px-6">
      <div class="aspect-video w-full flex-1 rounded-lg border border-dashed" />
    </TabsContent>

    <TabsContent value="inactive" class="flex flex-col px-4 lg:px-6">
      <div class="aspect-video w-full flex-1 rounded-lg border border-dashed" />
    </TabsContent>

    <TabsContent value="banned" class="flex flex-col px-4 lg:px-6">
      <div class="aspect-video w-full flex-1 rounded-lg border border-dashed" />
    </TabsContent>

    <UserEditDialog v-model:open="editDialogOpen" :user-id="selectedUserId" @success="loadUsers" />
    <UserCreateDialog v-model:open="createDialogOpen" @success="loadUsers" />
  </Tabs>
</template>
