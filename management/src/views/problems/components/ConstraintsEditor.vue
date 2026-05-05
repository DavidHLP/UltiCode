<script setup lang="ts">
import { ref } from 'vue'
import { useFieldArray } from 'vee-validate'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { IconPlus, IconTrash } from '@tabler/icons-vue'

const props = defineProps<{
  name: string
}>()

const { fields, push, remove } = useFieldArray<string>(props.name)

const renderTrigger = ref(0)

function addConstraint() {
  push('')
  renderTrigger.value++
}

function deleteConstraint(index: number) {
  remove(index)
  renderTrigger.value++
}

defineExpose({ addConstraint, deleteConstraint })
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
        No constraints added yet. Constraints describe the limits and rules for the problem (e.g.,
        array length, value ranges).
      </p>
    </div>

    <!-- Constraint rows -->
    <div v-for="(field, index) in fields" :key="field.key" class="flex items-center gap-2">
      <Input
        :name="`${name}[${index}]`"
        v-model="field.value"
        placeholder="e.g., 1 <= nums.length <= 10^5"
        class="flex-1"
      />
      <Button
        type="button"
        variant="outline"
        size="icon"
        class="shrink-0"
        @click="deleteConstraint(index)"
      >
        <IconTrash class="h-4 w-4" />
      </Button>
    </div>

    <!-- Add button -->
    <Button type="button" variant="outline" class="w-full" @click="addConstraint">
      <IconPlus class="h-4 w-4 mr-2" />
      Add new constraint
    </Button>
  </div>
</template>
