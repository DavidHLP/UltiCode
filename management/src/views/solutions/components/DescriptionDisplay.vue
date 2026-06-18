<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { Solution } from '@/api/admin/solutions'
import DescriptionMarkdown from '@/components/problems/DescriptionMarkdown.vue'
import { formatDate } from '@/lib/format/date'
import { IconFileText } from '@tabler/icons-vue'
import DataBlock from '@/components/ui/terminal/DataBlock.vue'
import TerminalBadge from '@/components/ui/terminal/TerminalBadge.vue'

const props = defineProps<{
  solution: Solution
}>()

const { t } = useI18n()

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
    <div class="border border-[var(--silver-200)] bg-[var(--card)]">
      <div class="border-b border-[var(--silver-200)] px-4 py-2 bg-[var(--surface-sunken)]">
        <span class="terminal-comment">description</span>
      </div>
      <div class="p-6">
        <div class="flex flex-col gap-4 mb-6">
          <div class="space-y-1">
            <h1 class="text-2xl font-bold tracking-tight text-[var(--foreground)]">
              {{ solution.title }}
            </h1>
            <div class="flex items-center gap-2 text-[var(--silver-400)] text-sm font-data">
              <IconFileText class="h-4 w-4" />
              <span>{{
                t('solutions.detail.solutionFor', { problem: solution.problem?.title })
              }}</span>
            </div>
          </div>
        </div>

        <div v-if="solution.content" class="prose prose-sm dark:prose-invert max-w-none">
          <DescriptionMarkdown :description="solutionContent" />
        </div>
        <div v-else class="text-center py-12 text-[var(--silver-400)] italic font-data">
          {{ t('solutions.detail.noDescriptionContent') }}
        </div>

        <div
          v-if="solution.summary"
          class="mt-8 p-4 bg-[var(--surface-sunken)] border border-[var(--silver-200)]"
        >
          <div class="border-b border-[var(--silver-200)] px-4 py-2">
            <span class="terminal-comment">// summary</span>
          </div>
          <div class="p-4">
            <p class="text-sm text-[var(--silver-600)]">{{ solution.summary }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Meta section with DataBlock -->
    <div class="border border-[var(--silver-200)] bg-[var(--card)]">
      <div class="border-b border-[var(--silver-200)] px-4 py-2 bg-[var(--surface-sunken)]">
        <span class="terminal-comment">metadata</span>
      </div>
      <div class="p-4 grid grid-cols-2 md:grid-cols-3 gap-4">
        <DataBlock
          v-if="solution.author"
          :label="t('solutions.detail.author')"
          :value="`${solution.author.username} (${solution.author.name})`"
        />
        <DataBlock
          :label="t('solutions.detail.problemDifficulty')"
          :value="solution.problem?.difficulty?.toLowerCase() || 'unknown'"
        />
        <DataBlock :label="t('solutions.detail.views')" :value="solution.views.toLocaleString()" />
        <DataBlock :label="t('solutions.detail.language')" :value="solution.language" />
        <DataBlock :label="t('solutions.detail.created')" :value="formatDate(solution.createdAt)" />
        <DataBlock :label="t('solutions.detail.updated')" :value="formatDate(solution.updatedAt)" />
      </div>
    </div>

    <!-- Tags section -->
    <div v-if="solution.tags?.length" class="border border-[var(--silver-200)] bg-[var(--card)]">
      <div class="border-b border-[var(--silver-200)] px-4 py-2 bg-[var(--surface-sunken)]">
        <span class="terminal-comment">tags</span>
      </div>
      <div class="p-4 flex flex-wrap gap-2">
        <TerminalBadge
          v-for="(tag, index) in solution.tags"
          :key="index"
          variant="default"
          :label="tag"
          class="text-2xs px-1.5 h-5"
        />
      </div>
    </div>
  </div>
</template>
