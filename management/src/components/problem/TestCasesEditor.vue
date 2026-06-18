<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { X, Plus } from 'lucide-vue-next'

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
      <Button
        v-for="testCase in caseTabs"
        :key="testCase.id"
        :variant="testCase.id === activeId ? 'secondary' : 'ghost'"
        size="sm"
        class="h-8 rounded-none px-3 text-xs font-medium"
        :class="
          testCase.id === activeId
            ? 'text-foreground shadow-none'
            : 'text-muted-foreground hover:text-foreground'
        "
        @click="selectCase(testCase.id)"
      >
        <span>{{ testCase.displayLabel }}</span>
        <button
          v-if="testCase.id === activeId && canRemoveCases && !readonly"
          type="button"
          class="ml-1 inline-flex h-4 w-4 items-center justify-center rounded-none text-2xs text-muted-foreground hover:text-foreground"
          @click.stop="removeCase(testCase.id)"
        >
          <X :size="12" />
        </button>
      </Button>

      <Button
        v-if="!readonly"
        variant="ghost"
        size="sm"
        class="h-8 rounded-none px-2 text-xs text-muted-foreground hover:text-foreground"
        @click="addCase"
      >
        <Plus :size="14" class="mr-1" />
        {{ t('problems.testCasesEditor.addExample') }}
      </Button>
    </div>

    <div v-if="activeCase" class="space-y-4">
      <div class="space-y-2">
        <label class="text-sm font-medium text-muted-foreground">{{
          t('problems.testCasesEditor.input')
        }}</label>
        <Textarea
          :model-value="activeCase.input"
          :readonly="readonly"
          :placeholder="t('problems.testCasesEditor.inputPlaceholder')"
          class="font-mono text-sm bg-muted border-none shadow-none focus-visible:ring-0 focus-visible:ring-offset-0 min-h-[80px]"
          @update:model-value="(v) => updateCase('input', v as string)"
        />
      </div>

      <div class="space-y-2">
        <label class="text-sm font-medium text-muted-foreground">{{
          t('problems.testCasesEditor.output')
        }}</label>
        <Textarea
          :model-value="activeCase.output"
          :readonly="readonly"
          :placeholder="t('problems.testCasesEditor.outputPlaceholder')"
          class="font-mono text-sm bg-muted border-none shadow-none focus-visible:ring-0 focus-visible:ring-offset-0 min-h-[80px]"
          @update:model-value="(v) => updateCase('output', v as string)"
        />
      </div>

      <div class="space-y-2">
        <label class="text-sm font-medium text-muted-foreground">{{
          t('problems.testCasesEditor.explanationOptional')
        }}</label>
        <Input
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
