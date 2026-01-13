<script setup lang="ts">
import { ref } from 'vue'
import { toast } from 'vue-sonner'
import { useI18n } from 'vue-i18n'
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
const reason = ref('')
const { t } = useI18n()

async function handleFlag() {
  if (!props.solutionId) return
  if (!reason.value.trim()) {
    toast.error(t('solutions.toast.reasonRequired'))
    return
  }

  loading.value = true
  try {
    await solutionsStore.flagSolution(props.solutionId, { reason: reason.value })
    toast.success(t('solutions.toast.flaggedSuccessfully'))
    reason.value = ''
    emit('update:open', false)
    emit('success')
  } catch (error) {
    toast.error(t('solutions.toast.failedToFlag'))
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
          {{ t('solutions.flag.title') }}
        </DialogTitle>
        <DialogDescription>
          <span v-html="t('solutions.flag.description', { title: solutionTitle })"></span>
        </DialogDescription>
      </DialogHeader>

      <div class="grid gap-4 py-4">
        <div class="space-y-2">
          <Label for="reason">{{ t('solutions.flag.reasonLabel') }}</Label>
          <Textarea
            id="reason"
            v-model="reason"
            :placeholder="t('solutions.flag.reasonPlaceholder')"
            class="min-h-[100px]"
          />
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="$emit('update:open', false)" :disabled="loading">
          {{ t('solutions.flag.cancel') }}
        </Button>
        <Button
          class="bg-amber-600 hover:bg-amber-700 text-white"
          @click="handleFlag"
          :disabled="loading"
        >
          <IconLoader v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
          {{ t('solutions.flag.confirm') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
