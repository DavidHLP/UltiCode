<script setup lang="ts">
import { ref, watch } from 'vue'
import { toast } from 'vue-sonner'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { useNotificationsStore } from '@/stores/admin/notifications'

const props = defineProps<{
  open: boolean
  notificationId: string | null
  notificationTitle: string | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const store = useNotificationsStore()
const loading = ref(false)

watch(
  () => props.open,
  (isOpen) => {
    if (!isOpen) {
      loading.value = false
    }
  },
)

async function handleDelete() {
  if (!props.notificationId) return
  loading.value = true
  try {
    await store.deleteAnnouncement(props.notificationId)
    toast.success('Notification deleted')
    emit('success')
    emit('update:open', false)
  } catch {
    toast.error('Failed to delete notification')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>Delete Notification</DialogTitle>
        <DialogDescription>
          Are you sure you want to delete
          <strong>"{{ notificationTitle || 'this notification' }}"</strong>? This action cannot be
          undone and the notification will be removed for all users.
        </DialogDescription>
      </DialogHeader>
      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)" :disabled="loading">
          Cancel
        </Button>
        <Button variant="destructive" @click="handleDelete" :disabled="loading">
          {{ loading ? 'Deleting...' : 'Delete' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
