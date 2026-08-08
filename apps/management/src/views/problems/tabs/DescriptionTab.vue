<script setup lang="ts">
import type { DescriptionData } from '@/api/admin/problems'
import DescriptionDisplay from '../components/DescriptionDisplay.vue'

defineProps<{
  problem: DescriptionData | null
}>()
</script>

<template>
  <DescriptionDisplay v-if="problem" :problem="problem" />
</template>
