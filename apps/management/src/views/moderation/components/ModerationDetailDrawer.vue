<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import { IconX, IconUser, IconFlag, IconClock, IconAlertTriangle } from '@tabler/icons-vue'

import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '@/components/ui/sheet'
import { Separator } from '@/components/ui/separator'
import {
  SemanticBadge,
  MODERATION_STATUS_COLOR_MAP,
  type SemanticColor,
} from '@/components/ui/terminal'
import {
  type ModerationQueueItem,
  type ModerationStatus,
  type ReportCategory,
  ModerationActionType,
} from '@/api/admin/moderation'

import EntityPreviewCard from './EntityPreviewCard.vue'
import ModerationActionPanel from './ModerationActionPanel.vue'

interface Props {
  open: boolean
  item: ModerationQueueItem | null
  loading?: boolean
}

defineProps<Props>()
const emit = defineEmits<{
  'update:open': [value: boolean]
  performAction: [action: ModerationActionType, note?: string, durationDays?: number]
  viewEntity: []
}>()

const { t } = useI18n()

const statusColors: Record<ModerationStatus, SemanticColor> = MODERATION_STATUS_COLOR_MAP

const categoryColors: Record<ReportCategory, string> = {
  SPAM: 'text-foreground-strong',
  HARASSMENT: 'text-foreground-strong',
  HATE_SPEECH: 'text-foreground-strong',
  VIOLENCE: 'text-foreground-strong',
  SEXUAL_CONTENT: 'text-foreground-strong',
  MISINFORMATION: 'text-foreground-strong',
  WRONG_ANSWER: 'text-foreground-strong',
  COPYRIGHT: 'text-foreground-strong',
  OTHER: 'text-[var(--foreground-muted)]',
}

function getPriorityLabel(priority: number): string {
  if (priority >= 8) return 'Critical'
  if (priority >= 5) return 'High'
  if (priority >= 3) return 'Medium'
  return 'Low'
}

function getPriorityColor(priority: number): string {
  if (priority >= 8) return 'text-[var(--foreground-strong)]'
  if (priority >= 5) return 'text-[var(--foreground-strong)]'
  if (priority >= 3) return 'text-[var(--foreground-strong)]'
  return 'text-[var(--foreground-muted)]'
}

function handlePerformAction(action: ModerationActionType, note?: string, durationDays?: number) {
  emit('performAction', action, note, durationDays)
}
</script>

<template>
  <Sheet :open="open" @update:open="emit('update:open', $event)">
    <SheetContent
      class="w-full sm:max-w-[600px] p-0 flex flex-col border-l border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--background)]"
    >
      <!-- Header -->
      <SheetHeader
        class="p-4 border-b border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center justify-between">
          <SheetTitle class="flex items-center gap-2 text-lg font-data">
            <IconFlag class="h-5 w-5 text-[var(--status-warning-mark)]" />
            <span>{{ t('moderation.detail.title') }}</span>
          </SheetTitle>
          <Button variant="ghost" size="icon" class="h-8 w-8" @click="emit('update:open', false)">
            <IconX class="h-4 w-4" />
          </Button>
        </div>
        <SheetDescription v-if="item" class="text-sm text-[var(--foreground-muted)]">
          {{ t('moderation.queue.description') }}
        </SheetDescription>
      </SheetHeader>

      <!-- Content -->
      <div v-if="item" class="flex-1 overflow-y-auto">
        <!-- Status & Priority Bar -->
        <div
          class="p-4 border-b border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--card)]"
        >
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <SemanticBadge
                :color="statusColors[item.status]"
                size="sm"
                class="font-data uppercase tracking-wider"
              >
                {{ t(`moderation.status.${item.status}`, item.status) }}
              </SemanticBadge>
              <div class="flex items-center gap-1.5">
                <IconFlag :class="['h-3.5 w-3.5', getPriorityColor(item.priority)]" />
                <span :class="['text-xs font-data', getPriorityColor(item.priority)]">
                  {{ getPriorityLabel(item.priority) }} ({{ item.priority }})
                </span>
              </div>
            </div>
            <div class="flex items-center gap-2 text-xs text-[var(--foreground-muted)]">
              <IconClock class="h-3.5 w-3.5" />
              <span class="font-data tabular-nums">{{
                formatDateTimeByLocale(item.createdAt)
              }}</span>
            </div>
          </div>
        </div>

        <!-- Main Content -->
        <div class="p-4 space-y-4">
          <!-- Entity Preview -->
          <EntityPreviewCard
            :entity-type="item.entityType"
            :entity-id="item.entityId"
            :title="item.entityId"
            @view-entity="emit('viewEntity')"
          />

          <!-- Category & Reports Count -->
          <div class="flex items-center gap-4">
            <div class="flex items-center gap-2">
              <span class="text-xs font-data uppercase tracking-wider text-[var(--foreground-muted)]">
                {{ t('moderation.columns.category') }}:
              </span>
              <span :class="['text-sm font-data', categoryColors[item.primaryCategory]]">
                {{ t(`moderation.categories.${item.primaryCategory}`, item.primaryCategory) }}
              </span>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-xs font-data uppercase tracking-wider text-[var(--foreground-muted)]">
                {{ t('moderation.queue.reportCount') }}:
              </span>
              <span
                :class="[
                  'text-sm font-data tabular-nums',
                  item.reportCount >= 3 ? 'text-foreground-strong' : 'text-[var(--foreground)]',
                ]"
              >
                {{ item.reportCount }}
              </span>
            </div>
          </div>

          <!-- Assigned To -->
          <div
            v-if="item.assignedToId"
            class="flex items-center gap-2 p-3 border border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--surface-sunken)]"
          >
            <IconUser class="h-4 w-4 text-[var(--foreground-muted)]" />
            <span class="text-xs font-data uppercase tracking-wider text-[var(--foreground-muted)]">
              {{ t('moderation.queue.assignedTo') }}:
            </span>
            <span class="text-sm">
              {{ item.assignedToName || item.assignedToUsername }}
            </span>
          </div>

          <!-- Reports Section -->
          <!-- TODO: Fetch reports separately via API -->
          <!-- <div v-if="item.reports && item.reports.length > 0" class="space-y-2">...</div> -->

          <Separator class="my-4 bg-[var(--border-subtle)] dark:bg-[var(--border-subtle)]" />

          <!-- Action History -->
          <!-- TODO: Fetch actions separately or remove this section -->
          <!-- <ActionHistoryTimeline :actions="item.actions || []" :loading="false" /> -->

          <Separator class="my-4 bg-[var(--border-subtle)] dark:bg-[var(--border-subtle)]" />

          <!-- Action Panel -->
          <ModerationActionPanel
            :item="item"
            :loading="loading"
            @perform-action="handlePerformAction"
          />
        </div>
      </div>

      <!-- Loading State -->
      <div v-else class="flex-1 flex items-center justify-center">
        <div class="text-center">
          <IconAlertTriangle class="h-8 w-8 text-[var(--foreground-muted)] mx-auto mb-2" />
          <p class="text-sm text-[var(--foreground-muted)]">{{ t('moderation.notFound') }}</p>
        </div>
      </div>
    </SheetContent>
  </Sheet>
</template>
