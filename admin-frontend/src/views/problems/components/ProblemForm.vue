<script setup lang="ts">
import { ref, watch } from 'vue'
import { Difficulty, ProblemStatus } from '@/api/admin/problems'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Checkbox } from '@/components/ui/checkbox'
import MarkdownEditor from '@/components/problem/MarkdownEditor.vue'
import TestCasesEditor from '@/components/problem/TestCasesEditor.vue'
import type { TestCaseExample } from '@/components/problem/TestCasesEditor.vue'
import type { ProblemFormData } from '@/lib/schemas/problem'

interface Problem {
  id?: string
  slug: string
  title: string
  difficulty: string
  status: string
  is_premium: boolean
  is_published: boolean
  summary?: string
  content?: string
  examples?: TestCaseExample[]
  constraints?: string[]
  hints?: string[]
  languages?: string[]
  tags?: string[]
}

const props = withDefaults(
  defineProps<{
    problem?: Problem
    isEdit?: boolean
  }>(),
  {
    isEdit: false,
  },
)

const emit = defineEmits<{
  submit: [data: ProblemFormData]
}>()

// Initialize form data from props or defaults
const formData = ref<ProblemFormData>({
  slug: props.problem?.slug || '',
  title: props.problem?.title || '',
  difficulty: (props.problem?.difficulty || Difficulty.MEDIUM) as Difficulty,
  status: (props.problem?.status || ProblemStatus.TODO) as ProblemStatus,
  is_premium: props.problem?.is_premium || false,
  is_published: props.problem?.is_published || false,
  summary: props.problem?.summary || '',
  content: props.problem?.content || '',
  examples: props.problem?.examples || [
    { id: 'example-1', input: '', output: '', explanation: '' },
  ],
  constraints: props.problem?.constraints || [],
  hints: props.problem?.hints || [],
  languages: props.problem?.languages || [],
  tags: props.problem?.tags || [],
})

// Update form when problem prop changes (for edit mode)
watch(
  () => props.problem,
  (newProblem) => {
    if (newProblem) {
      formData.value = {
        slug: newProblem.slug || '',
        title: newProblem.title || '',
        difficulty: newProblem.difficulty as Difficulty,
        status: newProblem.status as ProblemStatus,
        is_premium: newProblem.is_premium || false,
        is_published: newProblem.is_published || false,
        summary: newProblem.summary || '',
        content: newProblem.content || '',
        examples: newProblem.examples?.length
          ? newProblem.examples
          : [{ id: 'example-1', input: '', output: '', explanation: '' }],
        constraints: newProblem.constraints || [],
        hints: newProblem.hints || [],
        languages: newProblem.languages || [],
        tags: newProblem.tags || [],
      }
    }
  },
  { deep: true },
)

const newConstraint = ref('')
const newHint = ref('')
const newLanguage = ref('')
const newTag = ref('')
const loading = ref(false)

// Validation errors
const errors = ref<Record<string, string>>({})

function validate(): boolean {
  errors.value = {}

  if (!formData.value.slug?.trim()) {
    errors.value.slug = 'Slug is required'
  } else if (!/^[a-z0-9-]+$/.test(formData.value.slug)) {
    errors.value.slug = 'Slug must contain only lowercase letters, numbers, and hyphens'
  }

  if (!formData.value.title?.trim()) {
    errors.value.title = 'Title is required'
  }

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

function addLanguage() {
  if (newLanguage.value.trim() && !formData.value.languages!.includes(newLanguage.value.trim())) {
    formData.value.languages!.push(newLanguage.value.trim())
    newLanguage.value = ''
  }
}

function removeLanguage(index: number) {
  formData.value.languages!.splice(index, 1)
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
  <div class="flex flex-col gap-4">
    <!-- Basic Information Card -->
    <Card class="max-w-3xl">
      <CardHeader>
        <CardTitle>Basic Information</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="space-y-2">
          <label class="text-sm font-medium">Slug</label>
          <Input v-model="formData.slug" placeholder="two-sum" />
          <p v-if="errors.slug" class="text-sm text-destructive">{{ errors.slug }}</p>
        </div>

        <div class="space-y-2">
          <label class="text-sm font-medium">Title</label>
          <Input v-model="formData.title" placeholder="Two Sum" />
          <p v-if="errors.title" class="text-sm text-destructive">{{ errors.title }}</p>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <label class="text-sm font-medium">Difficulty</label>
            <Select v-model="formData.difficulty">
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem :value="Difficulty.EASY">Easy</SelectItem>
                <SelectItem :value="Difficulty.MEDIUM">Medium</SelectItem>
                <SelectItem :value="Difficulty.HARD">Hard</SelectItem>
              </SelectContent>
            </Select>
          </div>

          <div class="space-y-2">
            <label class="text-sm font-medium">Status</label>
            <Select v-model="formData.status">
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem :value="ProblemStatus.TODO">Todo</SelectItem>
                <SelectItem :value="ProblemStatus.ATTEMPTED">Attempted</SelectItem>
                <SelectItem :value="ProblemStatus.SOLVED">Solved</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>

        <div class="flex gap-4">
          <div class="flex items-center space-x-2">
            <Checkbox v-model:checked="formData.is_premium" id="premium" />
            <label for="premium" class="text-sm">Premium</label>
          </div>

          <div class="flex items-center space-x-2">
            <Checkbox v-model:checked="formData.is_published" id="published" />
            <label for="published" class="text-sm">
              {{ isEdit ? 'Published' : 'Publish immediately' }}
            </label>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- Problem Content Card -->
    <Card class="max-w-4xl">
      <CardHeader>
        <CardTitle>Problem Content</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="space-y-2">
          <label class="text-sm font-medium">Summary</label>
          <p class="text-xs text-muted-foreground">A brief description shown in problem listings</p>
          <Textarea
            v-model="formData.summary"
            rows="3"
            placeholder="Brief summary of the problem..."
          />
        </div>

        <div class="space-y-2">
          <label class="text-sm font-medium">Full Content</label>
          <p class="text-xs text-muted-foreground">
            Detailed problem description with markdown support
          </p>
          <MarkdownEditor
            :model-value="formData.content ?? ''"
            @update:model-value="(v) => (formData.content = v)"
            placeholder="Write the full problem description in markdown..."
          />
        </div>
      </CardContent>
    </Card>

    <!-- Test Cases Card -->
    <Card class="max-w-4xl">
      <CardHeader>
        <CardTitle>Test Case Examples</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <p class="text-xs text-muted-foreground">
          Add at least one example to help users understand the problem input/output format.
        </p>
        <TestCasesEditor v-model="formData.examples as TestCaseExample[]" />
        <p v-if="errors.examples" class="text-sm text-destructive">{{ errors.examples }}</p>
      </CardContent>
    </Card>

    <!-- Constraints Card -->
    <Card class="max-w-3xl">
      <CardHeader>
        <CardTitle>Constraints</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="flex gap-2">
          <Input
            v-model="newConstraint"
            placeholder="e.g., 1 <= nums.length <= 10^4"
            @keyup.enter="addConstraint"
          />
          <Button type="button" @click="addConstraint">Add</Button>
        </div>
        <div v-if="formData.constraints!.length === 0" class="text-sm text-muted-foreground italic">
          No constraints added yet.
        </div>
        <div class="flex flex-wrap gap-2">
          <Badge v-for="(constraint, idx) in formData.constraints" :key="idx" variant="secondary">
            {{ constraint }}
            <button class="ml-2 hover:text-destructive" @click="removeConstraint(idx)">×</button>
          </Badge>
        </div>
      </CardContent>
    </Card>

    <!-- Hints Card -->
    <Card class="max-w-3xl">
      <CardHeader>
        <CardTitle>Hints</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="flex gap-2">
          <Input v-model="newHint" placeholder="Add a hint..." @keyup.enter="addHint" />
          <Button type="button" @click="addHint">Add</Button>
        </div>
        <div v-if="formData.hints!.length === 0" class="text-sm text-muted-foreground italic">
          No hints added yet.
        </div>
        <div class="space-y-2">
          <div
            v-for="(hint, idx) in formData.hints"
            :key="idx"
            class="flex items-center gap-2 p-2 rounded border bg-amber-50/10 dark:bg-amber-950/20"
          >
            <span class="text-sm flex-1">{{ hint }}</span>
            <button class="text-muted-foreground hover:text-destructive" @click="removeHint(idx)">
              ×
            </button>
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- Languages Card -->
    <Card class="max-w-3xl">
      <CardHeader>
        <CardTitle>Supported Languages</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <p class="text-xs text-muted-foreground">
          Leave empty to allow all languages. Add specific languages to restrict the problem to
          those only.
        </p>
        <div class="flex gap-2">
          <Input
            v-model="newLanguage"
            placeholder="e.g., Python, JavaScript, C++"
            @keyup.enter="addLanguage"
          />
          <Button type="button" @click="addLanguage">Add</Button>
        </div>
        <div v-if="formData.languages!.length === 0" class="text-sm text-muted-foreground italic">
          All languages supported (no restrictions)
        </div>
        <div class="flex flex-wrap gap-2">
          <Badge v-for="(lang, idx) in formData.languages" :key="idx" variant="secondary">
            {{ lang }}
            <button class="ml-2 hover:text-destructive" @click="removeLanguage(idx)">×</button>
          </Badge>
        </div>
      </CardContent>
    </Card>

    <!-- Tags Card -->
    <Card class="max-w-3xl">
      <CardHeader>
        <CardTitle>Tags</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="flex gap-2">
          <Input
            v-model="newTag"
            placeholder="e.g., Array, Hash Table, Dynamic Programming"
            @keyup.enter="addTag"
          />
          <Button type="button" @click="addTag">Add</Button>
        </div>
        <div v-if="formData.tags!.length === 0" class="text-sm text-muted-foreground italic">
          No tags added yet.
        </div>
        <div class="flex flex-wrap gap-2">
          <Badge v-for="(tag, idx) in formData.tags" :key="idx" variant="secondary">
            {{ tag }}
            <button class="ml-2 hover:text-destructive" @click="removeTag(idx)">×</button>
          </Badge>
        </div>
      </CardContent>
    </Card>

    <!-- Submit Actions -->
    <div class="flex gap-2">
      <Button :disabled="loading" @click="submit">
        {{ loading ? 'Saving...' : isEdit ? 'Update Problem' : 'Create Problem' }}
      </Button>
      <slot name="cancel" />
    </div>
  </div>
</template>
