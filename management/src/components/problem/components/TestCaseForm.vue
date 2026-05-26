<script setup lang="ts">
import { Textarea } from '@/components/ui/textarea'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { IconLoader2 } from '@tabler/icons-vue'
import type { CreateTestCaseDto, TestCase } from '@/api/admin/test-cases'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps<{
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
        <div class="flex items-center gap-6">
          <div class="flex items-center gap-2">
            <Switch
              :model-value="formData.is_sample"
              @update:model-value="emit('update:formData', { ...formData, is_sample: $event })"
              id="is_sample"
            />
            <Label for="is_sample" class="text-sm">{{ t('testCases.isSample') }}</Label>
          </div>
          <div class="flex items-center gap-2">
            <Switch
              :model-value="formData.is_hidden"
              @update:model-value="emit('update:formData', { ...formData, is_hidden: $event })"
              id="is_hidden"
            />
            <Label for="is_hidden" class="text-sm">{{ t('testCases.isHidden') }}</Label>
          </div>
        </div>

        <div>
          <Label class="text-sm text-muted-foreground mb-1 block"
            >{{ t('testCases.input') }} *</Label
          >
          <Textarea
            :model-value="formData.input_text"
            @update:model-value="
              emit('update:formData', { ...formData, input_text: $event as string })
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
            :model-value="formData.output_text"
            @update:model-value="
              emit('update:formData', { ...formData, output_text: $event as string })
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
