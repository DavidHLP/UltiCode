<script setup lang="ts">
import { ref } from 'vue'
import { toast } from 'vue-sonner'
import { IconAlertTriangle, IconLoader } from '@tabler/icons-vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useForumStore } from '@/stores/admin/forum'

const props = defineProps<{
  open: boolean
  postId: string | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const forumStore = useForumStore()
const loading = ref(false)

async function handleDelete() {
  if (!props.postId) return

  loading.value = true
  try {
    await forumStore.deletePost(props.postId)
    toast.success('Post deleted successfully')
    emit('update:open', false)
    emit('success')
  } catch (error) {
    toast.error('Failed to delete post')
    console.error(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="$emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2 text-destructive">
          <IconAlertTriangle class="h-5 w-5" />
          Delete Post
        </DialogTitle>
        <DialogDescription>
          Are you sure you want to delete this post? This action cannot be undone.
        </DialogDescription>
      </DialogHeader>

      <DialogFooter>
        <Button variant="outline" @click="$emit('update:open', false)" :disabled="loading">
          Cancel
        </Button>
        <Button variant="destructive" @click="handleDelete" :disabled="loading">
          <IconLoader v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
          Delete Post
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
