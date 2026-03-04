<script setup lang="ts">
/**
 * AuthCard - 精密仪器风格卡片容器
 *
 * 细边框卡片 + 微光阴影效果 + 淡入动画
 */
import type { HTMLAttributes } from 'vue'
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: HTMLAttributes['class']
  title?: string
}>()

defineOptions({
  name: 'AuthCard',
})
</script>

<template>
  <div :class="cn('auth-card', props.class)">
    <!-- Terminal Window Header -->
    <div class="auth-card__header">
      <div class="auth-card__window-controls">
        <span class="auth-card__control auth-card__control--close"></span>
        <span class="auth-card__control auth-card__control--minimize"></span>
        <span class="auth-card__control auth-card__control--maximize"></span>
      </div>
      <span class="auth-card__title">{{ title }}</span>
    </div>
    <!-- Card Body -->
    <div class="auth-card__body">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.auth-card {
  width: 100%;
  max-width: 25rem;
  border: 1px solid var(--silver-200);
  border-radius: var(--radius-xl);
  background: var(--background);
  box-shadow:
    0 4px 24px -4px oklch(0 0 0 / 0.08),
    0 0 0 1px oklch(0 0 0 / 0.02);
  overflow: hidden;
  animation: card-fade-in 0.4s ease-out;
  transition:
    box-shadow var(--transition-normal),
    border-color var(--transition-normal),
    transform var(--transition-normal);
}

.dark .auth-card {
  border-color: var(--silver-300);
  box-shadow:
    0 4px 24px -4px oklch(0 0 0 / 0.25),
    0 0 0 1px oklch(0 0 0 / 0.1);
}

.auth-card:hover {
  box-shadow:
    0 8px 32px -4px oklch(0 0 0 / 0.1),
    0 0 0 1px var(--accent-primary),
    0 0 20px -4px var(--accent-glow);
}

.auth-card:focus-within {
  box-shadow:
    0 8px 32px -4px oklch(0 0 0 / 0.1),
    0 0 0 2px var(--accent-primary),
    0 0 30px -4px var(--accent-glow);
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

/* Terminal Window Header */
.auth-card__header {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.875rem 1rem;
  background: var(--silver-50);
  border-bottom: 1px solid var(--silver-200);
}

.dark .auth-card__header {
  background: oklch(0.18 0.01 270);
  border-bottom-color: var(--silver-300);
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
  background: oklch(0.6 0.2 25);
}

.auth-card__control--minimize {
  background: oklch(0.75 0.18 90);
}

.auth-card__control--maximize {
  background: oklch(0.7 0.18 145);
}

.auth-card__title {
  font-family: 'JetBrains Mono', 'Fira Code', ui-monospace, monospace;
  font-size: 0.75rem;
  color: var(--silver-500);
  letter-spacing: 0.02em;
}

/* Card Body */
.auth-card__body {
  padding: 2rem;
}

@media (min-width: 768px) {
  .auth-card__body {
    padding: 2.5rem;
  }
}
</style>
