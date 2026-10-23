<script setup lang="ts">
import { ref } from 'vue'
import { useFieldArray } from 'vee-validate'
import { useI18n } from 'vue-i18n'
import { Button } from '@ulticode/design-system/ui/button'
import { Input } from '@ulticode/design-system/ui/input'
import { Plus as IconPlus, Trash as IconTrash } from '@lucide/vue'

const props = defineProps<{
  name: string
}>()

const { t } = useI18n()
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
      class="flex flex-col items-center justify-center py-6 px-4 border border-dashed border-border bg-muted/5 rounded-lg"
    >
      <p class="text-xs text-muted-foreground font-mono text-center">
        {{ t('problems.descriptionForm.constraintsSection.emptyDescription') }}
      </p>
    </div>

    <!-- Constraint rows -->
    <div v-for="(field, index) in fields" :key="field.key" class="flex items-center gap-2">
      <Input
        :name="`${name}[${index}]`"
        v-model="field.value"
        :placeholder="t('problems.descriptionForm.constraintsSection.placeholder')"
        class="flex-1"
      />
      <Button
        type="button"
        variant="destructive"
        size="icon"
        class="w-9 shrink-0"
        @click="deleteConstraint(index)"
      >
        <IconTrash class="h-4 w-4" />
      </Button>
    </div>

    <!-- Add button -->
    <Button type="button" variant="outline" size="default" class="w-full" @click="addConstraint">
      <IconPlus class="h-4 w-4 mr-2" />
      {{ t('problems.descriptionForm.constraintsSection.addNew') }}
    </Button>
  </div>
</template>
