<script setup lang="ts">
import { ref } from 'vue'
import { toast } from 'vue-sonner'
import { useI18n } from 'vue-i18n'
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
import { useSolutionsStore } from '@/stores/admin/solutions'

const props = defineProps<{
  open: boolean
  solutionId: string | null
  solutionTitle: string | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const solutionsStore = useSolutionsStore()
const loading = ref(false)
const { t } = useI18n()

async function handleDelete() {
  if (!props.solutionId) return

  loading.value = true
  try {
    await solutionsStore.deleteSolution(props.solutionId)
    toast.success(t('solutions.toast.deletedSuccessfully'))
    emit('update:open', false)
    emit('success')
  } catch (error) {
    toast.error(t('solutions.toast.failedToDelete'))
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
          {{ t('solutions.delete.title') }}
        </DialogTitle>
        <DialogDescription>
          <span v-html="t('solutions.delete.description', { title: solutionTitle })"></span>
        </DialogDescription>
      </DialogHeader>

      <DialogFooter>
        <Button variant="outline" @click="$emit('update:open', false)" :disabled="loading">
          {{ t('solutions.delete.cancel') }}
        </Button>
        <Button variant="destructive" @click="handleDelete" :disabled="loading">
          <IconLoader v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
          {{ t('solutions.delete.confirm') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
