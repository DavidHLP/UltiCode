<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateByLocale } from '@/i18n/utils'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion'
import { IconTag, IconCalendar, IconBulb } from '@tabler/icons-vue'
import DescriptionMarkdown, {
  type ProblemDescription,
} from '@/components/problems/DescriptionMarkdown.vue'

const { t } = useI18n()

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
  isPremium: boolean
  isPublished: boolean
  hasSolution?: boolean
  submissionCount?: number
  solutionCount?: number
  createdAt: string | Date
  updatedAt: string | Date
  publishedAt?: string | Date
  detail?: {
    summary?: string
    difficultyRating?: number
    likes?: number
    dislikes?: number
    constraintsJson?: string[]
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
 * Difficulty color mapping - matches frontend design
 */
const difficultyClass = computed(() => {
  const difficulty = props.problem.difficulty.toLowerCase()
  if (difficulty === 'easy') return 'text-foreground-strong bg-status-success-surface border border-status-success-mark'
  if (difficulty === 'medium') return 'text-foreground-strong bg-status-warning-surface border border-status-warning-mark'
  if (difficulty === 'hard') return 'text-foreground-strong bg-status-error-surface border border-status-error-mark'
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
  constraints: props.problem.detail?.constraintsJson || [],
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
          {{ t(`problems.difficulty.${problem.difficulty.toUpperCase()}`, problem.difficulty) }}
        </div>

        <!-- Tags Button -->
        <button
          v-if="hasTags"
          class="relative inline-flex items-center justify-center px-1.5 py-0.5 gap-1 rounded-full bg-muted cursor-pointer transition-colors hover:bg-muted/80 hover:opacity-80 text-xs text-muted-foreground"
          @click="scrollToSection(tagsSection)"
        >
          <IconTag class="h-3.5 w-3.5" />
          <span>{{ t('problems.display.tags') }}</span>
        </button>

        <!-- Hints Button -->
        <button
          v-if="hasHints"
          class="relative inline-flex items-center justify-center px-1.5 py-0.5 gap-1 rounded-full bg-muted cursor-pointer transition-colors hover:bg-muted/80 hover:opacity-80 text-xs text-muted-foreground"
          @click="scrollToSection(hintsSection)"
        >
          <IconBulb class="h-3.5 w-3.5" />
          <span>{{ t('problems.display.hints') }}</span>
        </button>
      </div>

      <!-- Problem Description with Markdown Rendering -->
      <div
        v-if="problemDescription.content || problemDescription.examples?.length"
        class="p-4 rounded-none border bg-muted/20"
      >
        <DescriptionMarkdown :description="problemDescription" />
      </div>

      <!-- Statistics and Accordion Section -->
      <div class="mt-4 flex flex-col gap-3">
        <Separator />

        <!-- Accordion for Tags, Hints, Metadata -->
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <!-- Left: Accordion -->
          <div class="lg:col-span-2">
            <Accordion type="multiple" class="w-full">
              <!-- Related Tags -->
              <AccordionItem v-if="hasTags" ref="tagsSection" value="tags">
                <AccordionTrigger class="text-xs hover:no-underline py-3">
                  <div class="flex items-center gap-2">
                    <IconTag class="h-4 w-4" />
                    <span>{{ t('problems.display.tags') }}</span>
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
              <AccordionItem v-if="hasHints" ref="hintsSection" value="hints">
                <AccordionTrigger class="text-xs hover:no-underline py-3">
                  <div class="flex items-center gap-2">
                    <IconBulb class="h-4 w-4" />
                    <span>{{ t('problems.display.hints') }}</span>
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
            </Accordion>
          </div>

          <!-- Right: Metadata -->
          <div class="space-y-4">
            <!-- Metadata Card -->
            <div class="p-4 rounded-none border">
              <h3 class="text-xs font-medium mb-3">{{ t('problems.display.metadata') }}</h3>
              <div class="space-y-2 text-xs">
                <div class="grid grid-cols-2 gap-2">
                  <div>
                    <p class="text-muted-foreground mb-0.5">{{ t('problems.display.id') }}</p>
                    <span class="font-mono bg-muted px-1.5 py-0.5 rounded-none text-xs">
                      {{ problem.id.slice(0, 8) }}
                    </span>
                  </div>
                  <div>
                    <p class="text-muted-foreground mb-0.5">{{ t('common.status') }}</p>
                    <Badge
                      :variant="problem.isPublished ? 'default' : 'secondary'"
                      class="text-2xs px-1.5 py-0"
                    >
                      {{
                        problem.isPublished
                          ? t('problems.published.published')
                          : t('problems.published.draft')
                      }}
                    </Badge>
                  </div>
                </div>
                <Separator class="my-2" />
                <div class="space-y-1.5">
                  <div class="flex items-center gap-2">
                    <IconCalendar class="w-3 h-3 text-muted-foreground" />
                    <span class="text-muted-foreground">{{ t('problems.display.created') }}</span>
                    <span class="ml-auto">{{ formatDateByLocale(problem.createdAt) }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    <IconCalendar class="w-3 h-3 text-muted-foreground" />
                    <span class="text-muted-foreground">{{ t('problems.display.updated') }}</span>
                    <span class="ml-auto">{{ formatDateByLocale(problem.updatedAt) }}</span>
                  </div>
                  <div v-if="problem.publishedAt" class="flex items-center gap-2">
                    <IconCalendar class="w-3 h-3 text-muted-foreground" />
                    <span class="text-muted-foreground">{{ t('problems.display.published') }}</span>
                    <span class="ml-auto">{{ formatDateByLocale(problem.publishedAt) }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Languages -->
            <div v-if="problem.languages?.length" class="p-4 rounded-none border">
              <h3 class="text-xs font-medium mb-3">{{ t('problems.form.languages') }}</h3>
              <div class="flex flex-wrap gap-1.5">
                <Badge
                  v-for="lang in problem.languages"
                  :key="lang.id"
                  variant="outline"
                  class="text-xs px-2 py-0.5 font-mono"
                >
                  {{ lang.language }}
                </Badge>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </section>
</template>
