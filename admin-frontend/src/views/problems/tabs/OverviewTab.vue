<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { IconTag, IconCode, IconCalendar } from '@tabler/icons-vue'
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
</script>

<template>
  <div class="grid grid-cols-1 lg:grid-cols-3 gap-4">
    <!-- Left Column -->
    <div class="lg:col-span-2 space-y-4">
      <!-- Problem Description with Markdown Rendering -->
      <div
        v-if="problemDescription.content || problemDescription.examples?.length"
        class="p-4 rounded-lg border bg-muted/20"
      >
        <h3 class="text-xs font-medium mb-3">Description</h3>
        <DescriptionMarkdown :description="problemDescription" />
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
