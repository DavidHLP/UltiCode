<script setup lang="ts">
import type { CasesData } from '@/api/admin/problems'
import CasesDisplay from '../components/CasesDisplay.vue'

defineProps<{
  problem: CasesData | null
}>()
</script>

<template>
  <CasesDisplay v-if="problem" :problem="problem" />
</template>
