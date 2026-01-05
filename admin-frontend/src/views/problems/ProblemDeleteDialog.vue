<script setup lang="ts">
import { ref } from 'vue'
import { useProblemsStore } from '@/stores/admin/problems'
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
  problemId: string | null
  problemTitle: string | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const problemsStore = useProblemsStore()
const loading = ref(false)

async function handleDelete() {
  if (!props.problemId) return
  loading.value = true
  try {
    await problemsStore.deleteProblem(props.problemId)
    toast.success('Problem deleted successfully')
    emit('success')
    emit('update:open', false)
  } catch {
    toast.error('Failed to delete problem')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>Delete Problem</DialogTitle>
        <DialogDescription>
          Are you sure you want to delete <strong>{{ problemTitle || 'this problem' }}</strong
          >? This action cannot be undone and will remove all associated data.
        </DialogDescription>
      </DialogHeader>
      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)" :disabled="loading">
          Cancel
        </Button>
        <Button variant="destructive" @click="handleDelete" :disabled="loading">
          {{ loading ? 'Deleting...' : 'Delete Problem' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
