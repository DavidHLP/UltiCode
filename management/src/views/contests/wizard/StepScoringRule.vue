<script setup lang="ts">
import ScoringRuleSelector from '../components/ScoringRuleSelector.vue'

const props = defineProps<{
  formData: {
    scoring_rule_id?: string
    [key: string]: unknown
  }
}>()

const emit = defineEmits<{
  (e: 'update:formData', value: unknown): void
}>()

function updateField(field: string, value: string) {
  emit('update:formData', {
    ...props.formData,
    [field]: value,
  })
}
</script>

<template>
  <div class="space-y-6">
    <!-- Section Header -->
    <div class="flex items-center gap-2 mb-4">
      <span class="terminal-comment">scoring_config</span>
    </div>

    <!-- Scoring Rule Selector -->
    <ScoringRuleSelector
      :model-value="formData.scoring_rule_id"
      @update:model-value="updateField('scoring_rule_id', $event)"
    />
  </div>
</template>
