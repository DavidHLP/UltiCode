<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUsersStore } from '@/stores/admin/users'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

const route = useRoute()
const router = useRouter()
const usersStore = useUsersStore()

const loading = ref(true)

onMounted(async () => {
  const id = route.params.id as string
  await usersStore.fetchUser(id)
  loading.value = false
})

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
    <div class="flex items-center gap-4">
      <Button variant="ghost" @click="router.back()"> ← Back </Button>
      <h1 class="text-3xl font-bold tracking-tight">User Details</h1>
    </div>

    <Card v-if="loading">
      <CardContent class="pt-6">Loading...</CardContent>
    </Card>

    <Card v-else-if="usersStore.currentUser">
      <CardHeader>
        <div class="flex items-start justify-between">
          <div class="flex items-center gap-4">
            <img
              v-if="usersStore.currentUser.avatar"
              :src="usersStore.currentUser.avatar"
              :alt="usersStore.currentUser.name"
              class="w-20 h-20 rounded-full"
            />
            <div>
              <CardTitle class="text-2xl">
                {{ usersStore.currentUser.name || usersStore.currentUser.username }}
              </CardTitle>
              <p class="text-muted-foreground">
                {{ usersStore.currentUser.email || 'No email' }}
              </p>
            </div>
          </div>
          <Button
            @click="router.push({ name: 'user-edit', params: { id: usersStore.currentUser.id } })"
          >
            Edit User
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        <dl class="grid gap-4">
          <div class="grid grid-cols-3 items-center gap-4">
            <dt class="font-medium">Username</dt>
            <dd class="text-muted-foreground col-span-2">{{ usersStore.currentUser.username }}</dd>
          </div>
          <div class="grid grid-cols-3 items-center gap-4">
            <dt class="font-medium">Role</dt>
            <dd class="col-span-2">
              <Badge :variant="getRoleBadgeVariant(usersStore.currentUser.role)">
                {{ usersStore.currentUser.role }}
              </Badge>
            </dd>
          </div>
          <div class="grid grid-cols-3 items-center gap-4">
            <dt class="font-medium">Status</dt>
            <dd class="col-span-2">
              <Badge v-if="usersStore.currentUser.is_banned" variant="destructive"> Banned </Badge>
              <Badge v-else-if="!usersStore.currentUser.is_active" variant="secondary">
                Inactive
              </Badge>
              <Badge v-else variant="default"> Active </Badge>
            </dd>
          </div>
          <div class="grid grid-cols-3 items-center gap-4">
            <dt class="font-medium">Joined</dt>
            <dd class="text-muted-foreground col-span-2">
              {{ new Date(usersStore.currentUser.joined_at).toLocaleDateString() }}
            </dd>
          </div>
        </dl>
      </CardContent>
    </Card>

    <Card v-else>
      <CardContent class="pt-6">User not found</CardContent>
    </Card>
  </div>
</template>
