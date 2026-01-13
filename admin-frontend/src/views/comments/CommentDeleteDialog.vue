<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
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
import { useCommentsStore } from '@/stores/admin/comments'
import type { CommentType } from '@/api/admin/comments'

const { t } = useI18n()

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

async function handleDelete() {
  if (!props.commentId || !props.commentType) return

  loading.value = true
  try {
    await commentsStore.deleteComment(props.commentId, props.commentType)
    toast.success(t('comments.toast.deletedSuccessfully'))
    emit('update:open', false)
    emit('success')
  } catch (error) {
    toast.error(t('comments.toast.failedToDelete'))
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
          {{ t('comments.delete.title') }}
        </DialogTitle>
        <DialogDescription>
          {{ t('comments.delete.description') }}
        </DialogDescription>
      </DialogHeader>

      <DialogFooter>
        <Button variant="outline" @click="$emit('update:open', false)" :disabled="loading">
          {{ t('comments.delete.cancel') }}
        </Button>
        <Button variant="destructive" @click="handleDelete" :disabled="loading">
          <IconLoader v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
          {{ t('comments.delete.confirm') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
