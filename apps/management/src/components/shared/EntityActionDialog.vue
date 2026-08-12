<script setup lang="ts">
import { ref, watch, computed, type Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { IconAlertTriangle, IconBan, IconFlag, IconLoader } from '@tabler/icons-vue'
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
import { Input } from '@/components/ui/input'

export type ActionType = 'delete' | 'flag' | 'ban' | 'unban'

export interface EntityActionDialogProps<T extends string | number> {
  open: boolean
  entityId: T | null
  entityTitle?: string | null
  action: ActionType
  title?: string
  description?: string
  confirmLabel?: string
  cancelLabel?: string
  successLabel?: string
  errorLabel?: string
  requiresReason?: boolean
  reasonLabel?: string
  reasonPlaceholder?: string
  reasonRequiredLabel?: string
  onAction: (id: T, reason?: string) => Promise<void>
}

const props = withDefaults(defineProps<EntityActionDialogProps<string | number>>(), {
  entityTitle: null,
  title: undefined,
  description: undefined,
  confirmLabel: undefined,
  cancelLabel: undefined,
  successLabel: undefined,
  errorLabel: undefined,
  requiresReason: undefined,
  reasonLabel: undefined,
  reasonPlaceholder: undefined,
  reasonRequiredLabel: undefined,
})

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const { t } = useI18n()
const loading = ref(false)
const reason = ref('')

const isFlagAction = computed(() => props.action === 'flag')
const isBanAction = computed(() => props.action === 'ban')
const showReasonInput = computed(
  () => isFlagAction.value || isBanAction.value || props.requiresReason,
)

const defaultTitle = computed(() => {
  if (props.title) return props.title
  if (isFlagAction.value) return t('common.flag')
  if (isBanAction.value) return t('users.actions.banUser')
  if (props.action === 'unban') return t('users.actions.unbanUser')
  return t('common.delete')
})

const defaultDescription = computed(() => {
  if (props.description) return props.description
  if (isFlagAction.value) return t('common.flagDescription')
  if (isBanAction.value) {
    return t('users.actions.banUserDescription', {
      username: props.entityTitle || t('users.actions.thisUser'),
    })
  }
  if (props.action === 'unban') {
    return t('users.actions.unbanUserDescription', {
      username: props.entityTitle || t('users.actions.thisUser'),
    })
  }
  if (props.entityTitle) {
    return t('common.deleteDescriptionWithName', { name: props.entityTitle })
  }
  return t('common.deleteDescription')
})

const defaultConfirmLabel = computed(() => {
  if (props.confirmLabel) return props.confirmLabel
  if (isFlagAction.value) return t('common.flagConfirm')
  if (isBanAction.value) return t('users.actions.confirmBan')
  if (props.action === 'unban') return t('users.actions.confirmUnban')
  return t('common.deleteConfirm')
})

const defaultCancelLabel = computed(() => {
  if (props.cancelLabel) return props.cancelLabel
  return t('common.cancel')
})

const defaultSuccessLabel = computed(() => {
  if (props.successLabel) return props.successLabel
  if (isFlagAction.value) return t('common.flagSuccess')
  if (isBanAction.value) return t('users.toast.banSuccess')
  if (props.action === 'unban') return t('users.toast.unbanSuccess')
  return t('common.deleteSuccess')
})

const defaultErrorLabel = computed(() => {
  if (props.errorLabel) return props.errorLabel
  if (isFlagAction.value) return t('common.flagError')
  if (isBanAction.value) return t('users.toast.banFailed')
  if (props.action === 'unban') return t('users.toast.unbanFailed')
  return t('common.deleteError')
})

const defaultReasonLabel = computed(() => {
  if (props.reasonLabel) return props.reasonLabel
  return t('common.reasonLabel')
})

const defaultReasonPlaceholder = computed(() => {
  if (props.reasonPlaceholder) return props.reasonPlaceholder
  return t('common.reasonPlaceholder')
})

const defaultReasonRequiredLabel = computed(() => {
  if (props.reasonRequiredLabel) return props.reasonRequiredLabel
  return t('common.reasonRequired')
})

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) {
      reason.value = ''
    } else {
      loading.value = false
    }
  },
)

async function handleAction() {
  if (!props.entityId) return

  if (showReasonInput.value && !reason.value.trim()) {
    toast.error(defaultReasonRequiredLabel.value)
    return
  }

  loading.value = true
  try {
    await props.onAction(props.entityId, reason.value.trim())
    toast.success(defaultSuccessLabel.value)
    reason.value = ''
    emit('update:open', false)
    emit('success')
  } catch (error) {
    toast.error(defaultErrorLabel.value)
    console.error(error)
  } finally {
    loading.value = false
  }
}

const headerIcon = computed<Component>(() => {
  if (isFlagAction.value) return IconFlag
  if (isBanAction.value || props.action === 'unban') return IconBan
  return IconAlertTriangle
})

const headerClass = computed(() => {
  if (isFlagAction.value) return 'text-foreground-strong'
  if (isBanAction.value) return 'text-destructive'
  if (props.action === 'unban') return 'text-foreground-strong'
  return 'text-destructive'
})

const confirmButtonVariant = computed(() => {
  if (isFlagAction.value) {
    return 'default' as const
  }
  if (props.action === 'unban') {
    return 'default' as const
  }
  return 'destructive' as const
})

const confirmButtonClass = computed(() => {
  if (isFlagAction.value) {
    return 'bg-status-warning-surface text-foreground-strong border border-[var(--status-warning-mark)] hover:bg-status-warning-surface'
  }
  if (props.action === 'unban') {
    return 'bg-status-success-surface text-foreground-strong border border-[var(--status-success-mark)] hover:bg-status-success-surface'
  }
  return ''
})
</script>

<template>
  <Dialog :open="open" @update:open="$emit('update:open', $event)">
    <DialogContent>
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2 text-foreground-strong">
          <component :is="headerIcon" class="h-5 w-5" :class="headerClass" />
          {{ defaultTitle }}
        </DialogTitle>
        <DialogDescription>
          {{ defaultDescription }}
        </DialogDescription>
      </DialogHeader>

      <div v-if="showReasonInput" class="space-y-4 py-4">
        <div class="space-y-2">
          <Label for="reason">
            {{ defaultReasonLabel }} <span class="text-destructive">*</span>
          </Label>
          <Textarea
            v-if="action === 'flag' || action === 'ban'"
            id="reason"
            v-model="reason"
            :placeholder="defaultReasonPlaceholder"
            :disabled="loading"
            class="min-h-[100px]"
          />
          <Input
            v-else-if="action !== 'unban'"
            id="reason"
            v-model="reason"
            :placeholder="defaultReasonPlaceholder"
            :disabled="loading"
          />
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="$emit('update:open', false)" :disabled="loading">
          {{ defaultCancelLabel }}
        </Button>
        <Button
          :variant="confirmButtonVariant"
          :class="confirmButtonClass || undefined"
          @click="handleAction"
          :disabled="loading || (showReasonInput && !reason.trim())"
        >
          <IconLoader v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
          <component :is="headerIcon" v-else class="mr-2 h-4 w-4" />
          {{ defaultConfirmLabel }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
