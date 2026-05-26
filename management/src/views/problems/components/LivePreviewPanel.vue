<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import DescriptionMarkdown, {
  type ProblemDescription,
} from '@/components/problems/DescriptionMarkdown.vue'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Badge } from '@/components/ui/badge'
import { Separator } from '@/components/ui/separator'
import { SemanticBadge } from '@/components/ui/terminal'
import { DIFFICULTY_COLOR_MAP } from '@/components/ui/terminal'
import type { ProblemDescriptionFormData } from '@/lib/schemas/problemDescription'
import { IconChevronDown, IconLock } from '@tabler/icons-vue'

const props = defineProps<{
  /** Problem description form data to preview */
  data: ProblemDescriptionFormData
}>()

const { t } = useI18n()

/**
 * Map form data to ProblemDescription for the markdown renderer.
 * Hints are rendered separately as a collapsible section.
 */
const problemDescription = computed<ProblemDescription>(() => ({
  content: props.data.content,
  examples: props.data.examples,
  constraints: props.data.constraints,
}))

// Normalize difficulty to uppercase for lookup
const normalizedDifficulty = computed(() => {
  const d = props.data.difficulty
  if (!d) return 'EASY'
  return d.toUpperCase()
})

const difficultyColor = computed(
  () => DIFFICULTY_COLOR_MAP[normalizedDifficulty.value] ?? 'neutral',
)

const hasHints = computed(() => (props.data.hints?.length ?? 0) > 0)
</script>

<template>
  <div class="live-preview-panel flex flex-col h-full">
    <!-- Header -->
    <div class="shrink-0 border-b bg-card px-5 py-4">
      <div class="space-y-1 mb-3">
        <h2 class="text-lg font-semibold tracking-tight leading-tight">
          {{ data.title || t('problems.preview.untitled') }}
        </h2>
        <p class="text-xs text-muted-foreground font-mono">
          {{ data.slug || '—' }}
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <SemanticBadge
          :color="difficultyColor"
          :label="t(`problems.difficulty.${normalizedDifficulty}`)"
          size="sm"
        />
        <Badge
          v-if="data.isPremium"
          variant="outline"
          class="bg-amber-500/10 text-amber-600 border-amber-500/20 text-[10px] px-2 py-0.5"
        >
          <IconLock class="h-3 w-3 mr-1" />
          {{ t('problems.badges.premium') }}
        </Badge>
        <Badge
          :variant="data.isPublished ? 'default' : 'outline'"
          class="text-[10px] px-2 py-0.5 capitalize"
        >
          {{ data.isPublished ? t('problems.published.published') : t('problems.published.draft') }}
        </Badge>
      </div>
    </div>

    <!-- Scrollable Content -->
    <div class="flex-1 overflow-y-auto px-5 py-5">
      <!-- Summary -->
      <div v-if="data.summary" class="mb-4">
        <p class="text-sm text-muted-foreground leading-relaxed">
          {{ data.summary }}
        </p>
      </div>

      <!-- Markdown Content -->
      <DescriptionMarkdown :description="problemDescription" />

      <!-- Hints (Collapsible) -->
      <template v-if="hasHints">
        <Separator class="my-5" />

        <Collapsible>
          <CollapsibleTrigger class="w-full">
            <div class="flex items-center justify-between py-2 group">
              <span class="text-sm font-semibold">
                {{ t('problems.display.hints') }}
                <Badge variant="secondary" class="ml-2 text-[10px] px-1.5 py-0">
                  {{ data.hints.length }}
                </Badge>
              </span>
              <IconChevronDown
                class="h-4 w-4 text-muted-foreground transition-transform duration-200 group-data-[state=open]:rotate-180"
              />
            </div>
          </CollapsibleTrigger>

          <CollapsibleContent>
            <ul class="space-y-2 pt-2">
              <li
                v-for="(hint, index) in data.hints"
                :key="index"
                class="text-sm text-muted-foreground p-3 border bg-muted/30 flex items-start gap-2.5"
              >
                <span
                  class="font-mono text-xs font-medium text-foreground/70 bg-background border px-1.5 shrink-0 h-5 flex items-center justify-center"
                >
                  {{ index + 1 }}
                </span>
                <span class="leading-snug">{{ hint }}</span>
              </li>
            </ul>
          </CollapsibleContent>
        </Collapsible>
      </template>
    </div>
  </div>
</template>

<style scoped>
.live-preview-panel {
  background-color: var(--background);
  border-left: 1px solid var(--border);
}
</style>
