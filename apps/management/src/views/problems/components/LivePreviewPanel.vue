<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import DescriptionMarkdown, {
  type ProblemDescription,
} from '@/components/problems/DescriptionMarkdown.vue'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@ulticode/design-system/ui/collapsible'
import { Badge } from '@ulticode/design-system/ui/badge'
import { Separator } from '@ulticode/design-system/ui/separator'
import SemanticBadge from '@ulticode/badge-config/SemanticBadge.vue'
import { DIFFICULTY_COLOR_MAP } from '@ulticode/badge-config'
import type { ProblemDescriptionFormData } from '@/lib/schemas/problemDescription'
import { ChevronDown as IconChevronDown, Lock as IconLock } from '@lucide/vue'

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
  <div class="live-preview-panel flex flex-col h-full bg-background border-l border-border">
    <!-- Header -->
    <div class="shrink-0 border-b border-border bg-card px-5 py-4">
      <div class="space-y-1 mb-3">
        <h2 class="text-base font-bold tracking-tight text-foreground font-sans">
          {{ data.title || t('problems.preview.untitled') }}
        </h2>
        <p class="text-xs font-mono text-muted-foreground">
          {{ data.slug || '—' }}
        </p>
      </div>

      <div class="flex flex-wrap items-center gap-2">
        <SemanticBadge
          :color="difficultyColor"
          :label="t(`problems.difficulty.${normalizedDifficulty}`, normalizedDifficulty)"
          size="sm"
        />
        <Badge v-if="data.isPremium" variant="outline" class="px-2 py-0.5">
          <IconLock class="h-3 w-3 mr-1 shrink-0" />
          {{ t('problems.badges.premium') }}
        </Badge>
        <Badge :variant="data.isPublished ? 'default' : 'outline'" class="px-2 py-0.5">
          {{ data.isPublished ? t('problems.published.published') : t('problems.published.draft') }}
        </Badge>
      </div>
    </div>

    <!-- Scrollable Content -->
    <div class="flex-1 overflow-y-auto px-5 py-5 space-y-4">
      <!-- Summary -->
      <div v-if="data.summary" class="border-l-2 border-border pl-3">
        <p class="text-xs text-muted-foreground font-mono leading-relaxed">
          {{ data.summary }}
        </p>
      </div>

      <!-- Markdown Content -->
      <DescriptionMarkdown :description="problemDescription" />

      <!-- Hints (Collapsible) -->
      <template v-if="hasHints">
        <Separator class="my-4" />

        <Collapsible class="overflow-hidden">
          <CollapsibleTrigger class="w-full px-4 py-2 text-left">
            <div class="flex items-center justify-between group cursor-pointer">
              <span
                class="text-xs font-mono font-bold uppercase tracking-wider text-foreground flex items-center gap-2"
              >
                {{ t('problems.display.hints') }}
                <Badge variant="secondary" class="px-1.5 py-0">
                  {{ data.hints.length }}
                </Badge>
              </span>
              <IconChevronDown
                class="h-3.5 w-3.5 text-muted-foreground transition-transform duration-200 group-data-[state=open]:rotate-180"
              />
            </div>
          </CollapsibleTrigger>

          <CollapsibleContent>
            <ul class="divide-y divide-[var(--border)]">
              <li
                v-for="(hint, index) in data.hints"
                :key="index"
                class="text-xs text-muted-foreground p-3 bg-card flex items-start gap-2.5"
              >
                <span
                  class="font-mono text-xs font-bold text-foreground bg-muted/25 border border-border px-1.5 shrink-0 h-5 flex items-center justify-center rounded-lg"
                >
                  {{ index + 1 }}
                </span>
                <span class="leading-relaxed font-sans text-xs text-foreground/80">{{ hint }}</span>
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
