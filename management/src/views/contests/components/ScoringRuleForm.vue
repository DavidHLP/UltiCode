<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useForm } from 'vee-validate'
import { toTypedSchema } from '@vee-validate/zod'
import * as z from 'zod'
import { IconLoader, IconCalculator } from '@tabler/icons-vue'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Switch } from '@/components/ui/switch'
import { Button } from '@/components/ui/button'
import type { ScoringRule, CreateScoringRuleDto, UpdateScoringRuleDto } from '@/api/admin/scoring-rules'

const { t } = useI18n()

const props = defineProps<{
  open: boolean
  ruleToEdit: ScoringRule | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'save', data: CreateScoringRuleDto | UpdateScoringRuleDto): void
  (e: 'cancel'): void
}>()

const loading = ref(false)

const formSchema = toTypedSchema(
  z.object({
    name: z.string().min(1, t('scoringRules.form.nameRequired')).max(100, t('scoringRules.form.nameTooLong')),
    description: z.string().max(500, t('scoringRules.form.descriptionTooLong')).optional(),
    base_score_per_problem: z.coerce.number().min(0, t('scoringRules.form.mustBeNonNegative')),
    time_bonus_per_minute: z.coerce.number().min(0, t('scoringRules.form.mustBeNonNegative')),
    wrong_answer_penalty: z.coerce.number().min(0, t('scoringRules.form.mustBeNonNegative')),
    time_limit_penalty: z.coerce.number().min(0, t('scoringRules.form.mustBeNonNegative')).optional(),
    first_solve_bonus: z.coerce.number().min(0, t('scoringRules.form.mustBeNonNegative')),
    full_score_bonus: z.coerce.number().min(0, t('scoringRules.form.mustBeNonNegative')).optional(),
    is_default: z.boolean().optional(),
  }),
)

const form = useForm({
  validationSchema: formSchema,
})

watch(
  () => props.ruleToEdit,
  (rule) => {
    if (rule) {
      form.setValues({
        name: rule.name,
        description: rule.description || '',
        base_score_per_problem: rule.base_score_per_problem,
        time_bonus_per_minute: rule.time_bonus_per_minute,
        wrong_answer_penalty: rule.wrong_answer_penalty,
        time_limit_penalty: rule.time_limit_penalty,
        first_solve_bonus: rule.first_solve_bonus,
        full_score_bonus: rule.full_score_bonus,
        is_default: rule.is_default,
      })
    } else {
      form.resetForm()
      // Set default values for new rule
      form.setValues({
        name: '',
        description: '',
        base_score_per_problem: 100,
        time_bonus_per_minute: 1,
        wrong_answer_penalty: 5,
        time_limit_penalty: 0,
        first_solve_bonus: 10,
        full_score_bonus: 0,
        is_default: false,
      })
    }
  },
  { immediate: true },
)

function handleSubmit(values: typeof form.values) {
  emit('save', values)
}

function handleCancel() {
  emit('cancel')
  emit('update:open', false)
}
</script>

<template>
  <Dialog :open="open" @update:open="$emit('update:open', $event)">
    <DialogContent class="sm:max-w-[500px]">
      <DialogHeader>
        <DialogTitle class="flex items-center gap-2">
          <IconCalculator class="h-5 w-5" />
          {{ ruleToEdit ? t('scoringRules.form.editTitle') : t('scoringRules.form.createTitle') }}
        </DialogTitle>
        <DialogDescription>
          {{ ruleToEdit ? t('scoringRules.form.editDescription') : t('scoringRules.form.createDescription') }}
        </DialogDescription>
      </DialogHeader>

      <form @submit="form.handleSubmit(handleSubmit)" class="grid gap-4 py-4">
        <FormField v-slot="{ componentField }" name="name">
          <FormItem>
            <FormLabel>{{ t('scoringRules.form.name') }}</FormLabel>
            <FormControl>
              <Input v-bind="componentField" :placeholder="t('scoringRules.form.namePlaceholder')" />
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <FormField v-slot="{ componentField }" name="description">
          <FormItem>
            <FormLabel>{{ t('scoringRules.form.description') }}</FormLabel>
            <FormControl>
              <Textarea
                v-bind="componentField"
                :placeholder="t('scoringRules.form.descriptionPlaceholder')"
                rows="2"
              />
            </FormControl>
            <FormMessage />
          </FormItem>
        </FormField>

        <div class="grid grid-cols-2 gap-4">
          <FormField v-slot="{ componentField }" name="base_score_per_problem">
            <FormItem>
              <FormLabel>{{ t('scoringRules.form.baseScorePerProblem') }}</FormLabel>
              <FormControl>
                <Input type="number" v-bind="componentField" />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>

          <FormField v-slot="{ componentField }" name="time_bonus_per_minute">
            <FormItem>
              <FormLabel>{{ t('scoringRules.form.timeBonusPerMinute') }}</FormLabel>
              <FormControl>
                <Input type="number" v-bind="componentField" />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <FormField v-slot="{ componentField }" name="wrong_answer_penalty">
            <FormItem>
              <FormLabel>{{ t('scoringRules.form.wrongAnswerPenalty') }}</FormLabel>
              <FormControl>
                <Input type="number" v-bind="componentField" />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>

          <FormField v-slot="{ componentField }" name="time_limit_penalty">
            <FormItem>
              <FormLabel>{{ t('scoringRules.form.timeLimitPenalty') }}</FormLabel>
              <FormControl>
                <Input type="number" v-bind="componentField" />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>
        </div>

        <div class="grid grid-cols-2 gap-4">
          <FormField v-slot="{ componentField }" name="first_solve_bonus">
            <FormItem>
              <FormLabel>{{ t('scoringRules.form.firstSolveBonus') }}</FormLabel>
              <FormControl>
                <Input type="number" v-bind="componentField" />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>

          <FormField v-slot="{ componentField }" name="full_score_bonus">
            <FormItem>
              <FormLabel>{{ t('scoringRules.form.fullScoreBonus') }}</FormLabel>
              <FormControl>
                <Input type="number" v-bind="componentField" />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>
        </div>

        <FormField v-slot="{ value, setValue }" name="is_default">
          <FormItem class="flex items-center justify-between rounded-lg border p-3">
            <div class="space-y-0.5">
              <FormLabel class="text-base">{{ t('scoringRules.form.isDefault') }}</FormLabel>
              <p class="text-xs text-muted-foreground">
                {{ t('scoringRules.form.isDefaultDescription') }}
              </p>
            </div>
            <FormControl>
              <Switch :checked="value" @update:checked="setValue" />
            </FormControl>
          </FormItem>
        </FormField>

        <DialogFooter>
          <Button type="button" variant="outline" @click="handleCancel">
            {{ t('common.cancel') }}
          </Button>
          <Button type="submit" :disabled="loading">
            <IconLoader v-if="loading" class="mr-2 h-4 w-4 animate-spin" />
            {{ ruleToEdit ? t('scoringRules.form.saveChanges') : t('scoringRules.form.createRule') }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
