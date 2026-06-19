<script setup lang="ts">
/**
 * SignupForm - 注册表单
 *
 * 使用新的 AuthInput/AuthButton 组件
 * 保留 GitHub OAuth
 */
import type { HTMLAttributes } from 'vue'
import { useI18n } from 'vue-i18n'
import { cn } from '@/lib/utils'
import AuthInput from './AuthInput.vue'
import AuthButton from './AuthButton.vue'
import AuthDivider from './AuthDivider.vue'
import OAuthButton from './OAuthButton.vue'
import { ArrowRight } from 'lucide-vue-next'

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()

const { t } = useI18n()
</script>

<template>
  <form :class="cn('signup-form', props.class)">
    <!-- Header -->
    <div class="signup-form__header">
      <h1 class="signup-form__title">{{ t('auth.signup.title') }}</h1>
      <p class="signup-form__subtitle">{{ t('auth.signup.subtitle') }}</p>
    </div>

    <!-- Name Field -->
    <AuthInput
      :label="t('auth.signup.fullName')"
      type="text"
      :placeholder="t('auth.signup.fullNamePlaceholder')"
    />

    <!-- Email Field -->
    <AuthInput
      :label="t('auth.signup.email')"
      type="email"
      :placeholder="t('auth.signup.emailPlaceholder')"
    />

    <!-- Password Field -->
    <AuthInput :label="t('auth.signup.password')" type="password" />

    <!-- Confirm Password Field -->
    <AuthInput :label="t('auth.signup.confirmPassword')" type="password" />

    <!-- Submit Button -->
    <AuthButton class="signup-form__submit">
      <span>{{ t('auth.signup.submit') }}</span>
      <ArrowRight class="signup-form__submit-icon" />
    </AuthButton>

    <!-- Divider -->
    <AuthDivider />

    <!-- GitHub OAuth -->
    <OAuthButton>{{ t('auth.signup.github') }}</OAuthButton>

    <!-- Sign In Link -->
    <div class="signup-form__footer">
      <span>{{ t('auth.signup.alreadyHaveAccount') }}</span>
      <RouterLink to="/login" class="signup-form__link">{{ t('auth.signup.signIn') }}</RouterLink>
    </div>
  </form>
</template>

<style scoped>
.signup-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}


.signup-form__header {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--silver-100);
}

.dark .signup-form__header {
  border-bottom-color: var(--silver-300);
}

.signup-form__title {
  font-size: var(--uc-text-2xl);
  font-weight: var(--uc-font-weight-semibold);
  letter-spacing: var(--uc-tracking-normal);
  color: var(--foreground);
  line-height: 1.1;
}

.signup-form__subtitle {
  font-size: var(--uc-text-sm);
  color: var(--silver-500);
}


.signup-form__submit {
  margin-top: 0.5rem;
}

.signup-form__submit-icon {
  width: 1.125rem;
  height: 1.125rem;
}


.signup-form__footer {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding-top: 1rem;
  border-top: 1px solid var(--silver-100);
  font-size: var(--uc-text-sm);
  color: var(--silver-500);
}

.dark .signup-form__footer {
  border-top-color: var(--silver-300);
}

.signup-form__link {
  color: var(--accent-primary);
  text-decoration: none;
  font-weight: var(--uc-font-weight-medium);
  transition: opacity var(--transition-fast);
}

.signup-form__link:hover {
  opacity: 0.8;
  text-decoration: underline;
}
</style>
