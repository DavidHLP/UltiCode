<script setup lang="ts">
import { ref, watch } from 'vue'
import { useContestsStore } from '@/stores/admin/contests'
import { toast } from 'vue-sonner'
import { useI18n } from 'vue-i18n'
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
  contestId: string | null
  contestTitle: string | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const contestsStore = useContestsStore()
const { t } = useI18n()
const loading = ref(false)

watch(
  () => props.open,
  (isOpen) => {
    if (!isOpen) {
      loading.value = false
    }
  },
)

async function handleDelete() {
  if (!props.contestId) return
  loading.value = true
  try {
    await contestsStore.deleteContest(props.contestId)
    toast.success(t('contests.toast.deletedSuccessfully'))
    emit('success')
    emit('update:open', false)
  } catch {
    toast.error(t('contests.toast.failedToDelete'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle>{{ t('contests.delete.title') }}</DialogTitle>
        <DialogDescription>
          <span
            v-html="
              t('contests.delete.description', {
                title: contestTitle || t('contests.delete.thisContest'),
              })
            "
          />
        </DialogDescription>
      </DialogHeader>
      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)" :disabled="loading">
          {{ t('contests.delete.cancel') }}
        </Button>
        <Button variant="destructive" @click="handleDelete" :disabled="loading">
          {{ loading ? t('contests.delete.deleting') : t('contests.delete.confirm') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
