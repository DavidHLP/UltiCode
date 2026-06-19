<script setup lang="ts">
/**
 * SignupForm - Registration form
 *
 * Management surfaces do not allow open self-registration; accounts are
 * provisioned through the opt-in `AdminBootstrapRunner` or by an
 * existing administrator. This form is a UI placeholder that matches
 * the console's RegisterForm so the auth design stays consistent.
 */
import type { HTMLAttributes } from "vue";
import { ref } from "vue";
import { useI18n } from "vue-i18n";
import { cn } from "@/lib/utils";
import AuthInput from "./AuthInput.vue";
import AuthButton from "./AuthButton.vue";
import AuthDivider from "./AuthDivider.vue";
import OAuthButton from "./OAuthButton.vue";

const props = defineProps<{
  class?: HTMLAttributes["class"];
}>();

const { t } = useI18n();

const username = ref("");
const email = ref("");
const password = ref("");
const confirmPassword = ref("");
const error = ref("");
const loading = ref(false);

async function handleSubmit(event: Event) {
  event.preventDefault();
  error.value = "";

  if (password.value !== confirmPassword.value) {
    error.value = t("auth.messages.passwordsDoNotMatch");
    return;
  }

  // Management does not expose self-registration. Surface a clear
  // message so the design parity with the console does not hide the
  // operational policy.
  error.value = t("auth.messages.registerFailed");
  loading.value = false;
}
</script>

<template>
  <form :class="cn('signup-form', props.class)" @submit="handleSubmit">
    <!-- Header -->
    <div class="signup-form__header">
      <h1 class="signup-form__title">{{ t("auth.register.title") }}</h1>
      <p class="signup-form__subtitle">{{ t("auth.register.subtitle") }}</p>
    </div>

    <!-- Error Alert -->
    <div v-if="error" class="signup-form__error">
      <span class="signup-form__error-prefix">[ERROR]</span>
      <span>{{ error }}</span>
    </div>

    <!-- Username Field -->
    <AuthInput
      v-model="username"
      :label="t('auth.register.username')"
      type="text"
      autocomplete="username"
      :placeholder="t('auth.register.usernamePlaceholder')"
      :disabled="loading"
    />

    <!-- Email Field -->
    <AuthInput
      v-model="email"
      :label="t('auth.register.email')"
      type="email"
      autocomplete="email"
      :placeholder="t('auth.register.emailPlaceholder')"
      :disabled="loading"
    />

    <!-- Password Field -->
    <AuthInput
      v-model="password"
      :label="t('auth.register.password')"
      type="password"
      autocomplete="new-password"
      :placeholder="t('auth.register.passwordPlaceholder')"
      :disabled="loading"
    />

    <!-- Confirm Password Field -->
    <AuthInput
      v-model="confirmPassword"
      :label="t('auth.register.confirmPassword')"
      type="password"
      autocomplete="new-password"
      :placeholder="t('auth.register.confirmPasswordPlaceholder')"
      :disabled="loading"
    />

    <!-- Submit Button -->
    <AuthButton :loading="loading" class="signup-form__submit">
      <span>{{
        loading ? t("auth.register.submitting") : t("auth.register.submit")
      }}</span>
    </AuthButton>

    <!-- Divider -->
    <AuthDivider />

    <!-- OAuth buttons grid -->
    <div class="signup-form__oauth-grid">
      <OAuthButton provider="github">GitHub</OAuthButton>
      <OAuthButton provider="google">Google</OAuthButton>
    </div>

    <!-- Sign In Link -->
    <div class="signup-form__signin">
      {{ t("auth.register.alreadyHaveAccount") }}
      <a href="/login">{{ t("auth.register.signIn") }}</a>
    </div>
  </form>
</template>

<style scoped>
.signup-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.signup-form__header {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.25rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--border);
}

.signup-form__title {
  font-size: var(--uc-text-2xl);
  font-weight: var(--uc-font-weight-bold);
  letter-spacing: var(--uc-tracking-normal);
  color: var(--solarized-base03);
  line-height: 1.1;
}

.dark .signup-form__title {
  color: var(--silver-900);
}

.signup-form__subtitle {
  font-size: var(--uc-text-md);
  color: var(--solarized-base01);
}

.dark .signup-form__subtitle {
  color: var(--silver-400);
}

.signup-form__error {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.875rem 1rem;
  border-left: 3px solid var(--terminal-red);
  border-radius: 0;
  background: color-mix(in oklch, var(--terminal-red) 8%, transparent);
  color: var(--status-error);
  font-size: var(--uc-text-sm);
  font-family: var(--uc-font-code);
}

.dark .signup-form__error {
  background: color-mix(in oklch, var(--terminal-red) 15%, transparent);
  border-left-color: var(--terminal-red);
}

.signup-form__error-prefix {
  font-weight: var(--uc-font-weight-semibold);
  opacity: 0.9;
}

.signup-form__submit {
  margin-top: 0.5rem;
}

.signup-form__signin {
  font-size: var(--uc-text-md);
  color: var(--solarized-base01);
  text-align: center;
  font-family: var(--uc-font-code);
}

.dark .signup-form__signin {
  color: var(--silver-400);
}

.signup-form__signin a {
  color: var(--accent-electric);
  text-decoration: none;
  font-weight: var(--uc-font-weight-bold);
}

.signup-form__signin a:hover {
  text-decoration: underline;
}

.signup-form__oauth-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}
</style>
