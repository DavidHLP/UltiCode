<script setup lang="ts">
/**
 * AuthCard - Shared design-system card container
 */
import type { HTMLAttributes } from "vue";
import { cn } from "./cn";

const props = defineProps<{
  class?: HTMLAttributes["class"];
  title?: string;
}>();

defineOptions({
  name: "AuthCard",
});
</script>

<template>
  <div :class="cn('auth-card', props.class)">
    <div class="auth-card__header">
      <div class="auth-card__window-controls">
        <span class="auth-card__control auth-card__control--close"></span>
        <span class="auth-card__control auth-card__control--minimize"></span>
        <span class="auth-card__control auth-card__control--maximize"></span>
      </div>
      <span class="auth-card__title">{{ title }}</span>
    </div>
    <div class="auth-card__body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.auth-card {
  width: 100%;
  max-width: 25rem;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  background: var(--surface-elevated);
  box-shadow: var(--shadow-float);
  overflow: hidden;
  animation: card-fade-in 0.4s ease-out;
  transition:
    box-shadow var(--transition-normal),
    border-color var(--transition-normal);
}

.dark .auth-card {
  border-color: var(--border);
  box-shadow: var(--shadow-float);
}

.auth-card:hover,
.auth-card:focus-within {
  border-color: var(--border-control);
  box-shadow: var(--shadow-float-hover);
}

@keyframes card-fade-in {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.auth-card__header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.625rem 1rem;
  background: var(--surface-sunken);
  border-bottom: 1px solid var(--border);
}

.auth-card__window-controls {
  display: flex;
  gap: 0.5rem;
}

.auth-card__control {
  width: 0.75rem;
  height: 0.75rem;
  border-radius: 50%;
  opacity: 0.8;
}

.auth-card__control--close {
  background: var(--status-error-mark);
}

.auth-card__control--minimize {
  background: var(--status-warning-mark);
}

.auth-card__control--maximize {
  background: var(--status-success-mark);
}

.auth-card__title {
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-sm);
  color: var(--foreground-muted);
  letter-spacing: var(--uc-tracking-normal);
}

.auth-card__body {
  padding: 1.25rem;
}

@media (min-width: 768px) {
  .auth-card__body {
    padding: 1.5rem;
  }
}
</style>
