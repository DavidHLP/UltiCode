<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTimeByLocale } from '@/i18n/utils'
import { IconCalculator } from '@tabler/icons-vue'
import { badge, DIFFICULTY_COLOR_MAP, CONTEST_TYPE_COLOR_MAP } from '@/components/ui/terminal'
import { scoringRulesApi, type ScoringRule } from '@/api/admin/scoring-rules'

const props = defineProps<{
  formData: {
    title: string
    slug: string
    contestType: string
    scoringRuleId?: string
    startTime: string
    duration: number
    isPublished: boolean
    selectedProblems?: {
      id: string
      title: string
      difficulty: string
      score?: number
    }[]
    [key: string]: unknown
  }
}>()

const { t } = useI18n()

const scoringRule = ref<ScoringRule | null>(null)
const loadingRule = ref(false)

// Fetch scoring rule details when scoring_rule_id changes
async function fetchScoringRule() {
  if (!props.formData.scoringRuleId) {
    scoringRule.value = null
    return
  }

  loadingRule.value = true
  try {
    scoringRule.value = await scoringRulesApi.getById(props.formData.scoringRuleId)
  } catch {
    scoringRule.value = null
  } finally {
    loadingRule.value = false
  }
}

watch(() => props.formData.scoringRuleId, fetchScoringRule, { immediate: true })

const formattedDate = computed(() => {
  if (!props.formData.startTime) return t('contests.scheduleStep.notSet')
  return formatDateTimeByLocale(props.formData.startTime)
})

function renderDifficultyBadge(difficulty: string) {
  const color = DIFFICULTY_COLOR_MAP[difficulty] ?? 'neutral'
  return badge({ color, label: difficulty?.toUpperCase() || 'UNKNOWN', size: 'sm' })
}

function renderTypeBadge(type: string) {
  const color = CONTEST_TYPE_COLOR_MAP[type] ?? 'neutral'
  return badge({ color, label: type || 'UNKNOWN', size: 'sm' })
}
</script>

<template>
  <div class="space-y-6">
    <!-- Section Header -->
    <div class="flex items-center gap-2 mb-4">
      <span class="terminal-cursor" />
    </div>

    <div class="grid gap-4 md:grid-cols-3">
      <!-- Basic Info Card - Terminal Style -->
      <div
        class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--card)]"
      >
        <div
          class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">{{ t('contests.reviewStep.basicInfo') }}</span>
        </div>
        <div class="p-4 space-y-3">
          <div class="border-b border-[var(--silver-100)] dark:border-[var(--silver-800)] pb-2">
            <span class="terminal-label">{{ t('contests.basics.title') }}</span>
            <p class="font-medium text-sm text-[var(--foreground)]">{{ formData.title }}</p>
          </div>
          <div class="border-b border-[var(--silver-100)] dark:border-[var(--silver-800)] pb-2">
            <span class="terminal-label">{{ t('contests.basics.slug') }}</span>
            <p class="font-data text-sm text-[var(--terminal-cyan)]">{{ formData.slug }}</p>
          </div>
          <div>
            <span class="terminal-label">{{ t('contests.basics.type') }}</span>
            <p>
              <component :is="renderTypeBadge(formData.contestType)" />
            </p>
          </div>
        </div>
      </div>

      <!-- Schedule Card - Terminal Style -->
      <div
        class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--card)]"
      >
        <div
          class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">{{ t('contests.reviewStep.schedule') }}</span>
        </div>
        <div class="p-4 space-y-3">
          <div class="border-b border-[var(--silver-100)] dark:border-[var(--silver-800)] pb-2">
            <span class="terminal-label">{{ t('contests.reviewStep.startTime') }}</span>
            <p class="font-data text-sm tabular-nums text-[var(--foreground)]">
              {{ formattedDate }}
            </p>
          </div>
          <div class="border-b border-[var(--silver-100)] dark:border-[var(--silver-800)] pb-2">
            <span class="terminal-label">{{ t('contests.reviewStep.duration') }}</span>
            <p class="font-data text-sm tabular-nums text-[var(--foreground)]">
              {{ formData.duration }} {{ t('common.minutes') }}
            </p>
          </div>
          <div>
            <span class="terminal-label">{{ t('contests.reviewStep.visibility') }}</span>
            <p>
              <component
                :is="
                  formData.isPublished
                    ? badge({ color: 'success', label: 'PUBLISHED', size: 'sm' })
                    : badge({ color: 'neutral', label: 'DRAFT', size: 'sm' })
                "
              />
            </p>
          </div>
        </div>
      </div>

      <!-- Scoring Rule Card - Terminal Style -->
      <div
        class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--card)]"
      >
        <div
          class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <span class="terminal-comment">{{ t('contests.scoringRule.selectRule') }}</span>
        </div>
        <div class="p-4 space-y-3">
          <div v-if="loadingRule" class="py-2">
            <span class="terminal-comment text-xs">{{ t('common.loading') }}</span>
          </div>
          <div v-else-if="scoringRule" class="space-y-3">
            <div class="flex items-center gap-2">
              <IconCalculator class="h-4 w-4 text-[var(--accent-electric)]" />
              <span class="font-medium text-sm text-[var(--foreground)]">{{
                scoringRule.name
              }}</span>
              <span v-if="scoringRule.isDefault">
                <component
                  :is="
                    badge({ color: 'success', label: t('scoringRules.badges.default'), size: 'sm' })
                  "
                />
              </span>
            </div>
            <div class="grid grid-cols-2 gap-2 text-xs">
              <div>
                <span class="terminal-label text-2xs">{{
                  t('scoringRules.form.baseScorePerProblem')
                }}</span>
                <p class="font-data text-[var(--terminal-cyan)] tabular-nums">
                  {{ scoringRule.baseScorePerProblem }}
                </p>
              </div>
              <div>
                <span class="terminal-label text-2xs">{{
                  t('scoringRules.form.wrongAnswerPenalty')
                }}</span>
                <p class="font-data text-[var(--terminal-red)] tabular-nums">
                  -{{ scoringRule.wrongAnswerPenalty }}
                </p>
              </div>
            </div>
          </div>
          <div v-else class="py-2">
            <span class="terminal-comment text-xs">{{
              t('contests.reviewStep.defaultScoringRule')
            }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Problems Card - Terminal Style -->
    <div class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--card)]">
      <div
        class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] px-4 py-2 bg-[var(--surface-sunken)]"
      >
        <span class="terminal-comment">
          {{
            t('contests.reviewStep.problemsCount', {
              count: formData.selectedProblems?.length || 0,
            })
          }}
        </span>
      </div>
      <div class="p-4">
        <div class="space-y-2">
          <div
            v-for="(problem, index) in formData.selectedProblems || []"
            :key="problem.id"
            class="flex items-center justify-between border-b border-[var(--silver-100)] dark:border-[var(--silver-800)] py-2 last:border-0"
          >
            <div class="flex items-center gap-3">
              <span class="font-data text-xs text-[var(--accent-electric)] w-6">
                {{ String.fromCharCode(65 + index) }}
              </span>
              <span class="font-medium text-sm text-[var(--foreground)]">{{ problem.title }}</span>
              <component :is="renderDifficultyBadge(problem.difficulty)" />
            </div>
            <span
              class="font-data text-xs text-[var(--terminal-cyan)] tabular-nums w-16 text-right"
            >
              {{ problem.score }} {{ t('contests.drawer.pts') }}
            </span>
          </div>
          <div v-if="!formData.selectedProblems?.length" class="py-4 text-center">
            <span class="terminal-comment">{{ t('contests.reviewStep.noProblemsSelected') }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
