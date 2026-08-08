<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { IconPlus, IconTrash, IconBrackets, IconCheck } from '@tabler/icons-vue'
import { Badge } from '@/components/ui/badge'
import { Textarea } from '@/components/ui/textarea'
import { sanitizeTextInput } from '@/utils/sanitize-input'
import { getLanguageColor } from '@/lib/entities/language'

export interface LanguageWithCode {
  language: string
  starterCode: string
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
    starterCode: string
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

const formData = ref<CodeFormData>({ languages: [] })
const customLanguage = ref('')
const loading = ref(false)
const errors = ref<Record<string, string>>({})
const expandedLang = ref<string | null>(null)
const { t } = useI18n()

function updateForm(data?: ProblemData) {
  if (!data) {
    formData.value.languages = []
    return
  }
  formData.value.languages = (data.languages || []).map((lang) => ({
    language: lang.language,
    starterCode: lang.starterCode || '',
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
  // Sanitize input before processing
  const sanitizedName = sanitizeTextInput(name, 50)

  if (!sanitizedName) return

  const exists = formData.value.languages.some(
    (lang) => lang.language.toLowerCase() === sanitizedName.toLowerCase(),
  )

  if (exists) {
    errors.value.language = 'Language already added'
    return
  }

  formData.value.languages.push({ language: sanitizedName, starterCode: '' })
  expandedLang.value = sanitizedName
  delete errors.value.language
}

function handleAddLanguage() {
  addLanguage(customLanguage.value)
  customLanguage.value = ''
}

function removeLanguage(index: number) {
  formData.value.languages.splice(index, 1)
  if (expandedLang.value === formData.value.languages[index]?.language) {
    expandedLang.value = null
  }
}

function updateStarterCode(index: number, code: string) {
  const lang = formData.value.languages[index]
  if (lang) lang.starterCode = code
}

function isLanguageAdded(name: string): boolean {
  return formData.value.languages.some((l) => l.language.toLowerCase() === name.toLowerCase())
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
      <section class="p-5 rounded-none border bg-card">
        <div class="flex items-center gap-2 mb-4">
          <IconBrackets class="h-5 w-5 text-muted-foreground" />
          <h2 class="font-semibold">{{ t('problems.codeForm.addLanguages') }}</h2>
        </div>

        <!-- Quick Add -->
        <div class="space-y-3">
          <Label class="text-sm text-muted-foreground">{{ t('problems.codeForm.quickAdd') }}</Label>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="lang in commonLanguages"
              :key="lang"
              :class="[
                'px-2.5 py-1.5 rounded-none text-xs font-mono font-medium transition-all border shrink-0',
                isLanguageAdded(lang)
                  ? 'bg-muted/50 text-muted-foreground border-transparent opacity-60 cursor-not-allowed'
                  : getLanguageColor(lang) + ' hover:shadow-sm',
              ]"
              :disabled="isLanguageAdded(lang)"
              @click="addLanguage(lang)"
            >
              <IconPlus v-if="!isLanguageAdded(lang)" class="h-3 w-3 mr-1 inline-block" />
              <IconCheck v-else class="h-3 w-3 mr-1 inline-block" />
              {{ lang }}
            </button>
          </div>
        </div>

        <!-- Custom Language -->
        <div class="mt-4 flex gap-2">
          <Input
            v-model="customLanguage"
            :placeholder="t('problems.codeForm.customLanguagePlaceholder')"
            class="font-mono text-sm flex-1 max-w-xs"
            @keyup.enter="handleAddLanguage"
          />
          <Button size="sm" :disabled="!customLanguage.trim()" @click="handleAddLanguage">
            <IconPlus class="h-4 w-4 mr-1" />
            {{ t('problems.codeForm.add') }}
          </Button>
        </div>
        <p v-if="errors.language" class="text-sm text-destructive mt-2">{{ errors.language }}</p>
      </section>

      <!-- Configured Languages -->
      <section v-if="formData.languages.length > 0" class="space-y-3">
        <div
          v-for="(lang, index) in formData.languages"
          :key="index"
          class="rounded-none border bg-card overflow-hidden"
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
                {{ lang.starterCode.split('\n').filter(Boolean).length || 0 }}
                {{ t('problems.codeForm.lines') }}
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
            <Label class="text-xs text-muted-foreground mb-2 block">{{
              t('problems.codeForm.starterCodeTemplate')
            }}</Label>
            <Textarea
              :model-value="lang.starterCode"
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
        class="flex flex-col items-center justify-center py-12 px-4 text-center rounded-none border border-dashed bg-muted/20"
      >
        <div class="w-12 h-12 rounded-none bg-muted flex items-center justify-center mb-3">
          <IconBrackets class="h-6 w-6 text-muted-foreground" />
        </div>
        <h3 class="text-sm font-medium mb-1">{{ t('problems.codeForm.noLanguages') }}</h3>
        <p class="text-xs text-muted-foreground max-w-xs">
          {{ t('problems.codeForm.noLanguagesDescription') }}
        </p>
      </div>
    </div>

    <!-- Sidebar -->
    <aside class="lg:col-span-4 space-y-4 lg:sticky lg:top-6 h-fit self-start">
      <!-- Status Card -->
      <div class="p-4 rounded-none border bg-card">
        <h3 class="text-sm font-medium mb-3">{{ t('problems.codeForm.configuration') }}</h3>
        <div class="space-y-2 text-sm">
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">{{ t('problems.codeForm.languages') }}</span>
            <span class="font-medium tabular-nums">{{ formData.languages.length }}</span>
          </div>
          <p class="text-xs text-muted-foreground pt-2 border-t">
            {{
              formData.languages.length === 0
                ? t('problems.codeForm.allLanguages')
                : t('problems.codeForm.selectedLanguages')
            }}
          </p>
        </div>
      </div>

      <!-- Actions -->
      <div class="p-4 rounded-none border border-primary/20 bg-primary/5">
        <div class="flex flex-col gap-2">
          <Button
            class="w-full"
            :disabled="loading || formData.languages.length === 0"
            @click="submit"
          >
            <IconCheck v-if="!loading" class="h-4 w-4 mr-1" />
            {{ loading ? t('problems.codeForm.saving') : t('problems.codeForm.saveChanges') }}
          </Button>
          <Button variant="outline" class="w-full" @click="cancel">{{ t('common.cancel') }}</Button>
        </div>
      </div>
    </aside>
  </div>
</template>
