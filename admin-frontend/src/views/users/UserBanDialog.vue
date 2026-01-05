<script setup lang="ts">
import { ref, watch } from 'vue'
import { useUsersStore } from '@/stores/admin/users'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
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
const banReason = ref('')
const loading = ref(false)

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      banReason.value = ''
    }
  },
)

async function handleBan() {
  if (!props.userId || !banReason.value) return
  loading.value = true
  try {
    await usersStore.banUser(props.userId, banReason.value)
    toast.success('User has been banned')
    emit('success')
    emit('update:open', false)
  } catch {
    toast.error('Failed to ban user')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>Ban User</DialogTitle>
        <DialogDescription>
          Please provide a reason for banning <strong>{{ username || 'this user' }}</strong
          >.
        </DialogDescription>
      </DialogHeader>
      <div class="grid gap-4 py-4">
        <div class="grid gap-2">
          <Label for="ban-reason">Reason</Label>
          <Textarea
            id="ban-reason"
            v-model="banReason"
            placeholder="Violation of terms..."
            :disabled="loading"
          />
        </div>
      </div>
      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)" :disabled="loading">
          Cancel
        </Button>
        <Button variant="destructive" @click="handleBan" :disabled="!banReason || loading">
          {{ loading ? 'Banning...' : 'Confirm Ban' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
