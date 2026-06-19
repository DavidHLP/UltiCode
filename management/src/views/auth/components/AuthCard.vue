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
  border: 1px solid var(--border);
  border-radius: 0;
  background: var(--background);
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

.auth-card:hover {
  border-color: color-mix(in oklch, var(--accent-primary) 45%, var(--border));
  box-shadow: var(--shadow-float-hover);
}

.auth-card:focus-within {
  border-color: var(--accent-primary);
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
  gap: 0.625rem;
  padding: 0.625rem 1rem;
  background: transparent;
  border-bottom: 1px solid var(--border);
}

.auth-card__window-controls {
  display: flex;
  gap: 0.375rem;
}

.auth-card__control {
  width: 0.5rem;
  height: 0.5rem;
  border-radius: 50%;
  opacity: 0.55;
}

.auth-card__control--close {
  background: var(--terminal-red);
  opacity: 0.65;
}

.auth-card__control--minimize {
  background: var(--terminal-amber);
  opacity: 0.65;
}

.auth-card__control--maximize {
  background: var(--terminal-green);
  opacity: 0.65;
}

.auth-card__title {
  font-family: var(--uc-font-code);
  font-size: var(--uc-type-code-size);
  color: var(--silver-500);
  letter-spacing: var(--uc-tracking-label);
}


.auth-card__body {
  padding: 2rem;
}

@media (min-width: 768px) {
  .auth-card__body {
    padding: 2.5rem;
  }
}
</style>
