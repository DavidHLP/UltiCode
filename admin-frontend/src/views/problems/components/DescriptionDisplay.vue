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
import { IconTag, IconBulb, IconInfoCircle } from '@tabler/icons-vue'
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
  <div class="grid grid-cols-1 lg:grid-cols-4 gap-6">
    <!-- Main Content: Description -->
    <div class="lg:col-span-3 space-y-6">
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
        </div>

        <!-- Problem Description with Markdown Rendering -->
        <div
          v-if="problemDescription.content || problemDescription.examples?.length"
          class="p-4 rounded-lg border bg-muted/20"
        >
          <DescriptionMarkdown :description="problemDescription" />
        </div>
      </section>
    </div>

    <!-- Sidebar: Metadata, Tags, Hints -->
    <div class="lg:col-span-1 space-y-6">
      <div class="space-y-4">
        <Accordion type="multiple" class="w-full" :default-value="['metadata']">
          <!-- Metadata (Always visible by default recommended) -->
          <AccordionItem value="metadata">
            <AccordionTrigger class="text-xs hover:no-underline py-2">
              <div class="flex items-center gap-2">
                <IconInfoCircle class="h-4 w-4" />
                <span>Metadata</span>
              </div>
            </AccordionTrigger>
            <AccordionContent>
              <div class="space-y-4 pt-1">
                <!-- ID & Status -->
                <div class="grid grid-cols-2 gap-2">
                  <div>
                    <p class="text-[10px] text-muted-foreground uppercase tracking-wider mb-1">
                      ID
                    </p>
                    <span
                      class="font-mono bg-muted px-1.5 py-0.5 rounded text-xs select-all block w-fit"
                    >
                      {{ problem.id.slice(0, 8) }}
                    </span>
                  </div>
                  <div>
                    <p class="text-[10px] text-muted-foreground uppercase tracking-wider mb-1">
                      Status
                    </p>
                    <Badge
                      :variant="problem.is_published ? 'default' : 'secondary'"
                      class="text-[10px] px-1.5 py-0"
                    >
                      {{ problem.is_published ? 'Published' : 'Draft' }}
                    </Badge>
                  </div>
                </div>

                <Separator />

                <!-- Dates -->
                <div class="space-y-2 text-xs">
                  <div class="flex justify-between items-center">
                    <span class="text-muted-foreground">Created</span>
                    <span class="font-medium">{{
                      new Date(problem.created_at).toLocaleDateString()
                    }}</span>
                  </div>
                  <div class="flex justify-between items-center">
                    <span class="text-muted-foreground">Updated</span>
                    <span class="font-medium">{{
                      new Date(problem.updated_at).toLocaleDateString()
                    }}</span>
                  </div>
                  <div v-if="problem.published_at" class="flex justify-between items-center">
                    <span class="text-muted-foreground">Published</span>
                    <span class="font-medium">{{
                      new Date(problem.published_at).toLocaleDateString()
                    }}</span>
                  </div>
                </div>
              </div>
            </AccordionContent>
          </AccordionItem>

          <!-- Related Tags -->
          <AccordionItem v-if="hasTags" value="tags">
            <AccordionTrigger class="text-xs hover:no-underline py-2">
              <div class="flex items-center gap-2">
                <IconTag class="h-4 w-4" />
                <span>Tags</span>
              </div>
            </AccordionTrigger>
            <AccordionContent>
              <div class="flex flex-wrap gap-1.5 pt-1">
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
            <AccordionTrigger class="text-xs hover:no-underline py-2">
              <div class="flex items-center gap-2">
                <IconBulb class="h-4 w-4" />
                <span>Hints</span>
              </div>
            </AccordionTrigger>
            <AccordionContent>
              <ul class="space-y-3 pt-1">
                <li
                  v-for="(hint, index) in hintsList"
                  :key="index"
                  class="text-xs text-muted-foreground leading-relaxed bg-muted/30 p-2 rounded border"
                >
                  <span class="font-mono text-[10px] font-bold text-foreground mr-1"
                    >#{{ index + 1 }}</span
                  >
                  {{ hint }}
                </li>
              </ul>
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      </div>
    </div>
  </div>
</template>
