<script setup lang="ts">
/**
 * AuthButton - Precision style button
 */
import type { HTMLAttributes } from "vue";
import { cn } from "@/lib/utils";
import { Loader2 } from "lucide-vue-next";

const props = withDefaults(
  defineProps<{
    class?: HTMLAttributes["class"];
    type?: "button" | "submit" | "reset";
    disabled?: boolean;
    loading?: boolean;
    variant?: "primary" | "secondary";
  }>(),
  {
    type: "submit",
    variant: "primary",
  },
);

defineOptions({
  name: "AuthButton",
});
</script>

<template>
  <button
    :type="type"
    :disabled="disabled || loading"
    :class="
      cn(
        'auth-button',
        `auth-button--${variant}`,
        { 'auth-button--loading': loading },
        props.class,
      )
    "
  >
    <Loader2 v-if="loading" class="auth-button__spinner" />
    <span class="auth-button__content">
      <slot />
    </span>
  </button>
</template>

<style scoped>
.auth-button {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 2.375rem;
  font-family: var(--uc-font-code);
  font-weight: var(--uc-font-weight-bold)
  font-size: var(--uc-text-sm);
  letter-spacing: 0.05em;
  text-transform: uppercase;
  border-radius: 0;
  border: 1px solid transparent;
  cursor: pointer;
  overflow: hidden;
  transition:
    transform var(--transition-fast),
    box-shadow var(--transition-fast),
    background-color var(--transition-fast),
    border-color var(--transition-fast),
    color var(--transition-fast);
}

.auth-button--primary {
  background: var(--primary);
  color: var(--primary-foreground);
  border-color: var(--primary);
  box-shadow: 0 0 0 1px var(--accent-glow);
}

.auth-button--primary:hover:not(:disabled) {
  background: var(--foreground);
  color: var(--background);
  box-shadow: 0 0 0 1px var(--accent-electric);
}

.auth-button--secondary {
  background: transparent;
  color: var(--foreground);
  border-color: var(--border);
}

.auth-button--secondary:hover:not(:disabled) {
  background: var(--surface-sunken);
  border-color: color-mix(in oklch, var(--accent-electric) 35%, var(--border));
}

.auth-button--primary:focus-visible,
.auth-button--secondary:focus-visible {
  outline: 2px solid var(--accent-electric);
  outline-offset: 1px;
}

.auth-button:active:not(:disabled) {
  transform: translateY(0.5px);
}

.auth-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.auth-button--loading .auth-button__content {
  opacity: 0;
}

.auth-button__content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  transition: opacity var(--transition-fast);
}

.auth-button__spinner {
  position: absolute;
  width: 1.25rem;
  height: 1.25rem;
  animation: spin 1s linear infinite;
  color: currentColor;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}
</style>
