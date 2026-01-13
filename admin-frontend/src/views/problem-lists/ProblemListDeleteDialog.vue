<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAdminProblemListsStore } from '@/stores/admin/problem-lists'
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

const props = defineProps<{
  open: boolean
  listId: string | null
  listName: string | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const { t } = useI18n()
const store = useAdminProblemListsStore()
const loading = ref(false)

async function handleDelete() {
  if (!props.listId) return
  loading.value = true
  try {
    await store.deleteList(props.listId)
    toast.success(t('problemLists.toast.deletedSuccess'))
    emit('success')
    emit('update:open', false)
  } catch {
    toast.error(t('problemLists.toast.deleteFailed'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{ t('problemLists.delete.title') }}</DialogTitle>
        <DialogDescription>
          {{
            t('problemLists.delete.description', {
              name: listName || t('problemLists.delete.thisList'),
            })
          }}
        </DialogDescription>
      </DialogHeader>
      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)" :disabled="loading">
          {{ t('problemLists.delete.cancel') }}
        </Button>
        <Button variant="destructive" @click="handleDelete" :disabled="loading">
          {{ loading ? t('problemLists.delete.deleting') : t('problemLists.delete.confirm') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
