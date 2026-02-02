<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { IconBan, IconCircleXFilled, IconPlus } from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import { Separator } from '@/components/ui/separator'

import { useUsersStore } from '@/stores/admin/users'
import { useAuthStore } from '@/stores/auth'
import type { User } from '@/api/admin/users'

import DataTable from '@/components/table/DataTable.vue'
import DataTableToolbar, { type Filter } from '@/components/table/DataTableToolbar.vue'
import EntityActionDialog from '@/components/shared/EntityActionDialog.vue'
import UserEditDialog from './UserEditDialog.vue'
import UserCreateDialog from './UserCreateDialog.vue'
import UserDetailDrawer from './UserDetailDrawer.vue'
import UserResetPasswordDialog from './UserResetPasswordDialog.vue'
import { useDataTable } from '@/composables/useDataTable'
import { createColumns } from './columns'

const { t } = useI18n()
const usersStore = useUsersStore()
const authStore = useAuthStore()

const roleFilter = ref<string>('all')
const statusFilter = ref<string>('all')
const selectedUserId = ref<string | null>(null)
const selectedUsername = ref<string | null>(null)

const editDialogOpen = ref(false)
const createDialogOpen = ref(false)
const detailDrawerOpen = ref(false)
const resetPasswordDialogOpen = ref(false)
const banDialogOpen = ref(false)

const bulkActionLoading = ref(false)

const canCreateUser = computed(() => authStore.hasPermission('CREATE', 'USER'))
const canModerateUser = computed(() => authStore.hasPermission('MODERATE', 'USER'))
const canDeleteUser = computed(() => authStore.hasPermission('DELETE', 'USER'))

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

const {
  searchQuery,
  tablePagination,
  selectedRows,
  loading,
  data,
  total,
  error,
  loadEntities: loadUsers,
} = useDataTable<
  User,
  { role: string; status: string },
  Parameters<typeof usersStore.fetchUsers>[0]
>({
  store: {
    data: computed(() => usersStore.users),
    total: computed(() => usersStore.total),
    isLoading: computed(() => usersStore.loading),
    error: computed(() => usersStore.error),
    fetch: (params) => usersStore.fetchUsers(params),
  },
  filters: {
    role: roleFilter.value,
    status: statusFilter.value,
  },
  transformParams: ({ search, filters, page, limit }) => ({
    search,
    role: filters.role === 'all' ? undefined : filters.role,
    is_active:
      filters.status === 'active' ? true : filters.status === 'inactive' ? false : undefined,
    is_banned: filters.status === 'banned' ? true : undefined,
    page,
    limit,
  }),
  autoLoad: true,
})

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
  () => canModerateUser.value,
)

async function handleBanUser(id: string | number, reason?: string) {
  if (!reason) return
  await usersStore.banUser(id as string, reason)
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
  if (!confirm(t('users.deleteConfirm', { count }))) return

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
</script>

<template>
  <div class="relative flex flex-col gap-4 overflow-auto px-4 lg:px-6">
    <div
      v-if="selectedRows.length > 0"
      class="flex items-center justify-between rounded-lg border border-primary/20 bg-primary/5 p-2 px-4 animate-in fade-in slide-in-from-top-2"
    >
      <div class="flex items-center gap-3">
        <span class="text-sm font-medium">{{
          t('users.selected', { count: selectedRows.length })
        }}</span>
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
            <IconCircleXFilled class="h-3.5 w-3.5 mr-1" />
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
            <IconBan class="h-3.5 w-3.5 mr-1" />
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
      :data="data"
      :pagination="tablePagination"
      :row-count="total"
      :loading="loading"
      v-model:selected-rows="selectedRows"
      @update:pagination="tablePagination = $event"
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

      <template #extra-actions>
        <Button v-if="canCreateUser" variant="outline" size="sm" @click="createDialogOpen = true">
          <IconPlus />
          <span class="hidden lg:inline">{{ t('users.addUser') }}</span>
        </Button>
      </template>
    </DataTable>

    <!-- Error state -->
    <div
      v-if="error"
      class="flex items-center justify-between rounded-lg border border-destructive/50 bg-destructive/10 p-4"
    >
      <span class="text-destructive">{{ error }}</span>
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
</template>
