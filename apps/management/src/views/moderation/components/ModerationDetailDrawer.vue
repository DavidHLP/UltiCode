<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import {
  X as IconX,
  User as IconUser,
  Flag as IconFlag,
  Clock as IconClock,
  AlertTriangle as IconAlertTriangle,
} from '@lucide/vue'

import { Button } from '@ulticode/design-system/ui/button'
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '@ulticode/design-system/ui/sheet'
import { Separator } from '@ulticode/design-system/ui/separator'
import SemanticBadge from '@ulticode/badge-config/SemanticBadge.vue'
import { MODERATION_STATUS_COLOR_MAP, type SemanticColor } from '@ulticode/badge-config'
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
  SPAM: 'text-foreground',
  HARASSMENT: 'text-foreground',
  HATE_SPEECH: 'text-foreground',
  VIOLENCE: 'text-foreground',
  SEXUAL_CONTENT: 'text-foreground',
  MISINFORMATION: 'text-foreground',
  WRONG_ANSWER: 'text-foreground',
  COPYRIGHT: 'text-foreground',
  OTHER: 'text-muted-foreground',
}

function getPriorityLabel(priority: number): string {
  if (priority >= 8) return 'Critical'
  if (priority >= 5) return 'High'
  if (priority >= 3) return 'Medium'
  return 'Low'
}

function getPriorityColor(priority: number): string {
  if (priority >= 8) return 'text-foreground'
  if (priority >= 5) return 'text-foreground'
  if (priority >= 3) return 'text-foreground'
  return 'text-muted-foreground'
}

function handlePerformAction(action: ModerationActionType, note?: string, durationDays?: number) {
  emit('performAction', action, note, durationDays)
}
</script>

<template>
  <Sheet :open="open" @update:open="emit('update:open', $event)">
    <SheetContent class="w-full sm:max-w-[600px] p-0 flex flex-col">
      <!-- Header -->
      <SheetHeader class="p-4">
        <div class="flex items-center justify-between">
          <SheetTitle class="flex items-center gap-2">
            <IconFlag class="h-5 w-5 text-status-warning-mark" />
            <span>{{ t('moderation.detail.title') }}</span>
          </SheetTitle>
          <Button variant="ghost" size="icon" class="w-8" @click="emit('update:open', false)">
            <IconX class="h-4 w-4" />
          </Button>
        </div>
        <SheetDescription v-if="item">
          {{ t('moderation.queue.description') }}
        </SheetDescription>
      </SheetHeader>

      <!-- Content -->
      <div v-if="item" class="flex-1 overflow-y-auto">
        <!-- Status & Priority Bar -->
        <div class="p-4 border-b border-border dark:border-border bg-card">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-3">
              <SemanticBadge
                :color="statusColors[item.status]"
                size="sm"
                class="font-mono uppercase tracking-wider"
              >
                {{ t(`moderation.status.${item.status}`, item.status) }}
              </SemanticBadge>
              <div class="flex items-center gap-1.5">
                <IconFlag :class="['h-3.5 w-3.5', getPriorityColor(item.priority)]" />
                <span :class="['text-xs font-mono', getPriorityColor(item.priority)]">
                  {{ getPriorityLabel(item.priority) }} ({{ item.priority }})
                </span>
              </div>
            </div>
            <div class="flex items-center gap-2 text-xs text-muted-foreground">
              <IconClock class="h-3.5 w-3.5" />
              <span class="font-mono tabular-nums">{{
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
              <span class="text-xs font-mono uppercase tracking-wider text-muted-foreground">
                {{ t('moderation.columns.category') }}:
              </span>
              <span :class="['text-sm font-mono', categoryColors[item.primaryCategory]]">
                {{ t(`moderation.categories.${item.primaryCategory}`, item.primaryCategory) }}
              </span>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-xs font-mono uppercase tracking-wider text-muted-foreground">
                {{ t('moderation.queue.reportCount') }}:
              </span>
              <span
                :class="[
                  'text-sm font-mono tabular-nums',
                  item.reportCount >= 3 ? 'text-foreground' : 'text-foreground',
                ]"
              >
                {{ item.reportCount }}
              </span>
            </div>
          </div>

          <!-- Assigned To -->
          <div
            v-if="item.assignedToId"
            class="flex items-center gap-2 p-3 border border-border dark:border-border bg-muted"
          >
            <IconUser class="h-4 w-4 text-muted-foreground" />
            <span class="text-xs font-mono uppercase tracking-wider text-muted-foreground">
              {{ t('moderation.queue.assignedTo') }}:
            </span>
            <span class="text-sm">
              {{ item.assignedToName || item.assignedToUsername }}
            </span>
          </div>

          <!-- Reports Section -->
          <!-- TODO: Fetch reports separately via API -->
          <!-- <div v-if="item.reports && item.reports.length > 0" class="space-y-2">...</div> -->

          <Separator class="my-4" />

          <!-- Action History -->
          <!-- TODO: Fetch actions separately or remove this section -->
          <!-- <ActionHistoryTimeline :actions="item.actions || []" :loading="false" /> -->

          <Separator class="my-4" />

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
          <IconAlertTriangle class="h-8 w-8 text-muted-foreground mx-auto mb-2" />
          <p class="text-sm text-muted-foreground">{{ t('moderation.notFound') }}</p>
        </div>
      </div>
    </SheetContent>
  </Sheet>
</template>
