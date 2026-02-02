<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Separator } from '@/components/ui/separator'
import {
  IconCalendar,
  IconUser,
  IconEye,
  IconTag,
  IconFileText,
} from '@tabler/icons-vue'
import type { Solution } from '@/api/admin/solutions'
import DescriptionMarkdown from '@/components/problems/DescriptionMarkdown.vue'
import { formatDate } from '@/lib/format/date'
import ContentWithSidebarLayout from '@/components/shared/ContentWithSidebarLayout.vue'
import MetadataCard, { type MetadataItem } from '@/components/shared/MetadataCard.vue'
import TagsCard from '@/components/shared/TagsCard.vue'

const props = defineProps<{
  solution: Solution
}>()

const { t } = useI18n()

const metadataItems = computed<MetadataItem[]>(() => [
  {
    label: t('solutions.detail.author'),
    value: `${props.solution.author.username} (${props.solution.author.name})`,
    icon: IconUser,
  },
  {
    label: t('solutions.detail.problemDifficulty'),
    value: props.solution.problem?.difficulty.toLowerCase() || 'unknown',
  },
  {
    label: t('solutions.detail.views'),
    value: props.solution.views.toLocaleString(),
    icon: IconEye,
  },
  {
    label: t('solutions.detail.language'),
    value: props.solution.language,
    icon: IconTag,
  },
  { label: t('solutions.detail.created'), value: formatDate(props.solution.created_at), icon: IconCalendar },
  { label: t('solutions.detail.updated'), value: formatDate(props.solution.updated_at), icon: IconCalendar },
])

const solutionContent = computed(() => ({
  content: props.solution.content,
  examples: [],
  constraints: [],
  followUp: undefined,
}))
</script>

<template>
  <ContentWithSidebarLayout>
    <template #main-content>
      <div class="rounded-xl border bg-card p-6 shadow-sm">
        <div class="flex flex-col gap-4 mb-6">
          <div class="space-y-1">
            <h1 class="text-2xl font-bold tracking-tight">
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

        <Separator class="mb-6" />

        <div v-if="solution.content" class="prose prose-sm dark:prose-invert max-w-none">
          <DescriptionMarkdown :description="solutionContent" />
        </div>
        <div v-else class="text-center py-12 text-muted-foreground italic">
          {{ t('solutions.detail.noDescriptionContent') }}
        </div>

        <div v-if="solution.summary" class="mt-8 p-4 bg-muted/30 rounded-lg">
          <h3 class="font-semibold text-sm mb-2">{{ t('solutions.detail.summary') }}</h3>
          <p class="text-sm text-muted-foreground">{{ solution.summary }}</p>
        </div>
      </div>
    </template>

    <template #sidebar>
      <MetadataCard :title="t('solutions.detail.metadata')" :metadata="metadataItems" />

      <TagsCard
        v-if="solution.tags?.length"
        :title="t('solutions.detail.tags')"
        :tags="solution.tags"
      />
    </template>
  </ContentWithSidebarLayout>
</template>
