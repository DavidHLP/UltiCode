<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { IconBrackets, IconCopy, IconCheck } from '@tabler/icons-vue'
import { IconCode } from '@tabler/icons-vue'

interface ProblemLanguage {
  id: string
  language: string
  value?: string
  style?: string
  starter_code: string
}

interface Props {
  languages?: ProblemLanguage[]
}

const props = defineProps<Props>()

const selectedLanguage = ref<string>('')
const copied = ref(false)

// Set first language as default
const availableLanguages = computed(() => {
  if (!props.languages?.length) return []
  return props.languages
})

// Auto-select first language when available languages change
watch(
  availableLanguages,
  (langs) => {
    if (!selectedLanguage.value && langs.length > 0) {
      selectedLanguage.value = langs[0]?.language || ''
    }
  },
  { immediate: true },
)

const currentCode = computed(() => {
  if (!selectedLanguage.value) return ''
  const lang = availableLanguages.value.find(
    (l) => l.language.toLowerCase() === selectedLanguage.value.toLowerCase(),
  )
  return lang?.starter_code || ''
})

const hasLanguages = computed(() => {
  return availableLanguages.value.length > 0
})

async function copyToClipboard() {
  if (!currentCode.value) return
  try {
    await navigator.clipboard.writeText(currentCode.value)
    copied.value = true
    setTimeout(() => {
      copied.value = false
    }, 2000)
  } catch (err) {
    console.error('Failed to copy:', err)
  }
}

function getLanguageColor(lang: string): string {
  const colors: Record<string, string> = {
    python: 'text-blue-500',
    javascript: 'text-yellow-500',
    typescript: 'text-blue-600',
    java: 'text-orange-500',
    cpp: 'text-blue-400',
    c: 'text-gray-500',
    csharp: 'text-purple-500',
    go: 'text-cyan-500',
    rust: 'text-orange-600',
    ruby: 'text-red-500',
    php: 'text-indigo-500',
    swift: 'text-orange-500',
    kotlin: 'text-purple-600',
    scala: 'text-red-600',
  }
  return colors[lang.toLowerCase()] || 'text-gray-500'
}
</script>

<template>
  <div class="space-y-6">
    <!-- Empty State -->
    <Card v-if="!hasLanguages" class="border-dashed">
      <CardContent class="flex flex-col items-center justify-center py-12 text-center">
        <IconCode class="h-12 w-12 text-muted-foreground mb-3" />
        <p class="text-sm font-medium mb-1">No starter code configured</p>
        <p class="text-xs text-muted-foreground">
          This problem doesn't have any starter code templates set up.
        </p>
        <p class="text-xs text-muted-foreground mt-1">
          The problem will be available in all languages by default.
        </p>
      </CardContent>
    </Card>

    <!-- Code Display -->
    <Card v-else>
      <CardHeader>
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <IconBrackets class="h-5 w-5 text-muted-foreground" />
            <CardTitle>Starter Code</CardTitle>
          </div>
          <Button
            v-if="currentCode"
            variant="outline"
            size="sm"
            class="gap-1.5"
            @click="copyToClipboard"
          >
            <IconCheck v-if="copied" class="h-4 w-4 text-green-500" />
            <IconCopy v-else class="h-4 w-4" />
            {{ copied ? 'Copied!' : 'Copy' }}
          </Button>
        </div>
        <CardDescription>
          Starter code templates for different programming languages.
        </CardDescription>
      </CardHeader>
      <CardContent class="space-y-4">
        <!-- Language Selector -->
        <div class="space-y-2">
          <label class="text-sm font-medium">Select Language</label>
          <Select v-model="selectedLanguage">
            <SelectTrigger class="w-full sm:w-[200px]">
              <SelectValue placeholder="Select a language" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem v-for="lang in availableLanguages" :key="lang.id" :value="lang.language">
                <div class="flex items-center gap-2">
                  <Badge variant="outline" class="font-mono text-xs">
                    {{ lang.language }}
                  </Badge>
                </div>
              </SelectItem>
            </SelectContent>
          </Select>
        </div>

        <!-- Language Info -->
        <div v-if="selectedLanguage" class="flex items-center gap-2">
          <Badge variant="outline" class="font-mono" :class="getLanguageColor(selectedLanguage)">
            <IconBrackets class="h-3 w-3 mr-1" />
            {{ selectedLanguage }}
          </Badge>
          <span class="text-sm text-muted-foreground">
            {{ currentCode.split('\n').length }} lines
          </span>
        </div>

        <!-- Code Display -->
        <div v-if="currentCode" class="relative p-4 rounded-lg border bg-muted/30 overflow-x-auto">
          <pre
            class="text-sm font-mono whitespace-pre-wrap break-words"
          ><code>{{ currentCode || '// No starter code available for this language' }}</code></pre>
        </div>
        <div
          v-else
          class="p-8 rounded-lg border bg-muted/20 text-center text-sm text-muted-foreground italic"
        >
          No starter code available for {{ selectedLanguage }}
        </div>
      </CardContent>
    </Card>

    <!-- All Languages Summary -->
    <Card v-if="hasLanguages">
      <CardHeader class="pb-3">
        <CardTitle class="text-base">Configured Languages</CardTitle>
      </CardHeader>
      <CardContent>
        <div class="flex flex-wrap gap-2">
          <Badge
            v-for="lang in availableLanguages"
            :key="lang.id"
            variant="secondary"
            class="font-mono text-sm px-3 py-1"
            :class="getLanguageColor(lang.language)"
          >
            <IconBrackets class="h-3 w-3 mr-1" />
            {{ lang.language }}
          </Badge>
        </div>
        <p v-if="availableLanguages.length === 0" class="text-sm text-muted-foreground mt-2">
          No specific languages configured. Problem is available in all languages.
        </p>
        <p v-else class="text-xs text-muted-foreground mt-2">
          {{ availableLanguages.length }} language(s) configured with starter code.
        </p>
      </CardContent>
    </Card>
  </div>
</template>
