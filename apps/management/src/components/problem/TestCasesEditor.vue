<script setup lang="ts">
import { FieldLabel } from '@ulticode/design-system/ui/field'

import { ref, computed, watch, useId } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@ulticode/design-system/ui/button'
import { Input } from '@ulticode/design-system/ui/input'
import { Textarea } from '@ulticode/design-system/ui/textarea'
import { X, Plus } from '@lucide/vue'

export interface TestCaseExample {
  id: string
  input: string
  output: string
  explanation?: string
  inputs?: Array<{
    name: string
    value?: unknown
    label?: string
    fieldName?: string
  }>
}

const props = withDefaults(
  defineProps<{
    modelValue: TestCaseExample[]
    readonly?: boolean
  }>(),
  {
    readonly: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: TestCaseExample[]]
}>()

const { t } = useI18n()
const editorId = useId()

const activeId = ref('')
const localCases = ref<TestCaseExample[]>([])
const isLocalUpdate = ref(false)

const generateId = () => `case-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`

// Initialize with provided values
watch(
  () => props.modelValue,
  (cases) => {
    if (!isLocalUpdate.value) {
      localCases.value = cases.map((c) => ({ ...c }))
      if (!activeId.value && localCases.value.length > 0) {
        activeId.value = localCases.value[0]?.id ?? ''
      }
    }
    isLocalUpdate.value = false
  },
  { immediate: true },
)

// Emit changes when local cases change
watch(
  localCases,
  (cases) => {
    isLocalUpdate.value = true
    emit('update:modelValue', cases)
  },
  { deep: true },
)

// Update active ID if current one is removed
watch(localCases, (cases) => {
  const exists = cases.some((c) => c.id === activeId.value)
  if (!exists && cases.length > 0) {
    activeId.value = cases[0]?.id ?? ''
  }
})

const activeCase = computed(() => {
  if (!localCases.value.length) return undefined
  return localCases.value.find((c) => c.id === activeId.value) ?? localCases.value[0]
})

const caseTabs = computed(() =>
  localCases.value.map((testCase, index) => ({
    ...testCase,
    displayLabel: t('problems.testCasesEditor.example', { number: index + 1 }),
  })),
)

const canRemoveCases = computed(() => localCases.value.length > 1)

const addCase = () => {
  const newId = generateId()
  const newCase: TestCaseExample = {
    id: newId,
    input: '',
    output: '',
    explanation: '',
  }
  localCases.value = [...localCases.value, newCase]
  activeId.value = newId
}

const selectCase = (id: string) => {
  activeId.value = id
}

const removeCase = (id: string) => {
  if (!canRemoveCases.value || props.readonly) return
  localCases.value = localCases.value.filter((c) => c.id !== id)
}

const updateCase = (field: keyof TestCaseExample, value: string) => {
  if (!activeCase.value) return
  const index = localCases.value.findIndex((c) => c.id === activeCase.value!.id)
  if (index !== -1) {
    const current = localCases.value[index]
    if (current) {
      localCases.value[index] = {
        id: current.id,
        input: current.input,
        output: current.output,
        explanation: current.explanation,
        [field]: value,
      } as TestCaseExample
    }
  }
}
</script>

<template>
  <div class="test-cases-editor">
    <div class="flex flex-wrap items-center gap-2 mb-4">
      <div v-for="testCase in caseTabs" :key="testCase.id" class="flex items-center gap-1">
        <Button
          :variant="testCase.id === activeId ? 'secondary' : 'ghost'"
          size="sm"
          class="px-3"
          :class="
            testCase.id === activeId
              ? 'text-foreground shadow-none'
              : 'text-muted-foreground hover:text-foreground'
          "
          @click="selectCase(testCase.id)"
        >
          <span>{{ testCase.displayLabel }}</span>
        </Button>
        <Button
          variant="ghost"
          v-if="testCase.id === activeId && canRemoveCases && !readonly"
          type="button"
          size="icon-sm"
          :aria-label="t('common.remove')"
          @click.stop="removeCase(testCase.id)"
        >
          <X :size="12" />
        </Button>
      </div>

      <Button v-if="!readonly" variant="ghost" size="sm" class="px-2" @click="addCase">
        <Plus :size="14" class="mr-1" />
        {{ t('problems.testCasesEditor.addExample') }}
      </Button>
    </div>

    <div v-if="activeCase" class="space-y-4">
      <div class="space-y-2">
        <FieldLabel :for="`${editorId}-input`">{{
          t('problems.testCasesEditor.input')
        }}</FieldLabel>
        <Textarea
          :id="`${editorId}-input`"
          :model-value="activeCase.input"
          :readonly="readonly"
          :placeholder="t('problems.testCasesEditor.inputPlaceholder')"
          class="min-h-[80px]"
          @update:model-value="(v) => updateCase('input', v as string)"
        />
      </div>

      <div class="space-y-2">
        <FieldLabel :for="`${editorId}-output`">{{
          t('problems.testCasesEditor.output')
        }}</FieldLabel>
        <Textarea
          :id="`${editorId}-output`"
          :model-value="activeCase.output"
          :readonly="readonly"
          :placeholder="t('problems.testCasesEditor.outputPlaceholder')"
          class="min-h-[80px]"
          @update:model-value="(v) => updateCase('output', v as string)"
        />
      </div>

      <div class="space-y-2">
        <FieldLabel :for="`${editorId}-explanation`">{{
          t('problems.testCasesEditor.explanationOptional')
        }}</FieldLabel>
        <Input
          :id="`${editorId}-explanation`"
          :model-value="activeCase.explanation || ''"
          :readonly="readonly"
          :placeholder="t('problems.testCasesEditor.explanationPlaceholder')"
          @update:model-value="(v) => updateCase('explanation', v as string)"
        />
      </div>
    </div>

    <p v-else class="text-sm text-muted-foreground italic py-4">
      {{ t('problems.testCasesEditor.noCases') }}
    </p>
  </div>
</template>

<style scoped>
.test-cases-editor {
  display: flex;
  flex-direction: column;
}
</style>
