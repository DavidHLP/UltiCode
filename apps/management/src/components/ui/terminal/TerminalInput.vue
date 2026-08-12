<script setup lang="ts">
/**
 * TerminalInput - Monospace input with optional prompt prefix
 *
 * A terminal-style input field with monospace typography and optional
 * command prompt prefix for a command-line aesthetic.
 */
import { cn } from '@/lib/utils'

interface Props {
  modelValue?: string
  placeholder?: string
  prompt?: string
  disabled?: boolean
  type?: 'text' | 'password' | 'search'
}

const props = withDefaults(defineProps<Props>(), {
  prompt: '',
  type: 'text',
  placeholder: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  submit: [value: string]
}>()

function handleInput(event: Event) {
  const target = event.target as HTMLInputElement
  emit('update:modelValue', target.value)
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter') {
    emit('submit', props.modelValue || '')
  }
}
</script>

<template>
  <div
    :class="
      cn(
        'terminal-input flex items-center gap-2 px-3 py-2 transition-all duration-200',
        disabled && 'opacity-50 cursor-not-allowed',
      )
    "
  >
    <span v-if="prompt" class="terminal-prompt text-sm shrink-0">{{ prompt }}</span>
    <input
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      class="flex-1 bg-transparent outline-none text-[var(--foreground)] placeholder:text-[var(--foreground-muted)]"
      @input="handleInput"
      @keydown="handleKeydown"
    />
    <span class="terminal-cursor" />
  </div>
</template>
