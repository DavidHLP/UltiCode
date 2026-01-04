<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
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

const router = useRouter()
const usersStore = useUsersStore()

const form = ref({
  username: '',
  email: '',
  name: '',
  password: '',
  role: 'USER',
  is_active: true,
})

const error = ref('')
const loading = ref(false)

async function handleSubmit() {
  error.value = ''
  loading.value = true

  try {
    await usersStore.createUser({
      username: form.value.username,
      email: form.value.email,
      name: form.value.name,
      password: form.value.password,
      role: form.value.role as 'USER' | 'MODERATOR' | 'ADMIN' | 'SUPER_ADMIN',
      is_active: form.value.is_active,
    })

    router.push({ name: 'users' })
  } catch (err: unknown) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      'Failed to create user'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex flex-col gap-4">
    <div class="flex items-center gap-4">
      <Button variant="ghost" @click="router.back()"> ← Back </Button>
      <h1 class="text-3xl font-bold tracking-tight">Create User</h1>
    </div>

    <Card>
      <CardHeader>
        <CardTitle>Create New User</CardTitle>
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
                :disabled="loading"
              />
            </Field>

            <Field>
              <FieldLabel for="email">Email</FieldLabel>
              <Input id="email" v-model="form.email" type="email" required :disabled="loading" />
            </Field>

            <Field>
              <FieldLabel for="name">Full Name</FieldLabel>
              <Input id="name" v-model="form.name" type="text" required :disabled="loading" />
            </Field>

            <Field>
              <FieldLabel for="password">Password</FieldLabel>
              <Input
                id="password"
                v-model="form.password"
                type="password"
                required
                :disabled="loading"
              />
            </Field>

            <Field>
              <FieldLabel for="role">Role</FieldLabel>
              <Select v-model="form.role" :disabled="loading">
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
                  :disabled="loading"
                />
                <FieldLabel for="is_active">Active</FieldLabel>
              </div>
            </Field>
          </FieldGroup>

          <div class="flex gap-2">
            <Button type="submit" :disabled="loading">
              {{ loading ? 'Creating...' : 'Create User' }}
            </Button>
            <Button type="button" variant="outline" @click="router.back()" :disabled="loading">
              Cancel
            </Button>
          </div>
        </form>
      </CardContent>
    </Card>
  </div>
</template>
