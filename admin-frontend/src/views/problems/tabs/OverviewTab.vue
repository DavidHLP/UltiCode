<script setup lang="ts">
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { IconBrackets, IconBulb, IconTag, IconCode, IconCalendar } from '@tabler/icons-vue'
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

const renderedSummary = () => {
  return props.problem.detail?.summary ? renderMarkdown(props.problem.detail.summary) : ''
}
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
    <!-- Left Column -->
    <div class="lg:col-span-2 space-y-4">
      <!-- Summary -->
      <div v-if="problem.detail?.summary" class="p-4 rounded-lg border bg-muted/20">
        <h3 class="text-xs font-medium mb-2">Summary</h3>
        <div
          class="prose prose-sm dark:prose-invert max-w-none prose-p:text-muted-foreground prose-p:leading-relaxed"
          v-html="renderedSummary()"
        />
      </div>

      <!-- Constraints -->
      <div v-if="problem.detail?.constraints_json?.length" class="p-4 rounded-lg border">
        <div class="flex items-center gap-2 mb-3">
          <IconBrackets class="w-4 h-4 text-muted-foreground" />
          <h3 class="text-xs font-medium">Constraints</h3>
        </div>
        <ul class="grid sm:grid-cols-2 gap-2 text-sm text-muted-foreground">
          <li v-for="(c, i) in problem.detail.constraints_json" :key="i" class="flex gap-2">
            <span class="text-muted-foreground/60">•</span>
            <span>{{ c }}</span>
          </li>
        </ul>
      </div>

      <!-- Hints -->
      <div v-if="problem.detail?.hints?.length" class="p-4 rounded-lg border">
        <div class="flex items-center gap-2 mb-3">
          <IconBulb class="w-4 h-4 text-muted-foreground" />
          <h3 class="text-xs font-medium">Hints</h3>
        </div>
        <div class="space-y-2">
          <div
            v-for="(hint, i) in problem.detail.hints"
            :key="i"
            class="flex gap-3 p-2.5 rounded bg-muted/30 text-sm text-muted-foreground"
          >
            <span
              class="flex-shrink-0 w-5 h-5 flex items-center justify-center text-xs font-mono rounded bg-muted"
            >
              {{ i + 1 }}
            </span>
            <span>{{ hint }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Right Column: Meta -->
    <div class="space-y-4">
      <!-- Engagement -->
      <div class="p-4 rounded-lg border">
        <h3 class="text-xs font-medium mb-3">Engagement</h3>
        <div class="space-y-2 text-sm">
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">Likes</span>
            <span class="font-medium tabular-nums">{{ problem.detail?.likes || 0 }}</span>
          </div>
          <Separator class="my-2" />
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">Dislikes</span>
            <span class="font-medium tabular-nums">{{ problem.detail?.dislikes || 0 }}</span>
          </div>
          <Separator class="my-2" />
          <div class="flex items-center justify-between">
            <span class="text-muted-foreground">Rating</span>
            <span class="font-medium tabular-nums">{{
              problem.detail?.difficulty_rating || '-'
            }}</span>
          </div>
        </div>
      </div>

      <!-- Tags & Languages -->
      <div class="p-4 rounded-lg border">
        <div class="space-y-3">
          <div>
            <div class="flex items-center gap-1.5 mb-2">
              <IconTag class="w-3.5 h-3.5 text-muted-foreground" />
              <span class="text-xs text-muted-foreground">Tags</span>
            </div>
            <div class="flex flex-wrap gap-1.5">
              <Badge
                v-for="tag in problem.tags"
                :key="tag.id"
                variant="secondary"
                class="text-xs px-2 py-0.5"
              >
                {{ tag.label }}
              </Badge>
              <span v-if="!problem.tags.length" class="text-xs text-muted-foreground italic">
                No tags
              </span>
            </div>
          </div>

          <Separator />

          <div>
            <div class="flex items-center gap-1.5 mb-2">
              <IconCode class="w-3.5 h-3.5 text-muted-foreground" />
              <span class="text-xs text-muted-foreground">Languages</span>
            </div>
            <div class="flex flex-wrap gap-1.5">
              <Badge
                v-for="lang in problem.languages"
                :key="lang.id"
                variant="outline"
                class="text-xs px-2 py-0.5 font-mono"
              >
                {{ lang.language }}
              </Badge>
              <span v-if="!problem.languages?.length" class="text-xs text-muted-foreground italic">
                All languages
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Metadata -->
      <div class="p-4 rounded-lg border">
        <h3 class="text-xs font-medium mb-3">Metadata</h3>
        <div class="space-y-2 text-xs">
          <div class="grid grid-cols-2 gap-2">
            <div>
              <p class="text-muted-foreground mb-0.5">ID</p>
              <span class="font-mono bg-muted px-1.5 py-0.5 rounded text-xs">
                {{ problem.id.slice(0, 8) }}
              </span>
            </div>
            <div>
              <p class="text-muted-foreground mb-0.5">Difficulty</p>
              <Badge variant="secondary" class="text-[10px] px-1.5 py-0">
                {{ problem.difficulty }}
              </Badge>
            </div>
          </div>
          <Separator class="my-2" />
          <div class="space-y-1.5">
            <div class="flex items-center gap-2">
              <IconCalendar class="w-3 h-3 text-muted-foreground" />
              <span class="text-muted-foreground">Created</span>
              <span class="ml-auto">{{ new Date(problem.created_at).toLocaleDateString() }}</span>
            </div>
            <div class="flex items-center gap-2">
              <IconCalendar class="w-3 h-3 text-muted-foreground" />
              <span class="text-muted-foreground">Updated</span>
              <span class="ml-auto">{{ new Date(problem.updated_at).toLocaleDateString() }}</span>
            </div>
            <div v-if="problem.published_at" class="flex items-center gap-2">
              <IconCalendar class="w-3 h-3 text-muted-foreground" />
              <span class="text-muted-foreground">Published</span>
              <span class="ml-auto">{{ new Date(problem.published_at).toLocaleDateString() }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
