<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Switch } from '@/components/ui/switch'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { useAutoSave } from '@/composables/useAutoSave'
import { adminProblemListsApi } from '@/api/admin/problem-lists'
import type { ProblemListDetail, UpdateVisibilityDto } from '@/api/admin/problem-lists'

const props = defineProps<{
  modelValue: ProblemListDetail | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: ProblemListDetail): void
}>()

const { t } = useI18n()

const isPublic = ref(props.modelValue?.isPublic ?? true)
const isFeatured = ref(props.modelValue?.isFeatured ?? false)

watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal) {
      isPublic.value = newVal.isPublic
      isFeatured.value = newVal.isFeatured
    }
  },
)

const visibilityData = computed(() => ({
  isPublic: isPublic.value,
  isFeatured: isFeatured.value,
}))

const { saveStatus, save } = useAutoSave<UpdateVisibilityDto>(
  async (data) => {
    if (!props.modelValue) return
    const updated = await adminProblemListsApi.updateVisibility(props.modelValue.id, data)
    emit('update:modelValue', {
      ...props.modelValue!,
      ...updated,
      problems: props.modelValue!.problems,
    })
  },
  { debounceMs: 1000, blurTriggers: true },
)

function handleIsPublicChange(checked: boolean) {
  isPublic.value = checked
  emit('update:modelValue', { ...props.modelValue!, isPublic: checked } as ProblemListDetail)
  save(visibilityData.value)
}

function handleIsFeaturedChange(checked: boolean) {
  isFeatured.value = checked
  emit('update:modelValue', { ...props.modelValue!, isFeatured: checked } as ProblemListDetail)
  save(visibilityData.value)
}

function handleBlur() {
  save(visibilityData.value)
}

const saveStatusText = {
  idle: '',
  saving: t('problemLists.form.saving'),
  saved: t('problemLists.form.saved'),
  error: t('problemLists.form.saveError'),
}

const saveStatusColor = {
  idle: '',
  saving: 'text-[var(--silver-400)]',
  saved: 'text-[var(--terminal-green)]',
  error: 'text-[var(--terminal-red)]',
}
</script>

<template>
  <div class="space-y-4">
    <div class="terminal-comment">// {{ t('problemLists.sections.visibilityFeatured') }}</div>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <!-- isPublic Switch -->
      <div class="flex items-center justify-between">
        <div class="flex flex-col gap-1">
          <label class="terminal-label">{{ t('problemLists.form.isPublic') }}</label>
          <span class="font-data text-xs text-[var(--silver-400)]">
            {{ t('problemLists.form.isPublicDescription') }}
          </span>
        </div>
        <Switch
          :checked="isPublic"
          :disabled="disabled"
          @update:checked="handleIsPublicChange"
          @blur="handleBlur"
          class="data-[state=checked]:bg-[var(--terminal-green)]"
        />
      </div>

      <!-- isFeatured Switch with Tooltip -->
      <div class="flex items-center justify-between">
        <div class="flex flex-col gap-1">
          <div class="flex items-center gap-2">
            <label class="terminal-label">{{ t('problemLists.form.isFeatured') }}</label>
            <TooltipProvider v-if="isFeatured">
              <Tooltip>
                <TooltipTrigger as-child>
                  <span class="text-[var(--silver-400)] cursor-help font-data text-xs"> [?] </span>
                </TooltipTrigger>
                <TooltipContent
                  class="max-w-xs bg-[var(--surface-elevated)] border-[var(--silver-200)]"
                >
                  <p class="text-xs text-[var(--silver-100)]">
                    {{ t('problemLists.form.isFeaturedTooltip') }}
                  </p>
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          </div>
          <span class="font-data text-xs text-[var(--silver-400)]">
            {{ t('problemLists.form.isFeaturedDescription') }}
          </span>
        </div>
        <Switch
          :checked="isFeatured"
          :disabled="disabled"
          @update:checked="handleIsFeaturedChange"
          @blur="handleBlur"
          class="data-[state=checked]:bg-[var(--terminal-amber)]"
        />
      </div>
    </div>

    <!-- Save Status Indicator -->
    <div v-if="saveStatusText[saveStatus]" class="flex items-center gap-2">
      <span class="font-data text-xs animate-pulse" :class="saveStatusColor[saveStatus]">
        {{
          saveStatus === 'saving'
            ? '●'
            : saveStatus === 'saved'
              ? '✓'
              : saveStatus === 'error'
                ? '✗'
                : ''
        }}
        {{ saveStatusText[saveStatus] }}
      </span>
    </div>
  </div>
</template>
