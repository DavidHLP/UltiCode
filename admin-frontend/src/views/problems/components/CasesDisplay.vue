<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'
import { IconFlask, IconBrackets, IconBulb, IconTag, IconCode } from '@tabler/icons-vue'

interface ProblemExample {
  id: string
  input: string
  output: string
  explanation?: string
  order: number
}

interface ProblemDetail {
  detail?: {
    constraints_json?: string[]
    hints?: string[]
  }
  examples?: ProblemExample[]
  tags: Array<{ id: string; label: string }>
}

const props = defineProps<{
  problem: ProblemDetail
}>()

const examples = computed(() => {
  return (props.problem.examples || []).sort((a, b) => a.order - b.order)
})

const constraints = computed(() => {
  return props.problem.detail?.constraints_json || []
})

const hints = computed(() => {
  const hints = props.problem.detail?.hints
  if (!hints || hints.length === 0) return []
  if (typeof hints[0] === 'string') return hints
  const joined = hints.join('\n')
  return joined.split('\n').filter((h) => h.trim())
})

const tags = computed(() => {
  return props.problem.tags || []
})

const hasExamples = computed(() => examples.value.length > 0)
const hasConstraints = computed(() => constraints.value.length > 0)
const hasHints = computed(() => hints.value.length > 0)
const hasTags = computed(() => tags.value.length > 0)

const hasAnyContent = computed(
  () => hasExamples.value || hasConstraints.value || hasHints.value || hasTags.value,
)
</script>

<template>
  <!-- Empty State -->
  <div
    v-if="!hasAnyContent"
    class="flex flex-col items-center justify-center py-16 px-4 text-center"
  >
    <div class="w-16 h-16 rounded-2xl bg-muted flex items-center justify-center mb-4">
      <IconCode class="h-8 w-8 text-muted-foreground" />
    </div>
    <h3 class="text-base font-semibold mb-2">No Test Cases Added</h3>
    <p class="text-sm text-muted-foreground max-w-sm">
      This problem doesn't have any test cases, constraints, or hints configured yet.
    </p>
  </div>

  <!-- Content Grid -->
  <div v-else class="grid grid-cols-1 lg:grid-cols-12 gap-5">
    <!-- Test Cases -->
    <section v-if="hasExamples" class="lg:col-span-7 rounded-xl border bg-card overflow-hidden">
      <div class="flex items-center gap-2 p-4 border-b bg-muted/20">
        <IconFlask class="h-4 w-4 text-muted-foreground" />
        <h3 class="font-semibold text-sm">Examples</h3>
        <Badge variant="secondary" class="ml-auto text-xs">{{ examples.length }}</Badge>
      </div>

      <div class="divide-y">
        <div
          v-for="(example, index) in examples"
          :key="example.id"
          class="p-4"
          :class="index > 0 ? 'bg-muted/10' : ''"
        >
          <div class="flex items-center gap-2 mb-3">
            <Badge variant="outline" class="font-mono text-xs">
              {{ index + 1 }}
            </Badge>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
            <!-- Input -->
            <div class="space-y-1.5">
              <p class="text-xs font-medium text-muted-foreground">Input</p>
              <pre
                class="text-sm font-mono p-3 rounded-lg bg-[#0d1117] text-gray-100 overflow-x-auto"
                >{{ example.input }}</pre
              >
            </div>

            <!-- Output -->
            <div class="space-y-1.5">
              <p class="text-xs font-medium text-muted-foreground">Output</p>
              <pre
                class="text-sm font-mono p-3 rounded-lg bg-[#0d1117] text-gray-100 overflow-x-auto"
                >{{ example.output }}</pre
              >
            </div>
          </div>

          <!-- Explanation -->
          <p v-if="example.explanation" class="text-sm text-muted-foreground mt-3 pl-1">
            <span class="font-medium">Explanation:</span> {{ example.explanation }}
          </p>
        </div>
      </div>
    </section>

    <!-- Sidebar: Constraints, Hints, Tags -->
    <aside class="lg:col-span-5 space-y-4">
      <!-- Constraints & Hints Accordion -->
      <div v-if="hasConstraints || hasHints" class="rounded-xl border bg-card overflow-hidden">
        <div class="flex items-center gap-2 p-4 border-b bg-muted/20">
          <IconBrackets class="h-4 w-4 text-muted-foreground" />
          <h3 class="font-semibold text-sm">Constraints & Hints</h3>
        </div>

        <Accordion type="multiple" :default-value="['constraints', 'hints']" class="border-0">
          <!-- Constraints -->
          <AccordionItem v-if="hasConstraints" value="constraints" class="border-0">
            <AccordionTrigger class="px-4 py-3 hover:no-underline text-sm">
              <div class="flex items-center gap-2">
                <IconBrackets class="h-4 w-4" />
                <span>Constraints</span>
                <Badge variant="secondary" class="text-xs">{{ constraints.length }}</Badge>
              </div>
            </AccordionTrigger>
            <AccordionContent class="px-4 pb-4">
              <ul class="space-y-1.5">
                <li
                  v-for="(constraint, index) in constraints"
                  :key="index"
                  class="text-sm font-mono p-2 rounded-lg bg-muted/30 flex items-start gap-2"
                >
                  <span class="text-muted-foreground/70 shrink-0">{{ index + 1 }}.</span>
                  <span>{{ constraint }}</span>
                </li>
              </ul>
            </AccordionContent>
          </AccordionItem>

          <!-- Hints -->
          <AccordionItem v-if="hasHints" value="hints" class="border-0">
            <AccordionTrigger class="px-4 py-3 hover:no-underline text-sm">
              <div class="flex items-center gap-2">
                <IconBulb class="h-4 w-4" />
                <span>Hints</span>
                <Badge variant="secondary" class="text-xs">{{ hints.length }}</Badge>
              </div>
            </AccordionTrigger>
            <AccordionContent class="px-4 pb-4">
              <ul class="space-y-2">
                <li
                  v-for="(hint, index) in hints"
                  :key="index"
                  class="text-sm text-muted-foreground p-2 rounded-lg bg-muted/30 flex items-start gap-2"
                >
                  <span class="font-mono text-xs text-muted-foreground/70 shrink-0"
                    >{{ index + 1 }}.</span
                  >
                  <span>{{ hint }}</span>
                </li>
              </ul>
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      </div>

      <!-- Tags -->
      <div v-if="hasTags" class="rounded-xl border bg-card">
        <div class="flex items-center gap-2 p-4 border-b bg-muted/20">
          <IconTag class="h-4 w-4 text-muted-foreground" />
          <h3 class="font-semibold text-sm">Tags</h3>
        </div>
        <div class="p-4">
          <div class="flex flex-wrap gap-2">
            <Badge v-for="tag in tags" :key="tag.id" variant="secondary" class="text-sm px-3 py-1">
              {{ tag.label }}
            </Badge>
          </div>
        </div>
      </div>
    </aside>
  </div>
</template>
