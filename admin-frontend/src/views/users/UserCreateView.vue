<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUsersStore } from '@/stores/admin/users'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Field,
  FieldGroup,
  FieldLabel,
  FieldSet,
  FieldLegend,
  FieldDescription,
  FieldSeparator
} from '@/components/ui/field'
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

    <div class="w-full max-w-2xl mx-auto border rounded-lg p-6 bg-card text-card-foreground shadow-sm">
      <form @submit.prevent="handleSubmit">
        <div
          v-if="error"
          class="mb-6 p-3 text-sm text-red-600 bg-red-50 border border-red-200 rounded-md"
        >
          {{ error }}
        </div>

        <FieldGroup>
          <FieldSet>
            <FieldLegend>General Information</FieldLegend>
            <FieldDescription>
              Basic personal details for the new user.
            </FieldDescription>
            <FieldGroup>
              <Field>
                <FieldLabel for="name">Full Name</FieldLabel>
                <Input id="name" v-model="form.name" type="text" required :disabled="loading" placeholder="John Doe" />
              </Field>
              
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Field>
                  <FieldLabel for="username">Username</FieldLabel>
                  <Input
                    id="username"
                    v-model="form.username"
                    type="text"
                    required
                    :disabled="loading"
                    placeholder="johndoe"
                  />
                </Field>

                <Field>
                  <FieldLabel for="email">Email</FieldLabel>
                  <Input id="email" v-model="form.email" type="email" required :disabled="loading" placeholder="john@example.com" />
                </Field>
              </div>
            </FieldGroup>
          </FieldSet>

          <FieldSeparator />

          <FieldSet>
            <FieldLegend>Security & Access</FieldLegend>
            <FieldDescription>
              Configure authentication credentials and permissions.
            </FieldDescription>
            <FieldGroup>
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
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
                    <SelectTrigger id="role">
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
              </div>

              <Field orientation="horizontal">
                <Checkbox
                  id="is_active"
                  v-model:checked="form.is_active"
                  :disabled="loading"
                />
                <div class="flex flex-col gap-1">
                  <FieldLabel for="is_active" class="font-normal cursor-pointer">
                    Active Account
                  </FieldLabel>
                  <FieldDescription>
                    Inactive users cannot log in to the system.
                  </FieldDescription>
                </div>
              </Field>
            </FieldGroup>
          </FieldSet>

          <div class="flex gap-2 justify-end mt-6">
            <Button type="button" variant="outline" @click="router.back()" :disabled="loading">
              Cancel
            </Button>
            <Button type="submit" :disabled="loading">
              {{ loading ? 'Creating...' : 'Create User' }}
            </Button>
          </div>
        </FieldGroup>
      </form>
    </div>
  </div>
</template>