<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUsersStore } from '@/stores/admin/users'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Field, FieldGroup, FieldLabel } from '@/components/ui/field'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const route = useRoute()
const router = useRouter()
const usersStore = useUsersStore()

const loading = ref(true)
const saving = ref(false)
const error = ref('')

const form = ref({
  username: '',
  email: '',
  name: '',
  role: 'USER',
  is_active: true,
})

onMounted(async () => {
  const id = route.params.id as string
  const user = await usersStore.fetchUser(id)

  if (user) {
    form.value = {
      username: user.username,
      email: user.email || '',
      name: user.name || '',
      role: user.role,
      is_active: user.is_active,
    }
  }

  loading.value = false
})

async function handleSubmit() {
  error.value = ''
  saving.value = true

  try {
    const id = route.params.id as string
    await usersStore.updateUser(id, {
      username: form.value.username,
      email: form.value.email,
      name: form.value.name,
      role: form.value.role as 'USER' | 'MODERATOR' | 'ADMIN' | 'SUPER_ADMIN',
      is_active: form.value.is_active,
    })

    router.push({ name: 'users' })
  } catch (err: unknown) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      'Failed to update user'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <div class="flex items-center gap-4">
      <Button variant="ghost" @click="router.back()"> ← Back </Button>
      <h1 class="text-3xl font-bold tracking-tight">Edit User</h1>
    </div>

    <Card v-if="loading">
      <CardContent class="pt-6">Loading...</CardContent>
    </Card>

    <Card v-else>
      <CardHeader>
        <CardTitle>Edit User</CardTitle>
      </CardHeader>
      <CardContent>
        <form @submit.prevent="handleSubmit" class="space-y-6">
          <div
            v-if="error"
            class="p-3 text-sm text-red-600 bg-red-50 border border-red-200 rounded-md"
          >
            {{ error }}
          </div>

          <FieldGroup>
            <Field>
              <FieldLabel for="username">Username</FieldLabel>
              <Input
                id="username"
                v-model="form.username"
                type="text"
                required
                :disabled="saving"
              />
            </Field>

            <Field>
              <FieldLabel for="email">Email</FieldLabel>
              <Input id="email" v-model="form.email" type="email" :disabled="saving" />
            </Field>

            <Field>
              <FieldLabel for="name">Full Name</FieldLabel>
              <Input id="name" v-model="form.name" type="text" required :disabled="saving" />
            </Field>

            <Field>
              <FieldLabel for="role">Role</FieldLabel>
              <Select v-model="form.role" :disabled="saving">
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="USER">User</SelectItem>
                  <SelectItem value="MODERATOR">Moderator</SelectItem>
                  <SelectItem value="ADMIN">Admin</SelectItem>
                  <SelectItem value="SUPER_ADMIN">Super Admin</SelectItem>
                </SelectContent>
              </Select>
            </Field>

            <Field>
              <div class="flex items-center gap-2">
                <input
                  id="is_active"
                  v-model="form.is_active"
                  type="checkbox"
                  class="h-4 w-4"
                  :disabled="saving"
                />
                <FieldLabel for="is_active">Active</FieldLabel>
              </div>
            </Field>
          </FieldGroup>

          <div class="flex gap-2">
            <Button type="submit" :disabled="saving">
              {{ saving ? 'Saving...' : 'Save Changes' }}
            </Button>
            <Button type="button" variant="outline" @click="router.back()" :disabled="saving">
              Cancel
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  </div>
</template>
