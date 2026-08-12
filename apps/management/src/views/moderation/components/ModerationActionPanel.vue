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
  actionBgVar,
  actionBorderVar,
  actionColorVar,
  findAction,
} from '../workflow/moderationWorkflow'

/**
 * Map a workflow ActionColorKey onto the existing CSS variable convention
 * used by the action panel chrome. Kept here so the workflow module stays
 * framework-agnostic.
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
  <Card
    class="border-[var(--border-subtle)] dark:border-[var(--border-subtle)] bg-[var(--surface-sunken)]"
  >
    <CardHeader class="pb-3">
      <CardTitle class="flex items-center gap-2 text-sm font-data uppercase tracking-wider">
        <IconAlertCircle class="h-4 w-4 text-[var(--status-warning-mark)]" />
        <span class="text-[var(--foreground-muted)]">{{ t('moderation.actionPanel.title') }}</span>
      </CardTitle>
    </CardHeader>
    <CardContent class="space-y-4">
      <!-- Action Selection -->
      <div class="space-y-2">
        <Label class="text-xs font-data uppercase tracking-wider text-[var(--foreground-muted)]">
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
                : 'border-[var(--border-subtle)]',
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
          class="text-xs font-data uppercase tracking-wider text-[var(--foreground-muted)]"
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
            class="font-data text-sm border-[var(--border-subtle)] hover:border-[var(--primary)] bg-transparent"
          />
          <span class="text-xs text-[var(--foreground-muted)]">{{
            t('moderation.actionPanel.days')
          }}</span>
        </div>
      </div>

      <!-- Note -->
      <div class="space-y-2">
        <Label
          for="action-note"
          class="text-xs font-data uppercase tracking-wider text-[var(--foreground-muted)]"
        >
          {{ t('moderation.actionPanel.addNote') }}
        </Label>
        <Textarea
          id="action-note"
          v-model="note"
          :placeholder="t('moderation.actionPanel.notePlaceholder')"
          rows="3"
          class="font-data text-sm border-[var(--border-subtle)] hover:border-[var(--primary)] bg-transparent placeholder:text-[var(--foreground-muted)]"
        />
      </div>

      <!-- Warning -->
      <div
        class="flex items-start gap-2 p-3 border border-[var(--status-warning-mark)] bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)]"
      >
        <IconAlertTriangle class="h-4 w-4 text-[var(--status-warning-mark)] flex-shrink-0 mt-0.5" />
        <p class="text-xs text-[var(--foreground-strong)]">
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
