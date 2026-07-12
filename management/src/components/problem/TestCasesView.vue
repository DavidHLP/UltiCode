<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Button } from '@/components/ui/button'
import type { TestCaseExample } from './TestCasesEditor.vue'

const props = defineProps<{
  examples: TestCaseExample[]
}>()

const activeId = ref('')

const caseTabs = computed(() =>
  props.examples.map((testCase, index) => ({
    ...testCase,
    displayLabel: `Example ${index + 1}`,
  })),
)

// Set first case as active
const activeCase = computed(() => {
  if (!props.examples.length) return undefined
  return props.examples.find((c) => c.id === activeId.value) ?? props.examples[0]
})

// Initialize active ID when examples change
watch(
  () => props.examples,
  (examples) => {
    if (!activeId.value && examples.length > 0) {
      activeId.value = examples[0]?.id ?? ''
    }
  },
  { immediate: true },
)

const selectCase = (id: string) => {
  activeId.value = id
}
</script>

<template>
  <div class="test-cases-view">
    <div v-if="examples.length === 0" class="text-center py-8 text-muted-foreground">
      No test cases available.
    </div>

    <div v-else>
      <!-- Tab Headers -->
      <div class="flex flex-wrap items-center gap-2 mb-4">
        <Button
          v-for="testCase in caseTabs"
          :key="testCase.id"
          :variant="testCase.id === activeId ? 'secondary' : 'ghost'"
          size="sm"
          class="h-8 rounded-none px-3 text-xs font-medium"
          :class="
            testCase.id === activeId
              ? 'text-foreground shadow-none'
              : 'text-muted-foreground hover:text-foreground'
          "
          @click="selectCase(testCase.id)"
        >
          <span>{{ testCase.displayLabel }}</span>
        </Button>
      </div>

      <!-- Active Case Content -->
      <div v-if="activeCase" class="space-y-4 p-4 border rounded-none bg-muted/10">
        <div class="space-y-2">
          <p class="text-xs font-medium text-muted-foreground uppercase">Input</p>
          <pre
            class="text-xs bg-muted p-3 rounded-none border overflow-x-auto whitespace-pre-wrap"
            >{{ activeCase.input }}</pre
          >
        </div>

        <div class="space-y-2">
          <p class="text-xs font-medium text-muted-foreground uppercase">Output</p>
          <pre
            class="text-xs bg-muted p-3 rounded-none border overflow-x-auto whitespace-pre-wrap"
            >{{ activeCase.output }}</pre
          >
        </div>

        <div v-if="activeCase.explanation" class="space-y-2">
          <p class="text-xs font-medium text-muted-foreground uppercase">Explanation</p>
          <p class="text-sm italic text-muted-foreground">{{ activeCase.explanation }}</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.test-cases-view {
  display: flex;
  flex-direction: column;
}
</style>
