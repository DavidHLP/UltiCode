<script setup lang="ts">
import { Button } from '@ulticode/design-system/ui/button'
import {
  Eye as IconEye,
  EyeOff as IconEyeOff,
  Trash as IconTrash,
  Pencil as IconPencil,
  X as IconX,
} from '@lucide/vue'

defineProps<{
  selectedCount: number
  loading: boolean
}>()

const emit = defineEmits<{
  'bulk-delete': []
  'bulk-publish': []
  'bulk-unpublish': []
  'bulk-edit': []
  'clear-selection': []
}>()
</script>

<template>
  <div
    v-if="selectedCount > 0"
    :class="[
      'mt-4 flex items-center justify-between border border-status-warning-mark bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)] p-3',
      'animate-in fade-in slide-in-from-top-2 duration-200',
    ]"
  >
    <div class="flex items-center gap-3">
      <span class="font-mono text-xs text-foreground uppercase tracking-wider">
        {{ selectedCount }} selected
      </span>
      <div class="h-4 w-px bg-border" />
      <div class="flex items-center gap-1">
        <Button variant="outline" size="sm" :disabled="loading" @click="emit('bulk-publish')">
          <IconEye class="h-3 w-3 mr-1" />
          Publish
        </Button>
        <Button variant="outline" size="sm" :disabled="loading" @click="emit('bulk-unpublish')">
          <IconEyeOff class="h-3 w-3 mr-1" />
          Unpublish
        </Button>
        <Button variant="outline" size="sm" :disabled="loading" @click="emit('bulk-delete')">
          <IconTrash class="h-3 w-3 mr-1" />
          Delete
        </Button>
        <Button variant="outline" size="sm" @click="emit('bulk-edit')">
          <IconPencil class="h-3 w-3 mr-1" />
          Edit
        </Button>
      </div>
    </div>
    <Button variant="ghost" size="sm" class="w-7 p-0" @click="emit('clear-selection')">
      <IconX class="h-4 w-4" />
    </Button>
  </div>
</template>
