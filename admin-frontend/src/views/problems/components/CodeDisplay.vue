<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { IconCopy, IconCheck, IconCode, IconBrackets } from '@tabler/icons-vue'
import { getLanguageColor } from '@/lib/entities/language'

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

const { t } = useI18n()
const selectedLanguage = ref<string>('')
const copied = ref(false)

const availableLanguages = computed(() => props.languages || [])

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

const lineCount = computed(() => currentCode.value.split('\n').length)
const hasLanguages = computed(() => availableLanguages.value.length > 0)

async function copyToClipboard() {
  if (!currentCode.value) return
  try {
    await navigator.clipboard.writeText(currentCode.value)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch (err) {
    console.error('Failed to copy:', err)
  }
}
</script>

<template>
  <!-- Empty State -->
  <div
    v-if="!hasLanguages"
    class="flex flex-col items-center justify-center py-16 px-4 text-center"
  >
    <div class="w-16 h-16 rounded-2xl bg-muted flex items-center justify-center mb-4">
      <IconCode class="h-8 w-8 text-muted-foreground" />
    </div>
    <h3 class="text-base font-semibold mb-2">{{ t('problems.codeDisplay.noCode') }}</h3>
    <p class="text-sm text-muted-foreground max-w-sm">
      {{ t('problems.codeDisplay.noCodeDescription') }}
    </p>
  </div>

  <!-- Code Viewer -->
  <div v-else class="space-y-4">
    <!-- Language Selector -->
    <div class="flex items-center justify-between gap-4">
      <div class="flex flex-wrap gap-2">
        <button
          v-for="lang in availableLanguages"
          :key="lang.id"
          :class="[
            'px-3 py-1.5 rounded-lg text-sm font-mono font-medium transition-all border',
            selectedLanguage.toLowerCase() === lang.language.toLowerCase()
              ? getLanguageColor(lang.language) + ' shadow-sm'
              : 'bg-muted/50 text-muted-foreground hover:bg-muted border-transparent',
          ]"
          @click="selectedLanguage = lang.language"
        >
          <IconBrackets class="h-3.5 w-3.5 mr-1.5 inline-block" />
          {{ lang.language }}
        </button>
      </div>

      <Button
        v-if="currentCode"
        variant="outline"
        size="sm"
        class="gap-1.5 shrink-0"
        @click="copyToClipboard"
      >
        <IconCheck v-if="copied" class="h-4 w-4 text-green-500" />
        <IconCopy v-else class="h-4 w-4" />
        {{ copied ? t('problems.codeDisplay.copied') : t('problems.codeDisplay.copy') }}
      </Button>
    </div>

    <!-- Code Display -->
    <div class="relative rounded-xl border bg-card overflow-hidden">
      <!-- Code Header -->
      <div class="flex items-center justify-between px-4 py-2.5 bg-muted/30 border-b">
        <div class="flex items-center gap-3">
          <Badge
            variant="outline"
            class="font-mono text-xs"
            :class="getLanguageColor(selectedLanguage)"
          >
            <IconBrackets class="h-3 w-3 mr-1" />
            {{ selectedLanguage }}
          </Badge>
          <span class="text-xs text-muted-foreground"
            >{{ lineCount }} {{ t('problems.codeDisplay.lines') }}</span
          >
        </div>
        <div class="flex items-center gap-1">
          <div class="w-2.5 h-2.5 rounded-full bg-red-400/80" />
          <div class="w-2.5 h-2.5 rounded-full bg-yellow-400/80" />
          <div class="w-2.5 h-2.5 rounded-full bg-green-400/80" />
        </div>
      </div>

      <!-- Code Content -->
      <div v-if="currentCode" class="p-4 overflow-x-auto bg-[#0d1117]">
        <pre
          class="text-sm font-mono whitespace-pre-wrap break-words text-gray-100"
        ><code>{{ currentCode }}</code></pre>
      </div>
      <div v-else class="p-8 text-center text-sm text-muted-foreground italic bg-muted/20">
        {{ t('problems.codeDisplay.noCodeForLanguage', { language: selectedLanguage }) }}
      </div>
    </div>

    <!-- Summary Footer -->
    <div class="flex items-center justify-between text-xs text-muted-foreground px-1">
      <span>{{
        t('problems.codeDisplay.languagesConfigured', { count: availableLanguages.length })
      }}</span>
      <span>{{ t('problems.codeDisplay.selectLanguage') }}</span>
    </div>
  </div>
</template>
