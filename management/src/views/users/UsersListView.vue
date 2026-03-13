<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { IconBan, IconCircleXFilled, IconPlus, IconUsers, IconTrash } from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'

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
// Terminal UI components available for future use
// import { TerminalBadge, DataBlock } from '@/components/ui/terminal'
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

// Animation state for staggered reveal
const isLoaded = ref(false)

onMounted(() => {
  setTimeout(() => {
    isLoaded.value = true
  }, 100)
})

const canCreateUser = computed(() => authStore.hasPermission('CREATE', 'USER'))
const canModerateUser = computed(() => authStore.hasPermission('MODERATE', 'USER'))
const canDeleteUser = computed(() => authStore.hasPermission('DELETE', 'USER'))

// Stats for terminal ticker
const stats = computed(() => {
  const users = usersStore.users
  const total = usersStore.total
  const active = users.filter((u) => u.is_active && !u.is_banned).length
  const banned = users.filter((u) => u.is_banned).length
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
      <div class="py-4 flex items-center justify-between">
        <div class="flex items-center gap-4">
          <div class="flex items-center gap-2">
            <span class="terminal-prompt text-base">users</span>
            <span class="terminal-cursor" />
          </div>
          <h1 class="text-xl font-medium tracking-tight text-[var(--foreground)]">
            {{ t('users.title') }}
          </h1>
        </div>
        <Button
          v-if="canCreateUser"
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--accent-electric)] hover:text-[var(--accent-electric)] transition-colors"
          @click="createDialogOpen = true"
        >
          <IconPlus class="h-4 w-4 mr-1.5" />
          <span class="uppercase tracking-wider">{{ t('users.addUser') }}</span>
        </Button>
      </div>

      <!-- Stats Ticker -->
      <div
        class="py-2.5 flex items-center gap-6 border-t border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">total:</span>
          <span class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">{{
            stats.total
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">active:</span>
          <span class="font-data text-sm text-[var(--terminal-green)] tabular-nums">{{
            stats.active
          }}</span>
        </div>
        <div class="flex items-center gap-2">
          <span class="terminal-label text-[var(--silver-500)]">banned:</span>
          <span class="font-data text-sm text-[var(--terminal-red)] tabular-nums">{{
            stats.banned
          }}</span>
        </div>
        <div class="ml-auto flex items-center gap-2 text-[var(--silver-400)]">
          <IconUsers class="h-4 w-4" />
          <span class="text-xs font-data uppercase tracking-wider">user management</span>
        </div>
      </div>
    </div>

    <!-- Bulk Action Bar - Terminal Style -->
    <div
      v-if="selectedRows.length > 0"
      :class="[
        'mt-4 flex items-center justify-between border border-[var(--terminal-amber)] bg-[oklch(0.75_0.15_85/0.08)] dark:bg-[oklch(0.75_0.15_85/0.15)] p-3',
        'animate-in fade-in slide-in-from-top-2 duration-200',
      ]"
    >
      <div class="flex items-center gap-4">
        <div class="flex items-center gap-2">
          <span class="font-data text-sm text-[var(--terminal-amber)]">
            &gt; SELECTED:{{ selectedRows.length }}
          </span>
        </div>
        <div class="h-4 w-px bg-[var(--silver-300)]" />
        <div class="flex items-center gap-2">
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-amber)] hover:text-[var(--terminal-amber)]"
            @click="handleBulkBan"
            :disabled="bulkActionLoading"
          >
            <IconBan class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('users.bulkActions.bulkBan') }}</span>
          </Button>
          <Button
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--silver-300)] hover:border-[var(--terminal-green)] hover:text-[var(--terminal-green)]"
            @click="handleBulkUnban"
            :disabled="bulkActionLoading"
          >
            <IconCircleXFilled class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('users.bulkActions.bulkUnban') }}</span>
          </Button>
          <Button
            v-if="canDeleteUser"
            variant="terminal"
            size="sm"
            class="h-8 font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[oklch(0.6_0.2_25/0.1)]"
            @click="handleBulkDelete"
            :disabled="bulkActionLoading"
          >
            <IconTrash class="h-3.5 w-3.5 mr-1.5" />
            <span class="uppercase tracking-wider">{{ t('users.bulkActions.bulkDelete') }}</span>
          </Button>
        </div>
      </div>
      <Button
        variant="terminal"
        size="sm"
        class="h-8 font-data text-xs text-[var(--silver-500)] hover:text-[var(--foreground)]"
        @click="selectedRows = []"
      >
        [ESC] {{ t('users.clearSelection') }}
      </Button>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1 py-4">
      <DataTable
        :columns="columns"
        :data="data"
        :pagination="tablePagination"
        :row-count="total"
        :loading="loading"
        v-model:selected-rows="selectedRows"
        @update:pagination="tablePagination = $event"
        class="terminal-table"
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
      </DataTable>

      <!-- Error state - Terminal Style -->
      <div
        v-if="error"
        class="mt-4 flex items-center justify-between border border-[var(--terminal-red)] bg-[oklch(0.6_0.2_25/0.08)] p-4"
      >
        <div class="flex items-center gap-3">
          <span class="font-data text-sm text-[var(--terminal-red)]">&gt; ERROR:</span>
          <span class="text-sm text-[var(--foreground)]">{{ error }}</span>
        </div>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[oklch(0.6_0.2_25/0.1)]"
          @click="loadUsers()"
        >
          {{ t('common.retry') }}
        </Button>
      </div>
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
