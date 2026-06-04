<script setup lang="ts">
/**
 * AuthInput - Console parameter styled input field
 */
import type { HTMLAttributes, InputHTMLAttributes } from "vue";
import { computed, ref } from "vue";
import { cn } from "@/lib/utils";

const props = withDefaults(
  defineProps<{
    class?: HTMLAttributes["class"];
    modelValue?: string;
    label: string;
    type?: InputHTMLAttributes["type"];
    id?: string;
    placeholder?: string;
    disabled?: boolean;
    error?: string;
    autocomplete?: string;
  }>(),
  {
    type: "text",
    placeholder: "",
  },
);

const emit = defineEmits<{
  "update:modelValue": [value: string];
}>();

defineOptions({
  name: "AuthInput",
});

const inputId = computed(
  () => props.id || `auth-input-${Math.random().toString(36).slice(2, 9)}`,
);
const isFocused = ref(false);

function handleInput(event: Event) {
  const target = event.target as HTMLInputElement;
  emit("update:modelValue", target.value);
}

function handleFocus() {
  isFocused.value = true;
}

function handleBlur() {
  isFocused.value = false;
}
</script>

<template>
  <div
    :class="
      cn(
        'auth-input flex flex-col w-full',
        {
          'auth-input--focused': isFocused,
          'auth-input--error': error,
          'auth-input--disabled': disabled,
        },
        props.class,
      )
    "
  >
    <label :for="inputId" class="auth-input__label">
      <span class="text-[var(--accent-electric)] font-bold mr-1.5">></span>
      {{ label }}
    </label>
    <div class="relative mt-1 w-full">
      <input
        :id="inputId"
        :type="type"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :autocomplete="autocomplete"
        class="auth-input__field"
        @input="handleInput"
        @focus="handleFocus"
        @blur="handleBlur"
      />
    </div>
    <p v-if="error" class="auth-input__error">{{ error }}</p>
  </div>
</template>

<style scoped>
.auth-input {
  position: relative;
}

.auth-input__label {
  font-family: "JetBrains Mono", "Fira Code", ui-monospace, monospace;
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--solarized-base01);
}

.dark .auth-input__label {
  color: var(--silver-400);
}

.auth-input__field {
  width: 100%;
  height: 2.375rem;
  padding: 0 0.75rem;
  font-family: "JetBrains Mono", "Fira Code", ui-monospace, monospace;
  font-size: 0.875rem;
  color: var(--solarized-base03);
  background: var(--surface-sunken);
  border: 1px solid var(--border);
  border-radius: 0;
  outline: none;
  transition: all var(--transition-fast);
}

.dark .auth-input__field {
  color: var(--silver-900);
}

.auth-input__field:focus {
  background: var(--card);
  border-color: var(--accent-electric);
  box-shadow: 0 0 0 1px var(--accent-electric-glow);
}

.auth-input--error .auth-input__field {
  border-color: var(--status-error);
}

.auth-input--disabled .auth-input__field {
  opacity: 0.5;
  cursor: not-allowed;
}

.auth-input__error {
  margin-top: 0.375rem;
  font-size: 0.75rem;
  color: var(--status-error);
  font-family: "JetBrains Mono", "Fira Code", ui-monospace, monospace;
}
</style>
