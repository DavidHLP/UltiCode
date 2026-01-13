<script setup lang="ts">
import { ref, onMounted, computed, h, watch } from 'vue'
import { watchDebounced } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import type { ColumnDef } from '@tanstack/vue-table'
import { toast } from 'vue-sonner'
import {
  IconBan,
  IconCheck,
  IconCircleCheckFilled,
  IconCircleXFilled,
  IconDotsVertical,
  IconLoader,
  IconLock,
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useUsersStore } from '@/stores/admin/users'
import { useAuthStore } from '@/stores/admin/auth'
import type { User } from '@/api/admin/users'

import DataTable from '@/components/table/DataTable.vue'
import UserEditDialog from './UserEditDialog.vue'
import UserCreateDialog from './UserCreateDialog.vue'
import UserDetailDrawer from './UserDetailDrawer.vue'
import UserResetPasswordDialog from './UserResetPasswordDialog.vue'
import UserBanDialog from './UserBanDialog.vue'

const { t } = useI18n()
const usersStore = useUsersStore()
const authStore = useAuthStore()

const searchQuery = ref('')
const roleFilter = ref<string>('all')
const statusFilter = ref<string>('all')
const tablePagination = ref({ pageIndex: 0, pageSize: 10 })
const selectedUserId = ref<string | null>(null)
const selectedUsername = ref<string | null>(null)

const editDialogOpen = ref(false)
const createDialogOpen = ref(false)
const detailDrawerOpen = ref(false)
const resetPasswordDialogOpen = ref(false)
const banDialogOpen = ref(false)

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

function viewUser(user: User) {
  selectedUserId.value = user.id
  detailDrawerOpen.value = true
}

function editUser(user: User) {
  selectedUserId.value = user.id
  editDialogOpen.value = true
}

function resetPassword(user: User) {
  selectedUserId.value = user.id
  selectedUsername.value = user.username
  resetPasswordDialogOpen.value = true
}

function startBanUser(user: User) {
  selectedUserId.value = user.id
  selectedUsername.value = user.username
  banDialogOpen.value = true
}

async function unbanUser(id: string) {
  try {
    await usersStore.unbanUser(id)
    await loadUsers()
  } catch {
    toast.error(t('users.toast.unbanFailed'))
  }
}

async function handleBulkBan() {
  if (selectedRows.value.length === 0) return
  const ids = selectedRows.value.map((r) => r.id)
  const reason = prompt(t('users.banReasonPrompt'))
  if (reason === null) return

  bulkActionLoading.value = true
  try {
    await usersStore.bulkBan(ids, reason)
    await loadUsers()
    selectedRows.value = []
  } catch {
    toast.error(t('users.toast.bulkBanFailed'))
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
    await loadUsers()
    selectedRows.value = []
  } catch {
    toast.error(t('users.toast.bulkUnbanFailed'))
  } finally {
    bulkActionLoading.value = false
  }
}

async function handleBulkDelete() {
  if (selectedRows.value.length === 0) return
  const ids = selectedRows.value.map((r) => r.id)
  const count = ids.length
  if (!confirm(t('users.deleteConfirm', { count })))
    return

  bulkActionLoading.value = true
  try {
    await usersStore.bulkDelete(ids)
    await loadUsers()
    selectedRows.value = []
  } catch {
    toast.error(t('users.toast.bulkDeleteFailed'))
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
    return h(Badge, { variant: 'destructive' }, () => t('users.status.banned'))
  }
  if (user.is_active) {
    return h(Badge, { variant: 'default' }, () => t('users.status.active'))
  }
  return h(Badge, { variant: 'secondary' }, () => t('users.status.inactive'))
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
    header: () => t('users.columns.user'),
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
    header: () => t('users.columns.role'),
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
    header: () => t('common.status'),
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
    header: () => t('users.columns.joined'),
    cell: ({ row }) => {
      const date = new Date(row.getValue('joined_at') as string)
      return h('span', { class: 'text-muted-foreground text-sm' }, date.toLocaleDateString())
    },
  },
  {
    accessorKey: 'last_login_at',
    header: () => t('users.columns.lastLogin'),
    cell: ({ row }) => {
      const lastLogin = row.getValue('last_login_at') as string | undefined
      if (!lastLogin) {
        return h('span', { class: 'text-muted-foreground text-sm' }, t('common.never'))
      }
      const date = new Date(lastLogin)
      return h('span', { class: 'text-muted-foreground text-sm' }, date.toLocaleDateString())
    },
  },
  {
    id: 'actions',
    header: () => t('common.actions'),
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
                    { onClick: () => viewUser(user) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconUser, { class: 'h-4 w-4' }),
                          t('users.actions.viewDetails'),
                        ]),
                    },
                  ),
                  h(
                    DropdownMenuItem,
                    { onClick: () => editUser(user) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconShield, { class: 'h-4 w-4' }),
                          t('users.actions.editProfile'),
                        ]),
                    },
                  ),
                  h(
                    DropdownMenuItem,
                    { onClick: () => resetPassword(user) },
                    {
                      default: () =>
                        h('div', { class: 'flex items-center gap-2' }, [
                          h(IconLock, { class: 'h-4 w-4' }),
                          t('users.actions.resetPassword'),
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
                                t('users.actions.unbanUser'),
                              ]),
                          },
                        )
                      : h(
                          DropdownMenuItem,
                          { onClick: () => startBanUser(user) },
                          {
                            default: () =>
                              h('div', { class: 'flex items-center gap-2 text-destructive' }, [
                                h(IconBan, { class: 'h-4 w-4' }),
                                t('users.actions.banUser'),
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
        <span class="text-sm font-medium">{{ t('users.selected', { count: selectedRows.length }) }}</span>
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
            {{ t('users.bulkActions.bulkBan') }}
          </Button>
          <Button
            variant="outline"
            size="sm"
            class="h-8 text-xs"
            @click="handleBulkUnban"
            :disabled="bulkActionLoading"
          >
            <IconCheck class="h-3.5 w-3.5 mr-1" />
            {{ t('users.bulkActions.bulkUnban') }}
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
            {{ t('users.bulkActions.bulkDelete') }}
          </Button>
        </div>
      </div>
      <Button variant="ghost" size="sm" class="h-8 text-xs" @click="selectedRows = []">
        {{ t('users.clearSelection') }}
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
        <Input v-model="searchQuery" :placeholder="t('users.searchPlaceholder')" class="min-w-[200px] w-[260px]">
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
            <SelectValue :placeholder="t('users.filters.allRoles')" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{{ t('users.filters.allRoles') }}</SelectItem>
            <SelectItem value="USER">{{ t('users.filters.role.USER') }}</SelectItem>
            <SelectItem value="MODERATOR">{{ t('users.filters.role.MODERATOR') }}</SelectItem>
            <SelectItem value="ADMIN">{{ t('users.filters.role.ADMIN') }}</SelectItem>
            <SelectItem value="SUPER_ADMIN">{{ t('users.filters.role.SUPER_ADMIN') }}</SelectItem>
          </SelectContent>
        </Select>
        <Select v-model="statusFilter">
          <SelectTrigger class="w-[140px]">
            <SelectValue :placeholder="t('users.filters.allStatus')" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{{ t('users.filters.allStatus') }}</SelectItem>
            <SelectItem value="active">{{ t('users.filters.status.active') }}</SelectItem>
            <SelectItem value="inactive">{{ t('users.filters.status.inactive') }}</SelectItem>
            <SelectItem value="banned">{{ t('users.filters.status.banned') }}</SelectItem>
          </SelectContent>
        </Select>
        <Button variant="outline" size="icon" @click="loadUsers()" :title="t('common.refresh')">
          <IconRefresh class="h-4 w-4" :class="{ 'animate-spin': usersStore.loading }" />
        </Button>
      </template>

      <template #extra-actions>
        <Button v-if="canCreateUser" variant="outline" size="sm" @click="createDialogOpen = true">
          <IconPlus />
          <span class="hidden lg:inline">{{ t('users.addUser') }}</span>
        </Button>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="usersStore.error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ usersStore.error }}</span>
      <Button variant="outline" size="sm" @click="loadUsers()">{{ t('common.retry') }}</Button>
    </div>
  </div>

  <UserEditDialog v-model:open="editDialogOpen" :user-id="selectedUserId" @success="loadUsers" />
  <UserCreateDialog v-model:open="createDialogOpen" @success="loadUsers" />
  <UserDetailDrawer
    v-model:open="detailDrawerOpen"
    :user-id="selectedUserId"
    @success="loadUsers"
  />
  <UserResetPasswordDialog
    v-model:open="resetPasswordDialogOpen"
    :user-id="selectedUserId"
    :username="selectedUsername"
  />
  <UserBanDialog
    v-model:open="banDialogOpen"
    :user-id="selectedUserId"
    :username="selectedUsername"
    @success="loadUsers"
  />
</template>
