<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import {
  IconBrackets,
  IconBulb,
  IconThumbUp,
  IconThumbDown,
  IconTag,
  IconCode,
  IconCalendar,
  IconHash,
} from '@tabler/icons-vue'
import { renderMarkdown } from '@/utils/markdown'

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
}

const props = defineProps<{
  problem: ProblemDetail
}>()

const renderedSummary = computed(() => {
  return props.problem.detail?.summary ? renderMarkdown(props.problem.detail.summary) : ''
})

const getDifficultyColor = (difficulty: string) => {
  switch (difficulty) {
    case 'EASY':
      return 'text-emerald-600 dark:text-emerald-400 bg-emerald-500/10 border-emerald-500/20'
    case 'MEDIUM':
      return 'text-amber-600 dark:text-amber-400 bg-amber-500/10 border-amber-500/20'
    case 'HARD':
      return 'text-rose-600 dark:text-rose-400 bg-rose-500/10 border-rose-500/20'
    default:
      return 'text-muted-foreground bg-muted'
  }
}
</script>

<template>
  <div class="space-y-5">
    <!-- Summary Section -->
    <div v-if="problem.detail?.summary" class="space-y-3">
      <div class="flex items-center gap-2">
        <div class="h-px bg-border flex-1" />
        <h3 class="text-sm font-semibold text-muted-foreground uppercase tracking-wide px-2">
          Summary
        </h3>
        <div class="h-px bg-border flex-1" />
      </div>
      <Card class="border-none shadow-sm bg-muted/20">
        <CardContent class="pt-5">
          <div
            class="prose prose-sm dark:prose-invert max-w-none prose-headings:font-semibold prose-p:text-muted-foreground prose-p:leading-relaxed"
            v-html="renderedSummary"
          />
        </CardContent>
      </Card>
    </div>

    <!-- Two Column Layout -->
    <div class="grid grid-cols-1 xl:grid-cols-3 gap-5">
      <!-- Left Column: Constraints & Hints -->
      <div class="xl:col-span-2 space-y-5">
        <!-- Constraints -->
        <Card>
          <CardHeader class="pb-3">
            <CardTitle class="text-sm flex items-center gap-2">
              <IconBrackets class="w-4 h-4 text-muted-foreground" />
              Constraints
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul
              v-if="problem.detail?.constraints_json?.length"
              class="grid sm:grid-cols-2 gap-x-4 gap-y-2 text-sm"
            >
              <li
                v-for="(c, i) in problem.detail.constraints_json"
                :key="i"
                class="flex items-start gap-2 text-muted-foreground"
              >
                <span class="text-primary mt-0.5">•</span>
                <span>{{ c }}</span>
              </li>
            </ul>
            <p v-else class="text-sm text-muted-foreground italic py-1">No constraints provided.</p>
          </CardContent>
        </Card>

        <!-- Hints -->
        <Card v-if="problem.detail?.hints?.length" class="border-amber-500/20 bg-amber-500/5">
          <CardHeader class="pb-3">
            <CardTitle class="text-sm flex items-center gap-2 text-amber-700 dark:text-amber-400">
              <IconBulb class="w-4 h-4" />
              Hints
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div class="space-y-2">
              <div
                v-for="(hint, i) in problem.detail.hints"
                :key="i"
                class="text-sm p-3 rounded-lg bg-background/60 border border-amber-500/10 flex gap-3"
              >
                <span
                  class="flex-shrink-0 w-5 h-5 flex items-center justify-center text-xs font-mono font-semibold rounded bg-amber-500/10 text-amber-700 dark:text-amber-400"
                >
                  {{ i + 1 }}
                </span>
                <span class="text-muted-foreground">{{ hint }}</span>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      <!-- Right Column: Meta Info -->
      <div class="space-y-5">
        <!-- Engagement Card -->
        <Card>
          <CardHeader class="pb-3">
            <CardTitle class="text-sm">Engagement</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2 text-sm text-muted-foreground">
                <IconThumbUp class="w-4 h-4" />
                <span>Likes</span>
              </div>
              <span class="font-semibold tabular-nums">{{ problem.detail?.likes || 0 }}</span>
            </div>
            <Separator />
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2 text-sm text-muted-foreground">
                <IconThumbDown class="w-4 h-4" />
                <span>Dislikes</span>
              </div>
              <span class="font-semibold tabular-nums">{{ problem.detail?.dislikes || 0 }}</span>
            </div>
            <Separator />
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-2 text-sm text-muted-foreground">
                <IconHash class="w-4 h-4" />
                <span>Rating</span>
              </div>
              <span class="font-semibold tabular-nums">{{
                problem.detail?.difficulty_rating || '-'
              }}</span>
            </div>
          </CardContent>
        </Card>

        <!-- Taxonomy Card -->
        <Card>
          <CardHeader class="pb-3">
            <CardTitle class="text-sm">Taxonomy</CardTitle>
          </CardHeader>
          <CardContent class="space-y-4">
            <!-- Tags -->
            <div class="space-y-2">
              <div class="flex items-center gap-1.5 text-xs text-muted-foreground">
                <IconTag class="w-3.5 h-3.5" />
                <span class="uppercase tracking-wide font-medium">Tags</span>
              </div>
              <div class="flex flex-wrap gap-1.5">
                <Badge
                  v-for="tag in problem.tags"
                  :key="tag.id"
                  variant="secondary"
                  class="rounded-md font-normal text-xs px-2 py-0.5"
                >
                  {{ tag.label }}
                </Badge>
                <span v-if="!problem.tags.length" class="text-xs text-muted-foreground italic">
                  No tags
                </span>
              </div>
            </div>

            <Separator />

            <!-- Languages -->
            <div class="space-y-2">
              <div class="flex items-center gap-1.5 text-xs text-muted-foreground">
                <IconCode class="w-3.5 h-3.5" />
                <span class="uppercase tracking-wide font-medium">Languages</span>
              </div>
              <div class="flex flex-wrap gap-1.5">
                <Badge
                  v-for="lang in problem.languages"
                  :key="lang.id"
                  variant="outline"
                  class="rounded-md font-normal text-xs px-2 py-0.5 font-mono"
                >
                  {{ lang.language }}
                </Badge>
                <span
                  v-if="!problem.languages?.length"
                  class="text-xs text-muted-foreground italic"
                >
                  All languages
                </span>
              </div>
            </div>
          </CardContent>
        </Card>

        <!-- Metadata Card -->
        <Card>
          <CardHeader class="pb-3">
            <CardTitle class="text-sm">Metadata</CardTitle>
          </CardHeader>
          <CardContent class="space-y-3">
            <div class="grid grid-cols-2 gap-3 text-xs">
              <div class="space-y-0.5">
                <p class="text-muted-foreground">ID</p>
                <p class="font-mono bg-muted px-1.5 py-0.5 rounded inline-block">
                  {{ problem.id.slice(0, 8) }}
                </p>
              </div>
              <div class="space-y-0.5">
                <p class="text-muted-foreground">Difficulty</p>
                <Badge :class="['text-[10px]', getDifficultyColor(problem.difficulty)]">
                  {{ problem.difficulty }}
                </Badge>
              </div>
            </div>
            <Separator />
            <div class="space-y-2 text-xs">
              <div class="flex items-center justify-between gap-2">
                <span class="text-muted-foreground flex items-center gap-1">
                  <IconCalendar class="w-3 h-3" />
                  Created
                </span>
                <span class="font-medium">{{
                  new Date(problem.created_at).toLocaleDateString()
                }}</span>
              </div>
              <div class="flex items-center justify-between gap-2">
                <span class="text-muted-foreground flex items-center gap-1">
                  <IconCalendar class="w-3 h-3" />
                  Updated
                </span>
                <span class="font-medium">{{
                  new Date(problem.updated_at).toLocaleDateString()
                }}</span>
              </div>
              <div v-if="problem.published_at" class="flex items-center justify-between gap-2">
                <span class="text-muted-foreground flex items-center gap-1">
                  <IconCalendar class="w-3 h-3" />
                  Published
                </span>
                <span class="font-medium">{{
                  new Date(problem.published_at).toLocaleDateString()
                }}</span>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  </div>
</template>
