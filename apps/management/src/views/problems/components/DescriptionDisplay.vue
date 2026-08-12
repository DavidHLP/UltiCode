<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Badge } from '@/components/ui/badge'
import { IconBulb, IconCalendar, IconHash } from '@tabler/icons-vue'
import DescriptionMarkdown, {
  type ProblemDescription,
} from '@/components/problems/DescriptionMarkdown.vue'
import { Separator } from '@/components/ui/separator'
import { formatDate } from '@/lib/format/date'
import ContentWithSidebarLayout from '@/components/shared/ContentWithSidebarLayout.vue'
import MetadataCard, { type MetadataItem } from '@/components/shared/MetadataCard.vue'
import TagsCard from '@/components/shared/TagsCard.vue'

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
  isPremium: boolean
  isPublished: boolean
  createdAt: string | Date
  updatedAt: string | Date
  publishedAt?: string | Date
  detail?: {
    summary?: string
    constraintsJson?: string[]
    hints?: string[]
  }
  tags: Array<{ id: string; label: string }>
  examples?: ProblemExample[]
}

const props = defineProps<{
  problem: ProblemDetail
}>()

const { t } = useI18n()

const difficultyClass = computed(() => {
  const difficulty = props.problem.difficulty?.toLowerCase() || ''
  if (difficulty === 'easy') return 'text-foreground-strong bg-status-success-surface border-status-success-mark'
  if (difficulty === 'medium') return 'text-foreground-strong bg-status-warning-surface border-status-warning-mark'
  if (difficulty === 'hard') return 'text-foreground-strong bg-status-error-surface border-status-error-mark'
  return 'text-foreground bg-muted'
})

const hasHints = computed(() => {
  return props.problem.detail?.hints?.length || props.problem.detail?.hints?.join('\n')
})

const hintsList = computed(() => {
  const hints = props.problem.detail?.hints
  if (!hints || hints.length === 0) return []
  if (typeof hints[0] === 'string') return hints
  const joined = hints.join('\n')
  return joined.split('\n').filter((h) => h.trim())
})

const metadataItems = computed<MetadataItem[]>(() => [
  { label: t('problems.display.id'), value: props.problem.id?.slice(0, 8) ?? '-', icon: IconHash },
  {
    label: t('problems.display.created'),
    value: formatDate(props.problem.createdAt),
    icon: IconCalendar,
  },
  {
    label: t('problems.display.updated'),
    value: formatDate(props.problem.updatedAt),
    icon: IconCalendar,
  },
  ...(props.problem.publishedAt
    ? [
        {
          label: t('problems.display.published'),
          value: formatDate(props.problem.publishedAt),
          icon: IconCalendar,
        },
      ]
    : []),
])

const problemDescription = computed<ProblemDescription>(() => ({
  content: props.problem.detail?.summary || '',
  examples: (props.problem.examples || [])
    .sort((a, b) => a.order - b.order)
    .map((example) => ({
      input: example.input,
      output: example.output,
      explanation: example.explanation,
    })),
  constraints: props.problem.detail?.constraintsJson || [],
  followUp: props.problem.detail?.hints?.join('\n'),
}))
</script>

<template>
  <ContentWithSidebarLayout>
    <template #main-content>
      <div class="rounded-none border bg-card p-6 shadow-sm">
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
              {{
                t(
                  `problems.difficulty.${(problem.difficulty || 'UNKNOWN').toUpperCase()}`,
                  problem.difficulty || 'UNKNOWN',
                )
              }}
            </Badge>
            <Badge
              v-if="problem.isPremium"
              variant="secondary"
              class="bg-status-warning-surface text-foreground-strong hover:bg-status-warning-surface border-status-warning-mark border"
            >
              {{ t('problems.badges.premium') }}
            </Badge>
            <Badge :variant="problem.isPublished ? 'default' : 'outline'" class="capitalize">
              {{
                problem.isPublished
                  ? t('problems.published.published')
                  : t('problems.published.draft')
              }}
            </Badge>
          </div>
        </div>

        <Separator class="mb-6" />

        <div class="prose prose-sm dark:prose-invert max-w-none">
          <DescriptionMarkdown :description="problemDescription" />
        </div>
      </div>
    </template>

    <template #sidebar>
      <MetadataCard :title="t('problems.display.metadata')" :metadata="metadataItems" />

      <TagsCard
        v-if="problem.tags?.length"
        :title="t('problems.display.tags')"
        :tags="problem.tags"
      />

      <div v-if="hasHints" class="rounded-none border bg-card overflow-hidden shadow-sm">
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
              class="text-sm text-muted-foreground p-3 rounded-none bg-muted/30 flex items-start gap-2.5"
            >
              <span
                class="font-mono text-xs font-medium text-foreground/70 bg-background border px-1.5 rounded-none shrink-0 h-5 flex items-center justify-center"
              >
                {{ index + 1 }}
              </span>
              <span class="leading-snug">{{ hint }}</span>
            </li>
          </ul>
        </div>
      </div>
    </template>
  </ContentWithSidebarLayout>
</template>
