<script setup lang="ts">
import { computed } from 'vue'
import TestCasesView from '@/components/problem/TestCasesView.vue'
import type { TestCaseExample } from '@/components/problem/TestCasesEditor.vue'

interface Example {
  id: string
  input_text: string
  output_text: string
  explanation?: string
}

const props = defineProps<{
  examples: Example[]
}>()

// Convert backend format to component format
const testCases = computed<TestCaseExample[]>(() =>
  props.examples.map((ex) => ({
    id: ex.id,
    input: ex.input_text,
    output: ex.output_text,
    explanation: ex.explanation,
  })),
)
</script>

<template>
  <div class="p-4">
    <h3 class="text-lg font-semibold mb-4">Test Case Examples</h3>
    <TestCasesView :examples="testCases" />
  </div>
</template>
