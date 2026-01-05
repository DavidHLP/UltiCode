<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Code, FileOutput, FileInput, Lightbulb } from 'lucide-vue-next'

interface Example {
  id: string
  input: string
  output: string
  explanation?: string
  order: number
}

const props = defineProps<{
  examples: Example[]
}>()

const activeId = ref<string>('')

watch(
  () => props.examples,
  (examples) => {
    if (!activeId.value && examples.length > 0) {
      activeId.value = examples[0]?.id ?? ''
    }
  },
  { immediate: true },
)

const activeCase = computed(() => {
  if (!props.examples.length) return undefined
  return props.examples.find((c) => c.id === activeId.value) ?? props.examples[0]
})

function selectCase(id: string) {
  activeId.value = id
}
</script>

<template>
  <div
    v-if="!examples.length"
    class="flex flex-col items-center justify-center py-16 text-center border border-dashed rounded-lg"
  >
    <div class="w-10 h-10 rounded-full bg-muted flex items-center justify-center mb-3">
      <Code :size="20" class="text-muted-foreground" />
    </div>
    <p class="text-sm text-muted-foreground">No examples available</p>
  </div>

  <div v-else class="space-y-3">
    <!-- Tab Headers -->
    <div class="flex items-center gap-1.5 overflow-x-auto pb-1">
      <button
        v-for="(example, index) in examples"
        :key="example.id"
        :class="[
          'flex items-center gap-2 px-3 py-1.5 rounded-md text-xs font-medium transition-colors',
          example.id === activeId
            ? 'bg-primary text-primary-foreground'
            : 'bg-muted/50 text-muted-foreground hover:bg-muted',
        ]"
        @click="selectCase(example.id)"
      >
        <span class="tabular-nums">{{ index + 1 }}</span>
      </button>
    </div>

    <!-- Active Case Content -->
    <div v-if="activeCase" class="rounded-lg border divide-y">
      <div class="grid md:grid-cols-2 md:divide-x divide-border">
        <!-- Input -->
        <div class="p-3">
          <div class="flex items-center gap-1.5 text-xs text-muted-foreground mb-2">
            <FileInput :size="12" />
            <span>Input</span>
          </div>
          <pre
            class="text-xs bg-muted/30 p-3 rounded border overflow-x-auto whitespace-pre-wrap font-mono"
            >{{ activeCase.input || '(empty)' }}</pre
          >
        </div>

        <!-- Output -->
        <div class="p-3">
          <div class="flex items-center gap-1.5 text-xs text-muted-foreground mb-2">
            <FileOutput :size="12" />
            <span>Output</span>
          </div>
          <pre
            class="text-xs bg-muted/30 p-3 rounded border overflow-x-auto whitespace-pre-wrap font-mono"
            >{{ activeCase.output || '(empty)' }}</pre
          >
        </div>
      </div>

      <!-- Explanation -->
      <div v-if="activeCase.explanation" class="p-3 flex gap-2">
        <Lightbulb :size="14" class="text-muted-foreground flex-shrink-0 mt-0.5" />
        <div class="text-sm text-muted-foreground">
          <p class="font-medium mb-1">Explanation</p>
          <p>{{ activeCase.explanation }}</p>
        </div>
      </div>
    </div>
  </div>
</template>
