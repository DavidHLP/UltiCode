<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
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

const { t } = useI18n()

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
    toast.success(t('notifications.toast.deletedSuccessfully'))
    emit('success')
    emit('update:open', false)
  } catch {
    toast.error(t('notifications.toast.failedToDelete'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{ t('notifications.dialog.deleteTitle') }}</DialogTitle>
        <DialogDescription>
          {{
            t('notifications.dialog.deleteDescription', {
              title: notificationTitle || t('notifications.dialog.deleteFallback'),
            })
          }}
        </DialogDescription>
      </DialogHeader>
      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)" :disabled="loading">
          {{ t('common.cancel') }}
        </Button>
        <Button variant="destructive" @click="handleDelete" :disabled="loading">
          {{ loading ? t('notifications.dialog.deleting') : t('common.delete') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
