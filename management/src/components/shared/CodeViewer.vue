<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { IconCopy, IconCheck, IconCode, IconBrackets } from '@tabler/icons-vue'
import { getLanguageColor } from '@/lib/entities/language'

export interface LanguageOption {
  id: string
  language: string
  code?: string
  value?: string
  starter_code?: string
}

interface Props {
  code?: string
  language?: string
  languages?: LanguageOption[]
  showLanguageSelector?: boolean
  emptyStateTitle?: string
  emptyStateDescription?: string
  noCodeMessage?: string
}

const props = withDefaults(defineProps<Props>(), {
  code: '',
  language: 'text',
  languages: () => [],
  showLanguageSelector: true,
})

const { t } = useI18n()
const copied = ref(false)
const selectedLanguage = ref<string>(props.language)

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
  if (props.languages && props.languages.length > 0) {
    const lang = props.languages.find(
      (l) => l.language.toLowerCase() === selectedLanguage.value.toLowerCase(),
    )
    return lang?.code || lang?.starter_code || ''
  }
  return props.code || ''
})

const displayLanguage = computed(() => {
  if (props.languages && props.languages.length > 0) {
    return selectedLanguage.value
  }
  return props.language
})

const lineCount = computed(() => currentCode.value.split('\n').length)
const hasLanguages = computed(() => availableLanguages.value.length > 0)
const hasCode = computed(() => currentCode.value.length > 0)

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
    v-if="showLanguageSelector && !hasLanguages"
    class="flex flex-col items-center justify-center py-16 px-4 text-center"
  >
    <div class="w-16 h-16 rounded-2xl bg-muted flex items-center justify-center mb-4">
      <IconCode class="h-8 w-8 text-muted-foreground" />
    </div>
    <h3 class="text-base font-semibold mb-2">
      {{ emptyStateTitle || t('problems.codeDisplay.noCode') }}
    </h3>
    <p class="text-sm text-muted-foreground max-w-sm">
      {{ emptyStateDescription || t('problems.codeDisplay.noCodeDescription') }}
    </p>
  </div>

  <!-- Code Viewer -->
  <div v-else class="space-y-4">
    <!-- Language Selector (for multi-language scenarios) -->
    <div
      v-if="showLanguageSelector && hasLanguages"
      class="flex items-center justify-between gap-4"
    >
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
        v-if="hasCode"
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

    <!-- Single Language Header (for single-language scenarios) -->
    <div v-else-if="!showLanguageSelector" class="flex items-center justify-between gap-4">
      <div class="flex items-center gap-2">
        <Badge
          variant="outline"
          class="font-mono text-sm px-3 py-1.5"
          :class="getLanguageColor(displayLanguage)"
        >
          <IconBrackets class="h-3.5 w-3.5 mr-1.5 inline-block" />
          {{ displayLanguage }}
        </Badge>
        <span class="text-sm text-muted-foreground"
          >{{ lineCount }} {{ t('solutions.detail.lines') }}</span
        >
      </div>

      <Button
        v-if="hasCode"
        variant="outline"
        size="sm"
        class="gap-1.5 shrink-0"
        @click="copyToClipboard"
      >
        <IconCheck v-if="copied" class="h-4 w-4 text-green-500" />
        <IconCopy v-else class="h-4 w-4" />
        {{ copied ? t('solutions.detail.copied') : t('solutions.detail.copy') }}
      </Button>
    </div>

    <!-- Code Display -->
    <div class="relative rounded-xl border bg-card overflow-hidden">
      <!-- Code Header -->
      <div class="flex items-center justify-between px-4 py-2.5 bg-muted/30 border-b">
        <div class="flex items-center gap-3">
          <Badge
            v-if="showLanguageSelector && hasLanguages"
            variant="outline"
            class="font-mono text-xs"
            :class="getLanguageColor(displayLanguage)"
          >
            <IconBrackets class="h-3 w-3 mr-1" />
            {{ displayLanguage }}
          </Badge>
          <div v-else class="flex items-center gap-2">
            <IconCode class="h-4 w-4 text-muted-foreground" />
            <span class="text-xs font-medium text-muted-foreground">{{
              t('solutions.detail.sourceCode')
            }}</span>
          </div>
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
      <div v-if="hasCode" class="p-4 overflow-x-auto bg-[#0d1117]">
        <pre
          class="text-sm font-mono whitespace-pre-wrap break-words text-gray-100"
        ><code>{{ currentCode }}</code></pre>
      </div>
      <div v-else class="p-8 text-center text-sm text-muted-foreground italic bg-muted/20">
        {{
          noCodeMessage ||
          t('problems.codeDisplay.noCodeForLanguage', { language: displayLanguage })
        }}
      </div>
    </div>

    <!-- Summary Footer (only for multi-language) -->
    <div
      v-if="showLanguageSelector && hasLanguages"
      class="flex items-center justify-between text-xs text-muted-foreground px-1"
    >
      <span>{{
        t('problems.codeDisplay.languagesConfigured', { count: availableLanguages.length })
      }}</span>
      <span>{{ t('problems.codeDisplay.selectLanguage') }}</span>
    </div>
  </div>
</template>
