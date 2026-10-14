<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@ulticode/design-system/ui/dialog'
import SemanticBadge from '@ulticode/badge-config/SemanticBadge.vue'
import { CONTENT_FLAG_COLOR_MAP, type SemanticColor } from '@ulticode/badge-config'
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

const statusColor = computed((): SemanticColor => {
  if (!flagStatus.value) return 'neutral'
  return CONTENT_FLAG_COLOR_MAP[flagStatus.value] ?? 'neutral'
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
        <DialogTitle class="flex items-center gap-2">
          <span class="text-muted-foreground text-xs">info</span>
          {{ t('problems.flagInfo.title') }}
        </DialogTitle>
      </DialogHeader>

      <div v-if="problem" class="space-y-4">
        <!-- Problem Title -->
        <div class="border-b border-border dark:border-border pb-3">
          <span class="font-mono text-xs uppercase tracking-wider text-muted-foreground">
            Problem
          </span>
          <p class="text-sm font-medium mt-1">{{ problem.title }}</p>
        </div>

        <!-- Status -->
        <div class="flex items-center justify-between">
          <span class="font-mono text-xs uppercase tracking-wider text-muted-foreground">
            {{ t('problems.flagInfo.status') }}
          </span>
          <SemanticBadge :color="statusColor" size="sm" class="font-mono uppercase">
            {{ statusLabel }}
          </SemanticBadge>
        </div>

        <!-- Reason -->
        <div>
          <span class="font-mono text-xs uppercase tracking-wider text-muted-foreground">
            {{ t('problems.flagInfo.reason') }}
          </span>
          <p class="text-sm mt-1 p-2 bg-muted border border-border dark:border-border rounded-lg">
            {{ problem.flagReason || t('problems.flagInfo.noReason') }}
          </p>
        </div>

        <!-- Reported By -->
        <div v-if="problem.flagReportedBy" class="flex items-center justify-between">
          <span class="font-mono text-xs uppercase tracking-wider text-muted-foreground">
            {{ t('problems.flagInfo.reportedBy') }}
          </span>
          <span class="text-sm text-foreground dark:text-muted-foreground">
            {{ problem.flagReportedBy }}
          </span>
        </div>

        <!-- Reported At -->
        <div v-if="problem.flagReportedAt" class="flex items-center justify-between">
          <span class="font-mono text-xs uppercase tracking-wider text-muted-foreground">
            {{ t('problems.flagInfo.reportedAt') }}
          </span>
          <span class="text-sm text-foreground dark:text-muted-foreground font-mono">
            {{ formatDate(problem.flagReportedAt) }}
          </span>
        </div>

        <!-- Reviewed By -->
        <div v-if="problem.flagReviewedBy" class="flex items-center justify-between">
          <span class="font-mono text-xs uppercase tracking-wider text-muted-foreground">
            {{ t('problems.flagInfo.reviewedBy') }}
          </span>
          <span class="text-sm text-foreground dark:text-muted-foreground">
            {{ problem.flagReviewedBy }}
          </span>
        </div>

        <!-- Reviewed At -->
        <div v-if="problem.flagReviewedAt" class="flex items-center justify-between">
          <span class="font-mono text-xs uppercase tracking-wider text-muted-foreground">
            {{ t('problems.flagInfo.reviewedAt') }}
          </span>
          <span class="text-sm text-foreground dark:text-muted-foreground font-mono">
            {{ formatDate(problem.flagReviewedAt) }}
          </span>
        </div>

        <!-- Notes -->
        <div v-if="problem.flagNotes">
          <span class="font-mono text-xs uppercase tracking-wider text-muted-foreground">
            {{ t('problems.flagInfo.notes') }}
          </span>
          <p class="text-sm mt-1 p-2 bg-muted border border-border dark:border-border rounded-lg">
            {{ problem.flagNotes }}
          </p>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
