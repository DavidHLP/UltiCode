<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUsersStore } from '@/stores/admin/users'
import { useAuthStore } from '@/stores/admin/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const router = useRouter()
const usersStore = useUsersStore()
const authStore = useAuthStore()

const searchQuery = ref('')
const roleFilter = ref<string>('all')
const statusFilter = ref<string>('all')
const page = ref(1)
const pageSize = ref(20)

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
    page: page.value,
    limit: pageSize.value,
  })
}

function viewUser(id: string) {
  router.push({ name: 'user-detail', params: { id } })
}

function editUser(id: string) {
  router.push({ name: 'user-edit', params: { id } })
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

function getRoleBadgeVariant(role: string) {
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
</script>

<template>
  <div class="flex flex-col gap-4">
    <div class="flex items-center justify-between">
      <div>
        <h1 class="text-3xl font-bold tracking-tight">Users</h1>
        <p class="text-muted-foreground">Manage users and their permissions</p>
      </div>
      <Button v-if="canCreateUser" @click="router.push({ name: 'user-create' })">
        Create User
      </Button>
    </div>

    <Card>
      <CardHeader>
        <div class="flex items-center gap-4">
          <Input
            v-model="searchQuery"
            placeholder="Search users..."
            class="max-w-sm"
            @keyup.enter="loadUsers()"
          />
          <Select v-model="roleFilter" @update:model-value="loadUsers()">
            <SelectTrigger class="w-[180px]">
              <SelectValue placeholder="Filter by role" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Roles</SelectItem>
              <SelectItem value="USER">User</SelectItem>
              <SelectItem value="MODERATOR">Moderator</SelectItem>
              <SelectItem value="ADMIN">Admin</SelectItem>
              <SelectItem value="SUPER_ADMIN">Super Admin</SelectItem>
            </SelectContent>
          </Select>
          <Select v-model="statusFilter" @update:model-value="loadUsers()">
            <SelectTrigger class="w-[180px]">
              <SelectValue placeholder="Filter by status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Status</SelectItem>
              <SelectItem value="active">Active</SelectItem>
              <SelectItem value="inactive">Inactive</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </CardHeader>
      <CardContent>
        <div v-if="usersStore.loading" class="text-center py-8">Loading...</div>

        <div v-else-if="usersStore.error" class="text-center py-8 text-red-600">
          {{ usersStore.error }}
        </div>

        <div v-else class="space-y-4">
          <div
            v-for="user in usersStore.users"
            :key="user.id"
            class="flex items-center justify-between p-4 border rounded-lg hover:bg-muted/50"
          >
            <div class="flex items-center gap-4">
              <img
                v-if="user.avatar"
                :src="user.avatar"
                :alt="user.name"
                class="w-10 h-10 rounded-full"
              />
              <div class="flex-1 space-y-1">
                <p class="text-sm font-medium leading-none">
                  {{ user.name || user.username }}
                </p>
                <p class="text-sm text-muted-foreground">
                  {{ user.email || user.username }}
                </p>
              </div>
            </div>

            <div class="flex items-center gap-4">
              <Badge :variant="getRoleBadgeVariant(user.role)">
                {{ user.role }}
              </Badge>
              <Badge v-if="user.is_banned" variant="destructive"> Banned </Badge>
              <Badge v-else-if="!user.is_active" variant="secondary"> Inactive </Badge>
              <Badge v-else variant="default"> Active </Badge>
            </div>

            <div class="flex items-center gap-2">
              <Button size="sm" variant="ghost" @click="viewUser(user.id)"> View </Button>
              <Button size="sm" variant="ghost" @click="editUser(user.id)"> Edit </Button>
              <Button
                v-if="canModerateUser"
                size="sm"
                :variant="user.is_banned ? 'default' : 'destructive'"
                @click="user.is_banned ? unbanUser(user.id) : banUser(user.id)"
              >
                {{ user.is_banned ? 'Unban' : 'Ban' }}
              </Button>
            </div>
          </div>

          <div v-if="usersStore.users.length === 0" class="text-center py-8 text-muted-foreground">
            No users found
          </div>

          <div class="flex items-center justify-between pt-4 border-t">
            <p class="text-sm text-muted-foreground">
              Showing {{ usersStore.users.length }} of {{ usersStore.total }} users
            </p>
            <div class="flex gap-2">
              <Button
                size="sm"
                variant="outline"
                :disabled="page === 1"
                @click="
                  () => {
                    page--
                    loadUsers()
                  }
                "
              >
                Previous
              </Button>
              <Button
                size="sm"
                variant="outline"
                :disabled="page * pageSize >= usersStore.total"
                @click="
                  () => {
                    page++
                    loadUsers()
                  }
                "
              >
                Next
              </Button>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
