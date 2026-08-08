<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import {
  IconCheck,
  IconX,
  IconTrash,
  IconEyeOff,
  IconAlertCircle,
  IconClock,
  IconBan,
  IconScale,
  IconUser,
} from '@tabler/icons-vue'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { type ModerationAction, ModerationActionType } from '@/api/admin/moderation'

interface Props {
  actions: ModerationAction[]
  loading?: boolean
}

defineProps<Props>()
const { t } = useI18n()

const actionConfig = computed(() => ({
  [ModerationActionType.DELETED]: {
    icon: IconTrash,
    color: 'text-[var(--terminal-red)]',
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-red)_15%,_transparent)]',
  },
  [ModerationActionType.HIDDEN]: {
    icon: IconEyeOff,
    color: 'text-[var(--terminal-amber)]',
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)]',
  },
  [ModerationActionType.RESTORED]: {
    icon: IconCheck,
    color: 'text-[var(--terminal-green)]',
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-green)_15%,_transparent)]',
  },
  [ModerationActionType.WARNED]: {
    icon: IconAlertCircle,
    color: 'text-[var(--terminal-amber)]',
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)]',
  },
  [ModerationActionType.TEMP_BANNED]: {
    icon: IconClock,
    color: 'text-[var(--terminal-amber)]',
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)]',
  },
  [ModerationActionType.PERM_BANNED]: {
    icon: IconBan,
    color: 'text-[var(--terminal-red)]',
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-red)_15%,_transparent)]',
  },
  [ModerationActionType.DISMISSED]: {
    icon: IconX,
    color: 'text-[var(--terminal-red)]',
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-red)_15%,_transparent)]',
  },
  [ModerationActionType.RESOLVED]: {
    icon: IconCheck,
    color: 'text-[var(--terminal-green)]',
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-green)_15%,_transparent)]',
  },
  [ModerationActionType.APPEAL_PENDING]: {
    icon: IconScale,
    color: 'text-[var(--terminal-purple)]',
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-purple)_15%,_transparent)]',
  },
  [ModerationActionType.APPEAL_APPROVED]: {
    icon: IconCheck,
    color: 'text-[var(--terminal-green)]',
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-green)_15%,_transparent)]',
  },
  [ModerationActionType.APPEAL_REJECTED]: {
    icon: IconX,
    color: 'text-[var(--terminal-red)]',
    bg: 'bg-[color-mix(in_oklch,_var(--terminal-red)_15%,_transparent)]',
  },
}))

function getActionConfig(actionType: ModerationActionType) {
  return (
    actionConfig.value[actionType] || {
      icon: IconCheck,
      color: 'text-[var(--silver-500)]',
      bg: 'bg-[var(--surface-sunken)]',
    }
  )
}

function getRelativeTime(date: Date | string): string {
  const d = typeof date === 'string' ? new Date(date) : date
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMins / 60)
  const diffDays = Math.floor(diffHours / 24)

  if (diffMins < 1) return 'just now'
  if (diffMins < 60) return `${diffMins}m ago`
  if (diffHours < 24) return `${diffHours}h ago`
  if (diffDays < 7) return `${diffDays}d ago`
  return formatDateTimeByLocale(date)
}
</script>

<template>
  <Card
    class="border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
  >
    <CardHeader class="pb-3">
      <CardTitle class="flex items-center gap-2 text-sm font-data uppercase tracking-wider">
        <IconClock class="h-4 w-4 text-[var(--silver-500)]" />
        <span class="text-[var(--silver-500)]">
          {{ t('moderation.detail.actionsTitle', { count: actions.length }) }}
        </span>
      </CardTitle>
    </CardHeader>
    <CardContent>
      <!-- Loading State -->
      <div v-if="loading" class="flex items-center justify-center py-8">
        <div class="animate-pulse text-xs font-data text-[var(--silver-400)]">
          {{ t('moderation.terminal.loading') }}
        </div>
      </div>

      <!-- Empty State -->
      <div
        v-else-if="actions.length === 0"
        class="flex flex-col items-center justify-center py-8 text-center"
      >
        <IconClock class="h-8 w-8 text-[var(--silver-400)] mb-2" />
        <p class="text-xs font-data text-[var(--silver-400)]">
          {{ t('moderation.detail.noActions') }}
        </p>
      </div>

      <!-- Timeline -->
      <div v-else class="relative space-y-4">
        <!-- Timeline line -->
        <div class="absolute left-4 top-0 bottom-0 w-px bg-[var(--silver-300)]" />

        <!-- Action items -->
        <div v-for="action in actions" :key="action.id" class="relative flex gap-4">
          <!-- Icon circle -->
          <div
            :class="[
              'relative z-10 flex h-8 w-8 items-center justify-center rounded-full border',
              getActionConfig(action.action).bg,
              'border-[var(--silver-300)]',
            ]"
          >
            <component
              :is="getActionConfig(action.action).icon"
              :class="['h-4 w-4', getActionConfig(action.action).color]"
            />
          </div>

          <!-- Content -->
          <div class="flex-1 pb-4">
            <div class="flex items-start justify-between gap-2">
              <div class="flex-1">
                <p class="text-sm font-medium">
                  {{ t(`moderation.actions.${action.action}`, action.action) }}
                </p>
                <div class="flex items-center gap-2 mt-1 text-xs text-[var(--silver-500)]">
                  <IconUser class="h-3 w-3" />
                  <span>
                    {{
                      action.performer?.displayName ||
                      action.performer?.username ||
                      t('moderation.unknownReporter')
                    }}
                  </span>
                  <span class="text-[var(--silver-400)]">•</span>
                  <span class="font-data tabular-nums">
                    {{ getRelativeTime(action.createdAt) }}
                  </span>
                </div>
              </div>
            </div>

            <!-- Note -->
            <div
              v-if="action.note"
              class="mt-2 p-2 border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--background)]"
            >
              <p class="text-xs text-[var(--foreground)]">{{ action.note }}</p>
            </div>

            <!-- Duration (for temp bans) -->
            <div
              v-if="action.durationDays && action.action === ModerationActionType.TEMP_BANNED"
              class="mt-2 text-xs text-[var(--terminal-amber)]"
            >
              {{ t('moderation.detail.duration') }}:
              {{ t('moderation.detail.days', { count: action.durationDays }) }}
            </div>
          </div>
        </div>
      </div>
    </CardContent>
  </Card>
</template>
