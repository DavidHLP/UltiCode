<script setup lang="ts">
import { computed, ref } from 'vue'
import DescriptionMarkdown, {
  type ProblemDescription,
} from '@/components/problems/DescriptionMarkdown.vue'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'
import { Separator } from '@/components/ui/separator'
import { IconTag, IconBulb } from '@tabler/icons-vue'

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
  has_solution?: boolean
  submission_count?: number
  solution_count?: number
  created_at: string | Date
  updated_at: string | Date
  published_at?: string | Date
  detail?: {
    summary?: string
    difficulty_rating?: number
    likes?: number
    dislikes?: number
    constraints_json?: string[]
    hints?: string[]
  }
  tags: Array<{ id: string; label: string }>
  languages?: Array<{ id: string; language: string }>
  examples?: ProblemExample[]
}

const props = defineProps<{
  problem: ProblemDetail
}>()

/**
 * Difficulty color mapping
 */
const difficultyClass = computed(() => {
  const difficulty = props.problem.difficulty.toLowerCase()
  if (difficulty === 'easy') return 'text-green-600 dark:text-green-500'
  if (difficulty === 'medium') return 'text-orange-600 dark:text-orange-500'
  if (difficulty === 'hard') return 'text-red-600 dark:text-red-500'
  return 'text-foreground'
})

const reactionCounts = computed(() => ({
  likes: props.problem.detail?.likes ?? 0,
  dislikes: props.problem.detail?.dislikes ?? 0,
}))

// Refs for accordion sections
const tagsSection = ref<HTMLElement | null>(null)
const hintsSection = ref<HTMLElement | null>(null)

/**
 * Scroll to specific accordion section
 */
const scrollToSection = (element: HTMLElement | null) => {
  element?.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
  })
}

/**
 * Calculate acceptance rate
 */
const acceptanceRate = computed(() => {
  const accepted = reactionCounts.value.likes
  const total = reactionCounts.value.likes + reactionCounts.value.dislikes
  return total > 0 ? ((accepted / total) * 100).toFixed(1) : '0.0'
})

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

const tagLabels = computed(() => props.problem.tags.map((t) => t.label))
const hasHints = computed(
  () => props.problem.detail?.hints?.some((h) => h.trim().length > 0) ?? false,
)
</script>

<template>
  <div class="bg-[#f7f7f8] dark:bg-muted/10 -mx-4 -my-2 px-4 py-4 rounded-lg antialiased">
    <section class="space-y-6 max-w-3xl">
      <section class="space-y-3 px-1">
        <!-- Title -->
        <h1 class="text-2xl font-semibold leading-tight text-foreground">
          {{ props.problem.title }}
        </h1>

        <!-- Badges Row -->
        <div class="flex flex-wrap gap-1">
          <!-- Difficulty Badge -->
          <div
            class="relative inline-flex items-center justify-center px-1.5 py-0.5 gap-1 rounded-full bg-muted text-xs"
            :class="difficultyClass"
          >
            {{
              props.problem.difficulty.charAt(0) + props.problem.difficulty.slice(1).toLowerCase()
            }}
          </div>

          <!-- Tags Button -->
          <button
            v-if="tagLabels.length"
            class="relative inline-flex items-center justify-center px-1.5 py-0.5 gap-1 rounded-full bg-muted cursor-pointer transition-colors hover:bg-muted/80 hover:opacity-80 text-xs text-muted-foreground"
            @click="scrollToSection(tagsSection)"
          >
            <IconTag :size="14" />
            <span>Tags</span>
          </button>

          <!-- Hint Button -->
          <button
            v-if="hasHints"
            class="relative inline-flex items-center justify-center px-1.5 py-0.5 gap-1 rounded-full bg-muted cursor-pointer transition-colors hover:bg-muted/80 hover:opacity-80 text-xs text-muted-foreground"
            @click="scrollToSection(hintsSection)"
          >
            <IconBulb :size="14" />
            <span>Hints</span>
          </button>
        </div>

        <!-- Description Markdown -->
        <DescriptionMarkdown :description="problemDescription" />

        <!-- Statistics and Accordion Section -->
        <div class="mt-6 flex flex-col gap-3">
          <Separator />

          <!-- Acceptance Stats -->
          <div class="flex flex-wrap items-center gap-4">
            <div class="flex items-center gap-2 whitespace-nowrap">
              <div class="text-xs text-muted-foreground">Accepted</div>
              <div>
                <span class="text-xs text-foreground">{{
                  reactionCounts.likes.toLocaleString()
                }}</span>
                <span class="ml-0.5 text-[10px] text-muted-foreground">
                  /
                  {{ (reactionCounts.likes + reactionCounts.dislikes).toLocaleString() }}
                </span>
              </div>
            </div>
            <Separator orientation="vertical" class="h-2.5" />
            <div class="flex items-center gap-2 whitespace-nowrap">
              <div class="text-xs text-muted-foreground">Acceptance Rate</div>
              <div>
                <span class="text-xs text-foreground">{{ acceptanceRate }}</span>
                <span class="ml-0.5 text-[10px] text-muted-foreground">%</span>
              </div>
            </div>
          </div>

          <Separator />

          <!-- Accordion for Tags, Hints -->
          <Accordion type="multiple" class="w-full">
            <!-- Related Tags -->
            <AccordionItem v-if="tagLabels.length" ref="tagsSection" value="tags">
              <AccordionTrigger class="text-xs hover:no-underline py-3">
                <div class="flex items-center gap-2">
                  <IconTag :size="14" class="text-muted-foreground" />
                  <span>Tags</span>
                </div>
              </AccordionTrigger>
              <AccordionContent>
                <div class="mt-2 flex flex-wrap gap-1 pl-6">
                  <span
                    v-for="tag in tagLabels"
                    :key="tag"
                    class="rounded-full border border-border bg-background px-3 py-1 text-[11px] text-muted-foreground"
                  >
                    {{ tag }}
                  </span>
                </div>
              </AccordionContent>
            </AccordionItem>

            <!-- Hints -->
            <AccordionItem v-if="hasHints" ref="hintsSection" value="hints">
              <AccordionTrigger class="text-xs hover:no-underline py-3">
                <div class="flex items-center gap-2">
                  <IconBulb :size="14" class="text-muted-foreground" />
                  <span>Hints</span>
                </div>
              </AccordionTrigger>
              <AccordionContent>
                <ul class="mt-2 space-y-2 pl-6">
                  <li
                    v-for="(hint, index) in problem.detail?.hints"
                    :key="index"
                    class="text-sm text-muted-foreground"
                  >
                    <span class="font-medium">{{ index + 1 }}.</span> {{ hint }}
                  </li>
                </ul>
              </AccordionContent>
            </AccordionItem>
          </Accordion>
        </div>
      </section>
    </section>
  </div>
</template>
