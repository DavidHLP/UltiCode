<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ModerationActionType, type ModerationQueueItem } from '@/api/admin/moderation'
import {
  ACTION_CATALOG,
  findAction,
} from '../workflow/moderationWorkflow'

/**
 * Map a workflow ActionColorKey onto the existing CSS variable convention
 * used by the action panel chrome. Kept here so the workflow module stays
 * framework-agnostic.
 */
type TerminalColorKey =
  | 'red' | 'amber' | 'green' | 'cyan' | 'purple' | 'info' | 'error' | 'success' | 'warning' | 'neutral' | 'electric'

const ACTION_COLOR_VAR: Record<TerminalColorKey, string> = {
  red: 'text-[var(--terminal-red)]',
  amber: 'text-[var(--terminal-amber)]',
  green: 'text-[var(--terminal-green)]',
  cyan: 'text-[var(--terminal-cyan)]',
  purple: 'text-[var(--terminal-purple)]',
  info: 'text-[var(--terminal-cyan)]',
  error: 'text-[var(--terminal-red)]',
  success: 'text-[var(--terminal-green)]',
  warning: 'text-[var(--terminal-amber)]',
  neutral: 'text-[var(--silver-500)]',
  electric: 'text-[var(--accent-electric)]',
}
const ACTION_BG_VAR: Record<TerminalColorKey, string> = {
  red: 'bg-[color-mix(in_oklch,_var(--terminal-red)_15%,_transparent)]',
  amber: 'bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)]',
  green: 'bg-[color-mix(in_oklch,_var(--terminal-green)_15%,_transparent)]',
  cyan: 'bg-[color-mix(in_oklch,_var(--terminal-cyan)_15%,_transparent)]',
  purple: 'bg-[color-mix(in_oklch,_var(--terminal-purple)_15%,_transparent)]',
  info: 'bg-[color-mix(in_oklch,_var(--terminal-cyan)_15%,_transparent)]',
  error: 'bg-[color-mix(in_oklch,_var(--terminal-red)_15%,_transparent)]',
  success: 'bg-[color-mix(in_oklch,_var(--terminal-green)_15%,_transparent)]',
  warning: 'bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)]',
  neutral: 'bg-[var(--surface-sunken)]',
  electric: 'bg-[color-mix(in_oklch,_var(--accent-electric)_15%,_transparent)]',
}
const ACTION_BORDER_VAR: Record<TerminalColorKey, string> = {
  red: 'border-[color-mix(in_oklch,_var(--terminal-red)_40%,_transparent)]',
  amber: 'border-[color-mix(in_oklch,_var(--terminal-amber)_40%,_transparent)]',
  green: 'border-[color-mix(in_oklch,_var(--terminal-green)_40%,_transparent)]',
  cyan: 'border-[color-mix(in_oklch,_var(--terminal-cyan)_40%,_transparent)]',
  purple: 'border-[color-mix(in_oklch,_var(--terminal-purple)_40%,_transparent)]',
  info: 'border-[color-mix(in_oklch,_var(--terminal-cyan)_40%,_transparent)]',
  error: 'border-[color-mix(in_oklch,_var(--terminal-red)_40%,_transparent)]',
  success: 'border-[color-mix(in_oklch,_var(--terminal-green)_40%,_transparent)]',
  warning: 'border-[color-mix(in_oklch,_var(--terminal-amber)_40%,_transparent)]',
  neutral: 'border-[var(--silver-200)]',
  electric: 'border-[color-mix(in_oklch,_var(--accent-electric)_40%,_transparent)]',
}

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
    color: ACTION_COLOR_VAR[a.color] ?? ACTION_COLOR_VAR.red,
    bgColor: ACTION_BG_VAR[a.color] ?? ACTION_BG_VAR.red,
    borderColor: ACTION_BORDER_VAR[a.color] ?? ACTION_BORDER_VAR.red,
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
  <Card
    class="border-[var(--silver-200)] dark:border-[var(--silver-300)] bg-[var(--surface-sunken)]"
  >
    <CardHeader class="pb-3">
      <CardTitle class="flex items-center gap-2 text-sm font-data uppercase tracking-wider">
        <IconAlertCircle class="h-4 w-4 text-[var(--terminal-amber)]" />
        <span class="text-[var(--silver-500)]">{{ t('moderation.actionPanel.title') }}</span>
      </CardTitle>
    </CardHeader>
    <CardContent class="space-y-4">
      <!-- Action Selection -->
      <div class="space-y-2">
        <Label class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]">
          {{ t('moderation.actionPanel.selectAction') }}
        </Label>
        <div class="grid grid-cols-2 gap-2">
          <button
            v-for="option in actionOptions"
            :key="option.value"
            :class="[
              'flex items-center gap-2 p-2 border text-left transition-all',
              'hover:border-current hover:bg-current/5',
              selectedAction === option.value
                ? [option.borderColor, option.bgColor, 'border-2']
                : 'border-[var(--silver-300)]',
            ]"
            @click="selectedAction = option.value"
          >
            <component :is="option.icon" :class="['h-4 w-4 flex-shrink-0', option.color]" />
            <span :class="['text-xs font-data', option.color]">
              {{ option.label }}
            </span>
          </button>
        </div>
      </div>

      <!-- Selected Action Description -->
      <div
        v-if="selectedOption"
        :class="['p-3 border', selectedOption.borderColor, selectedOption.bgColor]"
      >
        <p class="text-xs text-[var(--foreground)]">
          {{ selectedOption.description }}
        </p>
      </div>

      <!-- Duration (for temporary bans) -->
      <div v-if="selectedOption?.requiresDuration" class="space-y-2">
        <Label
          for="duration-days"
          class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]"
        >
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
            class="font-data text-sm border-[var(--silver-300)] hover:border-[var(--accent-electric)] bg-transparent"
          />
          <span class="text-xs text-[var(--silver-500)]">{{
            t('moderation.actionPanel.days')
          }}</span>
        </div>
      </div>

      <!-- Note -->
      <div class="space-y-2">
        <Label
          for="action-note"
          class="text-xs font-data uppercase tracking-wider text-[var(--silver-500)]"
        >
          {{ t('moderation.actionPanel.addNote') }}
        </Label>
        <Textarea
          id="action-note"
          v-model="note"
          :placeholder="t('moderation.actionPanel.notePlaceholder')"
          rows="3"
          class="font-data text-sm border-[var(--silver-300)] hover:border-[var(--accent-electric)] bg-transparent placeholder:text-[var(--silver-400)]"
        />
      </div>

      <!-- Warning -->
      <div
        class="flex items-start gap-2 p-3 border border-[var(--terminal-amber)] bg-[color-mix(in_oklch,_var(--terminal-amber)_8%,_transparent)]"
      >
        <IconAlertTriangle class="h-4 w-4 text-[var(--terminal-amber)] flex-shrink-0 mt-0.5" />
        <p class="text-xs text-[var(--terminal-amber)]">
          {{ t('moderation.actionPanel.warning') }}
        </p>
      </div>

      <!-- Submit Button -->
      <Button
        :class="[
          'w-full h-10 font-data text-xs uppercase tracking-wider',
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
