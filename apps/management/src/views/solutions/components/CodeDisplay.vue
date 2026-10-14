<script setup lang="ts">
import { computed } from 'vue'
import CodeViewer from '@/components/shared/CodeViewer.vue'
import type { Solution } from '@/api/admin/solutions'

const props = defineProps<{
  solution: Solution
}>()

const language = computed(() => props.solution.language || 'text')
const code = computed(() => props.solution.content || '')
</script>

<template>
  <div class="border border-border bg-card">
    <div class="border-b border-border px-4 py-2 bg-muted">
      <div class="flex items-center gap-2">
        <span class="text-sm text-muted-foreground">code</span>
        <span class="font-mono text-xs text-muted-foreground">// {{ language }}</span>
      </div>
    </div>
    <div class="p-0">
      <CodeViewer
        :code="code"
        :language="language"
        :show-language-selector="false"
        :no-code-message="$t('solutions.detail.noCodeContent')"
      />
    </div>
  </div>
</template>
