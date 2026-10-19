<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@ulticode/design-system/ui/dialog'
import { Button } from '@ulticode/design-system/ui/button'
import { Label } from '@ulticode/design-system/ui/label'
import { Textarea } from '@ulticode/design-system/ui/textarea'
import {
  CheckCheck as IconChecks,
  Check as IconCheck,
  X as IconX,
  Loader2 as IconLoader2,
} from '@lucide/vue'

import { ModerationActionType, type ModerationQueueItem } from '@/api/admin/moderation'
import { useModerationStore } from '@/stores/admin/moderation'

const { t } = useI18n()
const store = useModerationStore()

const props = defineProps<{
  open: boolean
  selectedItems: ModerationQueueItem[]
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  complete: []
}>()

const batchAction = ref<ModerationActionType>(ModerationActionType.RESOLVED)
const batchNote = ref('')
const batchSaving = ref(false)

async function handleBatchAction() {
  if (props.selectedItems.length === 0) return

  batchSaving.value = true
  try {
    const result = await store.batchAction({
      queueIds: props.selectedItems.map((item) => item.id),
      action: batchAction.value,
      note: batchNote.value || undefined,
    })

    const successCount = result.successCount
    const failCount = result.failureCount

    if (failCount === 0) {
      toast.success(t('moderation.toast.batchCompleted'))
    } else {
      toast.warning(`${successCount} succeeded, ${failCount} failed`)
    }

    emit('update:open', false)
    emit('complete')
  } catch (error) {
    console.error('Failed to perform batch action:', error)
    toast.error(t('moderation.toast.error'))
  } finally {
    batchSaving.value = false
  }
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent>
      <DialogHeader class="flex items-center gap-2 px-4 py-3 px-4 py-3 -mx-6 -mt-6">
        <DialogTitle class="flex items-center gap-2">
          <IconChecks class="h-4 w-4" />
          &gt; {{ t('moderation.dialogs.confirmBatchTitle') }}
        </DialogTitle>
        <DialogDescription>
          {{
            t('moderation.dialogs.confirmBatchMessage', {
              count: selectedItems.length,
              action:
                batchAction === ModerationActionType.RESOLVED
                  ? t('moderation.actions.RESOLVED').toLowerCase()
                  : t('moderation.actions.DISMISSED').toLowerCase(),
            })
          }}
        </DialogDescription>
      </DialogHeader>
      <div class="space-y-4 pt-4">
        <div>
          <Label>
            {{ t('moderation.actionPanel.selectAction') }}
          </Label>
          <div class="mt-2 flex gap-2">
            <Button
              :variant="batchAction === ModerationActionType.RESOLVED ? 'default' : 'outline'"
              :class="[
                'h-9 font-mono text-xs',
                batchAction === ModerationActionType.RESOLVED
                  ? 'border-status-success-mark text-foreground bg-[color-mix(in_oklch,_var(--status-success-mark)_10%,_transparent)]'
                  : 'border-border hover:border-status-success-mark hover:text-foreground',
              ]"
              size="sm"
              @click="batchAction = ModerationActionType.RESOLVED"
            >
              <IconCheck class="h-3.5 w-3.5 mr-1.5" />
              {{ t('moderation.actions.RESOLVED') }}
            </Button>
            <Button
              :variant="batchAction === ModerationActionType.DISMISSED ? 'default' : 'outline'"
              :class="[
                'h-9 font-mono text-xs',
                batchAction === ModerationActionType.DISMISSED
                  ? 'border-status-error-mark text-foreground bg-[color-mix(in_oklch,_var(--status-error-mark)_10%,_transparent)]'
                  : 'border-border hover:border-status-error-mark hover:text-foreground',
              ]"
              size="sm"
              @click="batchAction = ModerationActionType.DISMISSED"
            >
              <IconX class="h-3.5 w-3.5 mr-1.5" />
              {{ t('moderation.actions.DISMISSED') }}
            </Button>
          </div>
        </div>
        <div>
          <Label for="batch-notes">
            {{ t('moderation.actionPanel.addNote') }}
          </Label>
          <Textarea
            id="batch-notes"
            v-model="batchNote"
            :placeholder="t('moderation.actionPanel.notePlaceholder')"
            rows="3"
            class="mt-2 placeholder:text-muted-foreground"
          />
        </div>
      </div>
      <DialogFooter class="gap-2">
        <Button variant="outline" size="sm" @click="emit('update:open', false)">
          {{ t('moderation.dialogs.cancel') }}
        </Button>
        <Button variant="outline" size="sm" :disabled="batchSaving" @click="handleBatchAction">
          <IconLoader2 v-if="batchSaving" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
          <IconChecks v-else class="h-3.5 w-3.5 mr-1.5" />
          {{ batchSaving ? t('common.saving') : t('moderation.dialogs.confirm') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
