<script setup lang="ts">
import type { CodeData } from '@/api/admin/problems'
import CodeDisplay from '../components/CodeDisplay.vue'

defineProps<{
  languages: CodeData['languages'] | undefined
}>()
</script>

<template>
  <CodeDisplay :languages="languages" />
</template>
