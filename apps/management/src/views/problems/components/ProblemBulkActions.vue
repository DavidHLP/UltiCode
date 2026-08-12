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
      'mt-4 flex items-center justify-between border border-[var(--status-warning-mark)] bg-[color-mix(in_oklch,_var(--status-warning-mark)_8%,_transparent)] dark:bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)] p-3',
      'animate-in fade-in slide-in-from-top-2 duration-200',
    ]"
  >
    <div class="flex items-center gap-3">
      <span class="font-data text-xs text-[var(--foreground-strong)] uppercase tracking-wider">
        {{ selectedCount }} selected
      </span>
      <div class="h-4 w-px bg-[var(--border-subtle)]" />
      <div class="flex items-center gap-1">
        <Button
          variant="terminal"
          size="sm"
          class="h-7 font-data text-2xs border-[var(--status-success-mark)] text-foreground-strong hover:bg-[color-mix(in_oklch,_var(--status-success-mark)_15%,_transparent)]"
          :disabled="loading"
          @click="emit('bulk-publish')"
        >
          <IconEye class="h-3 w-3 mr-1" />
          Publish
        </Button>
        <Button
          variant="terminal"
          size="sm"
          class="h-7 font-data text-2xs border-[var(--status-warning-mark)] text-foreground-strong hover:bg-[color-mix(in_oklch,_var(--status-warning-mark)_15%,_transparent)]"
          :disabled="loading"
          @click="emit('bulk-unpublish')"
        >
          <IconEyeOff class="h-3 w-3 mr-1" />
          Unpublish
        </Button>
        <Button
          variant="terminal"
          size="sm"
          class="h-7 font-data text-2xs border-[var(--status-error-mark)] text-foreground-strong hover:bg-[color-mix(in_oklch,_var(--status-error-mark)_15%,_transparent)]"
          :disabled="loading"
          @click="emit('bulk-delete')"
        >
          <IconTrash class="h-3 w-3 mr-1" />
          Delete
        </Button>
        <Button
          variant="terminal"
          size="sm"
          class="h-7 font-data text-2xs border-[var(--primary)] text-[var(--primary)] hover:bg-[color-mix(in_oklch,_var(--primary)_15%,_transparent)]"
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
      class="h-7 w-7 p-0 text-[var(--foreground-muted)]"
      @click="emit('clear-selection')"
    >
      <IconX class="h-4 w-4" />
    </Button>
  </div>
</template>
