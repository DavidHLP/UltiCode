<script setup lang="ts">
import { userPermissionRules } from '@/composables/domainPermissionMaps'
import { usePermissionMap } from '@/composables/usePermissionMap'
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import {
  Ban as IconBan,
  CircleX as IconCircleXFilled,
  Plus as IconPlus,
  Users as IconUsers,
  Trash as IconTrash,
} from '@lucide/vue'

import { Button } from '@ulticode/design-system/ui/button'
import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@ulticode/design-system/ui/alert-dialog'
import { Input } from '@ulticode/design-system/ui/input'
import { Label } from '@ulticode/design-system/ui/label'
import { AlertTriangle as IconAlertTriangle, Loader as IconLoader } from '@lucide/vue'

import { useUsersStore } from '@/stores/admin/users'
import type { User } from '@/api/admin/users'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import UserEditDialog from './UserEditDialog.vue'
import UserCreateDialog from './UserCreateDialog.vue'
import UserDetailDrawer from './UserDetailDrawer.vue'
import UserResetPasswordDialog from './UserResetPasswordDialog.vue'
import { useRemoteTable } from '@/composables/useRemoteTable'
import { createColumns } from './columns'

const { t } = useI18n()
const usersStore = useUsersStore()
const can = { user: usePermissionMap(userPermissionRules) }

const selectedUserId = ref<string | null>(null)
const selectedUsername = ref<string | null>(null)

const editDialogOpen = ref(false)
const createDialogOpen = ref(false)
const detailDrawerOpen = ref(false)
const resetPasswordDialogOpen = ref(false)
const banDialogOpen = ref(false)
const bulkBanDialogOpen = ref(false)
const bulkDeleteDialogOpen = ref(false)
const bulkDeleteConfirmText = ref('')

const bulkActionLoading = ref(false)
const selectedRows = ref<User[]>([])

// Animation state for staggered reveal
const isLoaded = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})
const {
  query,
  searchQuery,
  tablePagination,
  loading,
  data,
  total,
  error,
  refresh: loadUsers,
  setFilters,
} = useRemoteTable<
  User,
  { role: string; status: string },
  Parameters<typeof usersStore.fetchUsers>[0]
>({
  store: usersStore,
  initialQuery: { filters: { role: 'all', status: 'all' } },
  toParams: ({ search, filters, page, limit }) => ({
    search,
    role: filters.role === 'all' ? undefined : filters.role,
    isActive:
      filters.status === 'active' ? true : filters.status === 'inactive' ? false : undefined,
    isBanned: filters.status === 'banned' ? true : undefined,
    page,
    limit,
  }),
  autoLoad: true,
})

const roleFilter = computed({
  get: () => query.value.filters.role,
  set: (role: string) => setFilters({ ...query.value.filters, role }),
})
const statusFilter = computed({
  get: () => query.value.filters.status,
  set: (status: string) => setFilters({ ...query.value.filters, status }),
})

const canCreateUser = can.user.create
const canDeleteUser = can.user.delete

// Stats for terminal ticker
const stats = computed(() => {
  if (loading.value || error.value) return { total: '—', active: '—', banned: '—' }
  const users = usersStore.items
  const total = usersStore.total
  const active = users.filter((u) => u.isActive && !u.isBanned).length
  const banned = users.filter((u) => u.isBanned).length
  return { total, active, banned }
})

const toolbarFilters = computed<Filter[]>(() => [
  {
    modelValue: roleFilter.value,
    placeholder: t('users.filters.allRoles'),
    options: [
      { value: 'all', label: t('users.filters.allRoles') },
      { value: 'USER', label: t('users.filters.role.USER') },
      { value: 'MODERATOR', label: t('users.filters.role.MODERATOR') },
      { value: 'ADMIN', label: t('users.filters.role.ADMIN') },
      { value: 'SUPER_ADMIN', label: t('users.filters.role.SUPER_ADMIN') },
    ],
  },
  {
    modelValue: statusFilter.value,
    placeholder: t('users.filters.allStatus'),
    width: 'w-[140px]',
    options: [
      { value: 'all', label: t('users.filters.allStatus') },
      { value: 'active', label: t('users.filters.status.active') },
      { value: 'inactive', label: t('users.filters.status.inactive') },
      { value: 'banned', label: t('users.filters.status.banned') },
    ],
  },
])

const columns = createColumns(
  t,
  {
    viewUser: (user: User) => {
      selectedUserId.value = user.id
      detailDrawerOpen.value = true
    },
    editUser: (user: User) => {
      selectedUserId.value = user.id
      editDialogOpen.value = true
    },
    resetPassword: (user: User) => {
      selectedUserId.value = user.id
      selectedUsername.value = user.username
      resetPasswordDialogOpen.value = true
    },
    startBanUser: (user: User) => {
      selectedUserId.value = user.id
      selectedUsername.value = user.username
      banDialogOpen.value = true
    },
    unbanUser: async (id: string) => {
      try {
        await usersStore.unbanUser(id)
        await loadUsers()
      } catch {
        toast.error(t('users.toast.unbanFailed'))
      }
    },
  },
  can.user,
)

async function handleBanUser(id: string | number, reason?: string) {
  if (!reason) return
  await usersStore.banUser(String(id), reason)
}

async function handleBulkBan() {
  if (selectedRows.value.length === 0) return
  bulkBanDialogOpen.value = true
}

async function onBulkBanAction(_: string | number, reason?: string) {
  if (!reason) return
  const ids = selectedRows.value.map((r) => r.id)
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
  bulkDeleteConfirmText.value = ''
  bulkDeleteDialogOpen.value = true
}

async function confirmBulkDelete() {
  const expectedText = `DELETE ${selectedRows.value.length}`
  if (bulkDeleteConfirmText.value !== expectedText) return

  const ids = selectedRows.value.map((r) => r.id)
  bulkActionLoading.value = true
  try {
    await usersStore.bulkDelete(ids)
    await loadUsers()
    selectedRows.value = []
    bulkDeleteDialogOpen.value = false
  } catch {
    toast.error(t('users.toast.bulkDeleteFailed'))
  } finally {
    bulkActionLoading.value = false
  }
}
</script>

<template>
  <div class="relative flex flex-col gap-4 w-full min-w-0">
    <!-- Page header -->
    <div
      :class="[
        'border border-border dark:border-border bg-card',
        'transition-all duration-500',
        isLoaded ? 'opacity-100 translate-y-0' : 'opacity-0 -translate-y-2',
      ]"
    >
      <!-- Title Row -->
      <div class="px-4 lg:px-6 py-4 flex items-center justify-between">
        <h1 class="text-xl font-medium tracking-tight text-foreground">
          {{ t('nav.users') }}
        </h1>
        <Button v-if="canCreateUser" variant="outline" size="sm" @click="createDialogOpen = true">
          <IconPlus class="h-4 w-4 mr-1.5" />
          <span class="uppercase tracking-wider">{{ t('users.addUser') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="px-4 lg:px-6 py-2.5 flex flex-wrap items-center gap-x-6 gap-y-2 whitespace-nowrap border-t border-border dark:border-border bg-muted"
      >
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('users.stats.total') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.total }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('users.stats.active') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.active }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium text-muted-foreground"
            >{{ t('users.stats.banned') }}:</span
          >
          <span class="font-mono text-sm text-foreground tabular-nums">{{ stats.banned }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-muted-foreground">
          <IconUsers class="h-4 w-4" />
          <span class="text-xs font-mono uppercase tracking-wider">{{
            t('users.stats.userManagement')
          }}</span>
        </div>
      </div>
    </div>

    <!-- Bulk Action Bar - Terminal Style -->
    <div
      v-if="!error && selectedRows.length > 0"
      :class="[
        'mt-0 flex items-center justify-between border border-status-warning-mark bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)] p-3',
        'animate-in fade-in slide-in-from-top-2 duration-200',
      ]"
    >
      <div class="flex items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="font-mono text-sm text-foreground">
            &gt; SELECTED:{{ selectedRows.length }}
          </span>
        </div>
        <div class="h-4 w-px bg-border" />
        <div class="flex items-center gap-2">
          <Button variant="outline" size="sm" @click="handleBulkBan" :disabled="bulkActionLoading">
            <IconBan class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('users.bulkActions.bulkBan') }}</span>
          </Button>
          <Button
            variant="outline"
            size="sm"
            @click="handleBulkUnban"
            :disabled="bulkActionLoading"
          >
            <IconCircleXFilled class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('users.bulkActions.bulkUnban') }}</span>
          </Button>
          <Button
            v-if="canDeleteUser"
            variant="outline"
            size="sm"
            @click="handleBulkDelete"
            :disabled="bulkActionLoading"
          >
            <IconTrash class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('users.bulkActions.bulkDelete') }}</span>
          </Button>
        </div>
      </div>
      <Button variant="outline" size="sm" @click="selectedRows = []">
        [ESC] {{ t('users.clearSelection') }}
      </Button>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1">
      <DataTable
        :columns="columns"
        :data="error ? [] : data"
        :pagination="tablePagination"
        :row-count="error ? 0 : total"
        :hide-pagination="!!error"
        :loading="loading"
        v-model:selected-rows="selectedRows"
        @update:pagination="tablePagination = $event"
        class="w-full text-sm"
      >
        <template #toolbar-left>
          <DataTableToolbar
            :search-model-value="searchQuery"
            @update:search-model-value="searchQuery = $event"
            :search-placeholder="t('users.searchPlaceholder')"
            :filters="toolbarFilters"
            @update:filter="
              (index, value) =>
                index === 0 ? (roleFilter = String(value)) : (statusFilter = String(value))
            "
            :loading="loading"
            :on-refresh="loadUsers"
          />
        </template>
        <template v-if="error" #empty>
          <div
            role="alert"
            class="flex flex-wrap gap-3 items-center justify-between border border-status-error-mark bg-[color-mix(in_oklch,_var(--status-error-mark)_8%,_transparent)] p-4"
          >
            <div class="flex min-w-0 flex-1 flex-wrap items-center gap-3 break-words">
              <span class="font-mono text-sm text-foreground">&gt; ERROR:</span>
              <span class="text-sm text-foreground">{{ error }}</span>
            </div>
            <Button variant="outline" size="sm" @click="loadUsers()">
              {{ t('common.retry') }}
            </Button>
          </div>
        </template>
      </DataTable>
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
  <EntityActionDialog
    v-model:open="banDialogOpen"
    :entity-id="selectedUserId"
    :entity-title="selectedUsername"
    action="ban"
    :title="t('users.actions.banUser')"
    :description="
      t('users.actions.banUserDescription', {
        username: selectedUsername || t('users.actions.thisUser'),
      })
    "
    :reason-label="t('users.form.banReason')"
    :reason-placeholder="t('users.form.banReasonPlaceholder')"
    :on-action="handleBanUser"
    @success="loadUsers"
  />
  <EntityActionDialog
    v-model:open="bulkBanDialogOpen"
    entity-id="bulk"
    :entity-title="String(selectedRows.length)"
    action="ban"
    :title="t('users.actions.bulkBanUser')"
    :description="t('users.deleteConfirm', { count: selectedRows.length })"
    :reason-label="t('users.form.banReason')"
    :reason-placeholder="t('users.form.banReasonPlaceholder')"
    :on-action="onBulkBanAction"
    @success="loadUsers"
  />
  <AlertDialog v-model:open="bulkDeleteDialogOpen">
    <AlertDialogContent>
      <AlertDialogHeader>
        <AlertDialogTitle class="flex items-center gap-2">
          <IconAlertTriangle class="h-5 w-5" />
          {{ t('users.actions.deleteUsers') }}
        </AlertDialogTitle>
        <AlertDialogDescription>
          {{ t('users.deleteConfirm', { count: selectedRows.length }) }}
          <span class="mt-2 block font-mono text-sm text-destructive">
            {{ t('users.typeToConfirm', { text: `DELETE ${selectedRows.length}` }) }}
          </span>
        </AlertDialogDescription>
      </AlertDialogHeader>
      <div class="py-2">
        <Label for="bulk-delete-confirm">{{ t('users.typeConfirmLabel') }}</Label>
        <Input
          id="bulk-delete-confirm"
          v-model="bulkDeleteConfirmText"
          :placeholder="`DELETE ${selectedRows.length}`"
          class="mt-1"
        />
      </div>
      <AlertDialogFooter>
        <Button
          variant="outline"
          @click="bulkDeleteDialogOpen = false"
          :disabled="bulkActionLoading"
        >
          {{ t('common.cancel') }}
        </Button>
        <Button
          variant="destructive"
          :disabled="bulkActionLoading || bulkDeleteConfirmText !== `DELETE ${selectedRows.length}`"
          @click="confirmBulkDelete"
        >
          <IconLoader v-if="bulkActionLoading" class="mr-2 h-4 w-4 animate-spin" />
          <IconAlertTriangle v-else class="mr-2 h-4 w-4" />
          {{ t('common.delete') }}
        </Button>
      </AlertDialogFooter>
    </AlertDialogContent>
  </AlertDialog>
</template>
