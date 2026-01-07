<script setup lang="ts">
import { ref, computed } from 'vue'
import { toast } from 'vue-sonner'
import { IconGitMerge, IconLoader } from '@tabler/icons-vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { useTagsStore } from '@/stores/admin/tags'
import { TagType } from '@/api/admin/tags'

const props = defineProps<{
  open: boolean
  sourceTagId: string | null
  sourceTagName: string | null
  tagType: TagType | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const tagsStore = useTagsStore()
const loading = ref(false)
const targetTagId = ref<string>('')

// Filter out the source tag from available targets
const availableTargets = computed(() => {
  if (!props.sourceTagId) return tagsStore.tags
  return tagsStore.tags.filter(t => t.id !== props.sourceTagId)
})

async function handleMerge() {
  if (!props.sourceTagId || !targetTagId.value || !props.tagType) return

  loading.value = true
  try {
    await tagsStore.mergeTag({
      sourceId: props.sourceTagId,
      targetTagId: targetTagId.value,
      type: props.tagType,
    })
    toast.success('Tags merged successfully')
    emit('update:open', false)
    emit('success')
  } catch (error) {
    toast.error('Failed to merge tags')
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
        <DialogTitle class="flex items-center gap-2">
          <IconGitMerge class="h-5 w-5" />
          Merge Tags
        </DialogTitle>
        <DialogDescription>
          Merge <span class="font-medium text-foreground">"{{ sourceTagName }}"</span> into another tag.
          All relations will be moved to the target tag, and the source tag will be deleted.
        </DialogDescription>
      </DialogHeader>

      <div class="grid gap-4 py-4">
        <div class="grid gap-2">
          <Label for="target-tag">Target Tag</Label>
          <Select v-model="targetTagId">
            <SelectTrigger id="target-tag">
              <SelectValue placeholder="Select a tag to merge into" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem
                v-for="tag in availableTargets"
                :key="tag.id"
                :value="tag.id"
              >
                {{ tag.name }}
              </SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="$emit('update:open', false)" :disabled="loading">
          Cancel
        </Button>
        <Button @click="handleMerge" :disabled="loading || !targetTagId">
          <IconLoader v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
          Merge Tags
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
