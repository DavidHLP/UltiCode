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
import { SemanticBadge, MODERATION_STATUS_COLOR_MAP, type SemanticColor } from '@/components/ui/terminal'
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
  SPAM: 'text-[var(--terminal-amber)]',
  HARASSMENT: 'text-[var(--terminal-red)]',
  HATE_SPEECH: 'text-[var(--terminal-red)]',
  VIOLENCE: 'text-[var(--terminal-red)]',
  SEXUAL_CONTENT: 'text-[var(--terminal-red)]',
  MISINFORMATION: 'text-[var(--terminal-amber)]',
  WRONG_ANSWER: 'text-[var(--terminal-amber)]',
  COPYRIGHT: 'text-[var(--terminal-purple)]',
  OTHER: 'text-[var(--silver-500)]',
}


function getPriorityLabel(priority: number): string {
  if (priority >= 8) return 'Critical'
  if (priority >= 5) return 'High'
  if (priority >= 3) return 'Medium'
  return 'Low'
}

function getPriorityColor(priority: number): string {
  if (priority >= 8) return 'text-[var(--terminal-red)]'
  if (priority >= 5) return 'text-[var(--terminal-amber)]'
  if (priority >= 3) return 'text-[var(--terminal-cyan)]'
  return 'text-[var(--silver-500)]'
}

function handlePerformAction(action: ModerationActionType, note?: string, durationDays?: number) {
  emit('performAction', action, note, durationDays)
}
</script>

<template>
  <Sheet :open="open" @update:open="emit('update:open', $event)">
    <SheetContent
      class="w-full sm:max-w-[600px] p-0 flex flex-col border-l border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--background)]"
    >
      <!-- Header -->
      <SheetHeader
        class="p-4 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center justify-between">
          <SheetTitle class="flex items-center gap-2 text-lg font-data">
            <IconFlag class="h-5 w-5 text-[var(--terminal-amber)]" />
            <span>{{ t('moderation.detail.title') }}</span>
          </SheetTitle>
          <Button variant="ghost" size="icon" class="h-8 w-8" @click="emit('update:open', false)">
            <IconX class="h-4 w-4" />
          </Button>
        </div>
        <SheetDescription v-if="item" class="text-sm text-[var(--silver-500)]">
          {{ t('moderation.queue.description') }}
        </SheetDescription>
      </SheetHeader>

      <!-- Content -->
      <div v-if="item" class="flex-1 overflow-y-auto">
        <!-- Status & Priority Bar -->
        <div
          class="p-4 border-b border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--card)]"
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
            <div class="flex items-center gap-2 text-xs text-[var(--silver-500)]">
              <IconClock class="h-3.5 w-3.5" />
              <span class="font-data tabular-nums">{{ formatDateTimeByLocale(item.createdAt) }}</span>
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
              <span class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">
                {{ t('moderation.columns.category') }}:
              </span>
              <span :class="['text-sm font-data', categoryColors[item.primaryCategory]]">
                {{ t(`moderation.categories.${item.primaryCategory}`, item.primaryCategory) }}
              </span>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">
                {{ t('moderation.queue.reportCount') }}:
              </span>
              <span
                :class="[
                  'text-sm font-data tabular-nums',
                  item.reportCount >= 3 ? 'text-[var(--terminal-red)]' : 'text-[var(--foreground)]',
                ]"
              >
                {{ item.reportCount }}
              </span>
            </div>
          </div>

          <!-- Assigned To -->
          <div
            v-if="item.assignedToId"
            class="flex items-center gap-2 p-3 border border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
          >
            <IconUser class="h-4 w-4 text-[var(--silver-500)]" />
            <span class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">
              {{ t('moderation.queue.assignedTo') }}:
            </span>
            <span class="text-sm">
              {{ item.assignedToName || item.assignedToUsername }}
            </span>
          </div>

          <!-- Reports Section -->
          <!-- TODO: Fetch reports separately via API -->
          <!-- <div v-if="item.reports && item.reports.length > 0" class="space-y-2">...</div> -->

          <Separator class="my-4 bg-[var(--silver-200)] dark:bg-[var(--silver-300)]" />

          <!-- Action History -->
          <!-- TODO: Fetch actions separately or remove this section -->
          <!-- <ActionHistoryTimeline :actions="item.actions || []" :loading="false" /> -->

          <Separator class="my-4 bg-[var(--silver-200)] dark:bg-[var(--silver-300)]" />

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
          <IconAlertTriangle class="h-8 w-8 text-[var(--silver-400)] mx-auto mb-2" />
          <p class="text-sm text-[var(--silver-400)]">{{ t('moderation.notFound') }}</p>
        </div>
      </div>
    </SheetContent>
  </Sheet>
</template>
