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
import type {
  ScoringRule,
  CreateScoringRuleDto,
  UpdateScoringRuleDto,
} from '@/api/admin/scoring-rules'

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
    name: z
      .string()
      .min(1, t('scoringRules.form.nameRequired'))
      .max(100, t('scoringRules.form.nameTooLong')),
    description: z.string().max(500, t('scoringRules.form.descriptionTooLong')).optional(),
    baseScorePerProblem: z.coerce.number().min(0, t('scoringRules.form.mustBeNonNegative')),
    timeBonusPerMinute: z.coerce.number().min(0, t('scoringRules.form.mustBeNonNegative')),
    wrongAnswerPenalty: z.coerce.number().min(0, t('scoringRules.form.mustBeNonNegative')),
    timeLimitPenalty: z.coerce.number().min(0, t('scoringRules.form.mustBeNonNegative')).optional(),
    firstSolveBonus: z.coerce.number().min(0, t('scoringRules.form.mustBeNonNegative')),
    fullScoreBonus: z.coerce.number().min(0, t('scoringRules.form.mustBeNonNegative')).optional(),
    isDefault: z.boolean().optional(),
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
        baseScorePerProblem: rule.baseScorePerProblem,
        timeBonusPerMinute: rule.timeBonusPerMinute,
        wrongAnswerPenalty: rule.wrongAnswerPenalty,
        timeLimitPenalty: rule.timeLimitPenalty,
        firstSolveBonus: rule.firstSolveBonus,
        fullScoreBonus: rule.fullScoreBonus,
        isDefault: rule.isDefault,
      })
    } else {
      form.resetForm()
      form.setValues({
        name: '',
        description: '',
        baseScorePerProblem: 100,
        timeBonusPerMinute: 1,
        wrongAnswerPenalty: 5,
        timeLimitPenalty: 0,
        firstSolveBonus: 10,
        fullScoreBonus: 0,
        isDefault: false,
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
          {{
            ruleToEdit
              ? t('scoringRules.form.editDescription')
              : t('scoringRules.form.createDescription')
          }}
        </DialogDescription>
      </DialogHeader>

      <form @submit="form.handleSubmit(handleSubmit)" class="grid gap-4 py-4">
        <FormField v-slot="{ componentField }" name="name">
          <FormItem>
            <FormLabel>{{ t('scoringRules.form.name') }}</FormLabel>
            <FormControl>
              <Input
                v-bind="componentField"
                :placeholder="t('scoringRules.form.namePlaceholder')"
              />
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
          <FormField v-slot="{ componentField }" name="baseScorePerProblem">
            <FormItem>
              <FormLabel>{{ t('scoringRules.form.baseScorePerProblem') }}</FormLabel>
              <FormControl>
                <Input type="number" v-bind="componentField" />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>

          <FormField v-slot="{ componentField }" name="timeBonusPerMinute">
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
          <FormField v-slot="{ componentField }" name="wrongAnswerPenalty">
            <FormItem>
              <FormLabel>{{ t('scoringRules.form.wrongAnswerPenalty') }}</FormLabel>
              <FormControl>
                <Input type="number" v-bind="componentField" />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>

          <FormField v-slot="{ componentField }" name="timeLimitPenalty">
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
          <FormField v-slot="{ componentField }" name="firstSolveBonus">
            <FormItem>
              <FormLabel>{{ t('scoringRules.form.firstSolveBonus') }}</FormLabel>
              <FormControl>
                <Input type="number" v-bind="componentField" />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>

          <FormField v-slot="{ componentField }" name="fullScoreBonus">
            <FormItem>
              <FormLabel>{{ t('scoringRules.form.fullScoreBonus') }}</FormLabel>
              <FormControl>
                <Input type="number" v-bind="componentField" />
              </FormControl>
              <FormMessage />
            </FormItem>
          </FormField>
        </div>

        <FormField v-slot="{ value, setValue }" name="isDefault">
          <FormItem class="flex items-center justify-between rounded-none border p-3">
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
            {{
              ruleToEdit ? t('scoringRules.form.saveChanges') : t('scoringRules.form.createRule')
            }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>
