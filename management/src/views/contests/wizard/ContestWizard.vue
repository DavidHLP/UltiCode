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
import { useContestsStore } from '@/stores/admin/contests'
import { ContestType } from '@/api/admin/contests'

import StepBasicInfo from './StepBasicInfo.vue'
import StepScoringRule from './StepScoringRule.vue'
import StepSchedule from './StepSchedule.vue'
import StepProblems from './StepProblems.vue'
import StepReview from './StepReview.vue'

const props = defineProps<{
  open: boolean
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'success'): void
}>()

const contestsStore = useContestsStore()
const { t } = useI18n()
const currentStep = ref(1)
const loading = ref(false)

const steps = [
  { step: 1, title: 'Basics' },
  { step: 2, title: 'Scoring' },
  { step: 3, title: 'Schedule' },
  { step: 4, title: 'Problems' },
  { step: 5, title: 'Review' },
] as const

const formData = ref({
  title: '',
  slug: '',
  description: '',
  contestType: ContestType.ICPC,
  scoringRuleId: '',
  startTime: '',
  duration: 120,
  isPublished: false,
  selectedProblems: [] as {
    id: string
    title: string
    difficulty: string
    slug: string
    score?: number
  }[],
})

const isStepValid = computed(() => {
  switch (currentStep.value) {
    case 1:
      return !!formData.value.title.trim()
    case 2:
      return true // Scoring rule selection is optional (will use default)
    case 3:
      return !!formData.value.startTime && formData.value.duration > 0
    case 4:
      return true
    case 5:
      return true
    default:
      return false
  }
})

function nextStep() {
  if (currentStep.value < steps.length) {
    currentStep.value++
  }
}

function prevStep() {
  if (currentStep.value > 1) {
    currentStep.value--
  }
}

/**
 * Convert datetime-local format (YYYY-MM-DDTHH:MM) to ISO 8601 format
 * Returns null if the date is invalid
 */
function toISO8601(datetimeLocal: string): string | null {
  if (!datetimeLocal) return null

  // Parse the datetime-local format
  const date = new Date(datetimeLocal)

  // Check if the date is valid
  if (isNaN(date.getTime())) {
    return null
  }

  return date.toISOString()
}

async function handleSubmit() {
  loading.value = true
  try {
    // Convert startTime to ISO 8601 format
    const startTimeISO = toISO8601(formData.value.startTime)
    if (!startTimeISO) {
      toast.error(t('contests.toast.invalidStartTime'))
      loading.value = false
      return
    }

    await contestsStore.createContest({
      title: formData.value.title,
      description: formData.value.description,
      contestType: formData.value.contestType,
      startTime: startTimeISO,
      duration: formData.value.duration,
      isPublished: formData.value.isPublished,
      problemIds: formData.value.selectedProblems.map((p) => Number(p.id)),
      scoringRuleId: formData.value.scoringRuleId || undefined,
    })

    toast.success(t('contests.toast.createdSuccessfully'))
    emit('update:open', false)
    emit('success')

    // Reset form
    formData.value = {
      title: '',
      slug: '',
      description: '',
      contestType: ContestType.ICPC,
      scoringRuleId: '',
      startTime: '',
      duration: 120,
      isPublished: false,
      selectedProblems: [],
    }
    currentStep.value = 1
  } catch {
    toast.error(t('contests.toast.failedToCreate'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Dialog :open="props.open" @update:open="emit('update:open', $event)">
    <DialogContent
      class="max-w-3xl h-[80vh] flex flex-col p-0 gap-0 border-[var(--silver-200)] dark:border-[var(--silver-700)]"
    >
      <!-- Header - Terminal Style -->
      <DialogHeader
        class="px-6 py-4 border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--surface-sunken)]"
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
                  'border-[var(--silver-300)] text-[var(--silver-400)]',
                  'data-[state=active]:border-[var(--accent-electric)] data-[state=active]:text-[var(--accent-electric)] data-[state=active]:bg-[color-mix(in_oklch,_var(--accent-electric)_10%,_transparent)]',
                  'data-[state=completed]:border-[var(--terminal-green)] data-[state=completed]:text-[var(--terminal-green)] data-[state=completed]:bg-[color-mix(in_oklch,_var(--terminal-green)_10%,_transparent)]',
                ]"
              >
                {{ step.step }}
              </StepperTrigger>
              <span
                :class="[
                  'text-xs font-data uppercase tracking-wider',
                  currentStep === step.step
                    ? 'text-[var(--accent-electric)]'
                    : 'text-[var(--silver-400)]',
                ]"
              >
                {{ t(`contests.wizard.${step.title.toLowerCase()}`, step.title) }}
              </span>
              <StepperSeparator
                v-if="step.step !== steps.length"
                class="absolute left-[calc(50%+20px)] top-4 w-[calc(100%-40px)] h-0.5 bg-[var(--silver-200)] data-[state=completed]:bg-[var(--terminal-green)]"
              />
            </StepperItem>
          </Stepper>
        </div>

        <!-- Step Content -->
        <div class="mt-4">
          <StepBasicInfo v-show="currentStep === 1" v-model:formData="formData" />
          <StepScoringRule v-show="currentStep === 2" v-model:formData="formData" />
          <StepSchedule v-show="currentStep === 3" v-model:formData="formData" />
          <StepProblems v-show="currentStep === 4" v-model:formData="formData" />
          <StepReview v-show="currentStep === 5" v-model:formData="formData" />
        </div>
      </div>

      <!-- Footer - Terminal Style -->
      <DialogFooter
        class="px-6 py-4 border-t border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--surface-sunken)]"
      >
        <Button
          type="button"
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--silver-300)]"
          @click="prevStep"
          :disabled="currentStep === 1 || loading"
        >
          <IconArrowLeft class="mr-1.5 h-3.5 w-3.5" />
          {{ t('contests.wizard.previous') }}
        </Button>
        <Button
          v-if="currentStep < steps.length"
          type="button"
          variant="terminal"
          size="sm"
          class="font-data text-xs border-[var(--accent-electric)] text-[var(--accent-electric)] hover:bg-[color-mix(in_oklch,_var(--accent-electric)_10%,_transparent)]"
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
          class="font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[color-mix(in_oklch,_var(--terminal-green)_10%,_transparent)]"
          @click="handleSubmit"
          :disabled="!isStepValid || loading"
        >
          <IconLoader v-if="loading" class="mr-1.5 h-3.5 w-3.5 animate-spin" />
          <IconCheck v-else class="mr-1.5 h-3.5 w-3.5" />
          {{ t('contests.wizard.submit') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
