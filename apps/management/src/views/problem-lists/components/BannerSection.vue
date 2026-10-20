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
} from '@ulticode/design-system/ui/form'
import { Input } from '@ulticode/design-system/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  SelectGroup,
} from '@ulticode/design-system/ui/select'
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
  <div class="border border-border bg-card rounded-lg">
    <!-- Card Header -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-border select-none">
      <div class="flex items-center gap-1.5">
        <span class="text-xs font-mono font-bold text-foreground uppercase tracking-wider">
          03 // {{ t('problemLists.sections.bannerSettings') }}
        </span>
      </div>
      <!-- Auto-save Status Indicator in Header -->
      <div
        v-if="saveStatus !== 'idle'"
        class="flex items-center gap-1.5 font-mono text-xs uppercase font-bold"
      >
        <template v-if="saveStatus === 'saving'">
          <span class="h-1.5 w-1.5 bg-status-warning-mark shrink-0 animate-ping"></span>
          <span class="text-status-warning-mark animate-pulse">{{
            t('problemLists.status.saving')
          }}</span>
        </template>
        <template v-else-if="saveStatus === 'saved'">
          <span class="text-status-success-mark font-bold">{{ t('problemLists.form.saved') }}</span>
        </template>
        <template v-else-if="saveStatus === 'error'">
          <span class="text-status-error-mark font-bold">{{
            t('problemLists.form.saveError')
          }}</span>
        </template>
      </div>
    </div>

    <!-- Card Content -->
    <div class="p-4 lg:p-5 flex flex-col gap-4.5">
      <!-- Banner Tag -->
      <FormField name="bannerTag">
        <FormItem class="space-y-1.5">
          <div class="flex justify-between items-baseline select-none">
            <FormLabel>
              {{ t('problemLists.form.bannerTag') }}
            </FormLabel>
            <span class="text-xs text-muted-foreground font-mono">
              {{ localBannerTag.length }}/50
            </span>
          </div>
          <FormControl>
            <Input
              v-model="localBannerTag"
              :placeholder="t('problemLists.form.bannerTagPlaceholder')"
              :maxlength="50"
              :disabled="disabled"
              class=""
              @blur="handleBlur()"
            />
          </FormControl>
          <div class="select-none pt-0.5">
            <span class="text-xs text-muted-foreground font-mono leading-none">
              {{ t('problemLists.form.bannerTagDescription') }}
            </span>
          </div>
          <FormMessage />
        </FormItem>
      </FormField>

      <!-- Banner Theme -->
      <FormField name="bannerTheme">
        <FormItem class="space-y-1.5">
          <FormLabel>
            {{ t('problemLists.form.bannerTheme') }}
          </FormLabel>
          <Select
            v-model="localBannerTheme"
            :disabled="disabled"
            @update:modelValue="handleBlur()()"
          >
            <FormControl>
              <SelectTrigger class="select-none">
                <SelectValue :placeholder="t('problemLists.form.bannerThemePlaceholder')" />
              </SelectTrigger>
            </FormControl>
            <SelectContent>
              <SelectGroup
                ><SelectItem
                  v-for="theme in bannerThemes"
                  :key="theme.value"
                  :value="theme.value"
                  class="cursor-pointer"
                >
                  {{ theme.label }}
                </SelectItem></SelectGroup
              >
            </SelectContent>
          </Select>
          <FormMessage />
        </FormItem>
      </FormField>

      <!-- Banner Order -->
      <FormField name="bannerOrder">
        <FormItem class="space-y-1.5">
          <FormLabel>
            {{ t('problemLists.form.sortOrder') }}
          </FormLabel>
          <FormControl>
            <Input
              v-model.number="localBannerOrder"
              type="number"
              :disabled="disabled"
              class=""
              @blur="handleBlur()()"
            />
          </FormControl>
          <div class="select-none pt-0.5">
            <span class="text-xs text-muted-foreground font-mono leading-none">
              {{ t('problemLists.form.sortOrderDescription') }}
            </span>
          </div>
          <FormMessage />
        </FormItem>
      </FormField>
    </div>
  </div>
</template>
