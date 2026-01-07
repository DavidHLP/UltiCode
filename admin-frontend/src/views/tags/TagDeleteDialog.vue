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
import { useTagsStore } from '@/stores/admin/tags'
import { TagType } from '@/api/admin/tags'

const props = defineProps<{
  open: boolean
  tagId: string | null
  tagName: string | null
  tagType: TagType | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const tagsStore = useTagsStore()
const loading = ref(false)

async function handleDelete() {
  if (!props.tagId || !props.tagType) return

  loading.value = true
  try {
    await tagsStore.deleteTag(props.tagId, props.tagType)
    toast.success('Tag deleted successfully')
    emit('update:open', false)
    emit('success')
  } catch (error) {
    toast.error('Failed to delete tag')
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
          Delete Tag
        </DialogTitle>
        <DialogDescription>
          Are you sure you want to delete the tag
          <span class="font-medium text-foreground">"{{ tagName }}"</span>? This action cannot be
          undone.
        </DialogDescription>
      </DialogHeader>

      <DialogFooter>
        <Button variant="outline" @click="$emit('update:open', false)" :disabled="loading">
          Cancel
        </Button>
        <Button variant="destructive" @click="handleDelete" :disabled="loading">
          <IconLoader v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
          Delete Tag
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
