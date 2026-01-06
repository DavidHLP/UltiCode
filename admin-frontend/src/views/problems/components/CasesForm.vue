<script setup lang="ts">
import { ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { IconFlask, IconBrackets, IconBulb, IconTag, IconPlus, IconX } from '@tabler/icons-vue'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
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

// Validates and ensures examples array has at least one valid entry
function ensureExamples(examples?: TestCaseExample[]): TestCaseExample[] {
  if (examples && examples.length > 0) {
    return examples.map((ex) => ({
      id: ex.id || crypto.randomUUID(),
      input: ex.input || '',
      output: ex.output || '',
      explanation: ex.explanation || '',
    }))
  }
  return [{ id: crypto.randomUUID(), input: '', output: '', explanation: '' }]
}

// Initialize form data with safe defaults
const formData = ref<CasesFormData>({
  examples: ensureExamples(),
  constraints: [],
  hints: [],
  tags: [],
})

// Function to reset/update form data safely
function updateForm(data?: ProblemData) {
  if (!data) return

  formData.value = {
    examples: ensureExamples(data.examples),
    constraints: [...(data.constraints || [])],
    hints: [...(data.hints || [])],
    tags: [...(data.tags || [])],
  }
}

// Watch for prop changes to update form
watch(
  () => props.problem,
  (newVal) => {
    if (newVal) {
      updateForm(newVal)
    }
  },
  { deep: true, immediate: true },
)

const newConstraint = ref('')
const newHint = ref('')
const newTag = ref('')
const loading = ref(false)

// Validation errors
const errors = ref<Record<string, string>>({})

function validate(): boolean {
  errors.value = {}

  if (formData.value.examples?.length === 0) {
    errors.value.examples = 'At least one example is required'
  }

  for (let i = 0; i < (formData.value.examples?.length || 0); i++) {
    const example = formData.value.examples![i]
    if (example && !example.input?.trim()) {
      errors.value[`example-${i}-input`] = 'Input is required'
    }
    if (example && !example.output?.trim()) {
      errors.value[`example-${i}-output`] = 'Output is required'
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

// Expose loading state for parent to control
defineExpose({
  setLoading: (value: boolean) => {
    loading.value = value
  },
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
    <!-- Left Column: Main Content -->
    <div class="lg:col-span-2 space-y-6">
      <!-- Test Cases -->
      <Card>
        <CardHeader>
          <div class="flex items-center gap-2">
            <IconFlask class="h-5 w-5 text-muted-foreground" />
            <CardTitle>Test Cases</CardTitle>
          </div>
          <CardDescription>
            Add examples to help users understand input/output format.
          </CardDescription>
        </CardHeader>
        <CardContent class="space-y-4">
          <TestCasesEditor v-model="formData.examples as TestCaseExample[]" />
          <p v-if="errors.examples" class="text-sm text-destructive">{{ errors.examples }}</p>
        </CardContent>
      </Card>

      <!-- Additional Info: Constraints & Hints -->
      <Card>
        <CardHeader>
          <div class="flex items-center gap-2">
            <IconBrackets class="h-5 w-5 text-muted-foreground" />
            <CardTitle>Additional Information</CardTitle>
          </div>
        </CardHeader>
        <CardContent class="space-y-8">
          <!-- Constraints -->
          <div class="space-y-3">
            <Label class="text-base flex items-center gap-2">
              <IconBrackets class="h-4 w-4" /> Constraints
            </Label>

            <div class="flex gap-2">
              <Input
                v-model="newConstraint"
                placeholder="e.g. 1 <= nums.length <= 10^4"
                @keyup.enter="addConstraint"
                class="font-mono text-sm"
              />
              <Button type="button" variant="secondary" @click="addConstraint">Add</Button>
            </div>

            <ul v-if="formData.constraints!.length > 0" class="space-y-2">
              <li
                v-for="(constraint, idx) in formData.constraints"
                :key="idx"
                class="flex items-center justify-between p-2 rounded-md bg-muted/50 border group text-sm font-mono"
              >
                <span>{{ constraint }}</span>
                <Button
                  variant="ghost"
                  size="icon"
                  class="h-6 w-6 opacity-0 group-hover:opacity-100 transition-opacity"
                  @click="removeConstraint(idx)"
                >
                  <IconX class="h-3 w-3" />
                </Button>
              </li>
            </ul>
            <p v-else class="text-sm text-muted-foreground italic">No constraints added.</p>
          </div>

          <Separator />

          <!-- Hints -->
          <div class="space-y-3">
            <Label class="text-base flex items-center gap-2">
              <IconBulb class="h-4 w-4" /> Hints
            </Label>
            <div class="flex gap-2">
              <Input v-model="newHint" placeholder="Add a hint..." @keyup.enter="addHint" />
              <Button type="button" variant="secondary" @click="addHint">Add</Button>
            </div>

            <ul v-if="formData.hints!.length > 0" class="space-y-2">
              <li
                v-for="(hint, idx) in formData.hints"
                :key="idx"
                class="flex items-start justify-between p-2 rounded-md bg-muted/50 border group text-sm"
              >
                <div class="flex gap-2">
                  <span class="text-muted-foreground font-mono text-xs mt-0.5">{{ idx + 1 }}.</span>
                  <span>{{ hint }}</span>
                </div>
                <Button
                  variant="ghost"
                  size="icon"
                  class="h-6 w-6 opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0"
                  @click="removeHint(idx)"
                >
                  <IconX class="h-3 w-3" />
                </Button>
              </li>
            </ul>
            <p v-else class="text-sm text-muted-foreground italic">No hints added.</p>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- Right Column: Sidebar -->
    <div class="space-y-6 lg:sticky lg:top-6 h-fit">
      <!-- Actions Card -->
      <Card class="border-primary/10 shadow-sm">
        <CardHeader class="pb-3">
          <CardTitle>Actions</CardTitle>
        </CardHeader>
        <CardContent class="space-y-3">
          <div class="flex flex-col gap-3">
            <Button class="w-full" :disabled="loading" @click="submit">
              {{ loading ? 'Saving...' : 'Save Test Cases' }}
            </Button>
            <Button variant="outline" class="w-full" @click="cancel"> Cancel </Button>
          </div>
        </CardContent>
      </Card>

      <!-- Tags Card -->
      <Card>
        <CardHeader class="pb-3">
          <div class="flex items-center gap-2">
            <IconTag class="h-4 w-4" />
            <CardTitle>Tags</CardTitle>
          </div>
        </CardHeader>
        <CardContent class="space-y-3">
          <div class="relative">
            <Input v-model="newTag" placeholder="Add tag..." @keyup.enter="addTag" class="pr-8" />
            <button
              v-if="newTag"
              class="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-primary"
              @click="addTag"
            >
              <IconPlus class="h-4 w-4" />
            </button>
          </div>

          <div v-if="formData.tags?.length" class="flex flex-wrap gap-1.5">
            <Badge
              v-for="(tag, idx) in formData.tags"
              :key="idx"
              variant="outline"
              class="gap-1 pr-1.5"
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
          <p v-else class="text-sm text-muted-foreground italic">No tags added.</p>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
