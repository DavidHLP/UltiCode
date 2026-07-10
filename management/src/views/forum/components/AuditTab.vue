<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import { Skeleton } from '@/components/ui/skeleton'
import { TerminalBadge } from '@/components/ui/terminal'
import {
  IconCalendar,
  IconFlag,
  IconLock,
  IconPin,
  IconTrash,
  IconShield,
  IconActivity,
} from '@tabler/icons-vue'
import type { AuditLog } from '@/api/admin/audit'

defineProps<{
  auditHistory: AuditLog[]
  loading: boolean
}>()

const { t } = useI18n()

const actionConfig = computed(() => ({
  PIN_FORUM_POST: {
    label: t('forum.auditActions.PIN_FORUM_POST'),
    icon: IconPin,
    variant: 'info' as const,
  },
  UNPIN_FORUM_POST: {
    label: t('forum.auditActions.UNPIN_FORUM_POST'),
    icon: IconPin,
    variant: 'default' as const,
  },
  LOCK_FORUM_POST: {
    label: t('forum.auditActions.LOCK_FORUM_POST'),
    icon: IconLock,
    variant: 'warning' as const,
  },
  UNLOCK_FORUM_POST: {
    label: t('forum.auditActions.UNLOCK_FORUM_POST'),
    icon: IconLock,
    variant: 'default' as const,
  },
  DELETE_FORUM_POST: {
    label: t('forum.auditActions.DELETE_FORUM_POST'),
    icon: IconTrash,
    variant: 'error' as const,
  },
  FLAG_FORUM_POST: {
    label: t('forum.auditActions.FLAG_FORUM_POST'),
    icon: IconFlag,
    variant: 'error' as const,
  },
  UNFLAG_FORUM_POST: {
    label: t('forum.auditActions.UNFLAG_FORUM_POST'),
    icon: IconFlag,
    variant: 'success' as const,
  },
  BULK_DELETE_FORUM: {
    label: t('forum.auditActions.BULK_DELETE_FORUM'),
    icon: IconTrash,
    variant: 'error' as const,
  },
  BULK_PIN_FORUM: {
    label: t('forum.auditActions.BULK_PIN_FORUM'),
    icon: IconPin,
    variant: 'info' as const,
  },
}))

function getActionConfig(action: string) {
  return (
    actionConfig.value[action as keyof typeof actionConfig.value] || {
      label: t('forum.auditActions.' + action) || action,
      icon: IconActivity,
      variant: 'default' as const,
    }
  )
}


function getChangesText(entry: AuditLog): string | null {
  const changes: string[] = []

  if (entry.oldValues && Object.keys(entry.oldValues).length > 0) {
    changes.push(`${t('forum.audit.from')}: ${JSON.stringify(entry.oldValues)}`)
  }
  if (entry.newValues && Object.keys(entry.newValues).length > 0) {
    changes.push(`${t('forum.audit.to')}: ${JSON.stringify(entry.newValues)}`)
  }

  return changes.length > 0 ? changes.join('\n') : null
}
</script>

<template>
  <div class="space-y-4">
    <!-- Terminal Header -->
    <div class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]">
      <div
        class="px-4 py-3 flex items-center gap-3 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)]"
      >
        <div class="flex items-center gap-2">
          
          <span class="terminal-cursor" />
        </div>
        <IconShield class="h-4 w-4 text-[var(--terminal-cyan)]" />
        <h3 class="text-sm font-medium text-[var(--foreground)]">{{ t('forum.tabs.audit') }}</h3>
      </div>
      <div class="px-4 py-2 bg-[var(--surface-sunken)]">
        <span class="font-data text-xs text-[var(--silver-400)]">
          &gt; {{ t('forum.audit.description') }}
        </span>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="space-y-3">
      <div
        v-for="i in 3"
        :key="i"
        class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-4 bg-[var(--card)]"
      >
        <div class="flex items-start gap-4">
          <Skeleton class="h-10 w-10 rounded-none" />
          <div class="flex-1 space-y-2">
            <Skeleton class="h-4 w-32 rounded-none" />
            <Skeleton class="h-3 w-48 rounded-none" />
            <Skeleton class="h-3 w-24 rounded-none" />
          </div>
        </div>
      </div>
    </div>

    <!-- Empty State -->
    <div
      v-else-if="auditHistory.length === 0"
      class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] p-8 text-center bg-[var(--card)]"
    >
      <div
        class="w-10 h-10 border border-[var(--silver-300)] flex items-center justify-center mx-auto mb-3"
      >
        <IconShield class="h-5 w-5 text-[var(--silver-400)]" />
      </div>
      <p class="font-data text-xs text-[var(--silver-400)]">
        &gt; {{ t('forum.audit.noAuditHistory') }}
      </p>
    </div>

    <!-- Audit Timeline -->
    <div v-else class="space-y-3">
      <div
        v-for="entry in auditHistory"
        :key="entry.id"
        class="border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)] overflow-hidden"
      >
        <!-- Header with action badge -->
        <div
          class="px-4 py-3 flex items-center justify-between border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
        >
          <div class="flex items-center gap-3">
            <div class="h-8 w-8 border border-[var(--silver-300)] flex items-center justify-center">
              <component
                :is="getActionConfig(entry.action).icon"
                :class="[
                  'h-4 w-4',
                  getActionConfig(entry.action).variant === 'error' && 'text-[var(--terminal-red)]',
                  getActionConfig(entry.action).variant === 'warning' &&
                    'text-[var(--terminal-amber)]',
                  getActionConfig(entry.action).variant === 'success' &&
                    'text-[var(--terminal-green)]',
                  getActionConfig(entry.action).variant === 'info' && 'text-[var(--terminal-cyan)]',
                  getActionConfig(entry.action).variant === 'default' && 'text-[var(--silver-400)]',
                ]"
              />
            </div>
            <div class="flex items-center gap-2">
              <span class="font-data text-sm text-[var(--foreground)]">{{
                entry.performer?.username
              }}</span>
              <span class="font-data text-xs text-[var(--silver-400)]">{{
                t('forum.audit.performed')
              }}</span>
            </div>
          </div>
          <TerminalBadge
            :variant="getActionConfig(entry.action).variant"
            :label="getActionConfig(entry.action).label"
          />
        </div>

        <!-- Content -->
        <div class="p-4">
          <!-- Timestamp -->
          <div class="flex items-center gap-2 mb-3">
            <IconCalendar class="h-3.5 w-3.5 text-[var(--silver-400)]" />
            <span class="font-data text-xs text-[var(--silver-400)] tabular-nums">
              {{ formatDateTimeByLocale(entry.createdAt) }}
            </span>
          </div>

          <!-- Changes -->
          <div
            v-if="getChangesText(entry)"
            class="border border-[var(--silver-300)] p-3 bg-[var(--surface-sunken)]"
          >
            <pre
              class="font-data text-xs text-[var(--silver-400)] whitespace-pre-wrap overflow-x-auto"
              >{{ getChangesText(entry) }}</pre
            >
          </div>

          <!-- Additional Info -->
          <div
            v-if="entry.ipAddress || entry.userAgent"
            class="mt-3 text-xs text-[var(--silver-400)]"
          >
            <div v-if="entry.ipAddress" class="flex items-center gap-2">
              <span class="terminal-label">ip:</span>
              <span class="font-data">{{ entry.ipAddress }}</span>
            </div>
            <div v-if="entry.userAgent" class="font-data truncate mt-1">
              {{ entry.userAgent }}
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
