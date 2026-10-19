<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Solution } from '@/api/admin/solutions'
import DescriptionMarkdown from '@/components/problems/DescriptionMarkdown.vue'
import { formatDate } from '@/lib/format/date'
import { FileText as IconFileText } from '@lucide/vue'

import DetailField from '@ulticode/design-system/DetailField.vue'
import StatusBadge from '@ulticode/design-system/StatusBadge.vue'

const props = defineProps<{
  solution: Solution
}>()

const { t } = useI18n()

const tags = computed<string[]>(() => {
  const raw = props.solution.tags
  if (!raw?.trim()) return []
  try {
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) && parsed.every((tag) => typeof tag === 'string') ? parsed : [raw]
  } catch {
    return [raw]
  }
})

const solutionContent = computed(() => ({
  content: props.solution.content,
  examples: [],
  constraints: [],
  followUp: undefined,
}))
</script>

<template>
  <div class="space-y-4">
    <!-- Main content card -->
    <div class="border border-border bg-card">
      <div class="border-b border-border px-4 py-2 bg-muted">
        <span class="text-sm text-muted-foreground">{{ t('solutions.tabs.description') }}</span>
      </div>
      <div class="p-6">
        <div class="flex flex-col gap-4 mb-6">
          <div class="space-y-1">
            <h1 class="text-2xl font-bold tracking-tight text-foreground">
              {{ solution.title }}
            </h1>
            <div class="flex items-center gap-2 text-muted-foreground text-sm font-mono">
              <IconFileText class="h-4 w-4" />
              <span>{{
                t('solutions.detail.solutionFor', { problem: solution.problem?.title })
              }}</span>
            </div>
          </div>
        </div>

        <div v-if="solution.content" class="typeset typeset-docs max-w-none">
          <DescriptionMarkdown :description="solutionContent" />
        </div>
        <div v-else class="text-center py-12 text-muted-foreground italic font-mono">
          {{ t('solutions.detail.noDescriptionContent') }}
        </div>

        <div v-if="solution.summary" class="mt-8 p-4 bg-muted border border-border">
          <div class="border-b border-border px-4 py-2">
            <span class="text-sm text-muted-foreground">{{ t('solutions.detail.summary') }}</span>
          </div>
          <div class="p-4">
            <p class="text-sm text-foreground">{{ solution.summary }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Meta section with DetailField -->
    <div class="border border-border bg-card">
      <div class="border-b border-border px-4 py-2 bg-muted">
        <span class="text-sm text-muted-foreground">{{ t('solutions.detail.metadata') }}</span>
      </div>
      <div class="p-4 grid grid-cols-2 md:grid-cols-3 gap-4">
        <DetailField
          v-if="solution.author"
          :label="t('solutions.detail.author')"
          :value="`${solution.author.username} (${solution.author.name})`"
        />
        <DetailField
          :label="t('solutions.detail.problemDifficulty')"
          :value="solution.problem?.difficulty?.toLowerCase() || 'unknown'"
        />
        <DetailField
          :label="t('solutions.detail.views')"
          :value="solution.views.toLocaleString()"
        />
        <DetailField :label="t('solutions.detail.language')" :value="solution.language" />
        <DetailField
          :label="t('solutions.detail.created')"
          :value="formatDate(solution.createdAt)"
        />
        <DetailField
          :label="t('solutions.detail.updated')"
          :value="formatDate(solution.updatedAt)"
        />
      </div>
    </div>

    <!-- Tags section -->
    <div v-if="tags.length" class="border border-border bg-card">
      <div class="border-b border-border px-4 py-2 bg-muted">
        <span class="text-sm text-muted-foreground">{{ t('solutions.detail.tags') }}</span>
      </div>
      <div class="p-4 flex flex-wrap gap-2">
        <StatusBadge
          v-for="(tag, index) in tags"
          :key="index"
          variant="default"
          :label="tag"
          class="max-w-full whitespace-normal break-all text-xs px-1.5 min-h-5"
        />
      </div>
    </div>
  </div>
</template>
