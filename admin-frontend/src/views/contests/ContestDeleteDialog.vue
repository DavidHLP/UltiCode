<script setup lang="ts">
import { ref, watch } from 'vue'
import { useContestsStore } from '@/stores/admin/contests'
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
  contestId: string | null
  contestTitle: string | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const contestsStore = useContestsStore()
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
  if (!props.contestId) return
  loading.value = true
  try {
    await contestsStore.deleteContest(props.contestId)
    toast.success('Contest deleted successfully')
    emit('success')
    emit('update:open', false)
  } catch {
    toast.error('Failed to delete contest')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>Delete Contest</DialogTitle>
        <DialogDescription>
          Are you sure you want to delete
          <strong>{{ contestTitle || 'this contest' }}</strong
          >? This action cannot be undone.
        </DialogDescription>
      </DialogHeader>
      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)" :disabled="loading">
          Cancel
        </Button>
        <Button variant="destructive" @click="handleDelete" :disabled="loading">
          {{ loading ? 'Deleting...' : 'Delete Contest' }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
