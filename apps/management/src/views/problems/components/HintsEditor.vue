<script setup lang="ts">
import { ref } from 'vue'
import { useFieldArray } from 'vee-validate'
import { useI18n } from 'vue-i18n'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { IconPlus, IconTrash, IconArrowUp, IconArrowDown } from '@tabler/icons-vue'

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
      class="flex flex-col items-center justify-center py-6 px-4 border border-dashed border-[var(--border)] bg-muted/5 gap-3 rounded-none"
    >
      <p class="text-xs text-[var(--foreground-muted)] text-muted-foreground font-mono text-center">
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
        class="flex-1 font-mono text-sm bg-[var(--surface-sunken)]/25 border border-[var(--border)] focus-visible:ring-1 focus-visible:ring-[var(--accent-primary)] focus-visible:border-[var(--accent-primary)] focus-visible:ring-offset-0 min-h-[50px] resize-y rounded-none shadow-none"
      />
      <div class="flex flex-col gap-1 shrink-0">
        <Button
          type="button"
          variant="terminal_ghost"
          size="icon-sm"
          class="h-7 w-7"
          aria-label="Move hint up"
          :disabled="index === 0"
          @click="moveUp(index)"
        >
          <IconArrowUp class="h-3.5 w-3.5" />
        </Button>
        <Button
          type="button"
          variant="terminal_ghost"
          size="icon-sm"
          class="h-7 w-7"
          aria-label="Move hint down"
          :disabled="index === fields.length - 1"
          @click="moveDown(index)"
        >
          <IconArrowDown class="h-3.5 w-3.5" />
        </Button>
        <Button
          type="button"
          variant="terminal_danger"
          size="icon-sm"
          class="h-7 w-7 text-destructive"
          aria-label="Delete hint"
          @click="deleteHint(index)"
        >
          <IconTrash class="h-3.5 w-3.5" />
        </Button>
      </div>
    </div>

    <!-- Add button -->
    <Button type="button" variant="terminal" size="terminal" class="w-full" @click="addHint">
      <IconPlus class="h-4 w-4 mr-2" />
      {{ t('problems.descriptionForm.hintsSection.add') }}
    </Button>
  </div>
</template>
