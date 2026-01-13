<script setup lang="ts">
import { ref, watch } from 'vue'
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
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useForumStore } from '@/stores/admin/forum'

const props = defineProps<{
  open: boolean
  postId: string | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const { t } = useI18n()
const forumStore = useForumStore()
const loading = ref(false)
const reason = ref('')

// Reset form when dialog opens
watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      reason.value = ''
    }
  },
)

async function handleFlag() {
  if (!props.postId || !reason.value.trim()) {
    toast.error(t('forum.toast.reasonRequired'))
    return
  }

  loading.value = true
  try {
    await forumStore.flagPost(props.postId, reason.value.trim())
    toast.success(t('forum.toast.flaggedSuccessfully'))
    emit('update:open', false)
    emit('success')
  } catch (error) {
    toast.error(t('forum.toast.failedToFlag'))
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
          {{ t('forum.flag.title') }}
        </DialogTitle>
        <DialogDescription>
          {{ t('forum.flag.description') }}
        </DialogDescription>
      </DialogHeader>

      <div class="space-y-4 py-4">
        <div class="space-y-2">
          <Label for="reason"
            >{{ t('forum.flag.reasonLabel') }} <span class="text-destructive">*</span></Label
          >
          <Input
            id="reason"
            v-model="reason"
            :placeholder="t('forum.flag.reasonPlaceholder')"
            :disabled="loading"
          />
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="$emit('update:open', false)" :disabled="loading">
          {{ t('forum.flag.cancel') }}
        </Button>
        <Button
          variant="default"
          class="bg-amber-600 hover:bg-amber-700"
          @click="handleFlag"
          :disabled="loading || !reason.trim()"
        >
          <IconLoader v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
          <IconFlag v-else class="mr-2 h-4 w-4" />
          {{ t('forum.flag.confirm') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
