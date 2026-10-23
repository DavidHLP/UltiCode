<script setup lang="ts">
import { ref } from 'vue'
import { useFieldArray } from 'vee-validate'
import { useI18n } from 'vue-i18n'
import { Button } from '@ulticode/design-system/ui/button'
import { Textarea } from '@ulticode/design-system/ui/textarea'
import {
  Plus as IconPlus,
  Trash as IconTrash,
  ArrowUp as IconArrowUp,
  ArrowDown as IconArrowDown,
} from '@lucide/vue'

const props = defineProps<{
  name: string
}>()

const { t } = useI18n()
const { fields, push, remove, move } = useFieldArray<string>(props.name)

const renderTrigger = ref(0)

function addHint() {
  push('')
  renderTrigger.value++
}

function deleteHint(index: number) {
  remove(index)
  renderTrigger.value++
}

function moveUp(index: number) {
  if (index > 0) {
    move(index, index - 1)
    renderTrigger.value++
  }
}

function moveDown(index: number) {
  if (index < fields.value.length - 1) {
    move(index, index + 1)
    renderTrigger.value++
  }
}
</script>

<template>
  <div class="space-y-3">
    <span class="hidden">{{ renderTrigger }}</span>

    <!-- Empty state -->
    <div
      v-if="fields.length === 0"
      class="flex flex-col items-center justify-center py-6 px-4 border border-dashed border-border bg-muted/5 gap-3 rounded-lg"
    >
      <p class="text-xs text-muted-foreground text-muted-foreground font-mono text-center">
        {{ t('problems.descriptionForm.hintsSection.empty') }}
      </p>
    </div>

    <!-- Hint rows -->
    <div v-for="(field, index) in fields" :key="field.key" class="flex items-start gap-2">
      <Textarea
        :name="`${name}[${index}]`"
        v-model="field.value"
        placeholder="Enter a hint to guide users toward the solution..."
        rows="2"
        class="flex-1 min-h-[50px] resize-y"
      />
      <div class="flex flex-col gap-1 shrink-0">
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          class="w-7"
          aria-label="Move hint up"
          :disabled="index === 0"
          @click="moveUp(index)"
        >
          <IconArrowUp class="h-3.5 w-3.5" />
        </Button>
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          class="w-7"
          aria-label="Move hint down"
          :disabled="index === fields.length - 1"
          @click="moveDown(index)"
        >
          <IconArrowDown class="h-3.5 w-3.5" />
        </Button>
        <Button
          type="button"
          variant="destructive"
          size="icon-sm"
          class="w-7"
          aria-label="Delete hint"
          @click="deleteHint(index)"
        >
          <IconTrash class="h-3.5 w-3.5" />
        </Button>
      </div>
    </div>

    <!-- Add button -->
    <Button type="button" variant="outline" size="default" class="w-full" @click="addHint">
      <IconPlus class="h-4 w-4 mr-2" />
      {{ t('problems.descriptionForm.hintsSection.add') }}
    </Button>
  </div>
</template>
