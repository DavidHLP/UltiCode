<script setup lang="ts">
import type { CasesData } from '@/api/admin/problems'
import CasesDisplay from '../components/CasesDisplay.vue'
import HiddenCasesView from '@/components/problem/HiddenCasesView.vue'

defineProps<{
  problem: CasesData | null
  problemId: string
}>()
</script>

<template>
  <div v-if="problem" class="space-y-4">
    <CasesDisplay :problem="problem" />
    <!--
      Admin-only hidden cases (read-only) — same component as in
      ViewCasesView. See HiddenCasesView.vue for the ADR-001 visibility
      contract.
    -->
    <HiddenCasesView :problem-id="problemId" />
  </div>
</template>
