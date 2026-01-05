<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { IconBrackets, IconBulb, IconThumbUp, IconThumbDown } from '@tabler/icons-vue'
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

const acceptanceRate = computed(() => {
  if (!props.problem.submission_count || !props.problem.solution_count) return null
  return ((props.problem.solution_count / props.problem.submission_count) * 100).toFixed(1) + '%'
})

const renderedSummary = computed(() => {
  return props.problem.detail?.summary ? renderMarkdown(props.problem.detail.summary) : ''
})
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
    <!-- Main Column (Left) -->
    <div class="lg:col-span-2 space-y-6">
      <!-- Summary Section -->
      <div v-if="problem.detail?.summary" class="space-y-3">
        <h3 class="text-lg font-semibold tracking-tight">Summary</h3>
        <Card class="border-none shadow-none bg-muted/30">
          <CardContent class="pt-6">
            <div class="prose prose-sm dark:prose-invert max-w-none" v-html="renderedSummary" />
          </CardContent>
        </Card>
      </div>

      <!-- Constraints & Hints -->
      <div class="space-y-4">
        <h3 class="text-lg font-semibold tracking-tight">Technical Details</h3>

        <div class="grid gap-4">
          <!-- Constraints -->
          <Card>
            <CardHeader class="pb-3">
              <CardTitle class="text-base flex items-center gap-2">
                <IconBrackets class="w-4 h-4 text-muted-foreground" />
                Constraints
              </CardTitle>
            </CardHeader>
            <CardContent>
              <ul
                v-if="problem.detail?.constraints_json?.length"
                class="list-disc pl-4 text-sm space-y-1 text-muted-foreground"
              >
                <li v-for="(c, i) in problem.detail.constraints_json" :key="i">{{ c }}</li>
              </ul>
              <p v-else class="text-sm text-muted-foreground italic">No constraints provided.</p>
            </CardContent>
          </Card>

          <!-- Hints -->
          <Card v-if="problem.detail?.hints?.length">
            <CardHeader class="pb-3">
              <CardTitle class="text-base flex items-center gap-2">
                <IconBulb class="w-4 h-4 text-muted-foreground" />
                Hints
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div class="space-y-2">
                <div
                  v-for="(h, i) in problem.detail.hints"
                  :key="i"
                  class="text-sm p-3 rounded-md bg-muted/50 border flex gap-3"
                >
                  <span class="font-mono text-xs text-muted-foreground mt-0.5 select-none">
                    {{ i + 1 }}
                  </span>
                  <span>{{ h }}</span>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>

    <!-- Sidebar Column (Right) -->
    <div class="space-y-6">
      <!-- Statistics Card -->
      <Card>
        <CardHeader class="pb-3">
          <CardTitle class="text-base">Statistics</CardTitle>
        </CardHeader>
        <CardContent class="grid grid-cols-2 gap-4 pb-4">
          <div class="flex flex-col gap-1">
            <span class="text-xs text-muted-foreground uppercase tracking-wider">Submissions</span>
            <span class="text-2xl font-bold tabular-nums">{{ problem.submission_count || 0 }}</span>
          </div>
          <div class="flex flex-col gap-1">
            <span class="text-xs text-muted-foreground uppercase tracking-wider">Solutions</span>
            <span class="text-2xl font-bold tabular-nums">{{ problem.solution_count || 0 }}</span>
          </div>
          <div class="flex flex-col gap-1">
            <span class="text-xs text-muted-foreground uppercase tracking-wider">Acceptance</span>
            <span class="text-2xl font-bold tabular-nums">{{ acceptanceRate || 'N/A' }}</span>
          </div>
          <div class="flex flex-col gap-1">
            <span class="text-xs text-muted-foreground uppercase tracking-wider">Rating</span>
            <span class="text-2xl font-bold tabular-nums">{{
              problem.detail?.difficulty_rating || '-'
            }}</span>
          </div>
        </CardContent>
        <Separator />
        <div class="p-6 flex items-center justify-between text-sm">
          <div class="flex items-center gap-2 text-emerald-600 dark:text-emerald-500">
            <IconThumbUp class="w-4 h-4" />
            <span class="font-medium">{{ problem.detail?.likes || 0 }}</span>
          </div>
          <div class="flex items-center gap-2 text-red-600 dark:text-red-500">
            <IconThumbDown class="w-4 h-4" />
            <span class="font-medium">{{ problem.detail?.dislikes || 0 }}</span>
          </div>
        </div>
      </Card>

      <!-- Taxonomy Card -->
      <Card>
        <CardHeader class="pb-3">
          <CardTitle class="text-base">Taxonomy</CardTitle>
        </CardHeader>
        <CardContent class="space-y-5">
          <div class="space-y-2">
            <span class="text-xs text-muted-foreground uppercase font-medium tracking-wider"
              >Tags</span
            >
            <div class="flex flex-wrap gap-1.5">
              <Badge
                v-for="tag in problem.tags"
                :key="tag.id"
                variant="secondary"
                class="rounded-md font-normal"
              >
                {{ tag.label }}
              </Badge>
              <span v-if="!problem.tags.length" class="text-sm text-muted-foreground italic"
                >No tags</span
              >
            </div>
          </div>
          <div class="space-y-2">
            <span class="text-xs text-muted-foreground uppercase font-medium tracking-wider"
              >Languages</span
            >
            <div class="flex flex-wrap gap-1.5">
              <Badge
                v-for="lang in problem.languages"
                :key="lang.id"
                variant="outline"
                class="rounded-md font-normal"
              >
                {{ lang.language }}
              </Badge>
              <span v-if="!problem.languages?.length" class="text-sm text-muted-foreground italic"
                >All languages</span
              >
            </div>
          </div>
        </CardContent>
      </Card>

      <!-- Metadata Card -->
      <Card>
        <CardHeader class="pb-3">
          <CardTitle class="text-base">Metadata</CardTitle>
        </CardHeader>
        <CardContent class="space-y-3 text-sm">
          <div class="flex justify-between items-center">
            <span class="text-muted-foreground">ID</span>
            <span class="font-mono text-xs bg-muted px-1.5 py-0.5 rounded">{{
              problem.id.slice(0, 8)
            }}</span>
          </div>
          <div class="flex justify-between items-center">
            <span class="text-muted-foreground">Created</span>
            <span>{{ new Date(problem.created_at).toLocaleDateString() }}</span>
          </div>
          <div class="flex justify-between items-center">
            <span class="text-muted-foreground">Updated</span>
            <span>{{ new Date(problem.updated_at).toLocaleDateString() }}</span>
          </div>
          <div v-if="problem.published_at" class="flex justify-between items-center">
            <span class="text-muted-foreground">Published</span>
            <span>{{ new Date(problem.published_at).toLocaleDateString() }}</span>
          </div>
        </CardContent>
      </Card>
    </div>
  </div>
</template>
