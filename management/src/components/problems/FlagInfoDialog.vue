<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Badge } from '@/components/ui/badge'
import type { Problem } from '@/api/admin/problems'
import { formatDate } from '@/lib/format/date'

const props = defineProps<{
  open: boolean
  problem: Problem | null
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
}>()

const { t } = useI18n()

const flagStatus = computed(() => props.problem?.flagStatus || 'PENDING')

const statusStyles = computed((): { bg: string; border: string; text: string } => {
  const styles = {
    PENDING: {
      bg: 'bg-[color-mix(in_oklch,_var(--terminal-red)_15%,_transparent)]',
      border: 'border-[color-mix(in_oklch,_var(--terminal-red)_40%,_transparent)]',
      text: 'text-[var(--terminal-red)]',
    },
    REVIEWED: {
      bg: 'bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)]',
      border: 'border-[color-mix(in_oklch,_var(--terminal-amber)_40%,_transparent)]',
      text: 'text-[var(--terminal-amber)]',
    },
    RESOLVED: {
      bg: 'bg-[color-mix(in_oklch,_var(--terminal-green)_15%,_transparent)]',
      border: 'border-[color-mix(in_oklch,_var(--terminal-green)_40%,_transparent)]',
      text: 'text-[var(--terminal-green)]',
    },
    DISMISSED: {
      bg: 'bg-[var(--silver-100)] dark:bg-[var(--silver-800)]',
      border: 'border-[var(--silver-300)] dark:border-[var(--silver-600)]',
      text: 'text-[var(--silver-500)]',
    },
  } as const
  const status = (flagStatus.value || 'PENDING') as keyof typeof styles
  return styles[status]
})

const statusLabel = computed(() => {
  const key = `moderation.status${flagStatus.value.charAt(0).toUpperCase() + flagStatus.value.slice(1).toLowerCase()}`
  return t(key)
})

function handleClose(value: boolean) {
  emit('update:open', value)
}
</script>

<template>
  <Dialog :open="open" @update:open="handleClose">
    <DialogContent class="max-w-md">
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2 font-data text-sm uppercase tracking-wider">
          <span class="terminal-prompt text-xs">info</span>
          {{ t('problems.flagInfo.title') }}
        </DialogTitle>
      </DialogHeader>

      <div v-if="problem" class="space-y-4">
        <!-- Problem Title -->
        <div class="border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] pb-3">
          <span class="font-data text-[10px] uppercase tracking-wider text-[var(--silver-500)]">
            Problem
          </span>
          <p class="text-sm font-medium mt-1">{{ problem.title }}</p>
        </div>

        <!-- Status -->
        <div class="flex items-center justify-between">
          <span class="font-data text-[10px] uppercase tracking-wider text-[var(--silver-500)]">
            {{ t('problems.flagInfo.status') }}
          </span>
          <Badge
            :class="[
              'font-data text-[10px] uppercase px-2 py-1 border',
              statusStyles.bg,
              statusStyles.border,
              statusStyles.text,
            ]"
          >
            {{ statusLabel }}
          </Badge>
        </div>

        <!-- Reason -->
        <div>
          <span class="font-data text-[10px] uppercase tracking-wider text-[var(--silver-500)]">
            {{ t('problems.flagInfo.reason') }}
          </span>
          <p
            class="text-sm mt-1 p-2 bg-[var(--surface-sunken)] border border-[var(--silver-200)] dark:border-[var(--silver-300)] rounded-sm"
          >
            {{ problem.flagReason || t('problems.flagInfo.noReason') }}
          </p>
        </div>

        <!-- Reported By -->
        <div v-if="problem.flagReportedBy" class="flex items-center justify-between">
          <span class="font-data text-[10px] uppercase tracking-wider text-[var(--silver-500)]">
            {{ t('problems.flagInfo.reportedBy') }}
          </span>
          <span class="text-sm text-[var(--silver-600)] dark:text-[var(--silver-400)]">
            {{ problem.flagReportedBy }}
          </span>
        </div>

        <!-- Reported At -->
        <div v-if="problem.flagReportedAt" class="flex items-center justify-between">
          <span class="font-data text-[10px] uppercase tracking-wider text-[var(--silver-500)]">
            {{ t('problems.flagInfo.reportedAt') }}
          </span>
          <span class="text-sm text-[var(--silver-600)] dark:text-[var(--silver-400)] font-data">
            {{ formatDate(problem.flagReportedAt) }}
          </span>
        </div>

        <!-- Reviewed By -->
        <div v-if="problem.flagReviewedBy" class="flex items-center justify-between">
          <span class="font-data text-[10px] uppercase tracking-wider text-[var(--silver-500)]">
            {{ t('problems.flagInfo.reviewedBy') }}
          </span>
          <span class="text-sm text-[var(--silver-600)] dark:text-[var(--silver-400)]">
            {{ problem.flagReviewedBy }}
          </span>
        </div>

        <!-- Reviewed At -->
        <div v-if="problem.flagReviewedAt" class="flex items-center justify-between">
          <span class="font-data text-[10px] uppercase tracking-wider text-[var(--silver-500)]">
            {{ t('problems.flagInfo.reviewedAt') }}
          </span>
          <span class="text-sm text-[var(--silver-600)] dark:text-[var(--silver-400)] font-data">
            {{ formatDate(problem.flagReviewedAt) }}
          </span>
        </div>

        <!-- Notes -->
        <div v-if="problem.flagNotes">
          <span class="font-data text-[10px] uppercase tracking-wider text-[var(--silver-500)]">
            {{ t('problems.flagInfo.notes') }}
          </span>
          <p
            class="text-sm mt-1 p-2 bg-[var(--surface-sunken)] border border-[var(--silver-200)] dark:border-[var(--silver-300)] rounded-sm"
          >
            {{ problem.flagNotes }}
          </p>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
