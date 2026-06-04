<script setup lang="ts">
/**
 * AuthCard - Terminal Precision style card container
 */
import type { HTMLAttributes } from "vue";
import { cn } from "@/lib/utils";

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
  border-radius: 0;
  background: var(--card);
  box-shadow: 4px 4px 0px 0px var(--border);
  overflow: hidden;
  animation: card-fade-in 0.4s ease-out;
  transition: all var(--transition-normal);
}

.dark .auth-card {
  border-color: var(--border);
}

.auth-card:hover,
.auth-card:focus-within {
  box-shadow: 6px 6px 0px 0px var(--accent-electric);
  border-color: var(--accent-electric);
  transform: translate(-2px, -2px);
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
  background: var(--silver-50);
  border-bottom: 1px solid var(--border);
}

.dark .auth-card__header {
  background: var(--silver-800);
  border-bottom-color: var(--border);
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
  background: var(--terminal-red);
}

.auth-card__control--minimize {
  background: var(--terminal-amber);
}

.auth-card__control--maximize {
  background: var(--terminal-green);
}

.auth-card__title {
  font-family: "JetBrains Mono", "Fira Code", ui-monospace, monospace;
  font-size: 0.75rem;
  color: var(--silver-500);
  letter-spacing: 0.02em;
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
