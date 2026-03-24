<script setup lang="ts">
import { computed } from 'vue'
import CodeViewer from '@/components/shared/CodeViewer.vue'

interface ProblemLanguage {
  id: string
  language: string
  value?: string
  style?: string
  starterCode: string
}

interface Props {
  languages?: ProblemLanguage[]
}

const props = defineProps<Props>()

// Use computed to make languageOptions reactive
const languageOptions = computed(() =>
  (props.languages || []).map((lang) => ({
    id: lang.id,
    language: lang.language,
    code: lang.starterCode,
  })),
)
</script>

<template>
  <CodeViewer :languages="languageOptions" show-language-selector />
</template>
