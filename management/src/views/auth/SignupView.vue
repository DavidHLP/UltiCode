<script setup lang="ts">
/**
 * SignupView - 注册页面
 *
 * 使用 AuthGrid 背景 + AuthCard 容器 + SignupForm
 */
import { Terminal } from 'lucide-vue-next'
import { useI18n } from 'vue-i18n'
import SignupForm from '@/views/auth/components/SignupForm.vue'
import AuthCard from '@/views/auth/components/AuthCard.vue'
import AuthGrid from '@/views/auth/components/AuthGrid.vue'

const { t } = useI18n()

defineOptions({
  name: 'SignupView',
})
</script>

<template>
  <div class="auth-layout">
    <!-- Left Side - Form Area -->
    <div class="auth-layout__form-side">
      <!-- Header with Logo -->
      <div class="auth-layout__header">
        <RouterLink to="/" class="auth-logo">
          <div class="auth-logo__icon">
            <Terminal class="size-4" />
          </div>
          <div class="auth-logo__text-group">
            <span class="auth-logo__text">UltiCode</span>
            <span class="auth-logo__badge">ADMIN</span>
          </div>
        </RouterLink>
      </div>

      <!-- Content Area -->
      <div class="auth-layout__content">
        <AuthCard :title="t('auth.register.terminal')">
          <SignupForm />
        </AuthCard>
      </div>

      <!-- Footer -->
      <div class="auth-layout__footer">
        <span class="auth-layout__version">v2.0.0</span>
        <span class="auth-layout__separator">|</span>
        <span class="auth-layout__status">
          <span class="auth-layout__status-dot"></span>
          {{ t('auth.layout.systemOnline') }}
        </span>
      </div>
    </div>

    <!-- Right Side - Grid Background -->
    <AuthGrid>
      <div class="auth-pattern-text">
        <div class="auth-pattern-text__prefix">$</div>
        <h2 class="auth-pattern-text__title">{{ t('auth.layout.joinTheTeam') }}</h2>
        <p class="auth-pattern-text__subtitle">{{ t('auth.layout.joinTheTeamSubtitle') }}</p>
        <div class="auth-pattern-text__cursor"></div>
      </div>
    </AuthGrid>
  </div>
</template>

<style scoped>
.auth-layout {
  display: grid;
  min-height: 100vh;
  min-height: 100svh;
  grid-template-columns: 1fr;
  background: var(--background);
}

@media (min-width: 1024px) {
  .auth-layout {
    grid-template-columns: 2fr 3fr;
  }
}


.auth-layout__form-side {
  display: flex;
  flex-direction: column;
  padding: 1.5rem;
}

@media (min-width: 768px) {
  .auth-layout__form-side {
    padding: 2.5rem 3rem;
  }
}

@media (min-width: 1024px) {
  .auth-layout__form-side {
    padding: 3rem 4rem;
  }
}


.auth-layout__header {
  display: flex;
  justify-content: center;
}

@media (min-width: 1024px) {
  .auth-layout__header {
    justify-content: flex-start;
  }
}

.auth-logo {
  display: flex;
  align-items: center;
  gap: 0.875rem;
  padding: 0.5rem 0.75rem;
  text-decoration: none;
  color: var(--foreground);
  border: 1px solid var(--silver-200);
  border-radius: var(--radius-md);
  background: var(--background);
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast);
}

.auth-logo:hover {
  border-color: var(--silver-300);
  box-shadow: 0 2px 12px oklch(0 0 0 / 0.05);
}

.dark .auth-logo:hover {
  border-color: var(--silver-400);
}

.auth-logo__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  background: var(--silver-800);
  color: var(--background);
  border-radius: 4px;
  transition: box-shadow var(--transition-fast);
}

.auth-logo:hover .auth-logo__icon {
  box-shadow: 0 0 12px var(--accent-glow);
}

.dark .auth-logo__icon {
  background: var(--silver-200);
  color: var(--silver-900);
}

.auth-logo__text-group {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.auth-logo__text {
  font-size: var(--uc-text-md);
  font-weight: var(--uc-font-weight-semibold);
  letter-spacing: var(--uc-tracking-normal);
  color: var(--foreground);
}

.auth-logo__badge {
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-2xs);
  font-weight: var(--uc-font-weight-semibold);
  letter-spacing: var(--uc-tracking-terminal-wide);
  color: var(--silver-500);
  text-transform: uppercase;
}


.auth-layout__content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem 0;
}

@media (min-width: 768px) {
  .auth-layout__content {
    padding: 2rem 0;
  }
}


.auth-layout__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  padding-top: 1rem;
  font-family: var(--uc-font-code);
  font-size: var(--uc-type-code-size);
  color: var(--silver-400);
}

.auth-layout__separator {
  opacity: 0.4;
}

.auth-layout__status {
  display: flex;
  align-items: center;
  gap: 0.375rem;
}

.auth-layout__status-dot {
  width: 0.375rem;
  height: 0.375rem;
  background: var(--terminal-green);
  border-radius: 50%;
  animation: pulse-dot 2s ease-in-out infinite;
}

@keyframes pulse-dot {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.5;
  }
}


.auth-pattern-text {
  padding: 2.5rem;
  text-align: left;
}

.auth-pattern-text__prefix {
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-xl);
  color: var(--accent-primary);
  opacity: 0.8;
  margin-bottom: 0.5rem;
}

.auth-pattern-text__title {
  font-size: var(--uc-text-3xl);
  font-weight: var(--uc-font-weight-medium);
  letter-spacing: var(--uc-tracking-normal);
  line-height: 1.1;
  margin-bottom: 1rem;
  color: var(--silver-100);
  background: linear-gradient(135deg, var(--silver-100) 0%, var(--silver-300) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.auth-pattern-text__subtitle {
  font-family: var(--uc-font-code);
  font-size: var(--uc-text-sm);
  color: var(--silver-400);
  letter-spacing: var(--uc-tracking-normal);
  opacity: 0.8;
}

.auth-pattern-text__cursor {
  display: inline-block;
  width: 0.5rem;
  height: 1.25rem;
  background: var(--accent-primary);
  margin-left: 0.25rem;
  margin-top: 0.5rem;
  animation: blink 1s step-end infinite;
  box-shadow: 0 0 8px var(--accent-glow);
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}
</style>
