<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@ulticode/design-system/ui/button'
import { Input } from '@ulticode/design-system/ui/input'
import { Label } from '@ulticode/design-system/ui/label'
import { Textarea } from '@ulticode/design-system/ui/textarea'
import { Card, CardContent, CardHeader, CardTitle } from '@ulticode/design-system/ui/card'
import {
  AlertCircle as IconAlertCircle,
  AlertTriangle as IconAlertTriangle,
  Check as IconCheck,
} from '@lucide/vue'

import { ModerationActionType, type ModerationQueueItem } from '@/api/admin/moderation'
import {
  ACTION_CATALOG,
  actionBgVar,
  actionBorderVar,
  actionColorVar,
} from '../presentation/moderationPresentation'

/**
 * Map presentation color tokens onto the action panel's CSS variables.
 * Kept here so the presentation module stays focused on catalog data.
 */

interface Props {
  item: ModerationQueueItem
  loading?: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  performAction: [action: ModerationActionType, note?: string, durationDays?: number]
}>()

const { t } = useI18n()

const selectedAction = ref(ACTION_CATALOG[0].value)
const note = ref('')
const durationDays = ref<number | undefined>(undefined)

// Reset form when item changes
watch(
  () => props.item?.id,
  () => {
    selectedAction.value = ACTION_CATALOG[0].value
    note.value = ''
    durationDays.value = undefined
  },
)

const actionOptions = computed(() =>
  ACTION_CATALOG.map((a) => ({
    value: a.value,
    label: t(a.labelKey),
    description: t(a.descriptionKey),
    icon: a.icon,
    color: actionColorVar(a.color),
    bgColor: actionBgVar(a.color),
    borderColor: actionBorderVar(a.color),
    requiresDuration: a.requiresDuration,
  })),
)

const selectedOption = computed(() =>
  actionOptions.value.find((opt) => opt.value === selectedAction.value),
)

const canSubmit = computed(() => {
  if (selectedOption.value?.requiresDuration && !durationDays.value) {
    return false
  }
  return true
})

function handleSubmit() {
  if (!canSubmit.value || props.loading) return
  emit('performAction', selectedAction.value, note.value || undefined, durationDays.value)
}
</script>

<template>
  <Card>
    <CardHeader class="pb-3">
      <CardTitle class="flex items-center gap-2">
        <IconAlertCircle class="h-4 w-4 text-status-warning-mark" />
        <span class="text-muted-foreground">{{ t('moderation.actionPanel.title') }}</span>
      </CardTitle>
    </CardHeader>
    <CardContent class="space-y-4">
      <!-- Action Selection -->
      <div class="space-y-2">
        <Label>
          {{ t('moderation.actionPanel.selectAction') }}
        </Label>
        <div class="grid grid-cols-2 gap-2">
          <Button
            variant="outline"
            v-for="option in actionOptions"
            :key="option.value"
            :class="[
              'flex items-center gap-2 p-2 border text-left transition-all',
              'hover:border-current hover:bg-current/5',
              selectedAction === option.value
                ? [option.borderColor, option.bgColor, 'border-2']
                : 'border-border',
            ]"
            @click="selectedAction = option.value"
          >
            <component :is="option.icon" :class="['h-4 w-4 flex-shrink-0', option.color]" />
            <span :class="['text-xs font-mono', option.color]">
              {{ option.label }}
            </span>
          </Button>
        </div>
      </div>

      <!-- Selected Action Description -->
      <div
        v-if="selectedOption"
        :class="['p-3 border', selectedOption.borderColor, selectedOption.bgColor]"
      >
        <p class="text-xs text-foreground">
          {{ selectedOption.description }}
        </p>
      </div>

      <!-- Duration (for temporary bans) -->
      <div v-if="selectedOption?.requiresDuration" class="space-y-2">
        <Label for="duration-days">
          {{ t('moderation.actionPanel.durationLabel') }}
        </Label>
        <div class="flex items-center gap-2">
          <Input
            id="duration-days"
            v-model.number="durationDays"
            type="number"
            min="1"
            max="365"
            :placeholder="t('moderation.actionPanel.durationPlaceholder')"
          />
          <span class="text-xs text-muted-foreground">{{ t('moderation.actionPanel.days') }}</span>
        </div>
      </div>

      <!-- Note -->
      <div class="space-y-2">
        <Label for="action-note">
          {{ t('moderation.actionPanel.addNote') }}
        </Label>
        <Textarea
          id="action-note"
          v-model="note"
          :placeholder="t('moderation.actionPanel.notePlaceholder')"
          rows="3"
          class="placeholder:text-muted-foreground"
        />
      </div>

      <!-- Warning -->
      <div
        class="flex items-start gap-2 p-3 border border-status-warning-mark bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)]"
      >
        <IconAlertTriangle class="h-4 w-4 text-status-warning-mark flex-shrink-0 mt-0.5" />
        <p class="text-xs text-foreground">
          {{ t('moderation.actionPanel.warning') }}
        </p>
      </div>

      <!-- Submit Button -->
      <Button
        :class="[
          'w-full h-10 font-mono text-xs uppercase tracking-wider',
          selectedOption?.borderColor,
          selectedOption?.color,
          selectedOption?.bgColor,
          'hover:brightness-110',
        ]"
        :disabled="!canSubmit || loading"
        @click="handleSubmit"
      >
        <IconCheck v-if="!loading" class="h-4 w-4 mr-2" />
        <IconAlertCircle v-else class="h-4 w-4 mr-2 animate-pulse" />
        {{
          loading
            ? t('moderation.actionPanel.confirming')
            : t('moderation.actionPanel.confirmAction')
        }}
      </Button>
    </CardContent>
  </Card>
</template>
