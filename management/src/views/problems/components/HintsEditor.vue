<script setup lang="ts">
import { ref } from 'vue'
import { useFieldArray } from 'vee-validate'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { IconPlus, IconTrash, IconArrowUp, IconArrowDown } from '@tabler/icons-vue'

const props = defineProps<{
  name: string
}>()

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
      class="flex flex-col items-center justify-center py-8 px-4 border border-dashed border-muted-foreground/30 bg-muted/20"
    >
      <p class="text-sm text-muted-foreground text-center">
        No hints added yet. Hints provide guided steps to help users solve the problem.
      </p>
    </div>

    <!-- Hint rows -->
    <div
      v-for="(field, index) in fields"
      :key="field.key"
      class="flex items-start gap-2"
    >
      <Textarea
        :name="`${name}[${index}]`"
        v-model="field.value"
        placeholder="Enter a hint to guide users toward the solution..."
        rows="3"
        class="flex-1"
      />
      <div class="flex flex-col gap-1 shrink-0">
        <Button
          type="button"
          variant="outline"
          size="icon"
          aria-label="Move hint up"
          :disabled="index === 0"
          @click="moveUp(index)"
        >
          <IconArrowUp class="h-4 w-4" />
        </Button>
        <Button
          type="button"
          variant="outline"
          size="icon"
          aria-label="Move hint down"
          :disabled="index === fields.length - 1"
          @click="moveDown(index)"
        >
          <IconArrowDown class="h-4 w-4" />
        </Button>
        <Button
          type="button"
          variant="outline"
          size="icon"
          aria-label="Delete hint"
          @click="deleteHint(index)"
        >
          <IconTrash class="h-4 w-4" />
        </Button>
      </div>
    </div>

    <!-- Add button -->
    <Button type="button" variant="outline" class="w-full" @click="addHint">
      <IconPlus class="h-4 w-4 mr-2" />
      Add new hint
    </Button>
  </div>
</template>
