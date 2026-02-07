<script setup lang="ts">
import { ref, watch } from 'vue'
import { useUsersStore } from '@/stores/admin/users'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Field,
  FieldGroup,
  FieldLabel,
  FieldSet,
  FieldLegend,
  FieldDescription,
  FieldSeparator,
} from '@/components/ui/field'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const usersStore = useUsersStore()
const loading = ref(false)
const error = ref('')

const defaultForm = {
  username: '',
  email: '',
  name: '',
  password: '',
  role: 'USER',
  is_active: true,
}

const form = ref({ ...defaultForm })

// Reset form when dialog opens
watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      form.value = { ...defaultForm }
      error.value = ''
    }
  },
)

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

    emit('success')
    emit('update:open', false)
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
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="sm:max-w-[600px]">
      <DialogHeader>
        <DialogTitle>Create User</DialogTitle>
        <DialogDescription>
          Add a new user to the system. Click create when you're done.
        </DialogDescription>
      </DialogHeader>

      <form @submit.prevent="handleSubmit">
        <div
          v-if="error"
          class="mb-4 p-3 text-sm text-red-600 bg-red-50 border border-red-200 rounded-md"
        >
          {{ error }}
        </div>

        <FieldGroup class="max-h-[60vh] overflow-y-auto px-1">
          <FieldSet>
            <FieldLegend>General Information</FieldLegend>
            <FieldDescription>Basic personal details for the new user.</FieldDescription>
            <FieldGroup>
              <Field>
                <FieldLabel for="create-name">Full Name</FieldLabel>
                <Input
                  id="create-name"
                  v-model="form.name"
                  type="text"
                  required
                  :disabled="loading"
                  placeholder="John Doe"
                />
              </Field>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Field>
                  <FieldLabel for="create-username">Username</FieldLabel>
                  <Input
                    id="create-username"
                    v-model="form.username"
                    type="text"
                    required
                    :disabled="loading"
                    placeholder="johndoe"
                  />
                </Field>

                <Field>
                  <FieldLabel for="create-email">Email</FieldLabel>
                  <Input
                    id="create-email"
                    v-model="form.email"
                    type="email"
                    required
                    :disabled="loading"
                    placeholder="john@example.com"
                  />
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
                  <FieldLabel for="create-password">Password</FieldLabel>
                  <Input
                    id="create-password"
                    v-model="form.password"
                    type="password"
                    required
                    :disabled="loading"
                  />
                </Field>

                <Field>
                  <FieldLabel for="create-role">Role</FieldLabel>
                  <Select v-model="form.role" :disabled="loading">
                    <SelectTrigger id="create-role">
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
                  id="create-is_active"
                  v-model:checked="form.is_active"
                  :disabled="loading"
                />
                <div class="flex flex-col gap-1">
                  <FieldLabel for="create-is_active" class="font-normal cursor-pointer">
                    Active Account
                  </FieldLabel>
                  <FieldDescription> Inactive users cannot log in to the system. </FieldDescription>
                </div>
              </Field>
            </FieldGroup>
          </FieldSet>
        </FieldGroup>

        <DialogFooter class="mt-6">
          <Button type="button" variant="outline" @click="emit('update:open', false)">
            Cancel
          </Button>
          <Button type="submit" :disabled="loading">
            {{ loading ? 'Creating...' : 'Create User' }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
