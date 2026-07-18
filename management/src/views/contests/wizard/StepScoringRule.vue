<script setup lang="ts">
import ScoringRuleSelector from '../components/ScoringRuleSelector.vue'
import type { ScoringRuleSlice } from './useContestAuthoring'

defineProps<{ slice: ScoringRuleSlice }>()
const emit = defineEmits<{ (e: 'select', value: string): void }>()
</script>

<template>
  <div class="space-y-6">
    <!-- Section Header -->
    <div class="flex items-center gap-2 mb-4">
      <span class="terminal-comment">scoring_config</span>
    </div>

    <!--
      Scoring rule selector. The selector continues to own its on-mount
      default-pick behavior; the authoring module simply accepts whatever
      value flows out via `update:modelValue`. No new default policy is
      introduced at the wizard level.
    -->
    <ScoringRuleSelector
      :model-value="slice.scoringRuleId"
      @update:model-value="emit('select', $event)"
    />
  </div>
</template>
