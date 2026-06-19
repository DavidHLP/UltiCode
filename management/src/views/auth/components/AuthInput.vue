<script setup lang="ts">
/**
 * AuthInput - Console parameter styled input field
 */
import type { HTMLAttributes, InputHTMLAttributes } from "vue";
import { computed, ref, useId } from "vue";
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

const autoId = useId();
const inputId = computed(() => props.id || `auth-input-${autoId}`);
const isFocused = ref(false);
const hasValue = computed(() => !!props.modelValue);
const isFloating = computed(() => isFocused.value || hasValue.value);

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
          'auth-input--has-value': hasValue,
          'auth-input--floating': isFloating,
          'auth-input--error': error,
          'auth-input--disabled': disabled,
        },
        props.class,
      )
    "
  >
    <label
      :for="inputId"
      class="auth-input__label"
      :class="{ 'auth-input__label--floating': isFloating }"
    >
      <span class="auth-input__prompt">&gt;</span>
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
      <div class="auth-input__line">
        <div class="auth-input__line-glow"></div>
      </div>
    </div>
    <p v-if="error" class="auth-input__error">{{ error }}</p>
  </div>
</template>

<style scoped>
.auth-input {
  position: relative;
}

.auth-input__label {
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-sm);
  font-weight: var(--uc-font-weight-bold);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--solarized-base01);
  transform-origin: left center;
  transition:
    transform var(--transition-fast),
    color var(--transition-fast),
    font-size var(--transition-fast);
}

.auth-input__label--floating {
  font-size: var(--uc-text-xs);
  color: var(--silver-500);
  transform: translateY(-0.5rem);
}

.auth-input--focused .auth-input__label--floating {
  color: var(--accent-electric);
}

.auth-input--error .auth-input__label--floating {
  color: var(--status-error);
}

.dark .auth-input__label {
  color: var(--silver-400);
}

.auth-input__prompt {
  color: var(--accent-electric);
  font-weight: var(--uc-font-weight-bold);
  margin-right: 0.375rem;
}

.auth-input__field {
  width: 100%;
  height: 2.375rem;
  padding: 0 0.75rem;
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-sm);
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

.auth-input__field::placeholder {
  color: var(--solarized-base1);
  opacity: 0.7;
}

.dark .auth-input__field::placeholder {
  color: var(--solarized-base01);
  opacity: 0.7;
}

.auth-input--error .auth-input__field {
  border-color: var(--status-error);
}

.auth-input--disabled .auth-input__field {
  opacity: 0.5;
  cursor: not-allowed;
}

.auth-input__line {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--accent-electric);
  transform: scaleX(0);
  transform-origin: center;
  transition: transform var(--transition-fast);
}

.auth-input__line-glow {
  position: absolute;
  inset: -2px;
  background: var(--accent-electric);
  filter: blur(4px);
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.auth-input--focused .auth-input__line {
  transform: scaleX(1);
}

.auth-input--focused .auth-input__line-glow {
  opacity: 0.5;
}

.auth-input--error .auth-input__line,
.auth-input--error .auth-input__line-glow {
  background: var(--status-error);
}

.auth-input__error {
  margin-top: 0.375rem;
  font-size: var(--uc-text-sm);
  color: var(--status-error);
  font-family: var(--uc-font-code);
}
</style>
