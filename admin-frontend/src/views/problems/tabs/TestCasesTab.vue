<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
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

// Set first case as active when examples change
watch(
  () => props.examples,
  (examples) => {
    if (!activeId.value && examples.length > 0) {
      activeId.value = examples[0]?.id ?? ''
    }
  },
  { immediate: true },
)

const caseTabs = computed(() =>
  props.examples.map((testCase, index) => ({
    ...testCase,
    displayLabel: `Example ${index + 1}`,
    displayNumber: index + 1,
  })),
)

const activeCase = computed(() => {
  if (!props.examples.length) return undefined
  return props.examples.find((c) => c.id === activeId.value) ?? props.examples[0]
})

const isEmpty = computed(() => props.examples.length === 0)

function selectCase(id: string) {
  activeId.value = id
}
</script>

<template>
  <div class="space-y-4">
    <!-- Empty State -->
    <div
      v-if="isEmpty"
      class="flex flex-col items-center justify-center py-16 text-center border border-dashed rounded-lg"
    >
      <div class="w-12 h-12 rounded-full bg-muted flex items-center justify-center mb-3">
        <Code :size="24" class="text-muted-foreground" />
      </div>
      <p class="text-muted-foreground">No test case examples available.</p>
      <p class="text-sm text-muted-foreground mt-1">Edit this problem to add example test cases.</p>
    </div>

    <!-- Test Cases Content -->
    <div v-else class="space-y-4">
      <!-- Tab Headers -->
      <div class="flex items-center gap-2 overflow-x-auto pb-1">
        <div
          v-for="testCase in caseTabs"
          :key="testCase.id"
          class="flex-shrink-0 cursor-pointer transition-all"
          @click="selectCase(testCase.id)"
        >
          <div
            :class="[
              'flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium transition-all',
              testCase.id === activeId
                ? 'bg-primary text-primary-foreground shadow-sm'
                : 'bg-muted/30 text-muted-foreground hover:bg-muted/50 hover:text-foreground',
            ]"
          >
            <span class="tabular-nums">{{ testCase.displayNumber }}</span>
            <span
              :class="[
                'w-1.5 h-1.5 rounded-full',
                testCase.id === activeId ? 'bg-primary-foreground/50' : 'bg-muted-foreground/30',
              ]"
            />
          </div>
        </div>
      </div>

      <!-- Active Case Content -->
      <Card v-if="activeCase" class="border-muted/50">
        <CardContent class="p-0">
          <div class="grid md:grid-cols-2 gap-0 divide-y md:divide-y-0 md:divide-x divide-border">
            <!-- Input Section -->
            <div class="p-4 space-y-2">
              <div
                class="flex items-center gap-2 text-xs font-medium text-muted-foreground uppercase tracking-wide"
              >
                <FileInput :size="14" />
                <span>Input</span>
              </div>
              <pre
                class="text-xs bg-muted/50 p-3 rounded-lg border overflow-x-auto whitespace-pre-wrap font-mono leading-relaxed"
                >{{ activeCase.input || '(empty)' }}</pre
              >
            </div>

            <!-- Output Section -->
            <div class="p-4 space-y-2">
              <div
                class="flex items-center gap-2 text-xs font-medium text-muted-foreground uppercase tracking-wide"
              >
                <FileOutput :size="14" />
                <span>Output</span>
              </div>
              <pre
                class="text-xs bg-muted/50 p-3 rounded-lg border overflow-x-auto whitespace-pre-wrap font-mono leading-relaxed"
                >{{ activeCase.output || '(empty)' }}</pre
              >
            </div>
          </div>

          <!-- Explanation Section -->
          <div v-if="activeCase.explanation" class="border-t p-4 space-y-2 bg-amber-500/5">
            <div
              class="flex items-center gap-2 text-xs font-medium text-amber-700 dark:text-amber-400 uppercase tracking-wide"
            >
              <Lightbulb :size="14" />
              <span>Explanation</span>
            </div>
            <p class="text-sm text-muted-foreground leading-relaxed">
              {{ activeCase.explanation }}
            </p>
          </div>
        </CardContent>
      </Card>

      <!-- Quick Navigation -->
      <div v-if="caseTabs.length > 1" class="flex items-center justify-center gap-2">
        <Badge
          v-for="testCase in caseTabs"
          :key="testCase.id"
          :variant="testCase.id === activeId ? 'default' : 'outline'"
          :class="[
            'w-8 h-8 p-0 flex items-center justify-center cursor-pointer font-mono text-xs',
            testCase.id !== activeId && 'hover:bg-muted/50',
          ]"
          @click="selectCase(testCase.id)"
        >
          {{ testCase.displayNumber }}
        </Badge>
      </div>
    </div>
  </div>
</template>
