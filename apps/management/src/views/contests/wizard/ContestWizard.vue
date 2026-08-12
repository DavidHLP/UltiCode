<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Stepper, StepperItem, StepperTrigger, StepperSeparator } from '@/components/ui/stepper'
import { toast } from 'vue-sonner'
import { IconLoader, IconArrowLeft, IconArrowRight, IconCheck } from '@tabler/icons-vue'

import StepBasicInfo from './StepBasicInfo.vue'
import StepScoringRule from './StepScoringRule.vue'
import StepSchedule from './StepSchedule.vue'
import StepProblems from './StepProblems.vue'
import StepReview from './StepReview.vue'
import { useContestAuthoring } from './useContestAuthoring'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const { t } = useI18n()

// The authoring module owns the draft, per-step validators, actions, and
// submission orchestration. The shell is reduced to a stepper driver plus a
// thin submit handler that translates the composable's promise into the
// dialog's open/success events.
const {
  basicInfoSlice,
  scoringRuleSlice,
  scheduleSlice,
  problemsSlice,
  reviewSlice,
  basicInfoValid,
  scoringRuleValid,
  scheduleValid,
  problemsValid,
  reviewValid,
  submitting,
  patchBasicInfo,
  setScoringRuleId,
  patchSchedule,
  addProblem,
  removeProblem,
  setProblemScore,
  reset,
  submit,
} = useContestAuthoring()

const currentStep = ref(1)

const steps = [
  { step: 1, title: 'Basics' },
  { step: 2, title: 'Scoring' },
  { step: 3, title: 'Schedule' },
  { step: 4, title: 'Problems' },
  { step: 5, title: 'Review' },
] as const

const isStepValid = computed<boolean>(() => {
  switch (currentStep.value) {
    case 1:
      return basicInfoValid.value
    case 2:
      return scoringRuleValid.value
    case 3:
      return scheduleValid.value
    case 4:
      return problemsValid.value
    case 5:
      return reviewValid.value
    default:
      return false
  }
})

function nextStep(): void {
  if (currentStep.value < steps.length) currentStep.value++
}

function prevStep(): void {
  if (currentStep.value > 1) currentStep.value--
}

function handleScore(payload: { problemId: string; score: number }): void {
  setProblemScore(payload.problemId, payload.score)
}

async function handleSubmit(): Promise<void> {
  try {
    await submit()
    toast.success(t('contests.toast.createdSuccessfully'))
    emit('update:open', false)
    emit('success')
    reset()
    currentStep.value = 1
  } catch {
    toast.error(t('contests.toast.failedToCreate'))
  }
}
</script>

<template>
  <Dialog :open="props.open" @update:open="emit('update:open', $event)">
    <DialogContent
      class="max-w-3xl h-[80vh] flex flex-col p-0 gap-0 border-[var(--border-subtle)] dark:border-[var(--foreground-strong)]"
    >
      <!-- Header - Terminal Style -->
      <DialogHeader
        class="px-6 py-4 border-b border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--surface-sunken)]"
      >
        <div class="flex items-center gap-3">
          <DialogTitle class="font-data text-sm uppercase tracking-wider">
            {{ t('contests.wizard.createContest') }}
          </DialogTitle>
        </div>
        <DialogDescription class="text-muted-foreground text-sm">
          {{ t('contests.wizard.description') }}
        </DialogDescription>
      </DialogHeader>

      <div class="flex-1 overflow-y-auto px-6 py-4">
        <!-- Stepper Header - Terminal Style -->
        <div class="mb-8">
          <Stepper v-model="currentStep" class="flex w-full items-start gap-2">
            <StepperItem
              v-for="step in steps"
              :key="step.step"
              :step="step.step"
              class="relative flex flex-col items-center justify-center gap-2"
            >
              <StepperTrigger
                :class="[
                  'h-8 w-8 border-2 text-xs font-data font-semibold flex items-center justify-center',
                  'border-[var(--border-subtle)] text-[var(--foreground-muted)]',
                  'data-[state=active]:border-[var(--primary)] data-[state=active]:text-[var(--primary)] data-[state=active]:bg-[color-mix(in_oklch,_var(--primary)_10%,_transparent)]',
                  'data-[state=completed]:border-[var(--status-success-mark)] data-[state=completed]:text-foreground-strong data-[state=completed]:bg-[color-mix(in_oklch,_var(--status-success-mark)_10%,_transparent)]',
                ]"
              >
                {{ step.step }}
              </StepperTrigger>
              <span
                :class="[
                  'text-xs font-data uppercase tracking-wider',
                  currentStep === step.step
                    ? 'text-[var(--primary)]'
                    : 'text-[var(--foreground-muted)]',
                ]"
              >
                {{ t(`contests.wizard.${step.title.toLowerCase()}`, step.title) }}
              </span>
              <StepperSeparator
                v-if="step.step !== steps.length"
                class="absolute left-[calc(50%+20px)] top-4 w-[calc(100%-40px)] h-0.5 bg-[var(--border-subtle)] data-[state=completed]:bg-[var(--status-success-mark)]"
              />
            </StepperItem>
          </Stepper>
        </div>

        <!--
          Step Content. `v-show` keeps every step mounted so the scoring-rule
          selector's on-mount default-pick behavior fires exactly once when
          the dialog opens (preserving the pre-refactor policy).
        -->
        <div class="mt-4">
          <StepBasicInfo
            v-show="currentStep === 1"
            :slice="basicInfoSlice"
            @patch="patchBasicInfo"
          />
          <StepScoringRule
            v-show="currentStep === 2"
            :slice="scoringRuleSlice"
            @select="setScoringRuleId"
          />
          <StepSchedule
            v-show="currentStep === 3"
            :slice="scheduleSlice"
            @patch="patchSchedule"
          />
          <StepProblems
            v-show="currentStep === 4"
            :slice="problemsSlice"
            @add="addProblem"
            @remove="removeProblem"
            @score="handleScore"
          />
          <StepReview v-show="currentStep === 5" :slice="reviewSlice" />
        </div>
      </div>

      <!-- Footer - Terminal Style -->
      <DialogFooter
        class="px-6 py-4 border-t border-[var(--border-subtle)] dark:border-[var(--foreground-strong)] bg-[var(--surface-sunken)]"
      >
        <Button
          type="button"
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--border-subtle)]"
          @click="prevStep"
          :disabled="currentStep === 1 || submitting"
        >
          <IconArrowLeft class="mr-1.5 h-3.5 w-3.5" />
          {{ t('contests.wizard.previous') }}
        </Button>
        <Button
          v-if="currentStep < steps.length"
          type="button"
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--primary)] text-[var(--primary)] hover:bg-[color-mix(in_oklch,_var(--primary)_10%,_transparent)]"
          @click="nextStep"
          :disabled="!isStepValid"
        >
          {{ t('contests.wizard.next') }}
          <IconArrowRight class="ml-1.5 h-3.5 w-3.5" />
        </Button>
        <Button
          v-else
          type="button"
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--status-success-mark)] text-foreground-strong hover:bg-[color-mix(in_oklch,_var(--status-success-mark)_10%,_transparent)]"
          @click="handleSubmit"
          :disabled="!isStepValid || submitting"
        >
          <IconLoader v-if="submitting" class="mr-1.5 h-3.5 w-3.5 animate-spin" />
          <IconCheck v-else class="mr-1.5 h-3.5 w-3.5" />
          {{ t('contests.wizard.submit') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
