<script setup lang="ts">
import { FieldLabel } from '@ulticode/design-system/ui/field'

import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Switch } from '@ulticode/design-system/ui/switch'
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@ulticode/design-system/ui/tooltip'
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
  <div class="border border-border bg-card rounded-lg">
    <!-- Card Header -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-border select-none">
      <div class="flex items-center gap-1.5">
        <span class="text-xs font-mono font-bold text-foreground uppercase tracking-wider">
          02 // {{ t('problemLists.sections.visibilityFeatured') }}
        </span>
      </div>
      <!-- Auto-save Status Indicator in Header -->
      <div
        v-if="saveStatusText[saveStatus]"
        class="flex items-center gap-1.5 font-mono text-xs uppercase font-bold"
      >
        <template v-if="saveStatus === 'saving'">
          <span class="h-1.5 w-1.5 bg-status-warning-mark shrink-0 animate-ping"></span>
          <span class="text-status-warning-mark animate-pulse">{{
            t('problemLists.form.saving')
          }}</span>
        </template>
        <template v-else-if="saveStatus === 'saved'">
          <span class="text-status-success-mark">{{ t('problemLists.form.saved') }}</span>
        </template>
        <template v-else-if="saveStatus === 'error'">
          <span class="text-status-error-mark">{{ t('problemLists.form.saveError') }}</span>
        </template>
      </div>
    </div>

    <!-- Card Content -->
    <div class="divide-y divide-[var(--border)]">
      <!-- isPublic Switch Row -->
      <div
        class="flex items-center justify-between px-4 py-3.5 gap-4 hover:bg-muted/20 transition-colors duration-150"
      >
        <div class="flex flex-col gap-0.5 max-w-[80%]">
          <FieldLabel class="select-none cursor-pointer">
            {{ t('problemLists.form.isPublic') }}
          </FieldLabel>
          <span class="text-xs text-muted-foreground leading-normal font-mono">
            {{ t('problemLists.form.isPublicDescription') }}
          </span>
        </div>
        <Switch
          :checked="isPublic"
          :disabled="disabled"
          @update:checked="handleIsPublicChange"
          @blur="handleBlur"
          class=""
        />
      </div>

      <!-- isFeatured Switch Row -->
      <div
        class="flex items-center justify-between px-4 py-3.5 gap-4 hover:bg-muted/20 transition-colors duration-150"
      >
        <div class="flex flex-col gap-0.5 max-w-[80%]">
          <div class="flex items-center gap-1.5">
            <FieldLabel class="select-none cursor-pointer">
              {{ t('problemLists.form.isFeatured') }}
            </FieldLabel>
            <TooltipProvider v-if="isFeatured">
              <Tooltip>
                <TooltipTrigger as-child>
                  <span class="text-muted-foreground cursor-help font-mono text-xs font-bold">
                    [?]
                  </span>
                </TooltipTrigger>
                <TooltipContent class="max-w-xs">
                  <p class="text-xs text-foreground font-mono">
                    {{ t('problemLists.form.isFeaturedTooltip') }}
                  </p>
                </TooltipContent>
              </Tooltip>
            </TooltipProvider>
          </div>
          <span class="text-xs text-muted-foreground leading-normal font-mono">
            {{ t('problemLists.form.isFeaturedDescription') }}
          </span>
        </div>
        <Switch
          :checked="isFeatured"
          :disabled="disabled"
          @update:checked="handleIsFeaturedChange"
          @blur="handleBlur"
          class=""
        />
      </div>
    </div>
  </div>
</template>
