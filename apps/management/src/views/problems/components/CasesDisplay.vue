<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Badge } from '@/components/ui/badge'
import { IconFlask, IconCode } from '@tabler/icons-vue'

interface ProblemExample {
  id: string
  input: string
  output: string
  explanation?: string
  order: number
}

interface ProblemDetail {
  detail?: {
    constraintsJson?: string[]
    hints?: string[]
  }
  examples?: ProblemExample[]
  tags?: Array<{ id: string; label: string }>
}

const props = defineProps<{
  problem: ProblemDetail
}>()

const { t } = useI18n()

const examples = computed(() => {
  return (props.problem.examples || []).sort((a, b) => a.order - b.order)
})

const constraints = computed(() => {
  return props.problem.detail?.constraintsJson || []
})

const hints = computed(() => {
  const hints = props.problem.detail?.hints
  if (!hints || hints.length === 0) return []
  if (typeof hints[0] === 'string') return hints
  const joined = hints.join('\n')
  return joined.split('\n').filter((h) => h.trim())
})

const tags = computed(() => {
  return props.problem.tags || []
})

const hasExamples = computed(() => examples.value.length > 0)
const hasConstraints = computed(() => constraints.value.length > 0)
const hasHints = computed(() => hints.value.length > 0)
const hasTags = computed(() => tags.value.length > 0)

const hasAnyContent = computed(
  () => hasExamples.value || hasConstraints.value || hasHints.value || hasTags.value,
)
</script>

<template>
  <!-- Empty State -->
  <div
    v-if="!hasAnyContent"
    class="flex flex-col items-center justify-center py-16 px-4 text-center"
  >
    <div class="w-16 h-16 rounded-none bg-muted flex items-center justify-center mb-4">
      <IconCode class="h-8 w-8 text-muted-foreground" />
    </div>
    <h3 class="text-base font-semibold mb-2">{{ t('problems.casesDisplay.noCases') }}</h3>
    <p class="text-sm text-muted-foreground max-w-sm">
      {{ t('problems.casesDisplay.noCasesDescription') }}
    </p>
  </div>

  <!-- Content Grid -->
  <div v-else class="grid grid-cols-1 lg:grid-cols-12 gap-5">
    <!-- Test Cases -->
    <section
      v-if="hasExamples"
      class="lg:col-span-7 rounded-none border border-border-control bg-card overflow-hidden"
    >
      <div class="flex items-center gap-2 p-4 border-b border-border-control bg-muted/20">
        <IconFlask class="h-4 w-4 text-muted-foreground" />
        <h3 class="font-semibold text-sm">{{ t('problems.casesDisplay.examples') }}</h3>
        <Badge variant="secondary" class="ml-auto text-xs">{{ examples.length }}</Badge>
      </div>

      <div class="divide-y divide-[var(--border-subtle)] dark:divide-[var(--border-subtle)]">
        <div
          v-for="(example, index) in examples"
          :key="example.id"
          class="p-4"
          :class="index > 0 ? 'bg-muted/10' : ''"
        >
          <div class="flex items-center gap-2 mb-3">
            <Badge variant="outline" class="font-data text-xs">
              {{ index + 1 }}
            </Badge>
          </div>

          <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
            <!-- Input -->
            <div class="space-y-1.5">
              <p class="text-xs font-medium text-muted-foreground">
                {{ t('problems.casesDisplay.input') }}
              </p>
              <pre
                class="text-sm font-data p-3 rounded-none bg-[var(--surface-sunken)] text-[var(--foreground)] overflow-x-auto border border-border-control"
                >{{ example.input }}</pre
              >
            </div>

            <!-- Output -->
            <div class="space-y-1.5">
              <p class="text-xs font-medium text-muted-foreground">
                {{ t('problems.casesDisplay.output') }}
              </p>
              <pre
                class="text-sm font-data p-3 rounded-none bg-[var(--surface-sunken)] text-[var(--foreground)] overflow-x-auto border border-border-control"
                >{{ example.output }}</pre
              >
            </div>
          </div>

          <!-- Explanation -->
          <p v-if="example.explanation" class="text-sm text-muted-foreground mt-3 pl-1">
            <span class="font-medium">{{ t('problems.casesDisplay.explanation') }}:</span>
            {{ example.explanation }}
          </p>
        </div>
      </div>
    </section>
  </div>
</template>
