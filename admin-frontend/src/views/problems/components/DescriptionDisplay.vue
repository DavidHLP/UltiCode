<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Badge } from '@/components/ui/badge'
import { IconTag, IconBulb, IconInfoCircle, IconCalendar, IconHash } from '@tabler/icons-vue'
import DescriptionMarkdown, {
  type ProblemDescription,
} from '@/components/problems/DescriptionMarkdown.vue'
import { Separator } from '@/components/ui/separator'

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

const { t } = useI18n()

/**
 * Difficulty color mapping - matches frontend design
 */
const difficultyClass = computed(() => {
  const difficulty = props.problem.difficulty.toLowerCase()
  if (difficulty === 'easy') return 'text-green-600 bg-green-500/10 border-green-500/20'
  if (difficulty === 'medium') return 'text-orange-600 bg-orange-500/10 border-orange-500/20'
  if (difficulty === 'hard') return 'text-red-600 bg-red-500/10 border-red-500/20'
  return 'text-foreground bg-muted'
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
 * Parse hints as array
 */
const hintsList = computed(() => {
  const hints = props.problem.detail?.hints
  if (!hints || hints.length === 0) return []
  if (typeof hints[0] === 'string') return hints
  const joined = hints.join('\n')
  return joined.split('\n').filter((h) => h.trim())
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
    <!-- Main Content: Description -->
    <div class="lg:col-span-8 space-y-6">
      <div class="rounded-xl border bg-card p-6 shadow-sm">
        <!-- Header -->
        <div class="flex flex-col gap-4 mb-6">
          <div class="space-y-1">
            <h1 class="text-2xl font-bold tracking-tight">
              {{ problem.title }}
            </h1>
            <div class="flex items-center gap-2 text-muted-foreground text-sm font-mono">
              <span>{{ problem.slug }}</span>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <Badge variant="outline" :class="['capitalize px-2.5 py-0.5 border', difficultyClass]">
              {{ t(`problems.difficulty.${problem.difficulty.toUpperCase()}`) }}
            </Badge>
            <Badge
              v-if="problem.is_premium"
              variant="secondary"
              class="bg-amber-500/10 text-amber-600 hover:bg-amber-500/20 border-amber-500/20 border"
            >
              {{ t('problems.badges.premium') }}
            </Badge>
            <Badge :variant="problem.is_published ? 'default' : 'outline'" class="capitalize">
              {{
                problem.is_published
                  ? t('problems.published.published')
                  : t('problems.published.draft')
              }}
            </Badge>
          </div>
        </div>

        <Separator class="mb-6" />

        <!-- Problem Description with Markdown Rendering -->
        <div class="prose prose-sm dark:prose-invert max-w-none">
          <DescriptionMarkdown :description="problemDescription" />
        </div>
      </div>
    </div>

    <!-- Sidebar: Metadata, Tags, Hints -->
    <aside class="lg:col-span-4 space-y-6">
      <!-- Metadata Card -->
      <div class="rounded-xl border bg-card overflow-hidden shadow-sm">
        <div class="flex items-center gap-2 p-4 border-b bg-muted/20">
          <IconInfoCircle class="h-4 w-4 text-muted-foreground" />
          <h3 class="font-semibold text-sm">{{ t('problems.display.metadata') }}</h3>
        </div>
        <div class="p-4 space-y-4">
          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-1">
              <span class="text-xs text-muted-foreground flex items-center gap-1">
                <IconHash class="h-3 w-3" /> {{ t('problems.display.id') }}
              </span>
              <p class="font-mono text-xs bg-muted/50 p-1 rounded select-all truncate">
                {{ problem.id }}
              </p>
            </div>
            <div class="space-y-1">
              <span class="text-xs text-muted-foreground flex items-center gap-1">
                <IconCalendar class="h-3 w-3" /> {{ t('problems.display.created') }}
              </span>
              <p class="text-sm font-medium">
                {{ new Date(problem.created_at).toLocaleDateString() }}
              </p>
            </div>
            <div class="space-y-1">
              <span class="text-xs text-muted-foreground flex items-center gap-1">
                <IconCalendar class="h-3 w-3" /> {{ t('problems.display.updated') }}
              </span>
              <p class="text-sm font-medium">
                {{ new Date(problem.updated_at).toLocaleDateString() }}
              </p>
            </div>
            <div v-if="problem.published_at" class="space-y-1">
              <span class="text-xs text-muted-foreground flex items-center gap-1">
                <IconCalendar class="h-3 w-3" /> {{ t('problems.display.published') }}
              </span>
              <p class="text-sm font-medium">
                {{ new Date(problem.published_at).toLocaleDateString() }}
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Tags Card -->
      <div v-if="hasTags" class="rounded-xl border bg-card overflow-hidden shadow-sm">
        <div class="flex items-center gap-2 p-4 border-b bg-muted/20">
          <IconTag class="h-4 w-4 text-muted-foreground" />
          <h3 class="font-semibold text-sm">{{ t('problems.display.tags') }}</h3>
        </div>
        <div class="p-4">
          <div class="flex flex-wrap gap-1.5">
            <Badge
              v-for="tag in problem.tags"
              :key="tag.id"
              variant="secondary"
              class="px-2.5 py-0.5 text-xs font-normal"
            >
              {{ tag.label }}
            </Badge>
          </div>
        </div>
      </div>

      <!-- Hints Card -->
      <div v-if="hasHints" class="rounded-xl border bg-card overflow-hidden shadow-sm">
        <div class="flex items-center gap-2 p-4 border-b bg-muted/20">
          <IconBulb class="h-4 w-4 text-muted-foreground" />
          <h3 class="font-semibold text-sm">{{ t('problems.display.hints') }}</h3>
          <Badge variant="secondary" class="ml-auto text-xs">{{ hintsList.length }}</Badge>
        </div>
        <div class="p-4">
          <ul class="space-y-2">
            <li
              v-for="(hint, index) in hintsList"
              :key="index"
              class="text-sm text-muted-foreground p-3 rounded-lg bg-muted/30 flex items-start gap-2.5"
            >
              <span
                class="font-mono text-xs font-medium text-foreground/70 bg-background border px-1.5 rounded shrink-0 h-5 flex items-center justify-center"
              >
                {{ index + 1 }}
              </span>
              <span class="leading-snug">{{ hint }}</span>
            </li>
          </ul>
        </div>
      </div>
    </aside>
  </div>
</template>
