<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import {
  IconPlus,
  IconX,
  IconFileDescription,
  IconFlask,
  IconInfoCircle,
  IconBrackets,
  IconBulb,
  IconCheck,
} from '@tabler/icons-vue'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
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
import { Difficulty, ProblemStatus } from '@/api/admin/problems'

// Define a strict local interface for the incoming problem prop
interface ProblemData {
  slug: string
  title: string
  difficulty: string
  status: string
  isPremium: boolean
  isPublished: boolean
  summary: string
  content: string
  examples: TestCaseExample[]
  constraints: string[]
  hints: string[]
  languages: string[]
  tags: string[]
}

const props = withDefaults(
  defineProps<{
    problem?: ProblemData
    isEdit?: boolean
  }>(),
  {
    isEdit: false,
  },
)

const emit = defineEmits<{
  submit: [data: ProblemFormData]
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
const formData = ref<ProblemFormData>({
  slug: '',
  title: '',
  difficulty: Difficulty.MEDIUM,
  status: ProblemStatus.TODO,
  isPremium: false,
  isPublished: false,
  summary: '',
  content: '',
  examples: ensureExamples(),
  constraints: [],
  hints: [],
  languages: [],
  tags: [],
})

// Function to reset/update form data safely
function updateForm(data?: ProblemData) {
  if (!data) return

  // Normalize difficulty to uppercase to match frontend enum values
  // Backend may return "Easy" but frontend expects "EASY"
  const validDifficulties = [Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD]
  const normalizedDifficulty = validDifficulties.includes(
    data.difficulty?.toUpperCase() as Difficulty,
  )
    ? (data.difficulty?.toUpperCase() as Difficulty)
    : Difficulty.MEDIUM

  formData.value = {
    slug: data.slug || '',
    title: data.title || '',
    difficulty: normalizedDifficulty,
    status: (data.status as ProblemStatus) || ProblemStatus.TODO,
    isPremium: data.isPremium,
    isPublished: data.isPublished,
    summary: data.summary || '',
    content: data.content || '',
    examples: ensureExamples(data.examples),
    constraints: [...(data.constraints || [])],
    hints: [...(data.hints || [])],
    languages: [...(data.languages || [])],
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
const newLanguage = ref('')
const newTag = ref('')
const loading = ref(false)
const { t } = useI18n()

// Validation errors
const errors = ref<Record<string, string>>({})

async function validate(): Promise<boolean> {
  errors.value = {}

  await nextTick()

  if (!formData.value.slug?.trim()) {
    errors.value.slug = t('problems.form.validation.slugRequired')
  } else if (!/^[a-z0-9-]+$/.test(formData.value.slug)) {
    errors.value.slug = t('problems.form.validation.slugInvalid')
  }

  if (!formData.value.title?.trim()) {
    errors.value.title = t('problems.form.validation.titleRequired')
  }

  if (formData.value.examples?.length === 0) {
    errors.value.examples = t('problems.form.validation.examplesRequired')
  }

  for (let i = 0; i < (formData.value.examples?.length || 0); i++) {
    const example = formData.value.examples![i]
    if (example && !example.input?.trim()) {
      errors.value[`example-${i}-input`] = t('problems.form.validation.inputRequired')
    }
    if (example && !example.output?.trim()) {
      errors.value[`example-${i}-output`] = t('problems.form.validation.outputRequired')
    }
  }

  return Object.keys(errors.value).length === 0
}

async function submit() {
  if (!(await validate())) return
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
  <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
    <!-- Left Column: Main Content -->
    <div class="lg:col-span-8 space-y-6">
      <!-- General Information -->
      <Card>
        <CardHeader>
          <div class="flex items-center gap-2">
            <IconFileDescription class="h-5 w-5 text-muted-foreground" />
            <CardTitle>{{ t('problems.form.details.title') }}</CardTitle>
          </div>
          <CardDescription>{{ t('problems.form.details.description') }}</CardDescription>
        </CardHeader>
        <CardContent class="space-y-6">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div class="space-y-2">
              <Label>{{ t('problems.form.title') }}</Label>
              <Input v-model="formData.title" :placeholder="t('problems.form.titlePlaceholder')" />
              <p v-if="errors.title" class="text-sm text-destructive">{{ errors.title }}</p>
            </div>

            <div class="space-y-2">
              <Label>{{ t('problems.form.slug') }}</Label>
              <Input
                v-model="formData.slug"
                :placeholder="t('problems.form.slugPlaceholder')"
                class="font-mono"
              />
              <p v-if="errors.slug" class="text-sm text-destructive">{{ errors.slug }}</p>
            </div>
          </div>

          <div class="space-y-2">
            <Label>{{ t('problems.form.summary') }}</Label>
            <Textarea
              v-model="formData.summary"
              rows="2"
              :placeholder="t('problems.form.summaryPlaceholder')"
              class="resize-none"
            />
          </div>

          <div class="space-y-2">
            <Label>{{ t('problems.form.fullContent') }}</Label>
            <MarkdownEditor
              :model-value="formData.content ?? ''"
              @update:model-value="(v) => (formData.content = v)"
              :placeholder="t('problems.form.contentPlaceholder')"
            />
          </div>
        </CardContent>
      </Card>

      <!-- Test Cases -->
      <Card>
        <CardHeader>
          <div class="flex items-center gap-2">
            <IconFlask class="h-5 w-5 text-muted-foreground" />
            <CardTitle>{{ t('problems.form.testCasesSection.title') }}</CardTitle>
          </div>
          <CardDescription>
            {{ t('problems.form.testCasesSection.description') }}
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
            <IconInfoCircle class="h-5 w-5 text-muted-foreground" />
            <CardTitle>{{ t('problems.form.additionalInfo.title') }}</CardTitle>
          </div>
        </CardHeader>
        <CardContent class="space-y-8">
          <!-- Constraints -->
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <Label class="text-base flex items-center gap-2">
                <IconBrackets class="h-4 w-4" /> {{ t('problems.form.constraintsSection.title') }}
              </Label>
            </div>

            <div class="flex gap-2">
              <Input
                v-model="newConstraint"
                :placeholder="t('problems.form.constraintsSection.placeholder')"
                @keyup.enter="addConstraint"
                class="font-mono text-sm"
              />
              <Button type="button" variant="secondary" @click="addConstraint">{{
                t('problems.form.add')
              }}</Button>
            </div>

            <ul v-if="formData.constraints!.length > 0" class="space-y-2">
              <li
                v-for="(constraint, idx) in formData.constraints"
                :key="idx"
                class="flex items-center justify-between p-2 rounded-none bg-muted/50 border group text-sm font-mono"
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
            <div class="flex items-center justify-between">
              <Label class="text-base flex items-center gap-2">
                <IconBulb class="h-4 w-4" /> {{ t('problems.form.hints') }}
              </Label>
            </div>
            <div class="flex gap-2">
              <Input
                v-model="newHint"
                :placeholder="t('problems.form.addHintPlaceholder')"
                @keyup.enter="addHint"
              />
              <Button type="button" variant="secondary" @click="addHint">{{
                t('problems.form.addHint')
              }}</Button>
            </div>

            <ul v-if="formData.hints!.length > 0" class="space-y-2">
              <li
                v-for="(hint, idx) in formData.hints"
                :key="idx"
                class="flex items-start justify-between p-2 rounded-none bg-muted/50 border group text-sm"
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
            <p v-else class="text-sm text-muted-foreground italic">
              {{ t('problems.form.noHints') }}
            </p>
          </div>
        </CardContent>
      </Card>
    </div>

    <!-- Right Column: Sidebar -->
    <div class="lg:col-span-4 space-y-6 lg:sticky lg:top-6 h-fit">
      <!-- Actions Card -->
      <Card class="border-primary/10 shadow-sm">
        <CardHeader class="pb-3 border-b bg-muted/20">
          <CardTitle class="text-base">{{ t('problems.form.publishing') }}</CardTitle>
        </CardHeader>
        <CardContent class="pt-6 space-y-6">
          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-2">
              <Label class="text-xs text-muted-foreground uppercase tracking-wider">{{
                t('problems.form.status')
              }}</Label>
              <Select v-model="formData.status">
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem :value="ProblemStatus.TODO">{{
                    t('problems.status.TODO')
                  }}</SelectItem>
                  <SelectItem :value="ProblemStatus.ATTEMPTED">{{
                    t('problems.status.ATTEMPTED')
                  }}</SelectItem>
                  <SelectItem :value="ProblemStatus.SOLVED">{{
                    t('problems.status.SOLVED')
                  }}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div class="space-y-2">
              <Label class="text-xs text-muted-foreground uppercase tracking-wider">{{
                t('problems.form.difficulty')
              }}</Label>
              <Select v-model="formData.difficulty">
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem :value="Difficulty.EASY">{{
                    t('problems.difficulty.EASY')
                  }}</SelectItem>
                  <SelectItem :value="Difficulty.MEDIUM">{{
                    t('problems.difficulty.MEDIUM')
                  }}</SelectItem>
                  <SelectItem :value="Difficulty.HARD">{{
                    t('problems.difficulty.HARD')
                  }}</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <div class="space-y-4 pt-2">
            <div
              class="flex items-center justify-between p-3 rounded-none border bg-card hover:bg-muted/50 transition-colors cursor-pointer"
              @click="formData.isPremium = !formData.isPremium"
            >
              <div class="space-y-0.5">
                <Label class="text-base cursor-pointer">{{ t('problems.form.premium') }}</Label>
                <p class="text-xs text-muted-foreground">
                  {{ t('problems.form.premiumDescription') }}
                </p>
              </div>
              <Checkbox v-model="formData.isPremium" />
            </div>

            <div
              class="flex items-center justify-between p-3 rounded-none border bg-card hover:bg-muted/50 transition-colors cursor-pointer"
              @click="formData.isPublished = !formData.isPublished"
            >
              <div class="space-y-0.5">
                <Label class="text-base cursor-pointer">{{ t('problems.form.published') }}</Label>
                <p class="text-xs text-muted-foreground">
                  {{ t('problems.form.publishedDescription') }}
                </p>
              </div>
              <Checkbox v-model="formData.isPublished" />
            </div>
          </div>

          <div class="flex flex-col gap-3 pt-2">
            <Button class="w-full" :disabled="loading" @click="submit">
              <IconCheck v-if="!loading" class="h-4 w-4 mr-2" />
              {{
                loading
                  ? t('problems.form.saving')
                  : isEdit
                    ? t('problems.form.updateProblem')
                    : t('problems.form.createProblem')
              }}
            </Button>
            <slot name="cancel" />
          </div>
        </CardContent>
      </Card>

      <!-- Taxonomy Card -->
      <Card>
        <CardHeader class="pb-3 border-b bg-muted/20">
          <CardTitle class="text-base">{{ t('problems.form.taxonomy') }}</CardTitle>
        </CardHeader>
        <CardContent class="pt-6 space-y-6">
          <!-- Languages -->
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <Label class="text-sm">{{ t('problems.form.languages') }}</Label>
              <span v-if="!formData.languages?.length" class="text-xs text-muted-foreground">{{
                t('problems.form.all')
              }}</span>
            </div>

            <div class="relative">
              <Input
                v-model="newLanguage"
                :placeholder="t('problems.form.addLanguagePlaceholder')"
                @keyup.enter="addLanguage"
                class="pr-8"
              />
              <button
                v-if="newLanguage"
                class="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-primary"
                @click="addLanguage"
              >
                <IconPlus class="h-4 w-4" />
              </button>
            </div>

            <div v-if="formData.languages?.length" class="flex flex-wrap gap-1.5">
              <Badge
                v-for="(lang, idx) in formData.languages"
                :key="idx"
                variant="outline"
                class="gap-1 pr-1.5"
              >
                {{ lang }}
                <button
                  class="hover:text-destructive text-muted-foreground hover:bg-destructive/10 rounded-full p-0.5 transition-colors"
                  @click="removeLanguage(idx)"
                >
                  <IconX class="h-3 w-3" />
                </button>
              </Badge>
            </div>
          </div>

          <Separator />

          <!-- Tags -->
          <div class="space-y-3">
            <div class="flex items-center justify-between">
              <Label class="text-sm">{{ t('problems.form.tags') }}</Label>
            </div>

            <div class="relative">
              <Input
                v-model="newTag"
                :placeholder="t('problems.form.addTagPlaceholder')"
                @keyup.enter="addTag"
                class="pr-8"
              />
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
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
