<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useProblemsStore } from '@/stores/admin/problems'
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
  problemId: string | null
  problemTitle: string | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const { t } = useI18n()
const problemsStore = useProblemsStore()
const loading = ref(false)

async function handleDelete() {
  if (!props.problemId) return
  loading.value = true
  try {
    await problemsStore.deleteProblem(props.problemId)
    toast.success(t('problems.toast.deleteSuccess'))
    emit('success')
    emit('update:open', false)
  } catch {
    toast.error(t('problems.toast.deleteFailed'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{ t('problems.dialog.delete.title') }}</DialogTitle>
        <DialogDescription>
          {{
            t('problems.dialog.delete.description', {
              title: problemTitle || t('problems.dialog.delete.thisProblem'),
            })
          }}
        </DialogDescription>
      </DialogHeader>
      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)" :disabled="loading">
          {{ t('common.cancel') }}
        </Button>
        <Button variant="destructive" @click="handleDelete" :disabled="loading">
          {{ loading ? t('problems.dialog.delete.deleting') : t('problems.dialog.delete.confirm') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
