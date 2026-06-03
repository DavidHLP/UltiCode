<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  IconAlertTriangle,
  IconCheck,
  IconEye,
  IconEyeOff,
  IconRefresh,
  IconTrash,
  IconX,
} from '@tabler/icons-vue'

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'

interface Props {
  open: boolean
  action: 'publish' | 'unpublish' | 'delete' | 'restore'
  count: number
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:open': [value: boolean]
  confirm: []
}>()

const { t } = useI18n()

const actionConfig = computed(() => {
  switch (props.action) {
    case 'publish':
      return {
        title: t('problems.bulk.publishTitle'),
        description: t('problems.bulk.publishDescription', { count: props.count }),
        icon: IconEye,
        iconColor: 'text-emerald-600',
        confirmText: t('problems.bulk.confirmPublish'),
        variant: 'default' as const,
      }
    case 'unpublish':
      return {
        title: t('problems.bulk.unpublishTitle'),
        description: t('problems.bulk.unpublishDescription', { count: props.count }),
        icon: IconEyeOff,
        iconColor: 'text-amber-600',
        confirmText: t('problems.bulk.confirmUnpublish'),
        variant: 'default' as const,
      }
    case 'delete':
      return {
        title: t('problems.bulk.deleteTitle'),
        description: t('problems.bulk.deleteDescription', { count: props.count }),
        icon: IconTrash,
        iconColor: 'text-destructive',
        confirmText: t('problems.bulk.confirmDelete'),
        variant: 'destructive' as const,
      }
    case 'restore':
      return {
        title: t('problems.bulk.restoreTitle'),
        description: t('problems.bulk.restoreDescription', { count: props.count }),
        icon: IconRefresh,
        iconColor: 'text-blue-600',
        confirmText: t('problems.bulk.confirmRestore'),
        variant: 'default' as const,
      }
    default:
      return {
        title: t('problems.bulk.publishTitle'),
        description: t('problems.bulk.publishDescription', { count: props.count }),
        icon: IconEye,
        iconColor: 'text-emerald-600',
        confirmText: t('problems.bulk.confirmPublish'),
        variant: 'default' as const,
      }
  }
})

function handleConfirm() {
  emit('confirm')
}

function handleCancel() {
  emit('update:open', false)
}
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="sm:max-w-[425px]">
      <DialogHeader>
        <div class="flex items-center gap-3">
          <div class="rounded-full bg-muted p-2">
            <component :is="actionConfig.icon" :class="`h-5 w-5 ${actionConfig.iconColor}`" />
          </div>
          <div>
            <DialogTitle>{{ actionConfig.title }}</DialogTitle>
            <DialogDescription class="mt-1">
              {{ actionConfig.description }}
            </DialogDescription>
          </div>
        </div>
      </DialogHeader>

      <div class="rounded-none bg-muted p-4">
        <div class="flex items-start gap-3">
          <IconAlertTriangle class="h-5 w-5 text-amber-600 shrink-0 mt-0.5" />
          <div class="flex-1">
            <p class="text-sm font-medium">{{ t('problems.bulk.warning') }}</p>
            <p class="text-sm text-muted-foreground mt-1">
              {{ t('problems.bulk.warningDescription') }}
            </p>
          </div>
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="handleCancel">
          <IconX class="mr-2 h-4 w-4" />
          {{ t('common.cancel') }}
        </Button>
        <Button :variant="actionConfig.variant" @click="handleConfirm">
          <IconCheck class="mr-2 h-4 w-4" />
          {{ actionConfig.confirmText }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
