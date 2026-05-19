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
} from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Button } from '@/components/ui/button'
import { adminProblemListsApi, type ProblemListDetail, type UpdateBasicInfoDto } from '@/api/admin/problem-lists'

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
    await adminProblemListsApi.updateBasicInfo(props.modelValue!.id, updateData)

    // Update local model
    emit('update:modelValue', {
      ...props.modelValue!,
      name,
      description: description || '',
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
  <div class="space-y-4 max-w-2xl">
    <div class="terminal-comment">// {{ t('problemLists.sections.basicInfo') }}</div>

    <form class="space-y-4" @submit.prevent>
      <FormField v-slot="{ componentField }" name="name">
        <FormItem>
          <div class="flex items-center justify-between">
            <FormLabel class="terminal-label">{{ t('problemLists.form.name') }}</FormLabel>
            <span
              class="text-xs font-data"
              :class="{
                'text-[var(--silver-400)]': saveStatus === 'idle',
                'text-[var(--terminal-amber)] animate-pulse': saveStatus === 'saving',
                'text-[var(--terminal-green)]': saveStatus === 'saved',
                'text-[var(--terminal-red)]': saveStatus === 'error',
              }"
            >
              <template v-if="saveStatus === 'saving'">// {{ t('problemLists.form.saving') }}</template>
              <template v-else-if="saveStatus === 'saved'">// saved</template>
              <template v-else-if="saveStatus === 'error'">// error</template>
            </span>
          </div>
          <FormControl>
            <Input
              v-bind="componentField"
              :disabled="disabled || saveStatus === 'saving'"
              :placeholder="t('problemLists.form.namePlaceholder')"
              class="terminal-input h-9"
              @blur="handleBlur"
            />
          </FormControl>
          <FormMessage class="font-data text-xs text-[var(--terminal-red)]" />
        </FormItem>
      </FormField>

      <FormField v-slot="{ componentField }" name="description">
        <FormItem>
          <FormLabel class="terminal-label">{{ t('problemLists.form.description') }}</FormLabel>
          <FormControl>
            <Textarea
              v-bind="componentField"
              :disabled="disabled || saveStatus === 'saving'"
              :placeholder="t('problemLists.form.descriptionPlaceholder')"
              class="terminal-input min-h-[100px] resize-y"
              @blur="handleBlur"
            />
          </FormControl>
          <FormMessage class="font-data text-xs text-[var(--terminal-red)]" />
        </FormItem>
      </FormField>

      <div v-if="isCreate" class="pt-2">
        <Button
          type="button"
          variant="terminal"
          class="font-data text-xs"
          :disabled="saveStatus === 'saving'"
          @click="handleCreate"
        >
          <span v-if="saveStatus === 'saving'" class="animate-pulse">{{ t('problemLists.form.creating') }}</span>
          <span v-else>{{ t('problemLists.form.createList') }}</span>
        </Button>
      </div>
    </form>
  </div>
</template>
