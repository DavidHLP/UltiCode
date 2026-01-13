<script setup lang="ts">
import { ref, computed, toRefs } from 'vue'
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
import { useTagsStore } from '@/stores/admin/tags'
import { TagType } from '@/api/admin/tags'

const { t } = useI18n()

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

const { tagName } = toRefs(props)

const deleteDescription = computed(() => {
  return t('tags.delete.description', { name: tagName.value }).replace(/<[^>]*>/g, '')
})

async function handleDelete() {
  if (!props.tagId || !props.tagType) return

  loading.value = true
  try {
    await tagsStore.deleteTag(props.tagId, props.tagType)
    toast.success(t('tags.toast.deletedSuccessfully'))
    emit('update:open', false)
    emit('success')
  } catch (error) {
    toast.error(t('tags.toast.failedToDelete'))
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
          {{ t('tags.delete.title') }}
        </DialogTitle>
        <DialogDescription>
          {{ deleteDescription }}
        </DialogDescription>
      </DialogHeader>

      <DialogFooter>
        <Button variant="outline" @click="$emit('update:open', false)" :disabled="loading">
          {{ t('common.cancel') }}
        </Button>
        <Button variant="destructive" @click="handleDelete" :disabled="loading">
          <IconLoader v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
          {{ t('tags.delete.confirm') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
