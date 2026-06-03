<script setup lang="ts">
import { watch, ref } from 'vue'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { Card, CardContent, CardTitle } from '@/components/ui/card'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { IconFileDescription, IconCheck } from '@tabler/icons-vue'
import { Badge } from '@/components/ui/badge'
import MarkdownEditor from '@/components/problem/MarkdownEditor.vue'
import ExamplesEditor from './ExamplesEditor.vue'
import ConstraintsEditor from './ConstraintsEditor.vue'
import HintsEditor from './HintsEditor.vue'
import TagsSelector from './TagsSelector.vue'
import LivePreviewPanel from './LivePreviewPanel.vue'
import { Difficulty, ProblemStatus } from '@/api/admin/problems'
import {
  problemDescriptionSchema,
  type ProblemDescriptionFormData,
} from '@/lib/schemas/problemDescription'

export type DescriptionFormData = ProblemDescriptionFormData

interface ProblemData {
  slug: string
  title: string
  difficulty: string
  status: string
  isPremium: boolean
  isPublished: boolean
  summary?: string
  content: string
  examples?: Array<{
    input: string
    output: string
    explanation?: string
    inputs?: Array<{
      name: string
      value?: unknown
      label?: string
      fieldName?: string
    }>
  }>
  constraints?: string[]
  hints?: string[]
  tags?: string[]
  languages?: string[]
}

const props = defineProps<{
  problem?: ProblemData
  isEdit?: boolean
}>()

const emit = defineEmits<{
  submit: [data: DescriptionFormData]
  cancel: []
}>()

const { t } = useI18n()

const formSchema = toTypedSchema(problemDescriptionSchema)

const form = useForm({
  validationSchema: formSchema,
})

const { values: formValues, setValues, resetForm, handleSubmit, setFieldValue } = form

function updateForm(data?: ProblemData) {
  if (!data) {
    resetForm()
    return
  }

  // Normalize difficulty to uppercase to match frontend enum values
  // Backend may return "Easy" but frontend expects "EASY"
  const validDifficulties = [Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD]
  const normalizedDifficulty = validDifficulties.includes(
    data.difficulty?.toUpperCase() as Difficulty,
  )
    ? (data.difficulty?.toUpperCase() as Difficulty)
    : Difficulty.MEDIUM

  setValues({
    title: data.title || '',
    slug: data.slug || '',
    difficulty: normalizedDifficulty,
    status: (data.status as ProblemStatus) || ProblemStatus.TODO,
    isPremium: !!data.isPremium,
    isPublished: !!data.isPublished,
    summary: data.summary || '',
    content: data.content || '',
    examples: data.examples?.length
      ? data.examples.map((ex) => ({
        input: ex.input || '',
        output: ex.output || '',
        explanation: ex.explanation || '',
        inputs: ex.inputs,
      }))
      : [],
    constraints: data.constraints?.length ? data.constraints : [],
    hints: data.hints?.length ? data.hints : [],
    tags: data.tags?.length ? data.tags : [],
    languages: data.languages?.length ? data.languages : [],
  })
}

watch(
  () => props.problem,
  (newVal) => {
    updateForm(newVal)
  },
  { immediate: true },
)

const loading = ref(false)

function setLoading(value: boolean) {
  loading.value = value
}

const onSubmit = handleSubmit((values) => {
  emit('submit', values as DescriptionFormData)
})

function cancel() {
  emit('cancel')
}

defineExpose({
  setLoading,
  cancel,
  form,
})

const defaultOpenSections = ['basic', 'description', 'examples']

const availableLanguages = [
  'python',
  'javascript',
  'typescript',
  'java',
  'cpp',
  'c',
  'csharp',
  'go',
  'rust',
  'ruby',
  'php',
  'swift',
  'kotlin',
]

function toggleLanguage(lang: string) {
  const current = (formValues.languages as string[]) || []
  const index = current.indexOf(lang)
  const next = index > -1 ? current.filter((l) => l !== lang) : [...current, lang]
  setFieldValue('languages', next)
}

function isLanguageSelected(lang: string): boolean {
  return (formValues.languages || []).includes(lang)
}
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
    <!-- Left: Form sections -->
    <div class="lg:col-span-8 space-y-4">
      <form @submit="onSubmit" class="space-y-4">
        <Accordion type="multiple" :default-value="defaultOpenSections" class="space-y-4">
          <!-- Basic Info -->
          <AccordionItem value="basic" class="border-0">
            <Card>
              <AccordionTrigger class="px-6 py-4 hover:no-underline">
                <div class="flex items-center gap-2">
                  <IconFileDescription class="h-5 w-5 text-muted-foreground" />
                  <CardTitle class="text-base">{{
                    t('problems.descriptionForm.basicInfo')
                  }}</CardTitle>
                </div>
              </AccordionTrigger>
              <AccordionContent>
                <CardContent class="space-y-6">
                  <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <FormField v-slot="{ componentField }" name="title">
                      <FormItem>
                        <FormLabel>{{ t('problems.form.title') }}</FormLabel>
                        <FormControl>
                          <Input
                            v-bind="componentField"
                            :placeholder="t('problems.descriptionForm.titlePlaceholder')"
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    </FormField>

                    <FormField v-slot="{ componentField }" name="slug">
                      <FormItem>
                        <FormLabel>{{ t('problems.form.slug') }}</FormLabel>
                        <FormControl>
                          <Input
                            v-bind="componentField"
                            :placeholder="t('problems.descriptionForm.slugPlaceholder')"
                            class="font-mono"
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    </FormField>
                  </div>

                  <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <FormField v-slot="{ componentField }" name="difficulty">
                      <FormItem>
                        <FormLabel>{{ t('problems.form.difficulty') }}</FormLabel>
                        <Select v-bind="componentField">
                          <FormControl>
                            <SelectTrigger>
                              <SelectValue />
                            </SelectTrigger>
                          </FormControl>
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
                        <FormMessage />
                      </FormItem>
                    </FormField>

                    <FormField v-slot="{ componentField }" name="status">
                      <FormItem>
                        <FormLabel>{{ t('problems.form.status') }}</FormLabel>
                        <Select v-bind="componentField">
                          <FormControl>
                            <SelectTrigger>
                              <SelectValue />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            <SelectItem :value="ProblemStatus.TODO">{{
                              t('problems.status.todo')
                            }}</SelectItem>
                            <SelectItem :value="ProblemStatus.ATTEMPTED">{{
                              t('problems.status.attempted')
                            }}</SelectItem>
                            <SelectItem :value="ProblemStatus.SOLVED">{{
                              t('problems.status.solved')
                            }}</SelectItem>
                          </SelectContent>
                        </Select>
                        <FormMessage />
                      </FormItem>
                    </FormField>
                  </div>

                  <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <FormField v-slot="{ value, handleChange }" name="isPremium">
                      <FormItem>
                        <div
                          class="flex items-center justify-between p-3 border bg-card hover:bg-muted/50 transition-colors cursor-pointer"
                          @click="handleChange(!value)"
                        >
                          <div class="space-y-0.5">
                            <Label class="text-base cursor-pointer">{{
                              t('problems.descriptionForm.premium')
                            }}</Label>
                            <p class="text-xs text-muted-foreground">
                              {{ t('problems.descriptionForm.premiumDescription') }}
                            </p>
                          </div>
                          <Checkbox :checked="value" @update:checked="handleChange" />
                        </div>
                        <FormMessage />
                      </FormItem>
                    </FormField>

                    <FormField v-slot="{ value, handleChange }" name="isPublished">
                      <FormItem>
                        <div
                          class="flex items-center justify-between p-3 border bg-card hover:bg-muted/50 transition-colors cursor-pointer"
                          @click="handleChange(!value)"
                        >
                          <div class="space-y-0.5">
                            <Label class="text-base cursor-pointer">{{
                              t('problems.descriptionForm.published')
                            }}</Label>
                            <p class="text-xs text-muted-foreground">
                              {{ t('problems.descriptionForm.publishedDescription') }}
                            </p>
                          </div>
                          <Checkbox :checked="value" @update:checked="handleChange" />
                        </div>
                        <FormMessage />
                      </FormItem>
                    </FormField>
                  </div>
                </CardContent>
              </AccordionContent>
            </Card>
          </AccordionItem>

          <!-- Description -->
          <AccordionItem value="description" class="border-0">
            <Card>
              <AccordionTrigger class="px-6 py-4 hover:no-underline">
                <div class="flex items-center gap-2">
                  <IconFileDescription class="h-5 w-5 text-muted-foreground" />
                  <CardTitle class="text-base">{{
                    t('problems.descriptionForm.problemDescription')
                  }}</CardTitle>
                </div>
              </AccordionTrigger>
              <AccordionContent>
                <CardContent class="space-y-6">
                  <FormField v-slot="{ componentField }" name="summary">
                    <FormItem>
                      <FormLabel>{{ t('problems.form.summary') }}</FormLabel>
                      <FormControl>
                        <Textarea
                          v-bind="componentField"
                          rows="2"
                          :placeholder="t('problems.descriptionForm.summaryPlaceholder')"
                          class="resize-none"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  </FormField>

                  <FormField v-slot="{ componentField }" name="content">
                    <FormItem>
                      <FormLabel>{{ t('problems.form.fullContent') }}</FormLabel>
                      <FormControl>
                        <MarkdownEditor
                          :model-value="componentField.modelValue ?? ''"
                          @update:model-value="componentField['onUpdate:modelValue']"
                          :placeholder="t('problems.descriptionForm.contentPlaceholder')"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  </FormField>
                </CardContent>
              </AccordionContent>
            </Card>
          </AccordionItem>

          <!-- Examples -->
          <AccordionItem value="examples" class="border-0">
            <Card>
              <AccordionTrigger class="px-6 py-4 hover:no-underline">
                <div class="flex items-center gap-2">
                  <IconFileDescription class="h-5 w-5 text-muted-foreground" />
                  <CardTitle class="text-base">{{
                    t('problems.descriptionForm.examples')
                  }}</CardTitle>
                </div>
              </AccordionTrigger>
              <AccordionContent>
                <CardContent>
                  <FormField name="examples">
                    <FormItem>
                      <FormControl>
                        <ExamplesEditor name="examples" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  </FormField>
                </CardContent>
              </AccordionContent>
            </Card>
          </AccordionItem>

          <!-- Constraints -->
          <AccordionItem value="constraints" class="border-0">
            <Card>
              <AccordionTrigger class="px-6 py-4 hover:no-underline">
                <div class="flex items-center gap-2">
                  <IconFileDescription class="h-5 w-5 text-muted-foreground" />
                  <CardTitle class="text-base">{{
                    t('problems.descriptionForm.constraints')
                  }}</CardTitle>
                </div>
              </AccordionTrigger>
              <AccordionContent>
                <CardContent>
                  <FormField name="constraints">
                    <FormItem>
                      <FormControl>
                        <ConstraintsEditor name="constraints" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  </FormField>
                </CardContent>
              </AccordionContent>
            </Card>
          </AccordionItem>

          <!-- Hints -->
          <AccordionItem value="hints" class="border-0">
            <Card>
              <AccordionTrigger class="px-6 py-4 hover:no-underline">
                <div class="flex items-center gap-2">
                  <IconFileDescription class="h-5 w-5 text-muted-foreground" />
                  <CardTitle class="text-base">{{ t('problems.descriptionForm.hints') }}</CardTitle>
                </div>
              </AccordionTrigger>
              <AccordionContent>
                <CardContent>
                  <FormField name="hints">
                    <FormItem>
                      <FormControl>
                        <HintsEditor name="hints" />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  </FormField>
                </CardContent>
              </AccordionContent>
            </Card>
          </AccordionItem>

          <!-- Tags -->
          <AccordionItem value="tags" class="border-0">
            <Card>
              <AccordionTrigger class="px-6 py-4 hover:no-underline">
                <div class="flex items-center gap-2">
                  <IconFileDescription class="h-5 w-5 text-muted-foreground" />
                  <CardTitle class="text-base">{{ t('problems.descriptionForm.tags') }}</CardTitle>
                </div>
              </AccordionTrigger>
              <AccordionContent>
                <CardContent>
                  <FormField name="tags">
                    <FormItem>
                      <FormControl>
                        <TagsSelector
                          :model-value="formValues.tags ?? []"
                          @update:model-value="(v: string[]) => setFieldValue('tags', v)"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  </FormField>
                </CardContent>
              </AccordionContent>
            </Card>
          </AccordionItem>

          <!-- Languages -->
          <AccordionItem value="languages" class="border-0">
            <Card>
              <AccordionTrigger class="px-6 py-4 hover:no-underline">
                <div class="flex items-center gap-2">
                  <IconFileDescription class="h-5 w-5 text-muted-foreground" />
                  <CardTitle class="text-base">{{
                    t('problems.descriptionForm.languages')
                  }}</CardTitle>
                </div>
              </AccordionTrigger>
              <AccordionContent>
                <CardContent class="space-y-4">
                  <p class="text-sm text-muted-foreground">
                    {{ t('problems.descriptionForm.languagesDescription') }}
                  </p>
                  <div class="flex flex-wrap gap-2">
                    <button
                      v-for="lang in availableLanguages"
                      :key="lang"
                      type="button"
                      class="cursor-pointer transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                      @click="toggleLanguage(lang)"
                    >
                      <Badge
                        :variant="isLanguageSelected(lang) ? 'default' : 'outline'"
                        class="select-none font-mono text-sm"
                        :class="
                          isLanguageSelected(lang)
                            ? ''
                            : 'hover:bg-accent hover:text-accent-foreground'
                        "
                      >
                        {{ lang }}
                      </Badge>
                    </button>
                  </div>
                  <p
                    v-if="(formValues.languages || []).length === 0"
                    class="text-sm text-muted-foreground italic"
                  >
                    {{ t('problems.descriptionForm.noLanguagesSelected') }}
                  </p>
                </CardContent>
              </AccordionContent>
            </Card>
          </AccordionItem>
        </Accordion>

        <!-- Action buttons -->
        <div class="flex flex-col sm:flex-row gap-3 pt-2">
          <Button type="submit" class="w-full sm:w-auto" :disabled="loading">
            <IconCheck class="h-4 w-4 mr-2" />
            {{
              loading
                ? t('problems.descriptionForm.saving')
                : isEdit
                  ? t('problems.descriptionForm.updateDescription')
                  : t('problems.descriptionForm.saveDescription')
            }}
          </Button>
          <Button type="button" variant="outline" class="w-full sm:w-auto" @click="cancel">
            {{ t('common.cancel') }}
          </Button>
        </div>
      </form>
    </div>

    <!-- Right: Live Preview -->
    <div class="lg:col-span-4 lg:sticky lg:top-6 h-fit">
      <Card class="h-[calc(100vh-8rem)]">
        <LivePreviewPanel :data="formValues as ProblemDescriptionFormData" />
      </Card>
    </div>
  </div>
</template>
