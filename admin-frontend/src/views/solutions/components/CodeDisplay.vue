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
  <CodeViewer
    :code="code"
    :language="language"
    :show-language-selector="false"
    :no-code-message="$t('solutions.detail.noCodeContent')"
  />
</template>
