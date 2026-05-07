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
  FormDescription,
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type { ProblemList } from '@/api/admin/problem-lists'
import { adminProblemListsApi } from '@/api/admin/problem-lists'

const props = defineProps<{
  modelValue: ProblemList | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: ProblemList): void
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
    await adminProblemListsApi.updateBanner(props.modelValue.id, {
      bannerTag: localBannerTag.value || undefined,
      bannerTheme: localBannerTheme.value,
      bannerOrder: localBannerOrder.value,
    })

    saveStatus.value = 'saved'
    lastSavedAt.value = new Date()

    // Emit updated modelValue
    emit('update:modelValue', {
      ...props.modelValue,
      bannerTag: localBannerTag.value || undefined,
      bannerTheme: localBannerTheme.value,
      bannerOrder: localBannerOrder.value,
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
function handleBlur(field: 'tag' | 'theme' | 'order') {
  return () => {
    if (hasChanges.value) {
      // Cancel any pending debounced save
      ;(debouncedSave as { cancel?: () => void }).cancel?.()
      // Save immediately
      saveBanner()
    }
  }
}

const statusText = computed(() => {
  switch (saveStatus.value) {
    case 'saving':
      return t('problemLists.status.saving')
    case 'saved':
      return t('problemLists.status.saved')
    case 'error':
      return errorMessage.value || t('problemLists.status.error')
    default:
      return ''
  }
})
</script>

<template>
  <div class="space-y-4">
    <div class="terminal-comment">// {{ t('problemLists.sections.bannerSettings') }}</div>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <!-- Banner Tag -->
      <FormField name="bannerTag">
        <FormItem>
          <FormLabel class="terminal-label">{{ t('problemLists.form.bannerTag') }}</FormLabel>
          <FormControl>
            <Input
              v-model="localBannerTag"
              :placeholder="t('problemLists.form.bannerTagPlaceholder')"
              :maxlength="50"
              :disabled="disabled"
              class="terminal-input h-9"
              @blur="handleBlur('tag')"
            />
          </FormControl>
          <div class="flex justify-between items-center">
            <FormDescription class="text-xs text-[var(--silver-400)]">
              {{ t('problemLists.form.bannerTagDescription') }}
            </FormDescription>
            <span class="text-xs text-[var(--silver-400)] font-data">
              {{ localBannerTag.length }}/50
            </span>
          </div>
          <FormMessage class="font-data text-xs text-[var(--terminal-red)]" />
        </FormItem>
      </FormField>

      <!-- Banner Theme -->
      <FormField name="bannerTheme">
        <FormItem>
          <FormLabel class="terminal-label">{{ t('problemLists.form.bannerTheme') }}</FormLabel>
          <Select v-model="localBannerTheme" :disabled="disabled" @update:modelValue="handleBlur('theme')">
            <FormControl>
              <SelectTrigger class="terminal-input h-9 font-data text-xs">
                <SelectValue :placeholder="t('problemLists.form.bannerThemePlaceholder')" />
              </SelectTrigger>
            </FormControl>
            <SelectContent>
              <SelectItem
                v-for="theme in bannerThemes"
                :key="theme.value"
                :value="theme.value"
                class="font-data text-xs"
              >
                {{ theme.label }}
              </SelectItem>
            </SelectContent>
          </Select>
          <FormMessage class="font-data text-xs text-[var(--terminal-red)]" />
        </FormItem>
      </FormField>

      <!-- Banner Order -->
      <FormField name="bannerOrder">
        <FormItem>
          <FormLabel class="terminal-label">{{ t('problemLists.form.sortOrder') }}</FormLabel>
          <FormControl>
            <Input
              v-model.number="localBannerOrder"
              type="number"
              :disabled="disabled"
              class="terminal-input h-9"
              @blur="handleBlur('order')"
            />
          </FormControl>
          <FormDescription class="text-xs text-[var(--silver-400)]">
            {{ t('problemLists.form.sortOrderDescription') }}
          </FormDescription>
          <FormMessage class="font-data text-xs text-[var(--terminal-red)]" />
        </FormItem>
      </FormField>
    </div>

    <!-- Save Status Indicator -->
    <div class="flex items-center gap-2 min-h-[20px]">
      <template v-if="saveStatus === 'saving'">
        <span class="animate-pulse text-xs text-[var(--silver-400)] font-data">
          {{ t('problemLists.status.saving') }}
        </span>
      </template>
      <template v-else-if="saveStatus === 'saved'">
        <span class="text-xs text-[var(--terminal-green)] font-data">
          {{ t('problemLists.status.saved') }}
        </span>
      </template>
      <template v-else-if="saveStatus === 'error'">
        <span class="text-xs text-[var(--terminal-red)] font-data">
          {{ errorMessage || t('problemLists.status.error') }}
        </span>
      </template>
    </div>
  </div>
</template>
