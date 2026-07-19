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
      Scoring rule selector. The selector owns the on-mount default-pick
      (it surfaces the first available rule for the contest type via
      `update:modelValue`); the authoring module accepts the resulting
      value through its `select` patch and routes it into the draft. This
      keeps the default-policy lookup (which needs the rules endpoint)
      next to the selector's own data fetch rather than duplicating it
      in the authoring module.
    -->
    <ScoringRuleSelector
      :model-value="slice.scoringRuleId"
      @update:model-value="emit('select', $event)"
    />
  </div>
</template>
