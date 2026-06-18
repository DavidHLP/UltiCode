<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useDebounceFn } from '@vueuse/core'
import { isAxiosError } from 'axios'
import { toast } from 'vue-sonner'
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { adminProblemListsApi } from '@/api/admin/problem-lists'
import type { ProblemListDetail } from '@/api/admin/problem-lists'

const props = defineProps<{
  modelValue: ProblemListDetail | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: ProblemListDetail): void
}>()

const { t } = useI18n()

type SaveStatus = 'idle' | 'saving' | 'saved' | 'error'

const saveStatus = ref<SaveStatus>('idle')
const lastSavedAt = ref<Date | null>(null)
const errorMessage = ref<string | null>(null)

// Local form state
const localBannerTag = ref(props.modelValue?.bannerTag || '')
const localBannerTheme = ref(props.modelValue?.bannerTheme || 'blue')
const localBannerOrder = ref(props.modelValue?.bannerOrder || 0)

// Sync with modelValue prop
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal) {
      localBannerTag.value = newVal.bannerTag || ''
      localBannerTheme.value = newVal.bannerTheme || 'blue'
      localBannerOrder.value = newVal.bannerOrder || 0
    }
  },
  { immediate: true },
)

const bannerThemes = [
  { value: 'blue', label: t('problemLists.themes.blue') },
  { value: 'green', label: t('problemLists.themes.green') },
  { value: 'purple', label: t('problemLists.themes.purple') },
  { value: 'orange', label: t('problemLists.themes.orange') },
  { value: 'red', label: t('problemLists.themes.red') },
]

const hasChanges = computed(() => {
  if (!props.modelValue) return false
  return (
    localBannerTag.value !== (props.modelValue.bannerTag || '') ||
    localBannerTheme.value !== (props.modelValue.bannerTheme || 'blue') ||
    localBannerOrder.value !== (props.modelValue.bannerOrder || 0)
  )
})

async function saveBanner() {
  if (!props.modelValue || !hasChanges.value) return

  saveStatus.value = 'saving'
  errorMessage.value = null

  try {
    const updated = await adminProblemListsApi.updateBanner(props.modelValue.id, {
      bannerTag: localBannerTag.value || undefined,
      bannerTheme: localBannerTheme.value,
      bannerOrder: localBannerOrder.value,
    })

    saveStatus.value = 'saved'
    lastSavedAt.value = new Date()

    // Use API response to update local model
    emit('update:modelValue', {
      ...props.modelValue,
      ...updated,
      problems: props.modelValue!.problems,
    })

    // Reset status after 2 seconds
    setTimeout(() => {
      if (saveStatus.value === 'saved') {
        saveStatus.value = 'idle'
      }
    }, 2000)
  } catch (err) {
    saveStatus.value = 'error'
    if (isAxiosError(err) && err.response?.data?.message) {
      errorMessage.value = err.response.data.message
      toast.error(err.response.data.message)
    } else {
      errorMessage.value = t('problemLists.toast.updateFailed')
      toast.error(t('problemLists.toast.updateFailed'))
    }
  }
}

// Debounced save - 1 second delay
const debouncedSave = useDebounceFn(saveBanner, 1000)

// Watch for changes and trigger debounced save
watch([localBannerTag, localBannerTheme, localBannerOrder], () => {
  if (hasChanges.value) {
    debouncedSave()
  }
})

// Blur handler for immediate save
function handleBlur() {
  return () => {
    if (hasChanges.value) {
      // Cancel any pending debounced save
      ;(debouncedSave as { cancel?: () => void }).cancel?.()
      // Save immediately
      saveBanner()
    }
  }
}
</script>

<template>
  <div class="border border-[var(--editor-panel-border)] bg-[var(--editor-panel-bg)] rounded-none">
    <!-- Card Header -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-[var(--editor-border-weak)] select-none">
      <div class="flex items-center gap-1.5">
        <span class="text-xs font-mono font-bold text-[var(--editor-text-primary)] uppercase tracking-wider">
          03 // {{ t('problemLists.sections.bannerSettings') }}
        </span>
      </div>
      <!-- Auto-save Status Indicator in Header -->
      <div v-if="saveStatus !== 'idle'" class="flex items-center gap-1.5 font-mono text-2xs uppercase font-bold">
        <template v-if="saveStatus === 'saving'">
          <span class="h-1.5 w-1.5 bg-[var(--editor-yellow)] shrink-0 animate-ping"></span>
          <span class="text-[var(--editor-yellow)] animate-pulse">{{ t('problemLists.status.saving') }}</span>
        </template>
        <template v-else-if="saveStatus === 'saved'">
          <span class="text-[var(--editor-green)] font-bold">// SAVED</span>
        </template>
        <template v-else-if="saveStatus === 'error'">
          <span class="text-[var(--editor-red)] font-bold">// ERROR</span>
        </template>
      </div>
    </div>

    <!-- Card Content -->
    <div class="p-4 lg:p-5 flex flex-col gap-4.5">
      <!-- Banner Tag -->
      <FormField name="bannerTag">
        <FormItem class="space-y-1.5">
          <div class="flex justify-between items-baseline select-none">
            <FormLabel class="text-xs font-mono font-bold text-[var(--editor-text-primary)] uppercase tracking-wider">
              {{ t('problemLists.form.bannerTag') }}
            </FormLabel>
            <span class="text-2xs text-[var(--editor-text-muted)] font-mono">
              {{ localBannerTag.length }}/50
            </span>
          </div>
          <FormControl>
            <Input
              v-model="localBannerTag"
              :placeholder="t('problemLists.form.bannerTagPlaceholder')"
              :maxlength="50"
              :disabled="disabled"
              class="custom-terminal-input h-9"
              @blur="handleBlur()"
            />
          </FormControl>
          <div class="select-none pt-0.5">
            <span class="text-2xs text-[var(--editor-text-muted)] font-mono leading-none">
              {{ t('problemLists.form.bannerTagDescription') }}
            </span>
          </div>
          <FormMessage class="font-mono text-xs text-[var(--editor-red)]" />
        </FormItem>
      </FormField>

      <!-- Banner Theme -->
      <FormField name="bannerTheme">
        <FormItem class="space-y-1.5">
          <FormLabel class="text-xs font-mono font-bold text-[var(--editor-text-primary)] uppercase tracking-wider">
            {{ t('problemLists.form.bannerTheme') }}
          </FormLabel>
          <Select v-model="localBannerTheme" :disabled="disabled" @update:modelValue="handleBlur()()">
            <FormControl>
              <SelectTrigger class="custom-terminal-input h-9 font-mono text-xs select-none">
                <SelectValue :placeholder="t('problemLists.form.bannerThemePlaceholder')" />
              </SelectTrigger>
            </FormControl>
            <SelectContent class="rounded-none border-[var(--editor-panel-border)] bg-[var(--editor-panel-bg)]">
              <SelectItem
                v-for="theme in bannerThemes"
                :key="theme.value"
                :value="theme.value"
                class="font-mono text-xs rounded-none cursor-pointer focus:bg-[var(--editor-control-bg)]/50 focus:text-[var(--editor-cyan)]"
              >
                {{ theme.label }}
              </SelectItem>
            </SelectContent>
          </Select>
          <FormMessage class="font-mono text-xs text-[var(--editor-red)]" />
        </FormItem>
      </FormField>

      <!-- Banner Order -->
      <FormField name="bannerOrder">
        <FormItem class="space-y-1.5">
          <FormLabel class="text-xs font-mono font-bold text-[var(--editor-text-primary)] uppercase tracking-wider">
            {{ t('problemLists.form.sortOrder') }}
          </FormLabel>
          <FormControl>
            <Input
              v-model.number="localBannerOrder"
              type="number"
              :disabled="disabled"
              class="custom-terminal-input h-9"
              @blur="handleBlur()()"
            />
          </FormControl>
          <div class="select-none pt-0.5">
            <span class="text-2xs text-[var(--editor-text-muted)] font-mono leading-none">
              {{ t('problemLists.form.sortOrderDescription') }}
            </span>
          </div>
          <FormMessage class="font-mono text-xs text-[var(--editor-red)]" />
        </FormItem>
      </FormField>
    </div>
  </div>
</template>

<style scoped>
.custom-terminal-input {
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-sm);
  border-radius: 0 !important;
  border: 1px solid var(--editor-control-border);
  background: var(--editor-control-bg);
  color: var(--editor-text-primary);
  transition: border-color 0.15s ease-in-out, box-shadow 0.15s ease-in-out;
}

:deep(.custom-terminal-input) {
  border-radius: 0 !important;
  font-family: var(--uc-font-code);
}

.custom-terminal-input:hover:not(:disabled) {
  border-color: var(--editor-panel-border);
}

.custom-terminal-input:focus {
  outline: none;
  border-color: var(--editor-cyan) !important;
  box-shadow: 0 0 0 1px var(--editor-cyan) !important;
}

.custom-terminal-input:disabled {
  opacity: 0.55;
  background-color: color-mix(in srgb, var(--editor-control-bg) 60%, transparent);
  border-color: color-mix(in srgb, var(--editor-control-border) 40%, transparent);
  cursor: not-allowed;
}
</style>
