<script setup lang="ts">
import { ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { IconBrackets, IconPlus, IconTrash, IconPlus as IconAdd } from '@tabler/icons-vue'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { Textarea } from '@/components/ui/textarea'

export interface LanguageWithCode {
  language: string
  starter_code: string
  value?: string
  style?: string
}

export interface CodeFormData {
  languages: LanguageWithCode[]
}

interface ProblemData {
  languages?: Array<{
    id: string
    language: string
    value?: string
    style?: string
    starter_code: string
  }>
}

const props = withDefaults(
  defineProps<{
    problem?: ProblemData
  }>(),
  {},
)

const emit = defineEmits<{
  submit: [data: CodeFormData]
  cancel: []
}>()

// Common programming languages
const commonLanguages = [
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
  'scala',
]

// Initialize form data with safe defaults
const formData = ref<CodeFormData>({
  languages: [],
})

const selectedLanguage = ref('')
const newLanguageCode = ref('')

// Function to reset/update form data safely
function updateForm(data?: ProblemData) {
  if (!data) {
    formData.value.languages = []
    return
  }

  formData.value.languages = (data.languages || []).map((lang) => ({
    language: lang.language,
    starter_code: lang.starter_code || '',
    value: lang.value,
    style: lang.style,
  }))
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

const loading = ref(false)

// Validation errors
const errors = ref<Record<string, string>>({})

function validate(): boolean {
  errors.value = {}
  return Object.keys(errors.value).length === 0
}

function submit() {
  if (!validate()) return
  emit('submit', formData.value)
}

function cancel() {
  emit('cancel')
}

// Add a new language with starter code
function addLanguage() {
  if (!selectedLanguage.value.trim()) return

  // Check if language already exists
  const exists = formData.value.languages.some(
    (lang) => lang.language.toLowerCase() === selectedLanguage.value.toLowerCase(),
  )

  if (exists) {
    errors.value.language = 'Language already added'
    return
  }

  formData.value.languages.push({
    language: selectedLanguage.value,
    starter_code: newLanguageCode.value,
  })

  selectedLanguage.value = ''
  newLanguageCode.value = ''
  delete errors.value.language
}

function removeLanguage(index: number) {
  formData.value.languages.splice(index, 1)
}

function updateStarterCode(index: number, code: string) {
  const lang = formData.value.languages[index]
  if (lang) {
    lang.starter_code = code
  }
}

// Check if a language is already added
const isLanguageAdded = (lang: string) => {
  return formData.value.languages.some((l) => l.language.toLowerCase() === lang.toLowerCase())
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
      <!-- Add Language -->
      <Card>
        <CardHeader>
          <div class="flex items-center gap-2">
            <IconBrackets class="h-5 w-5 text-muted-foreground" />
            <CardTitle>Language Starter Code</CardTitle>
          </div>
          <CardDescription>
            Add programming languages with starter code templates for this problem.
          </CardDescription>
        </CardHeader>
        <CardContent class="space-y-6">
          <!-- Quick Add Common Languages -->
          <div class="space-y-3">
            <Label>Quick Add (Common Languages)</Label>
            <div class="flex flex-wrap gap-2">
              <Button
                v-for="lang in commonLanguages"
                :key="lang"
                :variant="isLanguageAdded(lang) ? 'outline' : 'secondary'"
                :disabled="isLanguageAdded(lang)"
                size="sm"
                @click="
                  () => {
                    selectedLanguage = lang
                    newLanguageCode = ''
                    addLanguage()
                  }
                "
              >
                <IconPlus v-if="!isLanguageAdded(lang)" class="h-3 w-3 mr-1" />
                {{ lang }}
              </Button>
            </div>
          </div>

          <Separator />

          <!-- Add Custom Language -->
          <div class="space-y-3">
            <Label>Add Custom Language</Label>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
              <div class="space-y-2">
                <Input
                  v-model="selectedLanguage"
                  placeholder="Language name (e.g., python)"
                  @keyup.enter="addLanguage"
                />
                <p v-if="errors.language" class="text-sm text-destructive">{{ errors.language }}</p>
              </div>
              <Button class="self-end" :disabled="!selectedLanguage.trim()" @click="addLanguage">
                <IconAdd class="h-4 w-4 mr-1" />
                Add Language
              </Button>
            </div>
          </div>

          <!-- Starter Code Editor for New Language -->
          <div v-if="selectedLanguage && !isLanguageAdded(selectedLanguage)" class="space-y-2">
            <Label>Starter Code (Optional)</Label>
            <Textarea
              v-model="newLanguageCode"
              rows="8"
              placeholder="// Enter starter code template here..."
              class="font-mono text-sm"
            />
            <p class="text-xs text-muted-foreground">
              You can also add the language first and edit the starter code below.
            </p>
          </div>
        </CardContent>
      </Card>

      <!-- Existing Languages with Starter Code -->
      <Card v-if="formData.languages.length > 0">
        <CardHeader>
          <CardTitle class="text-base">Configure Starter Code</CardTitle>
          <CardDescription>Edit starter code for each language.</CardDescription>
        </CardHeader>
        <CardContent class="space-y-6">
          <div
            v-for="(lang, index) in formData.languages"
            :key="index"
            class="space-y-3 p-4 rounded-lg border bg-muted/20"
          >
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2">
                <Badge variant="outline" class="font-mono">{{ lang.language }}</Badge>
                <span class="text-sm text-muted-foreground">Starter Code</span>
              </div>
              <Button
                variant="ghost"
                size="sm"
                class="h-8 text-destructive hover:text-destructive"
                @click="removeLanguage(index)"
              >
                <IconTrash class="h-4 w-4" />
              </Button>
            </div>
            <Textarea
              :model-value="lang.starter_code"
              @update:model-value="(v) => updateStarterCode(index, String(v))"
              rows="10"
              :placeholder="`// Starter code for ${lang.language}...`"
              class="font-mono text-sm"
            />
          </div>
        </CardContent>
      </Card>

      <Card v-else class="border-dashed">
        <CardContent class="flex flex-col items-center justify-center py-12 text-center">
          <IconBrackets class="h-12 w-12 text-muted-foreground mb-3" />
          <p class="text-sm font-medium mb-1">No languages added yet</p>
          <p class="text-xs text-muted-foreground">
            Add languages above to configure starter code templates.
          </p>
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
        <CardContent class="space-y-4">
          <div class="text-sm text-muted-foreground">
            <p class="mb-2">{{ formData.languages.length }} language(s) configured</p>
            <p v-if="formData.languages.length === 0" class="text-xs italic">
              Problem will be available in all languages if none are specified.
            </p>
          </div>

          <div class="flex flex-col gap-3">
            <Button class="w-full" :disabled="loading" @click="submit">
              {{ loading ? 'Saving...' : 'Save Languages' }}
            </Button>
            <Button variant="outline" class="w-full" @click="cancel"> Cancel </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
