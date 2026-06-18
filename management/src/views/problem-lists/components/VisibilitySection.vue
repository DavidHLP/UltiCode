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
</script>

<template>
  <div class="border border-[var(--editor-panel-border)] bg-[var(--editor-panel-bg)] rounded-none">
    <!-- Card Header -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-[var(--editor-border-weak)] select-none">
      <div class="flex items-center gap-1.5">
        <span class="text-xs font-mono font-bold text-[var(--editor-text-primary)] uppercase tracking-wider">
          02 // {{ t('problemLists.sections.visibilityFeatured') }}
        </span>
      </div>
      <!-- Auto-save Status Indicator in Header -->
      <div v-if="saveStatusText[saveStatus]" class="flex items-center gap-1.5 font-mono text-2xs uppercase font-bold">
        <template v-if="saveStatus === 'saving'">
          <span class="h-1.5 w-1.5 bg-[var(--editor-yellow)] shrink-0 animate-ping"></span>
          <span class="text-[var(--editor-yellow)] animate-pulse">{{ t('problemLists.form.saving') }}</span>
        </template>
        <template v-else-if="saveStatus === 'saved'">
          <span class="text-[var(--editor-green)]">// SAVED</span>
        </template>
        <template v-else-if="saveStatus === 'error'">
          <span class="text-[var(--editor-red)]">// ERROR</span>
        </template>
      </div>
    </div>

    <!-- Card Content -->
    <div class="divide-y divide-[var(--editor-border-weak)]">
      <!-- isPublic Switch Row -->
      <div class="flex items-center justify-between px-4 py-3.5 gap-4 hover:bg-[var(--editor-control-bg)]/20 transition-colors duration-150">
        <div class="flex flex-col gap-0.5 max-w-[80%]">
          <label class="text-xs font-mono font-bold text-[var(--editor-text-primary)] uppercase tracking-wider select-none cursor-pointer">
            {{ t('problemLists.form.isPublic') }}
          </label>
          <span class="text-xxs text-[var(--editor-text-muted)] leading-normal font-mono">
            {{ t('problemLists.form.isPublicDescription') }}
          </span>
        </div>
        <Switch
          :checked="isPublic"
          :disabled="disabled"
          @update:checked="handleIsPublicChange"
          @blur="handleBlur"
          class="custom-switch data-[state=checked]:bg-[var(--editor-green)]"
        />
      </div>

      <!-- isFeatured Switch Row -->
      <div class="flex items-center justify-between px-4 py-3.5 gap-4 hover:bg-[var(--editor-control-bg)]/20 transition-colors duration-150">
        <div class="flex flex-col gap-0.5 max-w-[80%]">
          <div class="flex items-center gap-1.5">
            <label class="text-xs font-mono font-bold text-[var(--editor-text-primary)] uppercase tracking-wider select-none cursor-pointer">
              {{ t('problemLists.form.isFeatured') }}
            </label>
            <TooltipProvider v-if="isFeatured">
              <Tooltip>
                <TooltipTrigger as-child>
                  <span class="text-[var(--editor-text-muted)] cursor-help font-mono text-xxs font-bold"> [?] </span>
                </TooltipTrigger>
                <TooltipContent
                  class="max-w-xs bg-[var(--surface-elevated)] border-[var(--editor-panel-border)] rounded-none"
                >
                  <p class="text-xxs text-[var(--editor-text-primary)] font-mono">
                    {{ t('problemLists.form.isFeaturedTooltip') }}
                  </p>
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          </div>
          <span class="text-xxs text-[var(--editor-text-muted)] leading-normal font-mono">
            {{ t('problemLists.form.isFeaturedDescription') }}
          </span>
        </div>
        <Switch
          :checked="isFeatured"
          :disabled="disabled"
          @update:checked="handleIsFeaturedChange"
          @blur="handleBlur"
          class="custom-switch data-[state=checked]:bg-[var(--editor-yellow)]"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Enforce completely straight edges for switch toggle */
:deep(.custom-switch) {
  border-radius: 0 !important;
  width: 38px !important;
  height: 20px !important;
  border: 1px solid var(--editor-control-border);
  background-color: var(--editor-control-bg);
  padding: 1px !important;
  cursor: pointer;
  transition: all 0.15s ease-in-out;
}

:deep(.custom-switch [data-state]) {
  border-radius: 0 !important;
}

:deep(.custom-switch span) {
  border-radius: 0 !important;
  width: 16px !important;
  height: 16px !important;
  background-color: var(--editor-text-muted) !important;
  transition: transform 0.15s ease-in-out;
}

:deep(.custom-switch[data-state=checked] span) {
  transform: translateX(18px) !important;
  background-color: #ffffff !important;
}

:deep(.custom-switch:disabled) {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
