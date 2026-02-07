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
  userId: string | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const usersStore = useUsersStore()
const loading = ref(false)
const saving = ref(false)
const error = ref('')

const form = ref({
  username: '',
  email: '',
  name: '',
  role: 'USER',
  is_active: true,
})

watch(
  () => props.open,
  async (isOpen) => {
    if (isOpen && props.userId) {
      loading.value = true
      error.value = ''
      try {
        const user = await usersStore.fetchUser(props.userId)
        if (user) {
          form.value = {
            username: user.username,
            email: user.email || '',
            name: user.name || '',
            role: user.role,
            is_active: user.is_active,
          }
        }
      } catch {
        error.value = 'Failed to load user details'
      } finally {
        loading.value = false
      }
    }
  },
)

async function handleSubmit() {
  if (!props.userId) return

  error.value = ''
  saving.value = true

  try {
    await usersStore.updateUser(props.userId, {
      username: form.value.username,
      email: form.value.email,
      name: form.value.name,
      role: form.value.role as 'USER' | 'MODERATOR' | 'ADMIN' | 'SUPER_ADMIN',
      is_active: form.value.is_active,
    })

    emit('success')
    emit('update:open', false)
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
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="sm:max-w-[600px]">
      <DialogHeader>
        <DialogTitle>Edit User</DialogTitle>
        <DialogDescription>
          Make changes to the user profile here. Click save when you're done.
        </DialogDescription>
      </DialogHeader>

      <div v-if="loading" class="flex items-center justify-center p-8">
        <span class="text-muted-foreground">Loading user details...</span>
      </div>

      <form v-else @submit.prevent="handleSubmit">
        <div
          v-if="error"
          class="mb-4 p-3 text-sm text-red-600 bg-red-50 border border-red-200 rounded-md"
        >
          {{ error }}
        </div>

        <FieldGroup class="max-h-[60vh] overflow-y-auto px-1">
          <FieldSet>
            <FieldLegend>General Information</FieldLegend>
            <FieldDescription>Update the user's personal details.</FieldDescription>
            <FieldGroup>
              <Field>
                <FieldLabel for="edit-name">Full Name</FieldLabel>
                <Input id="edit-name" v-model="form.name" type="text" required :disabled="saving" />
              </Field>

              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Field>
                  <FieldLabel for="edit-username">Username</FieldLabel>
                  <Input
                    id="edit-username"
                    v-model="form.username"
                    type="text"
                    required
                    :disabled="saving"
                  />
                </Field>

                <Field>
                  <FieldLabel for="edit-email">Email</FieldLabel>
                  <Input id="edit-email" v-model="form.email" type="email" :disabled="saving" />
                </Field>
              </div>
            </FieldGroup>
          </FieldSet>

          <FieldSeparator />

          <FieldSet>
            <FieldLegend>Access Control</FieldLegend>
            <FieldDescription>Manage user role and account status.</FieldDescription>
            <FieldGroup>
              <Field>
                <FieldLabel for="edit-role">Role</FieldLabel>
                <Select v-model="form.role" :disabled="saving">
                  <SelectTrigger id="edit-role">
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
                <Checkbox id="edit-is_active" v-model:checked="form.is_active" :disabled="saving" />
                <div class="flex flex-col gap-1">
                  <FieldLabel for="edit-is_active" class="font-normal cursor-pointer">
                    Active Account
                  </FieldLabel>
                  <FieldDescription>
                    Disable to prevent the user from logging in.
                  </FieldDescription>
                </div>
              </Field>
            </FieldGroup>
          </FieldSet>
        </FieldGroup>

        <DialogFooter class="mt-6">
          <Button type="button" variant="outline" @click="emit('update:open', false)">
            Cancel
          </Button>
          <Button type="submit" :disabled="saving">
            {{ saving ? 'Saving...' : 'Save Changes' }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
