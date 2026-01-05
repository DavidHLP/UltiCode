<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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

    <div v-if="loading" class="flex items-center justify-center p-12">
      <span class="text-muted-foreground">Loading user details...</span>
    </div>

    <div v-else class="w-full max-w-2xl mx-auto border rounded-lg p-6 bg-card text-card-foreground shadow-sm">
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
              Update the user's personal details.
            </FieldDescription>
            <FieldGroup>
              <Field>
                <FieldLabel for="name">Full Name</FieldLabel>
                <Input id="name" v-model="form.name" type="text" required :disabled="saving" />
              </Field>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
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
              </div>
            </FieldGroup>
          </FieldSet>

          <FieldSeparator />

          <FieldSet>
            <FieldLegend>Access Control</FieldLegend>
            <FieldDescription>
              Manage user role and account status.
            </FieldDescription>
            <FieldGroup>
              <Field>
                <FieldLabel for="role">Role</FieldLabel>
                <Select v-model="form.role" :disabled="saving">
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

              <Field orientation="horizontal">
                <Checkbox
                  id="is_active"
                  v-model:checked="form.is_active"
                  :disabled="saving"
                />
                <div class="flex flex-col gap-1">
                  <FieldLabel for="is_active" class="font-normal cursor-pointer">
                    Active Account
                  </FieldLabel>
                  <FieldDescription>
                    Disable to prevent the user from logging in.
                  </FieldDescription>
                </div>
              </Field>
            </FieldGroup>
          </FieldSet>

          <div class="flex gap-2 justify-end mt-6">
            <Button type="button" variant="outline" @click="router.back()" :disabled="saving">
              Cancel
            </Button>
            <Button type="submit" :disabled="saving">
              {{ saving ? 'Saving...' : 'Save Changes' }}
            </Button>
          </div>
        </FieldGroup>
      </form>
    </div>
  </div>
</template>