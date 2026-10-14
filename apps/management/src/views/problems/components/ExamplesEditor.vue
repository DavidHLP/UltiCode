<script setup lang="ts">
import { FieldLabel } from '@ulticode/design-system/ui/field'

import { computed } from 'vue'
import { useFieldArray } from 'vee-validate'
import { useI18n } from 'vue-i18n'
import { Card, CardContent, CardHeader } from '@ulticode/design-system/ui/card'
import { Button } from '@ulticode/design-system/ui/button'
import { Textarea } from '@ulticode/design-system/ui/textarea'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@ulticode/design-system/ui/collapsible'
import { ChevronDown, Plus, Trash2, ArrowUp, ArrowDown } from '@lucide/vue'
import type { Example } from '@/lib/schemas/problemDescription'

const props = withDefaults(
  defineProps<{
    name?: string
    minItems?: number
  }>(),
  {
    name: 'examples',
    minItems: 1,
  },
)

const { t } = useI18n()

const { fields, push, remove, swap, update } = useFieldArray<Example>(props.name)

const canRemove = computed(() => fields.value.length > props.minItems)

function addExample() {
  push({ input: '', output: '', explanation: '' })
}

function removeExample(index: number) {
  if (canRemove.value) {
    remove(index)
  }
}

function moveUp(index: number) {
  if (index > 0) {
    swap(index, index - 1)
  }
}

function moveDown(index: number) {
  if (index < fields.value.length - 1) {
    swap(index, index + 1)
  }
}

function updateField(index: number, field: { value: Example }, key: keyof Example, value: string) {
  update(index, { ...field.value, [key]: value })
}
</script>

<template>
  <div class="space-y-4">
    <!-- Empty state -->
    <div
      v-if="fields.length === 0"
      class="flex flex-col items-center justify-center py-8 px-4 border border-dashed border-border bg-muted/5 gap-3 rounded-lg"
    >
      <p class="text-xs text-muted-foreground text-muted-foreground font-mono">
        {{ t('problems.casesDisplay.noCasesDescription') }}
      </p>
      <Button variant="outline" size="default" @click="addExample">
        <Plus class="size-3.5 mr-1.5" />
        {{ t('problems.form.examples') }}
      </Button>
    </div>

    <!-- Example cards -->
    <Collapsible
      v-for="(field, index) in fields"
      :key="field.key"
      :default-open="true"
      class="overflow-hidden"
    >
      <Card class="gap-0 py-0">
        <CardHeader class="flex flex-row items-center justify-between py-2 px-4">
          <CollapsibleTrigger>
            <div class="flex items-center gap-2 cursor-pointer">
              <ChevronDown class="h-4 w-4 shrink-0 transition-transform duration-200" />
              <span class="text-xs font-mono font-bold uppercase tracking-wider text-foreground">
                {{ t('problems.descriptionDisplay.example') }} {{ index + 1 }}
              </span>
            </div>
          </CollapsibleTrigger>

          <div class="flex items-center gap-1">
            <Button
              variant="ghost"
              size="icon-sm"
              class="w-7"
              :disabled="index === 0"
              @click="moveUp(index)"
            >
              <ArrowUp class="h-3.5 w-3.5" />
            </Button>
            <Button
              variant="ghost"
              size="icon-sm"
              class="w-7"
              :disabled="index === fields.length - 1"
              @click="moveDown(index)"
            >
              <ArrowDown class="h-3.5 w-3.5" />
            </Button>
            <Button
              variant="destructive"
              size="icon-sm"
              class="w-7"
              :disabled="!canRemove"
              @click="removeExample(index)"
            >
              <Trash2 class="h-3.5 w-3.5" />
            </Button>
          </div>
        </CardHeader>

        <CollapsibleContent>
          <CardContent class="space-y-4 pt-4 px-4 pb-4">
            <div class="space-y-1">
              <FieldLabel class="mb-1 block">
                {{ t('problems.casesDisplay.input') }}
              </FieldLabel>
              <Textarea
                :model-value="field.value.input"
                rows="3"
                class="min-h-[60px] resize-y"
                :placeholder="t('problems.form.validation.inputRequired')"
                @update:model-value="(v) => updateField(index, field, 'input', v as string)"
              />
            </div>

            <div class="space-y-1">
              <FieldLabel class="mb-1 block">
                {{ t('problems.casesDisplay.output') }}
              </FieldLabel>
              <Textarea
                :model-value="field.value.output"
                rows="3"
                class="min-h-[60px] resize-y"
                :placeholder="t('problems.form.validation.outputRequired')"
                @update:model-value="(v) => updateField(index, field, 'output', v as string)"
              />
            </div>

            <div class="space-y-1">
              <FieldLabel class="mb-1 block">
                {{ t('problems.descriptionDisplay.explanation') }}
              </FieldLabel>
              <Textarea
                :model-value="field.value.explanation || ''"
                rows="2"
                class="min-h-[50px] resize-y"
                :placeholder="t('problems.descriptionDisplay.explanation')"
                @update:model-value="(v) => updateField(index, field, 'explanation', v as string)"
              />
            </div>
          </CardContent>
        </CollapsibleContent>
      </Card>
    </Collapsible>

    <!-- Add button -->
    <Button
      v-if="fields.length > 0"
      variant="outline"
      size="default"
      class="w-full"
      @click="addExample"
    >
      <Plus class="h-4 w-4 mr-2" />
      {{ t('problems.form.examples') }}
    </Button>
  </div>
</template>
