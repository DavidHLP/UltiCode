<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
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

const getDifficultyBadgeVariant = (difficulty: string) => {
  switch (difficulty) {
    case 'EASY':
      return 'default'
    case 'MEDIUM':
      return 'secondary'
    case 'HARD':
      return 'destructive'
    default:
      return 'outline'
  }
}

const acceptanceRate = computed(() => {
  if (!props.problem.submission_count || !props.problem.solution_count) return null
  return ((props.problem.solution_count / props.problem.submission_count) * 100).toFixed(1) + '%'
})

const renderedSummary = computed(() => {
  return props.problem.detail?.summary ? renderMarkdown(props.problem.detail.summary) : ''
})
</script>

<template>
  <div class="space-y-4">
    <!-- Basic Info Card -->
    <Card>
      <CardHeader>
        <CardTitle>{{ problem.title }}</CardTitle>
        <p class="text-sm text-muted-foreground">{{ problem.slug }}</p>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="flex gap-2 flex-wrap">
          <Badge :variant="getDifficultyBadgeVariant(problem.difficulty)">
            {{ problem.difficulty }}
          </Badge>
          <Badge variant="outline">{{ problem.status }}</Badge>
          <Badge v-if="problem.is_premium" variant="secondary">Premium</Badge>
          <Badge :variant="problem.is_published ? 'default' : 'secondary'">
            {{ problem.is_published ? 'Published' : 'Draft' }}
          </Badge>
        </div>

        <!-- Tags -->
        <div v-if="problem.tags.length > 0" class="flex items-center gap-2">
          <span class="text-sm font-medium">Tags:</span>
          <div class="flex flex-wrap gap-1">
            <Badge v-for="tag in problem.tags" :key="tag.id" variant="outline">
              {{ tag.label }}
            </Badge>
          </div>
        </div>

        <!-- Languages -->
        <div
          v-if="problem.languages && problem.languages.length > 0"
          class="flex items-center gap-2"
        >
          <span class="text-sm font-medium">Languages:</span>
          <div class="flex flex-wrap gap-1">
            <Badge
              v-for="lang in problem.languages"
              :key="lang.id"
              variant="outline"
              class="bg-background shadow-xs"
            >
              {{ lang.language }}
            </Badge>
          </div>
        </div>

        <!-- Summary -->
        <div v-if="problem.detail?.summary" class="space-y-2">
          <h4 class="text-sm font-medium uppercase tracking-wider text-muted-foreground">
            Summary
          </h4>
          <div class="prose prose-sm dark:prose-invert max-w-none" v-html="renderedSummary" />
        </div>

        <!-- Stats -->
        <div v-if="problem.detail" class="flex gap-6 text-sm text-muted-foreground">
          <div class="flex items-center gap-1">
            <span class="font-medium text-foreground">Likes:</span>
            {{ problem.detail.likes ?? 0 }}
          </div>
          <div class="flex items-center gap-1">
            <span class="font-medium text-foreground">Dislikes:</span>
            {{ problem.detail.dislikes ?? 0 }}
          </div>
          <div v-if="problem.detail.difficulty_rating" class="flex items-center gap-1">
            <span class="font-medium text-foreground">Rating:</span>
            {{ problem.detail.difficulty_rating }}
          </div>
        </div>
      </CardContent>
    </Card>

    <!-- Statistics Card -->
    <Card>
      <CardHeader>
        <CardTitle>Statistics</CardTitle>
      </CardHeader>
      <CardContent class="space-y-2">
        <div class="flex justify-between border-b pb-2">
          <span class="text-muted-foreground">Submissions</span>
          <span class="font-semibold tabular-nums">{{ problem.submission_count || 0 }}</span>
        </div>
        <div class="flex justify-between border-b pb-2">
          <span class="text-muted-foreground">Solutions</span>
          <span class="font-semibold tabular-nums">{{ problem.solution_count || 0 }}</span>
        </div>
        <div class="flex justify-between border-b pb-2">
          <span class="text-muted-foreground">Acceptance Rate</span>
          <span class="font-semibold tabular-nums">{{ acceptanceRate || 'N/A' }}</span>
        </div>
        <div class="flex justify-between">
          <span class="text-muted-foreground">Has Official Solution</span>
          <Badge :variant="problem.has_solution ? 'default' : 'secondary'">
            {{ problem.has_solution ? 'Yes' : 'No' }}
          </Badge>
        </div>
      </CardContent>
    </Card>

    <!-- Timeline Card -->
    <Card>
      <CardHeader>
        <CardTitle>Timeline</CardTitle>
      </CardHeader>
      <CardContent class="space-y-2">
        <div class="flex justify-between border-b pb-2">
          <span class="text-muted-foreground">Created</span>
          <span class="font-medium">{{ new Date(problem.created_at).toLocaleString() }}</span>
        </div>
        <div class="flex justify-between border-b pb-2">
          <span class="text-muted-foreground">Last Updated</span>
          <span class="font-medium">{{ new Date(problem.updated_at).toLocaleString() }}</span>
        </div>
        <div v-if="problem.published_at" class="flex justify-between pb-2">
          <span class="text-muted-foreground">Published On</span>
          <span class="font-medium">{{ new Date(problem.published_at).toLocaleString() }}</span>
        </div>
      </CardContent>
    </Card>

    <!-- Constraints & Hints Card -->
    <Card>
      <CardHeader>
        <CardTitle>Constraints & Hints</CardTitle>
      </CardHeader>
      <CardContent class="space-y-4">
        <div v-if="problem.detail?.constraints_json?.length" class="space-y-2">
          <h4 class="text-xs font-semibold text-muted-foreground uppercase">Constraints</h4>
          <ul class="list-disc pl-4 text-sm space-y-1">
            <li v-for="(c, i) in problem.detail.constraints_json" :key="i">{{ c }}</li>
          </ul>
        </div>
        <div v-if="problem.detail?.hints?.length" class="space-y-2">
          <h4 class="text-xs font-semibold text-muted-foreground uppercase">Hints</h4>
          <div class="space-y-2">
            <div
              v-for="(h, i) in problem.detail.hints"
              :key="i"
              class="text-sm p-2 rounded border bg-amber-50/10 dark:bg-amber-950/20"
            >
              {{ h }}
            </div>
          </div>
        </div>
        <p
          v-if="!problem.detail?.constraints_json?.length && !problem.detail?.hints?.length"
          class="text-sm text-muted-foreground italic"
        >
          No constraints or hints provided.
        </p>
      </CardContent>
    </Card>
  </div>
</template>
