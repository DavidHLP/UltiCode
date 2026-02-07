<script setup lang="ts">
import CodeViewer from '@/components/shared/CodeViewer.vue'

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

const languageOptions = (props.languages || []).map((lang) => ({
  id: lang.id,
  language: lang.language,
  code: lang.starter_code,
}))
</script>

<template>
  <CodeViewer :languages="languageOptions" show-language-selector />
</template>
