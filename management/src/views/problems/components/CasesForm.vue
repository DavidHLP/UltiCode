<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  IconFlask,
  IconBrackets,
  IconBulb,
  IconTag,
  IconPlus,
  IconX,
  IconCheck,
  IconTrash,
} from '@tabler/icons-vue'
import { Separator } from '@/components/ui/separator'
import TestCasesEditor from '@/components/problem/TestCasesEditor.vue'
import type { TestCaseExample } from '@/components/problem/TestCasesEditor.vue'

export interface CasesFormData {
  examples: TestCaseExample[]
  constraints: string[]
  hints: string[]
  tags: string[]
}

interface ProblemData {
  examples?: TestCaseExample[]
  constraints?: string[]
  hints?: string[]
  tags?: string[]
}

const props = withDefaults(
  defineProps<{
    problem?: ProblemData
  }>(),
  {},
)

const emit = defineEmits<{
  submit: [data: CasesFormData]
  cancel: []
}>()

const { t } = useI18n()

function ensureExamples(examples?: TestCaseExample[]): TestCaseExample[] {
  if (examples && examples.length > 0) {
    return examples.map((ex) => ({
      id: ex.id || crypto.randomUUID(),
      input: ex.input || '',
      output: ex.output || '',
      explanation: ex.explanation || '',
      inputs: ex.inputs,
    }))
  }
  return [{ id: crypto.randomUUID(), input: '', output: '', explanation: '' }]
}

const formData = ref<CasesFormData>({
  examples: ensureExamples(),
  constraints: [],
  hints: [],
  tags: [],
})

const newConstraint = ref('')
const newHint = ref('')
const newTag = ref('')
const loading = ref(false)
const errors = ref<Record<string, string>>({})

function updateForm(data?: ProblemData) {
  if (!data) return
  formData.value = {
    examples: ensureExamples(data.examples),
    constraints: [...(data.constraints || [])],
    hints: [...(data.hints || [])],
    tags: [...(data.tags || [])],
  }
}

watch(
  () => props.problem,
  (newVal) => {
    if (newVal) updateForm(newVal)
  },
  { deep: true, immediate: true },
)

function validate(): boolean {
  errors.value = {}
  if (formData.value.examples?.length === 0) {
    errors.value.examples = t('problems.casesForm.validation.examplesRequired')
  }
  for (let i = 0; i < (formData.value.examples?.length || 0); i++) {
    const example = formData.value.examples![i]
    if (example && !example.input?.trim()) {
      errors.value[`example-${i}-input`] = t('problems.casesForm.validation.inputRequired')
    }
    if (example && !example.output?.trim()) {
      errors.value[`example-${i}-output`] = t('problems.casesForm.validation.outputRequired')
    }
  }
  return Object.keys(errors.value).length === 0
}

function submit() {
  if (!validate()) return
  emit('submit', formData.value)
}

function cancel() {
  emit('cancel')
}

function addConstraint() {
  if (newConstraint.value.trim()) {
    formData.value.constraints!.push(newConstraint.value.trim())
    newConstraint.value = ''
  }
}

function removeConstraint(index: number) {
  formData.value.constraints!.splice(index, 1)
}

function addHint() {
  if (newHint.value.trim()) {
    formData.value.hints!.push(newHint.value.trim())
    newHint.value = ''
  }
}

function removeHint(index: number) {
  formData.value.hints!.splice(index, 1)
}

function addTag() {
  if (newTag.value.trim() && !formData.value.tags!.includes(newTag.value.trim())) {
    formData.value.tags!.push(newTag.value.trim())
    newTag.value = ''
  }
}

function removeTag(index: number) {
  formData.value.tags!.splice(index, 1)
}

defineExpose({
  setLoading: (value: boolean) => {
    loading.value = value
  },
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
    <!-- Main Content -->
    <div class="lg:col-span-8 space-y-5">
      <!-- Test Cases Section -->
      <section class="p-5 rounded-none border bg-card">
        <div class="flex items-center gap-2 mb-4">
          <IconFlask class="h-5 w-5 text-muted-foreground" />
          <h2 class="font-semibold">{{ t('problems.casesForm.testCasesSection') }}</h2>
        </div>
        <TestCasesEditor v-model="formData.examples as TestCaseExample[]" />
        <p v-if="errors.examples" class="text-sm text-destructive mt-3">{{ errors.examples }}</p>
      </section>

      <!-- Constraints & Hints Section -->
      <section class="p-5 rounded-none border bg-card">
        <div class="flex items-center gap-2 mb-4">
          <IconBrackets class="h-5 w-5 text-muted-foreground" />
          <h2 class="font-semibold">{{ t('problems.casesForm.constraintsAndHints') }}</h2>
        </div>

        <div class="space-y-6">
          <!-- Constraints -->
          <div class="space-y-3">
            <Label class="text-sm text-muted-foreground flex items-center gap-2">
              <IconBrackets class="h-4 w-4" />
              {{ t('problems.casesForm.constraints') }}
              <Badge v-if="formData.constraints.length > 0" variant="secondary" class="text-xs">
                {{ formData.constraints.length }}
              </Badge>
            </Label>

            <div class="flex gap-2">
              <Input
                v-model="newConstraint"
                :placeholder="t('problems.casesForm.constraintPlaceholder')"
                @keyup.enter="addConstraint"
                class="font-mono text-sm"
              />
              <Button size="sm" variant="secondary" @click="addConstraint">
                <IconPlus class="h-4 w-4 mr-1" />
                {{ t('problems.casesForm.add') }}
              </Button>
            </div>

            <div v-if="formData.constraints.length > 0" class="flex flex-wrap gap-2">
              <Badge
                v-for="(constraint, idx) in formData.constraints"
                :key="idx"
                variant="outline"
                class="font-mono text-xs px-2.5 py-1 gap-1 pr-1.5 group"
              >
                {{ constraint }}
                <button
                  class="hover:text-destructive text-muted-foreground rounded-none p-0.5 transition-colors"
                  @click="removeConstraint(idx)"
                >
                  <IconX class="h-3 w-3" />
                </button>
              </Badge>
            </div>
            <p v-else class="text-sm text-muted-foreground italic">
              {{ t('problems.casesForm.noConstraints') }}
            </p>
          </div>

          <Separator />

          <!-- Hints -->
          <div class="space-y-3">
            <Label class="text-sm text-muted-foreground flex items-center gap-2">
              <IconBulb class="h-4 w-4" />
              {{ t('problems.casesForm.hints') }}
              <Badge v-if="formData.hints.length > 0" variant="secondary" class="text-xs">
                {{ formData.hints.length }}
              </Badge>
            </Label>

            <div class="flex gap-2">
              <Input
                v-model="newHint"
                :placeholder="t('problems.casesForm.addHint')"
                @keyup.enter="addHint"
              />
              <Button size="sm" variant="secondary" @click="addHint">
                <IconPlus class="h-4 w-4 mr-1" />
                {{ t('problems.casesForm.add') }}
              </Button>
            </div>

            <div v-if="formData.hints.length > 0" class="space-y-2">
              <div
                v-for="(hint, idx) in formData.hints"
                :key="idx"
                class="flex items-start justify-between p-3 rounded-none bg-muted/20 border group"
              >
                <div class="flex gap-2 text-sm">
                  <span class="font-mono text-xs text-muted-foreground mt-0.5">{{ idx + 1 }}.</span>
                  <span>{{ hint }}</span>
                </div>
                <Button
                  variant="ghost"
                  size="icon"
                  class="h-7 w-7 opacity-0 group-hover:opacity-100 transition-opacity shrink-0"
                  @click="removeHint(idx)"
                >
                  <IconTrash class="h-4 w-4" />
                </Button>
              </div>
            </div>
            <p v-else class="text-sm text-muted-foreground italic">
              {{ t('problems.casesForm.noHints') }}
            </p>
          </div>
        </div>
      </section>
    </div>

    <!-- Sidebar -->
    <aside class="lg:col-span-4 space-y-4 lg:sticky lg:top-6 h-fit self-start">
      <!-- Tags Card -->
      <div class="p-4 rounded-none border bg-card">
        <div class="flex items-center gap-2 mb-3">
          <IconTag class="h-4 w-4 text-muted-foreground" />
          <h3 class="text-sm font-medium">{{ t('problems.casesForm.tags') }}</h3>
        </div>

        <div class="space-y-3">
          <div class="relative">
            <Input
              v-model="newTag"
              :placeholder="t('problems.casesForm.addTag')"
              @keyup.enter="addTag"
              class="pr-9 text-sm"
            />
            <button
              v-if="newTag"
              class="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-primary"
              @click="addTag"
            >
              <IconPlus class="h-4 w-4" />
            </button>
          </div>

          <div v-if="formData.tags.length > 0" class="flex flex-wrap gap-1.5">
            <Badge
              v-for="(tag, idx) in formData.tags"
              :key="idx"
              variant="secondary"
              class="gap-1 pr-1.5 text-sm"
            >
              {{ tag }}
              <button
                class="hover:text-destructive text-muted-foreground hover:bg-destructive/10 rounded-full p-0.5 transition-colors"
                @click="removeTag(idx)"
              >
                <IconX class="h-3 w-3" />
              </button>
            </Badge>
          </div>
          <p v-else class="text-xs text-muted-foreground italic">
            {{ t('problems.casesForm.noTags') }}
          </p>
        </div>
      </div>

      <!-- Summary Card -->
      <div class="p-4 rounded-none border bg-card">
        <h3 class="text-sm font-medium mb-3">{{ t('problems.casesForm.configurationSummary') }}</h3>
        <div class="space-y-2 text-sm">
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">{{
              t('problems.casesForm.summary.testCases')
            }}</span>
            <span class="font-medium tabular-nums">{{ formData.examples.length }}</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">{{
              t('problems.casesForm.summary.constraints')
            }}</span>
            <span class="font-medium tabular-nums">{{ formData.constraints.length }}</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">{{ t('problems.casesForm.summary.hints') }}</span>
            <span class="font-medium tabular-nums">{{ formData.hints.length }}</span>
          </div>
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">{{ t('problems.casesForm.summary.tags') }}</span>
            <span class="font-medium tabular-nums">{{ formData.tags.length }}</span>
          </div>
        </div>
      </div>

      <!-- Actions -->
      <div class="p-4 rounded-none border border-primary/20 bg-primary/5">
        <div class="flex flex-col gap-2">
          <Button class="w-full" :disabled="loading" @click="submit">
            <IconCheck v-if="!loading" class="h-4 w-4 mr-1" />
            {{ loading ? t('problems.casesForm.saving') : t('problems.casesForm.saveChanges') }}
          </Button>
          <Button variant="outline" class="w-full" @click="cancel">{{ t('common.cancel') }}</Button>
        </div>
      </div>
    </aside>
  </div>
</template>
