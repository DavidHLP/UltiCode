<script setup lang="ts">
import { ref, computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { IconCopy, IconCheck, IconCode, IconBrackets } from '@tabler/icons-vue'
import type { Solution } from '@/api/admin/solutions'

const props = defineProps<{
  solution: Solution
}>()

const copied = ref(false)

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

function getLanguageColor(lang: string): string {
  const colors: Record<string, string> = {
    python: 'bg-blue-500/10 text-blue-600 border-blue-500/20',
    javascript: 'bg-yellow-500/10 text-yellow-600 border-yellow-500/20',
    typescript: 'bg-blue-600/10 text-blue-700 border-blue-600/20',
    java: 'bg-orange-500/10 text-orange-600 border-orange-500/20',
    cpp: 'bg-blue-400/10 text-blue-500 border-blue-400/20',
    c: 'bg-gray-500/10 text-gray-600 border-gray-500/20',
    csharp: 'bg-purple-500/10 text-purple-600 border-purple-500/20',
    go: 'bg-cyan-500/10 text-cyan-600 border-cyan-500/20',
    rust: 'bg-orange-600/10 text-orange-700 border-orange-600/20',
    ruby: 'bg-red-500/10 text-red-600 border-red-500/20',
    php: 'bg-indigo-500/10 text-indigo-600 border-indigo-500/20',
    swift: 'bg-orange-500/10 text-orange-600 border-orange-500/20',
    kotlin: 'bg-purple-600/10 text-purple-700 border-purple-600/20',
    scala: 'bg-red-600/10 text-red-700 border-red-600/20',
  }
  return colors[lang.toLowerCase()] || 'bg-muted text-muted-foreground'
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
        <span class="text-sm text-muted-foreground">{{ lineCount }} lines</span>
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
        {{ copied ? 'Copied' : 'Copy' }}
      </Button>
    </div>

    <!-- Code Display -->
    <div class="relative rounded-xl border bg-card overflow-hidden">
      <!-- Code Header -->
      <div class="flex items-center justify-between px-4 py-2.5 bg-muted/30 border-b">
        <div class="flex items-center gap-2">
          <IconCode class="h-4 w-4 text-muted-foreground" />
          <span class="text-xs font-medium text-muted-foreground">Source Code</span>
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
        No code content available.
      </div>
    </div>
  </div>
</template>
