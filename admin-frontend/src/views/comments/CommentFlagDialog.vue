<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
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
const reason = ref('')

async function handleFlag() {
  if (!props.commentId || !props.commentType) return
  if (!reason.value.trim()) {
    toast.error(t('comments.toast.reasonRequired'))
    return
  }

  loading.value = true
  try {
    await commentsStore.flagComment(props.commentId, props.commentType, reason.value)
    toast.success(t('comments.toast.flaggedSuccessfully'))
    reason.value = ''
    emit('update:open', false)
    emit('success')
  } catch (error) {
    toast.error(t('comments.toast.failedToFlag'))
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
          {{ t('comments.flag.title') }}
        </DialogTitle>
        <DialogDescription>
          {{ t('comments.flag.description') }}
        </DialogDescription>
      </DialogHeader>

      <div class="grid gap-4 py-4">
        <div class="space-y-2">
          <Label for="reason">{{ t('comments.flag.reasonLabel') }}</Label>
          <Textarea
            id="reason"
            v-model="reason"
            :placeholder="t('comments.flag.reasonPlaceholder')"
            class="min-h-[100px]"
          />
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="$emit('update:open', false)" :disabled="loading">
          {{ t('comments.flag.cancel') }}
        </Button>
        <Button
          class="bg-amber-600 hover:bg-amber-700 text-white"
          @click="handleFlag"
          :disabled="loading"
        >
          <IconLoader v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
          {{ t('comments.flag.confirm') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
