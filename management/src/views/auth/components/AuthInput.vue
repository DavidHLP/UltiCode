<script setup lang="ts">
/**
 * AuthInput - 浮动标签输入框
 *
 * 特点：
 * - 浮动标签动画
 * - 底部细线样式
 * - 聚焦微光效果
 * - 错误状态样式
 */
import type { HTMLAttributes, InputHTMLAttributes } from 'vue'
import { computed, ref, useId } from 'vue'
import { cn } from '@/lib/utils'

const props = withDefaults(
  defineProps<{
    class?: HTMLAttributes['class']
    modelValue?: string
    label: string
    type?: InputHTMLAttributes['type']
    id?: string
    placeholder?: string
    disabled?: boolean
    error?: string
    autocomplete?: string
  }>(),
  {
    type: 'text',
    placeholder: ' ',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

defineOptions({
  name: 'AuthInput',
})

const inputId = computed(() => props.id || `auth-input-${useId()}`)
const isFocused = ref(false)
const hasValue = computed(() => !!props.modelValue)
const isFloating = computed(() => isFocused.value || hasValue.value)

function handleInput(event: Event) {
  const target = event.target as HTMLInputElement
  emit('update:modelValue', target.value)
}

function handleFocus() {
  isFocused.value = true
}

function handleBlur() {
  isFocused.value = false
}
</script>

<template>
  <div
    :class="
      cn(
        'auth-input',
        {
          'auth-input--focused': isFocused,
          'auth-input--has-value': hasValue,
          'auth-input--error': error,
          'auth-input--disabled': disabled,
        },
        props.class,
      )
    "
  >
    <div class="auth-input__container">
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
      <label
        :for="inputId"
        class="auth-input__label"
        :class="{ 'auth-input__label--floating': isFloating }"
      >
        {{ label }}
      </label>
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
  width: 100%;
}

.auth-input__container {
  position: relative;
  padding-top: 0.5rem;
}

/* Input field */
.auth-input__field {
  width: 100%;
  height: 2.75rem;
  padding: 0 0 0.5rem;
  font-size: var(--uc-text-sm);
  color: var(--foreground);
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--border);
  border-radius: 0;
  outline: none;
  transition: border-color var(--transition-fast);
}

.auth-input__field::placeholder {
  color: transparent;
}

.auth-input--focused .auth-input__field {
  border-color: var(--accent-primary);
}

.auth-input--error .auth-input__field {
  border-color: var(--status-error);
}

.auth-input--disabled .auth-input__field {
  cursor: not-allowed;
  opacity: 0.5;
}

/* Floating label */
.auth-input__label {
  position: absolute;
  left: 0;
  top: 1.125rem;
  font-size: var(--uc-text-md);
  color: var(--silver-500);
  pointer-events: none;
  transform-origin: left center;
  transition:
    transform var(--transition-fast),
    color var(--transition-fast),
    font-size var(--transition-fast);
}

.auth-input__label--floating {
  transform: translateY(-1.25rem) scale(0.75);
  color: var(--silver-600);
}

.auth-input--focused .auth-input__label--floating {
  color: var(--accent-primary);
}

.auth-input--error .auth-input__label--floating {
  color: var(--status-error);
}

/* Bottom line with glow effect */
.auth-input__line {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: var(--accent-primary);
  transform: scaleX(0);
  transform-origin: center;
  transition: transform var(--transition-fast);
}

.auth-input__line-glow {
  position: absolute;
  inset: -2px;
  background: var(--accent-primary);
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

.auth-input--error .auth-input__line {
  background: var(--status-error);
}

.auth-input--error .auth-input__line-glow {
  background: var(--status-error);
}

/* Error message */
.auth-input__error {
  margin-top: 0.375rem;
  font-size: var(--uc-text-sm);
  color: var(--status-error);
  font-family: var(--uc-font-code);
}
</style>
