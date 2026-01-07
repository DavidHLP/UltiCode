<script setup lang="ts">
import { ref } from 'vue'
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

const store = useAdminProblemListsStore()
const loading = ref(false)

async function handleDelete() {
  if (!props.listId) return
  loading.value = true
  try {
    await store.deleteList(props.listId)
    toast.success('Problem list deleted successfully')
    emit('success')
    emit('update:open', false)
  } catch {
    toast.error('Failed to delete problem list')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>Delete Problem List</DialogTitle>
        <DialogDescription>
          Are you sure you want to delete <strong>{{ listName || 'this list' }}</strong
          >? This action cannot be undone.
        </DialogDescription>
      </DialogHeader>
      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)" :disabled="loading">
          Cancel
        </Button>
        <Button variant="destructive" @click="handleDelete" :disabled="loading">
          {{ loading ? 'Deleting...' : 'Delete List' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
