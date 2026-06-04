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
    <span class="auth-button__glow"></span>
  </button>
</template>

<style scoped>
.auth-button {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 3rem;
  font-weight: 600;
  font-size: 0.9375rem;
  letter-spacing: 0.02em;
  border-radius: 0;
  border: none;
  cursor: pointer;
  overflow: hidden;
  transition:
    transform var(--transition-fast),
    box-shadow var(--transition-fast),
    background-color var(--transition-fast);
}

.auth-button--primary {
  background: var(--silver-800);
  color: var(--background);
}

.auth-button--primary:hover:not(:disabled) {
  background: var(--silver-900);
  box-shadow: 0 4px 20px oklch(0 0 0 / 0.15);
}

.auth-button--primary:hover:not(:disabled) .auth-button__glow {
  opacity: 0.15;
}

.auth-button--secondary {
  background: transparent;
  color: var(--foreground);
  border: 1px solid var(--silver-300);
}

.auth-button--secondary:hover:not(:disabled) {
  background: var(--silver-50);
  border-color: var(--silver-400);
}

.dark .auth-button--secondary:hover:not(:disabled) {
  background: var(--silver-800);
  border-color: var(--silver-400);
}

.auth-button:active:not(:disabled) {
  transform: scale(0.98);
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
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.auth-button__glow {
  position: absolute;
  inset: 0;
  background: radial-gradient(
    circle at center,
    oklch(1 0 0) 0%,
    transparent 70%
  );
  opacity: 0;
  pointer-events: none;
  transition: opacity var(--transition-fast);
}
</style>
