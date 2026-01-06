<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'
import { IconFlask, IconBrackets, IconBulb, IconTag } from '@tabler/icons-vue'

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
  // If hints is an array of strings, return as is
  if (typeof hints[0] === 'string') return hints
  // If hints is a single string, split by newlines
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
</script>

<template>
  <div class="space-y-6">
    <!-- Test Cases / Examples -->
    <Card>
      <CardHeader>
        <div class="flex items-center gap-2">
          <IconFlask class="h-5 w-5 text-muted-foreground" />
          <CardTitle>Test Cases</CardTitle>
        </div>
        <CardDescription>
          Example inputs and outputs to help users understand the problem format.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div v-if="hasExamples" class="space-y-4">
          <div
            v-for="(example, index) in examples"
            :key="example.id"
            class="p-4 rounded-lg border bg-muted/20"
          >
            <div class="flex items-center gap-2 mb-3">
              <Badge variant="outline" class="font-mono text-xs"> Example {{ index + 1 }} </Badge>
            </div>
            <div class="space-y-3">
              <div>
                <p class="text-xs font-medium text-muted-foreground mb-1.5">Input</p>
                <pre
                  class="text-sm font-mono p-3 rounded bg-background border whitespace-pre-wrap break-words"
                  >{{ example.input }}</pre
                >
              </div>
              <div>
                <p class="text-xs font-medium text-muted-foreground mb-1.5">Output</p>
                <pre
                  class="text-sm font-mono p-3 rounded bg-background border whitespace-pre-wrap break-words"
                  >{{ example.output }}</pre
                >
              </div>
              <div v-if="example.explanation">
                <p class="text-xs font-medium text-muted-foreground mb-1.5">Explanation</p>
                <p class="text-sm text-muted-foreground">{{ example.explanation }}</p>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="text-center py-8 text-muted-foreground">
          <IconFlask class="h-8 w-8 mx-auto mb-2 opacity-50" />
          <p class="text-sm">No examples provided</p>
        </div>
      </CardContent>
    </Card>

    <!-- Constraints & Hints -->
    <Card>
      <CardHeader>
        <div class="flex items-center gap-2">
          <IconBrackets class="h-5 w-5 text-muted-foreground" />
          <CardTitle>Constraints & Hints</CardTitle>
        </div>
      </CardHeader>
      <CardContent>
        <Accordion type="multiple" class="w-full" :default-value="['constraints', 'hints']">
          <!-- Constraints -->
          <AccordionItem v-if="hasConstraints" value="constraints">
            <AccordionTrigger class="text-sm hover:no-underline py-3">
              <div class="flex items-center gap-2">
                <IconBrackets class="h-4 w-4" />
                <span>Constraints ({{ constraints.length }})</span>
              </div>
            </AccordionTrigger>
            <AccordionContent>
              <ul class="mt-2 space-y-2">
                <li
                  v-for="(constraint, index) in constraints"
                  :key="index"
                  class="flex items-start gap-2 text-sm font-mono p-2 rounded bg-muted/30"
                >
                  <span class="text-muted-foreground">{{ index + 1 }}.</span>
                  <span>{{ constraint }}</span>
                </li>
              </ul>
            </AccordionContent>
          </AccordionItem>

          <!-- Hints -->
          <AccordionItem v-if="hasHints" value="hints">
            <AccordionTrigger class="text-sm hover:no-underline py-3">
              <div class="flex items-center gap-2">
                <IconBulb class="h-4 w-4" />
                <span>Hints ({{ hints.length }})</span>
              </div>
            </AccordionTrigger>
            <AccordionContent>
              <ul class="mt-2 space-y-2">
                <li
                  v-for="(hint, index) in hints"
                  :key="index"
                  class="flex items-start gap-2 text-sm text-muted-foreground p-2 rounded bg-muted/30"
                >
                  <span class="font-medium">{{ index + 1 }}.</span>
                  <span>{{ hint }}</span>
                </li>
              </ul>
            </AccordionContent>
          </AccordionItem>
        </Accordion>

        <!-- Empty State -->
        <div v-if="!hasConstraints && !hasHints" class="text-center py-6 text-muted-foreground">
          <p class="text-sm">No constraints or hints provided</p>
        </div>
      </CardContent>
    </Card>

    <!-- Tags -->
    <Card v-if="hasTags">
      <CardHeader class="pb-3">
        <div class="flex items-center gap-2">
          <IconTag class="h-4 w-4" />
          <CardTitle class="text-base">Tags</CardTitle>
        </div>
      </CardHeader>
      <CardContent>
        <div class="flex flex-wrap gap-2">
          <Badge v-for="tag in tags" :key="tag.id" variant="secondary" class="text-sm px-3 py-1">
            {{ tag.label }}
          </Badge>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
