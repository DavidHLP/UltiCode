<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import {
  IconCalendar,
  IconFlag,
  IconLock,
  IconPin,
  IconTrash,
  IconShield,
  IconActivity,
} from '@tabler/icons-vue'
import type { AuditEntry } from '@/api/admin/forum'

defineProps<{
  auditHistory: AuditEntry[]
  loading: boolean
}>()

const actionConfig = computed(() => ({
  PIN_FORUM_POST: {
    label: 'Pinned',
    icon: IconPin,
    variant: 'default' as const,
    color: 'text-blue-500',
  },
  UNPIN_FORUM_POST: {
    label: 'Unpinned',
    icon: IconPin,
    variant: 'secondary' as const,
    color: 'text-gray-500',
  },
  LOCK_FORUM_POST: {
    label: 'Locked',
    icon: IconLock,
    variant: 'default' as const,
    color: 'text-amber-500',
  },
  UNLOCK_FORUM_POST: {
    label: 'Unlocked',
    icon: IconLock,
    variant: 'secondary' as const,
    color: 'text-gray-500',
  },
  DELETE_FORUM_POST: {
    label: 'Deleted',
    icon: IconTrash,
    variant: 'destructive' as const,
    color: 'text-red-500',
  },
  FLAG_FORUM_POST: {
    label: 'Flagged',
    icon: IconFlag,
    variant: 'destructive' as const,
    color: 'text-red-500',
  },
  UNFLAG_FORUM_POST: {
    label: 'Unflagged',
    icon: IconFlag,
    variant: 'secondary' as const,
    color: 'text-green-500',
  },
  BULK_DELETE_FORUM: {
    label: 'Bulk Delete',
    icon: IconTrash,
    variant: 'destructive' as const,
    color: 'text-red-500',
  },
  BULK_PIN_FORUM: {
    label: 'Bulk Pin',
    icon: IconPin,
    variant: 'default' as const,
    color: 'text-blue-500',
  },
}))

function getActionConfig(action: string) {
  return (
    actionConfig.value[action as keyof typeof actionConfig.value] || {
      label: action,
      icon: IconActivity,
      variant: 'outline' as const,
      color: 'text-gray-500',
    }
  )
}

function formatDate(dateStr: string) {
  return new Date(dateStr).toLocaleString()
}

function getChangesText(entry: AuditEntry): string | null {
  const changes: string[] = []

  if (entry.oldValues && Object.keys(entry.oldValues).length > 0) {
    changes.push(`From: ${JSON.stringify(entry.oldValues)}`)
  }
  if (entry.newValues && Object.keys(entry.newValues).length > 0) {
    changes.push(`To: ${JSON.stringify(entry.newValues)}`)
  }

  return changes.length > 0 ? changes.join('\n') : null
}
</script>

<template>
  <div class="space-y-4">
    <!-- Loading State -->
    <div v-if="loading" class="space-y-4">
      <Card v-for="i in 3" :key="i">
        <CardContent class="p-4">
          <div class="flex items-start gap-4">
            <Skeleton class="h-10 w-10 rounded-full" />
            <div class="flex-1 space-y-2">
              <Skeleton class="h-4 w-32" />
              <Skeleton class="h-3 w-48" />
              <Skeleton class="h-3 w-24" />
            </div>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- Empty State -->
    <Card v-else-if="auditHistory.length === 0">
      <CardContent class="p-8 text-center">
        <div class="w-12 h-12 rounded-full bg-muted flex items-center justify-center mx-auto mb-3">
          <IconShield class="h-5 w-5 text-muted-foreground" />
        </div>
        <p class="text-sm text-muted-foreground">No audit history available</p>
      </CardContent>
    </Card>

    <!-- Audit Timeline -->
    <div v-else class="space-y-3">
      <Card
        v-for="entry in auditHistory"
        :key="entry.id"
        class="overflow-hidden"
      >
        <CardContent class="p-4">
          <div class="flex items-start gap-4">
            <!-- Action Icon -->
            <div
              class="h-10 w-10 rounded-full bg-muted flex items-center justify-center flex-shrink-0"
            >
              <component
                :is="getActionConfig(entry.action).icon"
                :class="['h-5 w-5', getActionConfig(entry.action).color]"
              />
            </div>

            <!-- Content -->
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap mb-1">
                <span class="font-medium text-sm">{{ entry.performer.username }}</span>
                <span class="text-muted-foreground text-xs">performed</span>
                <Badge :variant="getActionConfig(entry.action).variant" class="text-xs">
                  {{ getActionConfig(entry.action).label }}
                </Badge>
              </div>

              <div class="text-xs text-muted-foreground flex items-center gap-1 mb-2">
                <IconCalendar class="h-3 w-3" />
                {{ formatDate(entry.created_at) }}
              </div>

              <!-- Changes -->
              <div
                v-if="getChangesText(entry)"
                class="mt-2 p-2 rounded bg-muted/50 text-xs font-mono whitespace-pre-wrap overflow-x-auto"
              >
                {{ getChangesText(entry) }}
              </div>

              <!-- Additional Info -->
              <div v-if="entry.ipAddress || entry.userAgent" class="mt-2 text-xs text-muted-foreground">
                <div v-if="entry.ipAddress">IP: {{ entry.ipAddress }}</div>
                <div v-if="entry.userAgent" class="truncate">{{ entry.userAgent }}</div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
