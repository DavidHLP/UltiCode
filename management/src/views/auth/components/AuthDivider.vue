<script setup lang="ts">
/**
 * AuthDivider - 分隔线组件
 *
 * "或" 文字居中，细线分隔
 */
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineOptions({
  name: 'AuthDivider',
})
</script>

<template>
  <div class="auth-divider">
    <span class="auth-divider__line"></span>
    <span class="auth-divider__text">{{ t('auth.signup.orContinueWith') }}</span>
    <span class="auth-divider__line"></span>
  </div>
</template>

<style scoped>
.auth-divider {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.75rem 0;
}

.auth-divider__line {
  flex: 1;
  height: 1px;
  background: var(--silver-200);
}

.dark .auth-divider__line {
  background: var(--silver-300);
}

.auth-divider__text {
  font-size: var(--uc-text-sm);
  font-weight: var(--uc-font-weight-medium);
  color: var(--silver-500);
  text-transform: uppercase;
  letter-spacing: var(--uc-tracking-label);
  white-space: nowrap;
}
</style>
