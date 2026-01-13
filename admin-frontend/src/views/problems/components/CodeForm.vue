<script setup lang="ts">
import { ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { IconPlus, IconTrash, IconBrackets, IconCheck } from '@tabler/icons-vue'
import { Badge } from '@/components/ui/badge'
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

const commonLanguages = [
  { name: 'python', color: 'bg-blue-500/10 text-blue-600 border-blue-500/20 hover:bg-blue-500/20' },
  {
    name: 'javascript',
    color: 'bg-yellow-500/10 text-yellow-600 border-yellow-500/20 hover:bg-yellow-500/20',
  },
  {
    name: 'typescript',
    color: 'bg-blue-600/10 text-blue-700 border-blue-600/20 hover:bg-blue-600/20',
  },
  {
    name: 'java',
    color: 'bg-orange-500/10 text-orange-600 border-orange-500/20 hover:bg-orange-500/20',
  },
  { name: 'cpp', color: 'bg-blue-400/10 text-blue-500 border-blue-400/20 hover:bg-blue-400/20' },
  { name: 'c', color: 'bg-gray-500/10 text-gray-600 border-gray-500/20 hover:bg-gray-500/20' },
  {
    name: 'csharp',
    color: 'bg-purple-500/10 text-purple-600 border-purple-500/20 hover:bg-purple-500/20',
  },
  { name: 'go', color: 'bg-cyan-500/10 text-cyan-600 border-cyan-500/20 hover:bg-cyan-500/20' },
  {
    name: 'rust',
    color: 'bg-orange-600/10 text-orange-700 border-orange-600/20 hover:bg-orange-600/20',
  },
  { name: 'ruby', color: 'bg-red-500/10 text-red-600 border-red-500/20 hover:bg-red-500/20' },
  {
    name: 'php',
    color: 'bg-indigo-500/10 text-indigo-600 border-indigo-500/20 hover:bg-indigo-500/20',
  },
  {
    name: 'swift',
    color: 'bg-orange-500/10 text-orange-600 border-orange-500/20 hover:bg-orange-500/20',
  },
  {
    name: 'kotlin',
    color: 'bg-purple-600/10 text-purple-700 border-purple-600/20 hover:bg-purple-600/20',
  },
]

const formData = ref<CodeFormData>({ languages: [] })
const customLanguage = ref('')
const loading = ref(false)
const errors = ref<Record<string, string>>({})
const expandedLang = ref<string | null>(null)

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

watch(
  () => props.problem,
  (newVal) => {
    if (newVal) updateForm(newVal)
  },
  { deep: true, immediate: true },
)

function validate(): boolean {
  errors.value = {}
  return true
}

function submit() {
  if (!validate()) return
  emit('submit', formData.value)
}

function cancel() {
  emit('cancel')
}

function addLanguage(name: string) {
  if (!name.trim()) return

  const exists = formData.value.languages.some(
    (lang) => lang.language.toLowerCase() === name.toLowerCase(),
  )

  if (exists) {
    errors.value.language = 'Language already added'
    return
  }

  formData.value.languages.push({ language: name, starter_code: '' })
  expandedLang.value = name
  delete errors.value.language
}

function removeLanguage(index: number) {
  formData.value.languages.splice(index, 1)
  if (expandedLang.value === formData.value.languages[index]?.language) {
    expandedLang.value = null
  }
}

function updateStarterCode(index: number, code: string) {
  const lang = formData.value.languages[index]
  if (lang) lang.starter_code = code
}

function isLanguageAdded(name: string): boolean {
  return formData.value.languages.some((l) => l.language.toLowerCase() === name.toLowerCase())
}

function getLanguageColor(name: string): string {
  const lang = commonLanguages.find((l) => l.name === name.toLowerCase())
  return lang?.color || 'bg-muted text-muted-foreground'
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
      <!-- Add Languages Section -->
      <section class="p-5 rounded-xl border bg-card">
        <div class="flex items-center gap-2 mb-4">
          <IconBrackets class="h-5 w-5 text-muted-foreground" />
          <h2 class="font-semibold">Add Languages</h2>
        </div>

        <!-- Quick Add -->
        <div class="space-y-3">
          <Label class="text-sm text-muted-foreground">Quick Add (Common Languages)</Label>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="lang in commonLanguages"
              :key="lang.name"
              :class="[
                'px-2.5 py-1.5 rounded-lg text-xs font-mono font-medium transition-all border shrink-0',
                isLanguageAdded(lang.name)
                  ? 'bg-muted/50 text-muted-foreground border-transparent opacity-60 cursor-not-allowed'
                  : lang.color + ' hover:shadow-sm',
              ]"
              :disabled="isLanguageAdded(lang.name)"
              @click="addLanguage(lang.name)"
            >
              <IconPlus v-if="!isLanguageAdded(lang.name)" class="h-3 w-3 mr-1 inline-block" />
              <IconCheck v-else class="h-3 w-3 mr-1 inline-block" />
              {{ lang.name }}
            </button>
          </div>
        </div>

        <!-- Custom Language -->
        <div class="mt-4 flex gap-2">
          <Input
            v-model="customLanguage"
            placeholder="Custom language name..."
            class="font-mono text-sm flex-1 max-w-xs"
            @keyup.enter="addLanguage(customLanguage); customLanguage = ''"
          />
          <Button
            size="sm"
            :disabled="!customLanguage.trim()"
            @click="addLanguage(customLanguage); customLanguage = ''"
          >
            <IconPlus class="h-4 w-4 mr-1" />
            Add
          </Button>
        </div>
        <p v-if="errors.language" class="text-sm text-destructive mt-2">{{ errors.language }}</p>
      </section>

      <!-- Configured Languages -->
      <section v-if="formData.languages.length > 0" class="space-y-3">
        <div
          v-for="(lang, index) in formData.languages"
          :key="index"
          class="rounded-xl border bg-card overflow-hidden"
        >
          <!-- Language Header -->
          <button
            :class="[
              'w-full flex items-center justify-between p-4 transition-colors hover:bg-muted/30',
              expandedLang === lang.language ? 'bg-muted/20' : '',
            ]"
            @click="expandedLang = expandedLang === lang.language ? null : lang.language"
          >
            <div class="flex items-center gap-3">
              <Badge
                variant="outline"
                class="font-mono text-sm"
                :class="getLanguageColor(lang.language)"
              >
                <IconBrackets class="h-3.5 w-3.5 mr-1.5" />
                {{ lang.language }}
              </Badge>
              <span class="text-xs text-muted-foreground">
                {{ lang.starter_code.split('\n').filter(Boolean).length || 0 }} lines
              </span>
            </div>
            <div class="flex items-center gap-2">
              <Button
                variant="ghost"
                size="icon"
                class="h-8 w-8 text-destructive hover:text-destructive hover:bg-destructive/10"
                @click.stop="removeLanguage(index)"
              >
                <IconTrash class="h-4 w-4" />
              </Button>
              <IconPlus
                :class="[
                  'h-4 w-4 transition-transform text-muted-foreground',
                  expandedLang === lang.language ? 'rotate-45' : '',
                ]"
              />
            </div>
          </button>

          <!-- Starter Code Editor -->
          <div v-if="expandedLang === lang.language" class="p-4 border-t bg-muted/10">
            <Label class="text-xs text-muted-foreground mb-2 block">Starter Code Template</Label>
            <Textarea
              :model-value="lang.starter_code"
              @update:model-value="(v) => updateStarterCode(index, String(v))"
              rows="12"
              :placeholder="`// Starter code for ${lang.language}...`"
              class="font-mono text-sm bg-background"
            />
          </div>
        </div>
      </section>

      <!-- Empty State -->
      <div
        v-else
        class="flex flex-col items-center justify-center py-12 px-4 text-center rounded-xl border border-dashed bg-muted/20"
      >
        <div class="w-12 h-12 rounded-xl bg-muted flex items-center justify-center mb-3">
          <IconBrackets class="h-6 w-6 text-muted-foreground" />
        </div>
        <h3 class="text-sm font-medium mb-1">No Languages Added</h3>
        <p class="text-xs text-muted-foreground max-w-xs">
          Add languages above to configure starter code templates.
        </p>
      </div>
    </div>

    <!-- Sidebar -->
    <aside class="lg:col-span-4 space-y-4 lg:sticky lg:top-6 h-fit self-start">
      <!-- Status Card -->
      <div class="p-4 rounded-xl border bg-card">
        <h3 class="text-sm font-medium mb-3">Configuration</h3>
        <div class="space-y-2 text-sm">
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">Languages</span>
            <span class="font-medium tabular-nums">{{ formData.languages.length }}</span>
          </div>
          <p class="text-xs text-muted-foreground pt-2 border-t">
            {{
              formData.languages.length === 0
                ? 'Problem will be available in all languages.'
                : 'Starter code configured for selected languages.'
            }}
          </p>
        </div>
      </div>

      <!-- Actions -->
      <div class="p-4 rounded-xl border border-primary/20 bg-primary/5">
        <div class="flex flex-col gap-2">
          <Button
            class="w-full"
            :disabled="loading || formData.languages.length === 0"
            @click="submit"
          >
            <IconCheck v-if="!loading" class="h-4 w-4 mr-1" />
            {{ loading ? 'Saving...' : 'Save Changes' }}
          </Button>
          <Button variant="outline" class="w-full" @click="cancel"> Cancel </Button>
        </div>
      </div>
    </aside>
  </div>
</template>
