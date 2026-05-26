<script setup lang="ts">
import { computed } from 'vue'
import { useFieldArray } from 'vee-validate'
import { useI18n } from 'vue-i18n'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { ChevronDown, Plus, Trash2, ArrowUp, ArrowDown } from 'lucide-vue-next'
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
      class="flex flex-col items-center justify-center py-10 border border-dashed border-border rounded-lg gap-4"
    >
      <p class="text-sm text-muted-foreground">
        {{ t('problems.casesDisplay.noCasesDescription') }}
      </p>
      <Button variant="outline" size="sm" @click="addExample">
        <Plus class="h-4 w-4 mr-2" />
        {{ t('problems.form.examples') }}
      </Button>
    </div>

    <!-- Example cards -->
    <Collapsible
      v-for="(field, index) in fields"
      :key="field.key"
      :default-open="true"
      class="border border-border rounded-lg overflow-hidden"
    >
      <Card class="border-0 shadow-none gap-0 py-0">
        <CardHeader class="flex flex-row items-center justify-between py-3 px-4">
          <CollapsibleTrigger>
            <div class="flex items-center gap-2 cursor-pointer">
              <ChevronDown class="h-4 w-4 shrink-0 transition-transform duration-200" />
              <CardTitle class="text-sm font-medium">
                {{ t('problems.descriptionDisplay.example') }} {{ index + 1 }}
              </CardTitle>
            </div>
          </CollapsibleTrigger>

          <div class="flex items-center gap-0.5">
            <Button
              variant="ghost"
              size="icon"
              class="h-8 w-8"
              :disabled="index === 0"
              @click="moveUp(index)"
            >
              <ArrowUp class="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              class="h-8 w-8"
              :disabled="index === fields.length - 1"
              @click="moveDown(index)"
            >
              <ArrowDown class="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              class="h-8 w-8 text-destructive hover:text-destructive"
              :disabled="!canRemove"
              @click="removeExample(index)"
            >
              <Trash2 class="h-4 w-4" />
            </Button>
          </div>
        </CardHeader>

        <CollapsibleContent>
          <CardContent class="space-y-4 pt-0 px-4 pb-4">
            <div class="space-y-2">
              <label class="text-sm font-medium text-muted-foreground">
                {{ t('problems.casesDisplay.input') }}
              </label>
              <Textarea
                :model-value="field.value.input"
                rows="3"
                class="font-mono text-sm bg-muted border-none shadow-none focus-visible:ring-0 focus-visible:ring-offset-0 min-h-[80px] resize-y"
                :placeholder="t('problems.form.validation.inputRequired')"
                @update:model-value="(v) => updateField(index, field, 'input', v as string)"
              />
            </div>

            <div class="space-y-2">
              <label class="text-sm font-medium text-muted-foreground">
                {{ t('problems.casesDisplay.output') }}
              </label>
              <Textarea
                :model-value="field.value.output"
                rows="3"
                class="font-mono text-sm bg-muted border-none shadow-none focus-visible:ring-0 focus-visible:ring-offset-0 min-h-[80px] resize-y"
                :placeholder="t('problems.form.validation.outputRequired')"
                @update:model-value="(v) => updateField(index, field, 'output', v as string)"
              />
            </div>

            <div class="space-y-2">
              <label class="text-sm font-medium text-muted-foreground">
                {{ t('problems.descriptionDisplay.explanation') }}
              </label>
              <Textarea
                :model-value="field.value.explanation || ''"
                rows="2"
                class="text-sm bg-muted border-none shadow-none focus-visible:ring-0 focus-visible:ring-offset-0 min-h-[60px] resize-y"
                :placeholder="t('problems.descriptionDisplay.explanation')"
                @update:model-value="(v) => updateField(index, field, 'explanation', v as string)"
              />
            </div>
          </CardContent>
        </CollapsibleContent>
      </Card>
    </Collapsible>

    <!-- Add button -->
    <Button v-if="fields.length > 0" variant="outline" size="sm" class="w-full" @click="addExample">
      <Plus class="h-4 w-4 mr-2" />
      {{ t('problems.form.examples') }}
    </Button>
  </div>
</template>
