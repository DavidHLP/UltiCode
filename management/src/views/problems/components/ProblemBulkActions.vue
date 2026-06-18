<script setup lang="ts">
import { Button } from '@/components/ui/button'
import { IconEye, IconEyeOff, IconTrash, IconPencil, IconX } from '@tabler/icons-vue'

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
      'mt-4 flex items-center justify-between border border-[var(--terminal-amber)] bg-[color-mix(in_oklch,_var(--terminal-amber)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)] p-3',
      'animate-in fade-in slide-in-from-top-2 duration-200',
    ]"
  >
    <div class="flex items-center gap-3">
      <span class="font-data text-xs text-[var(--terminal-amber)] uppercase tracking-wider">
        {{ selectedCount }} selected
      </span>
      <div class="h-4 w-px bg-[var(--silver-300)]" />
      <div class="flex items-center gap-1">
        <Button
          variant="terminal"
          size="sm"
          class="h-7 font-data text-2xs border-[var(--terminal-green)] text-[var(--terminal-green)] hover:bg-[color-mix(in_oklch,_var(--terminal-green)_15%,_transparent)]"
          :disabled="loading"
          @click="emit('bulk-publish')"
        >
          <IconEye class="h-3 w-3 mr-1" />
          Publish
        </Button>
        <Button
          variant="terminal"
          size="sm"
          class="h-7 font-data text-2xs border-[var(--terminal-amber)] text-[var(--terminal-amber)] hover:bg-[color-mix(in_oklch,_var(--terminal-amber)_15%,_transparent)]"
          :disabled="loading"
          @click="emit('bulk-unpublish')"
        >
          <IconEyeOff class="h-3 w-3 mr-1" />
          Unpublish
        </Button>
        <Button
          variant="terminal"
          size="sm"
          class="h-7 font-data text-2xs border-[var(--terminal-red)] text-[var(--terminal-red)] hover:bg-[color-mix(in_oklch,_var(--terminal-red)_15%,_transparent)]"
          :disabled="loading"
          @click="emit('bulk-delete')"
        >
          <IconTrash class="h-3 w-3 mr-1" />
          Delete
        </Button>
        <Button
          variant="terminal"
          size="sm"
          class="h-7 font-data text-2xs border-[var(--accent-electric)] text-[var(--accent-electric)] hover:bg-[color-mix(in_oklch,_var(--accent-electric)_15%,_transparent)]"
          @click="emit('bulk-edit')"
        >
          <IconPencil class="h-3 w-3 mr-1" />
          Edit
        </Button>
      </div>
    </div>
    <Button
      variant="ghost"
      size="sm"
      class="h-7 w-7 p-0 text-[var(--silver-500)]"
      @click="emit('clear-selection')"
    >
      <IconX class="h-4 w-4" />
    </Button>
  </div>
</template>
