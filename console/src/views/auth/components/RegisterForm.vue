<script setup lang="ts">
/**
 * RegisterForm - Registration form with AuthInput/AuthButton components
 */
import type { HTMLAttributes } from "vue";
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/stores/auth";
import AuthInput from "./AuthInput.vue";
import AuthButton from "./AuthButton.vue";
import AuthDivider from "./AuthDivider.vue";
import OAuthButton from "./OAuthButton.vue";

const props = defineProps<{
  class?: HTMLAttributes["class"];
}>();

const { t } = useI18n();
const router = useRouter();
const authStore = useAuthStore();

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

  loading.value = true;

  try {
    await authStore.register({
      username: username.value,
      password: password.value,
      email: email.value || undefined,
      name: username.value,
    });

    router.push("/");
  } catch (err: unknown) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data
        ?.message || t("auth.messages.registerFailed");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <form :class="cn('register-form', props.class)" @submit="handleSubmit">
    <!-- Header -->
    <div class="register-form__header">
      <h1 class="register-form__title">{{ t("auth.register.title") }}</h1>
      <p class="register-form__subtitle">{{ t("auth.register.subtitle") }}</p>
    </div>

    <!-- Error Alert -->
    <div v-if="error" class="register-form__error">
      <span class="register-form__error-prefix">[ERROR]</span>
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
    <AuthButton :loading="loading" class="register-form__submit">
      <span>{{
        loading ? t("auth.register.submitting") : t("auth.register.submit")
      }}</span>
    </AuthButton>

    <!-- Divider -->
    <AuthDivider />

    <!-- GitHub OAuth -->
    <OAuthButton provider="github">{{
      t("auth.login.loginWithGithub")
    }}</OAuthButton>

    <!-- Google OAuth -->
    <OAuthButton provider="google">{{
      t("auth.login.loginWithGoogle")
    }}</OAuthButton>

    <!-- Sign In Link -->
    <div class="register-form__signin">
      {{ t("auth.register.alreadyHaveAccount") }}
      <a href="/login">{{ t("auth.register.signIn") }}</a>
    </div>
  </form>
</template>

<style scoped>
.register-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.register-form__header {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--silver-100);
}

.dark .register-form__header {
  border-bottom-color: var(--silver-300);
}

.register-form__title {
  font-size: 1.75rem;
  font-weight: 600;
  letter-spacing: -0.03em;
  color: var(--foreground);
  line-height: 1.1;
}

.register-form__subtitle {
  font-size: 0.875rem;
  color: var(--silver-500);
}

.register-form__error {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.875rem 1rem;
  border-left: 3px solid var(--terminal-red);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: color-mix(in oklch, var(--terminal-red) 8%, transparent);
  color: var(--status-error);
  font-size: 0.875rem;
  font-family: "JetBrains Mono", "Fira Code", ui-monospace, monospace;
}

.dark .register-form__error {
  background: color-mix(in oklch, var(--terminal-red) 15%, transparent);
  border-left-color: var(--terminal-red);
}

.register-form__error-prefix {
  font-weight: 600;
  opacity: 0.9;
}

.register-form__submit {
  margin-top: 0.5rem;
}

.register-form__signin {
  font-size: 0.8125rem;
  color: var(--silver-500);
  text-align: center;
}

.register-form__signin a {
  color: var(--accent-primary);
  text-decoration: none;
  font-weight: 500;
}

.register-form__signin a:hover {
  text-decoration: underline;
}
</style>
