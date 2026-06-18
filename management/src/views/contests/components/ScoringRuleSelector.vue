<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { toast } from 'vue-sonner'
import { IconPlus, IconLoader, IconCalculator } from '@tabler/icons-vue'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import {
  scoringRulesApi,
  type ScoringRule,
  type CreateScoringRuleDto,
} from '@/api/admin/scoring-rules'
import { SemanticBadge } from '@/components/ui/terminal'

const props = defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const { t } = useI18n()

const scoringRules = ref<ScoringRule[]>([])
const loading = ref(false)
const creatingNew = ref(false)
const createLoading = ref(false)

const newRuleForm = ref({
  name: '',
  description: '',
  baseScorePerProblem: 100,
  timeBonusPerMinute: 1,
  wrongAnswerPenalty: 5,
  timeLimitPenalty: 0,
  firstSolveBonus: 10,
  fullScoreBonus: 0,
})

async function fetchScoringRules() {
  loading.value = true
  try {
    scoringRules.value = await scoringRulesApi.getAll(false)
    if (!props.modelValue && scoringRules.value.length > 0) {
      const defaultRule = scoringRules.value.find((r) => r.isDefault)
      if (defaultRule) {
        emit('update:modelValue', defaultRule.id)
      } else if (scoringRules.value[0]) {
        emit('update:modelValue', scoringRules.value[0].id)
      }
    }
  } catch {
    scoringRules.value = []
  } finally {
    loading.value = false
  }
}

onMounted(fetchScoringRules)

const selectedRule = computed(() => {
  return scoringRules.value.find((r) => r.id === props.modelValue)
})

function handleSelectionChange(value: string) {
  if (value === '__create_new__') {
    creatingNew.value = true
    return
  }
  emit('update:modelValue', value)
}

async function handleCreateRule() {
  if (!newRuleForm.value.name.trim()) {
    toast.error(t('scoringRules.form.nameRequired'))
    return
  }

  createLoading.value = true
  try {
    const dto: CreateScoringRuleDto = {
      name: newRuleForm.value.name,
      description: newRuleForm.value.description || undefined,
      baseScorePerProblem: newRuleForm.value.baseScorePerProblem,
      timeBonusPerMinute: newRuleForm.value.timeBonusPerMinute,
      wrongAnswerPenalty: newRuleForm.value.wrongAnswerPenalty,
      timeLimitPenalty: newRuleForm.value.timeLimitPenalty,
      firstSolveBonus: newRuleForm.value.firstSolveBonus,
      fullScoreBonus: newRuleForm.value.fullScoreBonus,
    }

    const newRule = await scoringRulesApi.create(dto)
    scoringRules.value.push(newRule)
    emit('update:modelValue', newRule.id)
    creatingNew.value = false

    newRuleForm.value = {
      name: '',
      description: '',
      baseScorePerProblem: 100,
      timeBonusPerMinute: 1,
      wrongAnswerPenalty: 5,
      timeLimitPenalty: 0,
      firstSolveBonus: 10,
      fullScoreBonus: 0,
    }

    toast.success(t('scoringRules.toast.createdSuccessfully'))
  } catch (error) {
    toast.error(t('scoringRules.toast.failedToCreate'))
    console.error(error)
  } finally {
    createLoading.value = false
  }
}

function cancelCreateNew() {
  creatingNew.value = false
  newRuleForm.value = {
    name: '',
    description: '',
    baseScorePerProblem: 100,
    timeBonusPerMinute: 1,
    wrongAnswerPenalty: 5,
    timeLimitPenalty: 0,
    firstSolveBonus: 10,
    fullScoreBonus: 0,
  }
}
</script>

<template>
  <div class="space-y-6">
    <!-- Section Header -->
    <div class="flex items-center gap-2 mb-4">
      <span class="terminal-comment">scoring_rule</span>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="flex items-center justify-center py-8">
      <IconLoader class="h-6 w-6 animate-spin text-[var(--accent-electric)]" />
      <span class="ml-2 terminal-comment">{{ t('common.loading') }}</span>
    </div>

    <!-- Selector -->
    <div v-else-if="!creatingNew" class="space-y-4">
      <div class="space-y-2">
        <label class="terminal-label">{{ t('contests.scoringRule.selectRule') }}</label>
        <Select
          :model-value="modelValue"
          @update:model-value="handleSelectionChange($event as string)"
        >
          <SelectTrigger
            class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm"
          >
            <SelectValue :placeholder="t('contests.scoringRule.selectPlaceholder')" />
          </SelectTrigger>
          <SelectContent class="border-[var(--silver-200)] dark:border-[var(--silver-700)]">
            <SelectItem
              v-for="rule in scoringRules"
              :key="rule.id"
              :value="rule.id"
              class="font-data text-xs cursor-pointer"
            >
              <div class="flex items-center gap-2">
                <span>{{ rule.name }}</span>
                <SemanticBadge
                  v-if="rule.isDefault"
                  color="success"
                  :label="t('scoringRules.badges.default')"
                  size="xs"
                />
              </div>
            </SelectItem>
            <SelectItem
              value="__create_new__"
              class="font-data text-xs cursor-pointer text-[var(--accent-electric)]"
            >
              <div class="flex items-center gap-2">
                <IconPlus class="h-3.5 w-3.5" />
                <span>{{ t('contests.scoringRule.createNew') }}</span>
              </div>
            </SelectItem>
          </SelectContent>
        </Select>
        <span class="terminal-comment text-xs">{{
          t('contests.scoringRule.selectDescription')
        }}</span>
      </div>

      <!-- Selected Rule Details -->
      <div
        v-if="selectedRule"
        class="border border-[var(--silver-200)] dark:border-[var(--silver-700)] bg-[var(--card)]"
      >
        <div
          class="border-b border-[var(--silver-200)] dark:border-[var(--silver-700)] px-4 py-2 bg-[var(--surface-sunken)]"
        >
          <div class="flex items-center gap-2">
            <IconCalculator class="h-4 w-4 text-[var(--accent-electric)]" />
            <span class="terminal-comment">{{ selectedRule.name }}</span>
            <SemanticBadge
              v-if="selectedRule.isDefault"
              color="success"
              :label="t('scoringRules.badges.default')"
              size="xs"
            />
          </div>
        </div>
        <div class="p-4">
          <p v-if="selectedRule.description" class="text-sm text-[var(--foreground)] mb-4">
            {{ selectedRule.description }}
          </p>
          <div class="grid grid-cols-2 md:grid-cols-3 gap-3">
            <div class="space-y-1">
              <span class="terminal-label text-2xs">{{
                t('scoringRules.form.baseScorePerProblem')
              }}</span>
              <p class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">
                {{ selectedRule.baseScorePerProblem }}
              </p>
            </div>
            <div class="space-y-1">
              <span class="terminal-label text-2xs">{{
                t('scoringRules.form.timeBonusPerMinute')
              }}</span>
              <p class="font-data text-sm text-[var(--terminal-cyan)] tabular-nums">
                {{ selectedRule.timeBonusPerMinute }}
              </p>
            </div>
            <div class="space-y-1">
              <span class="terminal-label text-2xs">{{
                t('scoringRules.form.wrongAnswerPenalty')
              }}</span>
              <p class="font-data text-sm text-[var(--terminal-red)] tabular-nums">
                -{{ selectedRule.wrongAnswerPenalty }}
              </p>
            </div>
            <div class="space-y-1">
              <span class="terminal-label text-2xs">{{
                t('scoringRules.form.timeLimitPenalty')
              }}</span>
              <p class="font-data text-sm text-[var(--terminal-red)] tabular-nums">
                -{{ selectedRule.timeLimitPenalty }}
              </p>
            </div>
            <div class="space-y-1">
              <span class="terminal-label text-2xs">{{
                t('scoringRules.form.firstSolveBonus')
              }}</span>
              <p class="font-data text-sm text-[var(--terminal-green)] tabular-nums">
                +{{ selectedRule.firstSolveBonus }}
              </p>
            </div>
            <div class="space-y-1">
              <span class="terminal-label text-2xs">{{
                t('scoringRules.form.fullScoreBonus')
              }}</span>
              <p class="font-data text-sm text-[var(--terminal-green)] tabular-nums">
                +{{ selectedRule.fullScoreBonus }}
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Create New Rule Form -->
    <div v-else class="space-y-4">
      <div
        class="border border-[var(--accent-electric)] bg-[color-mix(in_oklch,_var(--accent-electric)_5%,_transparent)] p-4"
      >
        <div class="flex items-center justify-between mb-4">
          <div class="flex items-center gap-2">
            <IconPlus class="h-4 w-4 text-[var(--accent-electric)]" />
            <span class="terminal-comment">{{ t('contests.scoringRule.createNew') }}</span>
          </div>
          <Button
            type="button"
            variant="terminal"
            size="sm"
            class="font-data text-xs border-[var(--silver-300)]"
            @click="cancelCreateNew"
          >
            {{ t('common.cancel') }}
          </Button>
        </div>

        <div class="space-y-4">
          <!-- Name -->
          <div class="space-y-2">
            <label class="terminal-label">{{ t('scoringRules.form.name') }}</label>
            <Input
              v-model="newRuleForm.name"
              :placeholder="t('scoringRules.form.namePlaceholder')"
              class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)]"
            />
          </div>

          <!-- Description -->
          <div class="space-y-2">
            <label class="terminal-label">{{ t('scoringRules.form.description') }}</label>
            <Textarea
              v-model="newRuleForm.description"
              :placeholder="t('scoringRules.form.descriptionPlaceholder')"
              rows="2"
              class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)] resize-none"
            />
          </div>

          <!-- Score Parameters -->
          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-2">
              <label class="terminal-label">{{ t('scoringRules.form.baseScorePerProblem') }}</label>
              <Input
                v-model.number="newRuleForm.baseScorePerProblem"
                type="number"
                min="0"
                class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)]"
              />
            </div>
            <div class="space-y-2">
              <label class="terminal-label">{{ t('scoringRules.form.timeBonusPerMinute') }}</label>
              <Input
                v-model.number="newRuleForm.timeBonusPerMinute"
                type="number"
                min="0"
                class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)]"
              />
            </div>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-2">
              <label class="terminal-label">{{ t('scoringRules.form.wrongAnswerPenalty') }}</label>
              <Input
                v-model.number="newRuleForm.wrongAnswerPenalty"
                type="number"
                min="0"
                class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)]"
              />
            </div>
            <div class="space-y-2">
              <label class="terminal-label">{{ t('scoringRules.form.timeLimitPenalty') }}</label>
              <Input
                v-model.number="newRuleForm.timeLimitPenalty"
                type="number"
                min="0"
                class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)]"
              />
            </div>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <div class="space-y-2">
              <label class="terminal-label">{{ t('scoringRules.form.firstSolveBonus') }}</label>
              <Input
                v-model.number="newRuleForm.firstSolveBonus"
                type="number"
                min="0"
                class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)]"
              />
            </div>
            <div class="space-y-2">
              <label class="terminal-label">{{ t('scoringRules.form.fullScoreBonus') }}</label>
              <Input
                v-model.number="newRuleForm.fullScoreBonus"
                type="number"
                min="0"
                class="border-[var(--silver-200)] dark:border-[var(--silver-700)] font-data text-sm focus:border-[var(--accent-electric)]"
              />
            </div>
          </div>

          <!-- Submit Button -->
          <div class="flex justify-end pt-2">
            <Button
              type="button"
              variant="terminal"
              size="sm"
              class="font-data text-xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[color-mix(in_oklch,_var(--terminal-green)_10%,_transparent)]"
              :disabled="createLoading || !newRuleForm.name.trim()"
              @click="handleCreateRule"
            >
              <IconLoader v-if="createLoading" class="mr-1.5 h-3.5 w-3.5 animate-spin" />
              <IconPlus v-else class="mr-1.5 h-3.5 w-3.5" />
              {{ t('scoringRules.form.createRule') }}
            </Button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
