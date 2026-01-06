<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'
import { IconTag, IconCalendar, IconBulb, IconInfoCircle } from '@tabler/icons-vue'
import DescriptionMarkdown, {
  type ProblemDescription,
} from '@/components/problems/DescriptionMarkdown.vue'

interface ProblemExample {
  id: string
  input: string
  output: string
  explanation?: string
  order: number
}

interface ProblemDetail {
  id: string
  title: string
  slug: string
  difficulty: string
  status: string
  is_premium: boolean
  is_published: boolean
  created_at: string | Date
  updated_at: string | Date
  published_at?: string | Date
  detail?: {
    summary?: string
    constraints_json?: string[]
    hints?: string[]
  }
  tags: Array<{ id: string; label: string }>
  examples?: ProblemExample[]
}

const props = defineProps<{
  problem: ProblemDetail
}>()

/**
 * Difficulty color mapping - matches frontend design
 */
const difficultyClass = computed(() => {
  const difficulty = props.problem.difficulty.toLowerCase()
  if (difficulty === 'easy') return 'text-green-600 dark:text-green-500'
  if (difficulty === 'medium') return 'text-orange-600 dark:text-orange-500'
  if (difficulty === 'hard') return 'text-red-600 dark:text-red-500'
  return 'text-foreground'
})

/**
 * Check if hints are available
 */
const hasHints = computed(() => {
  return props.problem.detail?.hints?.length || props.problem.detail?.hints?.join('\n')
})

/**
 * Check if tags are available
 */
const hasTags = computed(() => {
  return props.problem.tags?.length > 0
})

// Note: refs for accordion sections are not needed since scroll-to-section was removed

/**
 * Normalize problem data into the structure expected by DescriptionMarkdown.
 */
const problemDescription = computed<ProblemDescription>(() => ({
  content: props.problem.detail?.summary || '',
  examples: (props.problem.examples || [])
    .sort((a, b) => a.order - b.order)
    .map((example) => ({
      input: example.input,
      output: example.output,
      explanation: example.explanation,
    })),
  constraints: props.problem.detail?.constraints_json || [],
  followUp: props.problem.detail?.hints?.join('\n'),
}))

/**
 * Parse hints as array for accordion display
 */
const hintsList = computed(() => {
  const hints = props.problem.detail?.hints
  if (!hints || hints.length === 0) return []
  // If hints is an array of strings, return as is
  if (typeof hints[0] === 'string') return hints
  // If hints is a single string, split by newlines
  const joined = hints.join('\n')
  return joined.split('\n').filter((h) => h.trim())
})
</script>

<template>
  <section class="space-y-6">
    <section class="space-y-3">
      <!-- Title -->
      <h1 class="text-2xl font-semibold leading-tight">
        {{ problem.title }}
      </h1>

      <!-- Badges Row -->
      <div class="flex flex-wrap gap-1">
        <!-- Difficulty Badge -->
        <div
          class="relative inline-flex items-center justify-center px-1.5 py-0.5 gap-1 rounded-full bg-muted text-xs"
          :class="difficultyClass"
        >
          {{ problem.difficulty }}
        </div>

        <!-- Tags Button -->
        <button
          v-if="hasTags"
          class="relative inline-flex items-center justify-center px-1.5 py-0.5 gap-1 rounded-full bg-muted cursor-pointer transition-colors hover:bg-muted/80 hover:opacity-80 text-xs text-muted-foreground"
        >
          <IconTag class="h-3.5 w-3.5" />
          <span>Tags</span>
        </button>

        <!-- Hints Button -->
        <button
          v-if="hasHints"
          class="relative inline-flex items-center justify-center px-1.5 py-0.5 gap-1 rounded-full bg-muted cursor-pointer transition-colors hover:bg-muted/80 hover:opacity-80 text-xs text-muted-foreground"
        >
          <IconBulb class="h-3.5 w-3.5" />
          <span>Hints</span>
        </button>
      </div>

      <!-- Problem Description with Markdown Rendering -->
      <div
        v-if="problemDescription.content || problemDescription.examples?.length"
        class="p-4 rounded-lg border bg-muted/20"
      >
        <DescriptionMarkdown :description="problemDescription" />
      </div>

      <!-- Statistics and Accordion Section -->
      <div class="mt-4">
        <Separator />

        <!-- Accordion for Tags, Hints, Metadata -->
        <Accordion type="multiple" class="w-full">
          <!-- Related Tags -->
          <AccordionItem v-if="hasTags" value="tags">
            <AccordionTrigger class="text-xs hover:no-underline py-3">
              <div class="flex items-center gap-2">
                <IconTag class="h-4 w-4" />
                <span>Tags</span>
              </div>
            </AccordionTrigger>
            <AccordionContent>
              <div class="mt-2 flex flex-wrap gap-1.5 pl-7">
                <Badge
                  v-for="tag in problem.tags"
                  :key="tag.id"
                  variant="secondary"
                  class="text-xs px-2 py-1"
                >
                  {{ tag.label }}
                </Badge>
              </div>
            </AccordionContent>
          </AccordionItem>

          <!-- Hints -->
          <AccordionItem v-if="hasHints" value="hints">
            <AccordionTrigger class="text-xs hover:no-underline py-3">
              <div class="flex items-center gap-2">
                <IconBulb class="h-4 w-4" />
                <span>Hints</span>
              </div>
            </AccordionTrigger>
            <AccordionContent>
              <ul class="mt-2 space-y-2 pl-7">
                <li
                  v-for="(hint, index) in hintsList"
                  :key="index"
                  class="text-sm text-muted-foreground"
                >
                  <span class="font-medium">{{ index + 1 }}.</span> {{ hint }}
                </li>
              </ul>
            </AccordionContent>
          </AccordionItem>

          <!-- Metadata -->
          <AccordionItem value="metadata">
            <AccordionTrigger class="text-xs hover:no-underline py-3">
              <div class="flex items-center gap-2">
                <IconInfoCircle class="h-4 w-4" />
                <span>Metadata</span>
              </div>
            </AccordionTrigger>
            <AccordionContent>
              <div class="mt-2 text-xs pl-7 max-w-2xl">
                <div class="grid grid-cols-2 gap-4 sm:grid-cols-4 mb-4">
                  <div>
                    <p class="text-muted-foreground mb-1">ID</p>
                    <span class="font-mono bg-muted px-1.5 py-0.5 rounded text-xs select-all">
                      {{ problem.id }}
                    </span>
                  </div>
                  <div>
                    <p class="text-muted-foreground mb-1">Status</p>
                    <Badge
                      :variant="problem.is_published ? 'default' : 'secondary'"
                      class="text-[10px] px-1.5 py-0"
                    >
                      {{ problem.is_published ? 'Published' : 'Draft' }}
                    </Badge>
                  </div>
                </div>

                <div class="space-y-2">
                  <div class="flex items-center gap-2">
                    <IconCalendar class="w-3.5 h-3.5 text-muted-foreground shrink-0" />
                    <span class="text-muted-foreground">Created:</span>
                    <span class="font-medium">{{
                      new Date(problem.created_at).toLocaleString()
                    }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <IconCalendar class="w-3.5 h-3.5 text-muted-foreground shrink-0" />
                    <span class="text-muted-foreground">Updated:</span>
                    <span class="font-medium">{{
                      new Date(problem.updated_at).toLocaleString()
                    }}</span>
                  </div>
                  <div v-if="problem.published_at" class="flex items-center gap-2">
                    <IconCalendar class="w-3.5 h-3.5 text-muted-foreground shrink-0" />
                    <span class="text-muted-foreground">Published:</span>
                    <span class="font-medium">{{
                      new Date(problem.published_at).toLocaleString()
                    }}</span>
                  </div>
                </div>
              </div>
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      </div>
    </section>
  </section>
</template>
