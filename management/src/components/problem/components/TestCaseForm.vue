<script setup lang="ts">
import { computed } from 'vue'
import { Textarea } from '@/components/ui/textarea'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Button } from '@/components/ui/button'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { IconLoader2 } from '@tabler/icons-vue'
import {
  type CreateTestCaseDto,
  type TestCase,
  type CaseScope,
  mapFlagsToCaseScope,
  mapCaseScopeToFlags,
} from '@/api/admin/test-cases'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps<{
  open: boolean
  editingTestCase: TestCase | null
  formData: CreateTestCaseDto
  saving: boolean
}>()

const emit = defineEmits<{
  'update:open': [value: boolean]
  'update:formData': [value: CreateTestCaseDto]
  save: []
  cancel: []
}>()

/**
 * Two-way binding between the radio (canonical CaseScope) and the underlying
 * (is_sample, is_hidden) flag pair on `formData`. Always emits both flags
 * explicitly on set, so the wire contract to the backend satisfies the XOR
 * filter and never carries a half-defined state.
 */
const caseScope = computed<CaseScope>({
  get: () => mapFlagsToCaseScope(props.formData.isSample, props.formData.isHidden),
  set: (scope: CaseScope) => {
    emit('update:formData', {
      ...props.formData,
      ...mapCaseScopeToFlags(scope),
    })
  },
})
</script>

<template>
  <Dialog :open="open" @update:open="emit('update:open', $event)">
    <DialogContent class="max-w-2xl">
      <DialogHeader>
        <DialogTitle>
          {{ editingTestCase ? t('testCases.editTestCase') : t('testCases.createTestCase') }}
        </DialogTitle>
      </DialogHeader>

      <div class="space-y-4 py-4">
        <!--
          Canonical scope radio: replaces the previous (is_sample, is_hidden)
          Switch pair. Frontend invariants:
            - exactly one of {SAMPLE, HIDDEN} is selected at all times
            - SAMPLE ⇔ is_sample=true ∧ is_hidden=false
            - HIDDEN ⇔ is_sample=false ∧ is_hidden=true
          This guarantees the wire payload satisfies the backend XOR filter
          (see backend CreateTestCaseDTO + TestCaseMapper.findActiveCasesForJudging).
        -->
        <div>
          <Label class="text-sm font-medium mb-2 block">
            {{ t('testCases.scope.sample') }} / {{ t('testCases.scope.hidden') }}
          </Label>
          <RadioGroup
            :model-value="caseScope"
            @update:model-value="(v) => (caseScope = v as CaseScope)"
            class="flex flex-col gap-2"
          >
            <div class="flex items-start gap-2">
              <RadioGroupItem id="scope-sample" value="SAMPLE" />
              <div class="grid gap-0.5">
                <Label for="scope-sample" class="text-sm font-medium">
                  {{ t('testCases.scope.sample') }}
                </Label>
                <p class="text-xs text-muted-foreground">
                  {{ t('testCases.scope.sampleHelp') }}
                </p>
              </div>
            </div>
            <div class="flex items-start gap-2">
              <RadioGroupItem id="scope-hidden" value="HIDDEN" />
              <div class="grid gap-0.5">
                <Label for="scope-hidden" class="text-sm font-medium">
                  {{ t('testCases.scope.hidden') }}
                </Label>
                <p class="text-xs text-muted-foreground">
                  {{ t('testCases.scope.hiddenHelp') }}
                </p>
              </div>
            </div>
          </RadioGroup>
        </div>

        <div>
          <Label class="text-sm text-muted-foreground mb-1 block"
            >{{ t('testCases.input') }} *</Label
          >
          <Textarea
            :model-value="formData.inputText"
            @update:model-value="
              emit('update:formData', { ...formData, inputText: $event as string })
            "
            :placeholder="t('testCases.inputPlaceholder')"
            class="font-mono text-sm min-h-[120px]"
          />
        </div>

        <div>
          <Label class="text-sm text-muted-foreground mb-1 block"
            >{{ t('testCases.output') }} *</Label
          >
          <Textarea
            :model-value="formData.outputText"
            @update:model-value="
              emit('update:formData', { ...formData, outputText: $event as string })
            "
            :placeholder="t('testCases.outputPlaceholder')"
            class="font-mono text-sm min-h-[120px]"
          />
        </div>

        <div>
          <Label class="text-sm text-muted-foreground mb-1 block">
            {{ t('testCases.explanation') }} ({{ t('common.optional') }})
          </Label>
          <Input
            :model-value="formData.explanation"
            @update:model-value="
              emit('update:formData', { ...formData, explanation: $event as string })
            "
            :placeholder="t('testCases.explanationPlaceholder')"
          />
        </div>
      </div>

      <DialogFooter>
        <Button variant="outline" @click="emit('update:open', false)">{{
          t('common.cancel')
        }}</Button>
        <Button :disabled="saving" @click="emit('save')">
          <IconLoader2 v-if="saving" class="h-4 w-4 mr-1 animate-spin" />
          {{ saving ? t('common.saving') : t('common.save') }}
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>
