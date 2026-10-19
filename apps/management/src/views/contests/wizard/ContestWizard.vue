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
} from '@ulticode/design-system/ui/dialog'
import { Button } from '@ulticode/design-system/ui/button'
import {
  Stepper,
  StepperItem,
  StepperTrigger,
  StepperSeparator,
} from '@ulticode/design-system/ui/stepper'
import { toast } from 'vue-sonner'
import {
  Loader as IconLoader,
  ArrowLeft as IconArrowLeft,
  ArrowRight as IconArrowRight,
  Check as IconCheck,
} from '@lucide/vue'

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
    <DialogContent class="w-[calc(100%-2rem)] sm:max-w-3xl p-0 gap-0">
      <!-- Header - Terminal Style -->
      <DialogHeader class="px-6 py-4">
        <div class="flex items-center gap-3">
          <DialogTitle>
            {{ t('contests.wizard.createContest') }}
          </DialogTitle>
        </div>
        <DialogDescription>
          {{ t('contests.wizard.description') }}
        </DialogDescription>
      </DialogHeader>

      <div class="px-6 py-4">
        <!-- Stepper Header - Terminal Style -->
        <div class="mb-8">
          <Stepper
            v-model="currentStep"
            class="grid w-full grid-cols-2 items-start gap-x-4 gap-y-3 sm:flex sm:gap-2"
          >
            <StepperItem
              v-for="step in steps"
              :key="step.step"
              :step="step.step"
              class="relative flex min-w-0 flex-col items-center justify-center gap-2 sm:flex-1"
            >
              <StepperTrigger
                :class="[
                  'h-8 w-8 border-2 text-xs font-mono font-semibold flex items-center justify-center',
                  'border-border text-muted-foreground',
                  'data-[state=active]:border-primary data-[state=active]:text-primary data-[state=active]:bg-[color-mix(in_oklch,_var(--primary)_10%,_transparent)]',
                  'data-[state=completed]:border-status-success-mark data-[state=completed]:text-foreground data-[state=completed]:bg-[color-mix(in_oklch,_var(--status-success-mark)_10%,_transparent)]',
                ]"
              >
                {{ step.step }}
              </StepperTrigger>
              <span
                :class="[
                  'text-xs font-mono uppercase tracking-wider',
                  currentStep === step.step ? 'text-primary' : 'text-muted-foreground',
                ]"
              >
                {{ t(`contests.wizard.${step.title.toLowerCase()}`, step.title) }}
              </span>
              <StepperSeparator
                v-if="step.step !== steps.length"
                class="absolute left-[calc(50%+20px)] top-4 hidden w-[calc(100%-40px)] h-0.5 sm:block"
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
          <StepSchedule v-show="currentStep === 3" :slice="scheduleSlice" @patch="patchSchedule" />
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
      <DialogFooter class="mx-0 mb-0 px-6 py-4">
        <Button
          type="button"
          variant="outline"
          size="sm"
          @click="prevStep"
          :disabled="currentStep === 1 || submitting"
        >
          <IconArrowLeft class="mr-1.5 h-3.5 w-3.5" />
          {{ t('contests.wizard.previous') }}
        </Button>
        <Button
          v-if="currentStep < steps.length"
          type="button"
          variant="outline"
          size="sm"
          @click="nextStep"
          :disabled="!isStepValid"
        >
          {{ t('contests.wizard.next') }}
          <IconArrowRight class="ml-1.5 h-3.5 w-3.5" />
        </Button>
        <Button
          v-else
          type="button"
          variant="outline"
          size="sm"
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
