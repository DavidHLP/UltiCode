<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import {
  IconInfoCircle,
  IconCalendar,
  IconHash,
  IconUser,
  IconEye,
  IconFlag,
  IconTag,
  IconFileText,
} from '@tabler/icons-vue'
import type { Solution } from '@/api/admin/solutions'
import DescriptionMarkdown from '@/components/problems/DescriptionMarkdown.vue'

const props = defineProps<{
  solution: Solution
}>()

/**
 * Difficulty color mapping
 */
const difficultyClass = computed(() => {
  const difficulty = props.solution.problem?.difficulty.toLowerCase() || 'unknown'
  if (difficulty === 'easy') return 'text-green-600 bg-green-500/10 border-green-500/20'
  if (difficulty === 'medium') return 'text-orange-600 bg-orange-500/10 border-orange-500/20'
  if (difficulty === 'hard') return 'text-red-600 bg-red-500/10 border-red-500/20'
  return 'text-foreground bg-muted'
})

// Adapt content for markdown viewer
const solutionContent = computed(() => ({
  content: props.solution.content,
  examples: [],
  constraints: [],
  followUp: undefined,
}))
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
              {{ solution.title }}
            </h1>
            <div class="flex items-center gap-2 text-muted-foreground text-sm font-mono">
              <IconFileText class="h-4 w-4" />
              <span>Solution for {{ solution.problem?.title }}</span>
            </div>
          </div>
        </div>

        <Separator class="mb-6" />

        <!-- Description Content -->
        <div v-if="solution.content" class="prose prose-sm dark:prose-invert max-w-none">
          <DescriptionMarkdown :description="solutionContent" />
        </div>
        <div v-else class="text-center py-12 text-muted-foreground italic">
          No description content provided.
        </div>

        <!-- Additional info if summary exists -->
        <div v-if="solution.summary" class="mt-8 p-4 bg-muted/30 rounded-lg">
          <h3 class="font-semibold text-sm mb-2">Summary</h3>
          <p class="text-sm text-muted-foreground">{{ solution.summary }}</p>
        </div>
      </div>
    </div>

    <!-- Sidebar: Metadata -->
    <aside class="lg:col-span-4 space-y-6">
      <!-- Metadata Card -->
      <div class="rounded-xl border bg-card overflow-hidden shadow-sm">
        <div class="flex items-center gap-2 p-4 border-b bg-muted/20">
          <IconInfoCircle class="h-4 w-4 text-muted-foreground" />
          <h3 class="font-semibold text-sm">Metadata</h3>
        </div>
        <div class="p-4 space-y-4">
          <div class="space-y-4">
             <div class="space-y-1">
              <span class="text-xs text-muted-foreground flex items-center gap-1">
                <IconUser class="h-3 w-3" /> Author
              </span>
              <div class="flex items-center gap-2">
                <span class="text-sm font-medium">{{ solution.author.username }}</span>
                <span class="text-xs text-muted-foreground">({{ solution.author.name }})</span>
              </div>
            </div>

            <div class="space-y-1">
              <span class="text-xs text-muted-foreground flex items-center gap-1">
                <IconHash class="h-3 w-3" /> Problem Difficulty
              </span>
              <Badge variant="outline" :class="['capitalize px-2 py-0.5 border w-fit', difficultyClass]">
                {{ solution.problem?.difficulty.toLowerCase() }}
              </Badge>
            </div>

             <div class="grid grid-cols-2 gap-4">
                <div class="space-y-1">
                  <span class="text-xs text-muted-foreground flex items-center gap-1">
                    <IconEye class="h-3 w-3" /> Views
                  </span>
                  <p class="text-sm font-medium tabular-nums">
                    {{ solution.views.toLocaleString() }}
                  </p>
                </div>
                 <div class="space-y-1">
                  <span class="text-xs text-muted-foreground flex items-center gap-1">
                    <IconTag class="h-3 w-3" /> Language
                  </span>
                  <p class="text-sm font-medium">
                    {{ solution.language }}
                  </p>
                </div>
             </div>

            <Separator />

            <div class="grid grid-cols-2 gap-4">
              <div class="space-y-1">
                <span class="text-xs text-muted-foreground flex items-center gap-1">
                  <IconCalendar class="h-3 w-3" /> Created
                </span>
                <p class="text-sm font-medium">
                  {{ new Date(solution.created_at).toLocaleDateString() }}
                </p>
              </div>
              <div class="space-y-1">
                <span class="text-xs text-muted-foreground flex items-center gap-1">
                  <IconCalendar class="h-3 w-3" /> Updated
                </span>
                <p class="text-sm font-medium">
                  {{ new Date(solution.updated_at).toLocaleDateString() }}
                </p>
              </div>
            </div>

            <div v-if="solution.is_flagged" class="p-3 rounded bg-red-500/10 border border-red-500/20 text-red-600 space-y-1">
                <span class="text-xs font-semibold flex items-center gap-1">
                  <IconFlag class="h-3 w-3" /> Flagged Reason
                </span>
                <p class="text-xs italic">"{{ solution.flagged_reason }}"</p>
                <p class="text-[10px] opacity-70" v-if="solution.flagged_at">
                  at {{ new Date(solution.flagged_at).toLocaleString() }}
                </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Tags Card -->
      <div v-if="solution.tags && solution.tags.length" class="rounded-xl border bg-card overflow-hidden shadow-sm">
        <div class="flex items-center gap-2 p-4 border-b bg-muted/20">
          <IconTag class="h-4 w-4 text-muted-foreground" />
          <h3 class="font-semibold text-sm">Tags</h3>
        </div>
        <div class="p-4">
          <div class="flex flex-wrap gap-1.5">
            <Badge
              v-for="tag in solution.tags"
              :key="tag"
              variant="secondary"
              class="px-2.5 py-0.5 text-xs font-normal"
            >
              {{ tag }}
            </Badge>
          </div>
        </div>
      </div>
    </aside>
  </div>
</template>
