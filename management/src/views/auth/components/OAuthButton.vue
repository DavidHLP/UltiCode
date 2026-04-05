<script setup lang="ts">
/**
 * OAuthButton - OAuth 按钮
 *
 * GitHub 图标，边框按钮样式，悬停效果
 */
import type { HTMLAttributes } from 'vue'
import { Github } from 'lucide-vue-next'
import { cn } from '@/lib/utils'

const props = defineProps<{
  class?: HTMLAttributes['class']
  provider?: 'github'
}>()

defineOptions({
  name: 'OAuthButton',
})

function handleOAuth() {
  // Redirect to GitHub OAuth
  window.location.href = `${import.meta.env.VITE_API_BASE_URL}/auth/github`
}
</script>

<template>
  <button type="button" :class="cn('oauth-button', props.class)" @click="handleOAuth">
    <Github class="oauth-button__icon" />
    <span class="oauth-button__text">
      <slot>Continue with GitHub</slot>
    </span>
  </button>
</template>

<style scoped>
.oauth-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 3rem;
  gap: 0.75rem;
  padding: 0 1.25rem;
  font-weight: 500;
  font-size: 0.9375rem;
  color: var(--foreground);
  background: transparent;
  border: 1px solid var(--silver-300);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    background-color var(--transition-fast),
    border-color var(--transition-fast),
    box-shadow var(--transition-fast);
}

.oauth-button:hover {
  background: var(--silver-50);
  border-color: var(--silver-400);
  box-shadow: 0 2px 8px oklch(0 0 0 / 0.05);
}

.dark .oauth-button:hover {
  background: oklch(0.2 0.01 270);
  border-color: var(--silver-400);
}

.oauth-button:active {
  transform: scale(0.98);
}

.oauth-button__icon {
  width: 1.25rem;
  height: 1.25rem;
}

.oauth-button__text {
  display: flex;
  align-items: center;
}
</style>
