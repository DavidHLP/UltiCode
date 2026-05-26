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
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { IconChecks, IconCheck, IconX, IconLoader2 } from '@tabler/icons-vue'
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
    <DialogContent class="terminal-card border-[var(--silver-300)]">
      <DialogHeader
        class="terminal-card-header border-b border-[var(--silver-300)] bg-[var(--surface-sunken)] px-4 py-3 -mx-6 -mt-6"
      >
        <DialogTitle
          class="flex items-center gap-2 font-data text-sm uppercase tracking-wider text-[var(--terminal-amber)]"
        >
          <IconChecks class="h-4 w-4" />
          &gt; {{ t('moderation.dialogs.confirmBatchTitle') }}
        </DialogTitle>
        <DialogDescription class="font-data text-xs text-[var(--silver-400)]">
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
          <Label class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">
            {{ t('moderation.actionPanel.selectAction') }}
          </Label>
          <div class="mt-2 flex gap-2">
            <Button
              :variant="batchAction === ModerationActionType.RESOLVED ? 'default' : 'terminal'"
              :class="[
                'h-9 font-data text-xs',
                batchAction === ModerationActionType.RESOLVED
                  ? 'border-[var(--terminal-green)] text-[var(--terminal-green)] bg-[color-mix(in_oklch,_var(--terminal-green)_10%,_transparent)]'
                  : 'border-[var(--silver-300)] hover:border-[var(--terminal-green)] hover:text-[var(--terminal-green)]',
              ]"
              size="sm"
              @click="batchAction = ModerationActionType.RESOLVED"
            >
              <IconCheck class="h-3.5 w-3.5 mr-1.5" />
              {{ t('moderation.actions.RESOLVED') }}
            </Button>
            <Button
              :variant="batchAction === ModerationActionType.DISMISSED ? 'default' : 'terminal'"
              :class="[
                'h-9 font-data text-xs',
                batchAction === ModerationActionType.DISMISSED
                  ? 'border-[var(--terminal-red)] text-[var(--terminal-red)] bg-[color-mix(in_oklch,_var(--terminal-red)_10%,_transparent)]'
                  : 'border-[var(--silver-300)] hover:border-[var(--terminal-red)] hover:text-[var(--terminal-red)]',
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
          <Label
            for="batch-notes"
            class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]"
          >
            {{ t('moderation.actionPanel.addNote') }}
          </Label>
          <Textarea
            id="batch-notes"
            v-model="batchNote"
            :placeholder="t('moderation.actionPanel.notePlaceholder')"
            rows="3"
            class="mt-2 font-data text-sm border-[var(--silver-300)] hover:border-[var(--accent-electric)] bg-transparent placeholder:text-[var(--silver-400)]"
          />
        </div>
      </div>
      <DialogFooter class="gap-2">
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)] hover:border-[var(--silver-500)]"
          @click="emit('update:open', false)"
        >
          {{ t('moderation.dialogs.cancel') }}
        </Button>
        <Button
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[color-mix(in_oklch,_var(--terminal-green)_10%,_transparent)]"
          :disabled="batchSaving"
          @click="handleBatchAction"
        >
          <IconLoader2 v-if="batchSaving" class="h-3.5 w-3.5 mr-1.5 animate-spin" />
          <IconChecks v-else class="h-3.5 w-3.5 mr-1.5" />
          {{ batchSaving ? t('common.saving') : t('moderation.dialogs.confirm') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
