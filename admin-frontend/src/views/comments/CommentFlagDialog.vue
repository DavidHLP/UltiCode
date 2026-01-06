<script setup lang="ts">
import { ref } from 'vue'
import { toast } from 'vue-sonner'
import { IconFlag, IconLoader } from '@tabler/icons-vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { useCommentsStore } from '@/stores/admin/comments'
import type { CommentType } from '@/api/admin/comments'

const props = defineProps<{
  open: boolean
  commentId: string | null
  commentType: CommentType | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const commentsStore = useCommentsStore()
const loading = ref(false)
const reason = ref('')

async function handleFlag() {
  if (!props.commentId || !props.commentType) return
  if (!reason.value.trim()) {
    toast.error('Please provide a reason for flagging')
    return
  }

  loading.value = true
  try {
    await commentsStore.flagComment(props.commentId, props.commentType, reason.value)
    toast.success('Comment flagged successfully')
    reason.value = ''
    emit('update:open', false)
    emit('success')
  } catch (error) {
    toast.error('Failed to flag comment')
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
        <DialogTitle class="flex items-center gap-2 text-amber-600">
          <IconFlag class="h-5 w-5" />
          Flag Comment
        </DialogTitle>
        <DialogDescription>
          Flagging this comment will mark it for review and may hide it from public view depending on settings.
        </DialogDescription>
      </DialogHeader>

      <div class="grid gap-4 py-4">
        <div class="space-y-2">
          <Label for="reason">Reason for flagging</Label>
          <Textarea
            id="reason"
            v-model="reason"
            placeholder="Please explain why this comment violates community guidelines..."
            class="min-h-[100px]"
          />
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="$emit('update:open', false)" :disabled="loading">
          Cancel
        </Button>
        <Button
          class="bg-amber-600 hover:bg-amber-700 text-white"
          @click="handleFlag"
          :disabled="loading"
        >
          <IconLoader v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
          Flag Comment
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
