<script setup lang="ts">
import { ref, watch } from 'vue'
import { useUsersStore } from '@/stores/admin/users'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

const props = defineProps<{
  open: boolean
  userId: string | null
  username: string | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const usersStore = useUsersStore()
const newPassword = ref('')
const loading = ref(false)

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      newPassword.value = ''
    }
  },
)

async function handleReset() {
  if (!props.userId || !newPassword.value) return
  loading.value = true
  try {
    await usersStore.resetPassword(props.userId, newPassword.value)
    emit('success')
    emit('update:open', false)
  } catch {
    toast.error('Failed to reset password', {
      description: 'An error occurred while attempting to update the password.',
    })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>Reset Password</DialogTitle>
        <DialogDescription>
          Set a new password for <strong>{{ username || 'this user' }}</strong
          >.
        </DialogDescription>
      </DialogHeader>
      <div class="grid gap-4 py-4">
        <div class="grid gap-2">
          <Label for="new-password">New Password</Label>
          <Input
            id="new-password"
            v-model="newPassword"
            type="password"
            placeholder="Enter new password"
            :disabled="loading"
          />
        </div>
      </div>
      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)" :disabled="loading">
          Cancel
        </Button>
        <Button @click="handleReset" :disabled="!newPassword || loading">
          {{ loading ? 'Resetting...' : 'Reset Password' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
