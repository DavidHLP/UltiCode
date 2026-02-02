<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { IconCopy, IconCheck, IconCode, IconBrackets } from '@tabler/icons-vue'
import type { Solution } from '@/api/admin/solutions'
import { getLanguageColor } from '@/lib/entities/language'

const props = defineProps<{
  solution: Solution
}>()

const copied = ref(false)
const { t } = useI18n()

const currentCode = computed(() => props.solution.content || '')
const language = computed(() => props.solution.language || 'text')
const lineCount = computed(() => currentCode.value.split('\n').length)

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
  <div class="space-y-4">
    <!-- Header Controls -->
    <div class="flex items-center justify-between gap-4">
      <div class="flex items-center gap-2">
        <Badge
          variant="outline"
          class="font-mono text-sm px-3 py-1.5"
          :class="getLanguageColor(language)"
        >
          <IconBrackets class="h-3.5 w-3.5 mr-1.5 inline-block" />
          {{ language }}
        </Badge>
        <span class="text-sm text-muted-foreground"
          >{{ lineCount }} {{ t('solutions.detail.lines') }}</span
        >
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
        {{ copied ? t('solutions.detail.copied') : t('solutions.detail.copy') }}
      </Button>
    </div>

    <!-- Code Display -->
    <div class="relative rounded-xl border bg-card overflow-hidden">
      <!-- Code Header -->
      <div class="flex items-center justify-between px-4 py-2.5 bg-muted/30 border-b">
        <div class="flex items-center gap-2">
          <IconCode class="h-4 w-4 text-muted-foreground" />
          <span class="text-xs font-medium text-muted-foreground">{{
            t('solutions.detail.sourceCode')
          }}</span>
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
        {{ t('solutions.detail.noCodeContent') }}
      </div>
    </div>
  </div>
</template>
