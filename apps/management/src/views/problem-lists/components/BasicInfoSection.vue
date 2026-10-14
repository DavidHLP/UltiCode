<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { isAxiosError } from 'axios'
import { z } from 'zod'
import { toTypedSchema } from '@vee-validate/zod'
import { useForm } from 'vee-validate'
import { watchDebounced } from '@vueuse/core'
import { toast } from 'vue-sonner'
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@ulticode/design-system/ui/form'
import { Input } from '@ulticode/design-system/ui/input'
import { Textarea } from '@ulticode/design-system/ui/textarea'
import { Button } from '@ulticode/design-system/ui/button'
import {
  adminProblemListsApi,
  type ProblemListDetail,
  type UpdateBasicInfoDto,
} from '@/api/admin/problem-lists'

type SaveStatus = 'idle' | 'saving' | 'saved' | 'error'

const props = defineProps<{
  modelValue: ProblemListDetail | null
  disabled?: boolean
  isCreate?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: ProblemListDetail): void
  (e: 'success', id: string): void
}>()

const { t } = useI18n()
const saveStatus = ref<SaveStatus>('idle')
const lastSavedValues = ref<{ name: string; description: string } | null>(null)

const formSchema = toTypedSchema(
  z.object({
    name: z.string().min(1, t('problemLists.form.validation.nameRequired')).max(100),
    description: z.string().optional(),
  }),
)

const form = useForm({
  validationSchema: formSchema,
  initialValues: {
    name: props.modelValue?.name || '',
    description: props.modelValue?.description || '',
  },
})

// Sync form values when modelValue changes
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal) {
      form.setFieldValue('name', newVal.name)
      form.setFieldValue('description', newVal.description || '')
      lastSavedValues.value = {
        name: newVal.name,
        description: newVal.description || '',
      }
    }
  },
  { immediate: true },
)

// Track initial values for comparison
watch(
  () => [form.values.name, form.values.description],
  () => {
    if (saveStatus.value === 'saved') {
      saveStatus.value = 'idle'
    }
  },
)

// Auto-save with debounce
async function saveChanges() {
  if (!props.modelValue || props.disabled) return

  const name = form.values.name ?? ''
  const description = form.values.description ?? ''
  const currentValues = { name, description }

  // Skip if no changes
  if (
    lastSavedValues.value &&
    lastSavedValues.value.name === currentValues.name &&
    lastSavedValues.value.description === currentValues.description
  ) {
    return
  }

  // Validate before saving
  const result = await form.validate()
  if (!result.valid) return

  saveStatus.value = 'saving'

  try {
    const updateData: UpdateBasicInfoDto = {
      name,
      description: description || undefined,
    }
    const updated = await adminProblemListsApi.updateBasicInfo(props.modelValue!.id, updateData)

    // Use API response to update local model
    emit('update:modelValue', {
      ...props.modelValue!,
      ...updated,
      problems: props.modelValue!.problems,
    })

    lastSavedValues.value = currentValues
    saveStatus.value = 'saved'
  } catch (err) {
    saveStatus.value = 'error'
    if (isAxiosError(err) && !err.response) {
      toast.error(t('problemLists.toast.networkError'))
    } else if (isAxiosError(err) && err.response?.data?.message) {
      toast.error(err.response.data.message)
    } else {
      toast.error(t('problemLists.toast.updateFailed'))
    }
  }
}

// Debounced auto-save on field changes (edit mode only)
watchDebounced(
  [() => form.values.name, () => form.values.description],
  () => {
    if (!props.disabled && props.modelValue && !props.isCreate) {
      saveChanges()
    }
  },
  { debounce: 1000, maxWait: 2000 },
)

// Save on blur
function handleBlur() {
  if (!props.disabled && props.modelValue && !props.isCreate) {
    saveChanges()
  }
}

async function handleCreate() {
  if (!props.isCreate) return

  const result = await form.validate()
  if (!result.valid) return

  saveStatus.value = 'saving'

  try {
    const newList = await adminProblemListsApi.createList({
      name: form.values.name ?? '',
      description: form.values.description || undefined,
    })
    saveStatus.value = 'saved'
    emit('success', newList.id)
  } catch (err) {
    saveStatus.value = 'error'
    if (isAxiosError(err) && !err.response) {
      toast.error(t('problemLists.toast.networkError'))
    } else if (isAxiosError(err) && err.response?.data?.message) {
      toast.error(err.response.data.message)
    } else {
      toast.error(t('problemLists.toast.createFailed'))
    }
  }
}

// Expose for testing
defineExpose({ saveStatus, form, saveChanges })
</script>

<template>
  <div class="border border-border bg-card rounded-lg">
    <!-- Card Header -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-border select-none">
      <div class="flex items-center gap-1.5">
        <span class="text-xs font-mono font-bold text-foreground uppercase tracking-wider">
          01 // {{ t('problemLists.sections.basicInfo') }}
        </span>
      </div>

      <!-- Auto-save Status Indicator in Header -->
      <div v-if="!isCreate" class="flex items-center gap-1.5 font-mono text-xs uppercase font-bold">
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
    <div class="p-4 lg:p-5">
      <form class="space-y-4.5" @submit.prevent>
        <FormField v-slot="{ componentField }" name="name">
          <FormItem class="space-y-1.5">
            <FormLabel>
              {{ t('problemLists.form.name') }}
            </FormLabel>
            <FormControl>
              <Input
                v-bind="componentField"
                :disabled="disabled || saveStatus === 'saving'"
                :placeholder="t('problemLists.form.namePlaceholder')"
                class=""
                :class="{
                  'border-status-error-mark focus:ring-status-error-mark': form.errors.value?.name,
                }"
                @blur="handleBlur"
              />
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField }" name="description">
          <FormItem class="space-y-1.5">
            <FormLabel>
              {{ t('problemLists.form.description') }}
            </FormLabel>
            <FormControl>
              <Textarea
                v-bind="componentField"
                :disabled="disabled || saveStatus === 'saving'"
                :placeholder="t('problemLists.form.descriptionPlaceholder')"
                class="min-h-[120px] py-2 px-3 resize-y"
                :class="{
                  'border-status-error-mark focus:ring-status-error-mark':
                    form.errors.value?.description,
                }"
                @blur="handleBlur"
              />
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <div v-if="isCreate" class="pt-2">
          <Button type="button" class=" " :disabled="saveStatus === 'saving'" @click="handleCreate">
            <span v-if="saveStatus === 'saving'" class="animate-pulse">{{
              t('problemLists.form.creating')
            }}</span>
            <span v-else>{{ t('problemLists.form.createList') }}</span>
          </Button>
        </div>
      </form>
    </div>
  </div>
</template>
