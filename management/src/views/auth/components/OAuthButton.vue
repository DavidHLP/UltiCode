<script setup lang="ts">
/**
 * OAuthButton - OAuth 按钮
 *
 * GitHub 图标，边框按钮样式，悬停效果
 */
import type { HTMLAttributes } from 'vue'
import { useI18n } from 'vue-i18n'
import { Github } from 'lucide-vue-next'
import { cn } from '@/lib/utils'

const { t } = useI18n()

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
      <slot>{{ t('auth.login.continueWithGithub') }}</slot>
    </span>
  </button>
</template>

<style scoped>
.oauth-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 2.75rem;
  gap: 0.75rem;
  padding: 0 1.25rem;
  font-weight: var(--uc-font-weight-medium);
  font-size: var(--uc-text-sm);
  color: var(--foreground);
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 0;
  cursor: pointer;
  transition:
    background-color var(--transition-fast),
    border-color var(--transition-fast),
    box-shadow var(--transition-fast),
    transform var(--transition-fast);
}

.oauth-button:hover {
  background: var(--surface-sunken);
  border-color: color-mix(in oklch, var(--accent-primary) 35%, var(--border));
}

.oauth-button:focus-visible {
  outline: 2px solid var(--accent-primary);
  outline-offset: 1px;
}

.oauth-button:active {
  background: color-mix(in oklch, var(--accent-primary) 12%, transparent);
  transform: translateY(0.5px);
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
