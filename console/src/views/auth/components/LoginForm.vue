<script setup lang="ts">
/**
 * LoginForm - Login form with AuthInput/AuthButton components
 */
import type { HTMLAttributes } from "vue";
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/stores/auth";
import AuthInput from "./AuthInput.vue";
import AuthButton from "./AuthButton.vue";
import AuthDivider from "./AuthDivider.vue";
import OAuthButton from "./OAuthButton.vue";
import { ArrowRight } from "lucide-vue-next";

const props = defineProps<{
  class?: HTMLAttributes["class"];
}>();

const { t } = useI18n();
const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const username = ref("");
const password = ref("");
const error = ref("");
const loading = ref(false);

async function handleSubmit(event: Event) {
  event.preventDefault();
  error.value = "";
  loading.value = true;

  try {
    await authStore.login({
      username: username.value,
      password: password.value,
    });

    if (authStore.isAuthenticated) {
      const redirect = (route.query.redirect as string) || "/";
      await router.push(redirect);
    }
  } catch (err: unknown) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data
        ?.message || t("auth.login.loginFailed");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <form :class="cn('login-form', props.class)" @submit="handleSubmit">
    <!-- Header -->
    <div class="login-form__header">
      <h1 class="login-form__title">{{ t("auth.login.title") }}</h1>
      <p class="login-form__subtitle">{{ t("auth.login.subtitle") }}</p>
    </div>

    <!-- Error Alert -->
    <div v-if="error" class="login-form__error">
      <span class="login-form__error-prefix">[ERROR]</span>
      <span>{{ error }}</span>
    </div>

    <!-- Username Field -->
    <AuthInput
      v-model="username"
      :label="t('auth.login.username')"
      type="text"
      autocomplete="username"
      :placeholder="t('auth.login.usernamePlaceholder')"
      :disabled="loading"
    />

    <!-- Password Field -->
    <AuthInput
      v-model="password"
      :label="t('auth.login.password')"
      type="password"
      autocomplete="current-password"
      :placeholder="t('auth.login.passwordPlaceholder')"
      :disabled="loading"
    />

    <!-- Forgot Password -->
    <div class="login-form__forgot">
      <a href="/forgot-password">{{ t("auth.login.forgotPassword") }}</a>
    </div>

    <!-- Submit Button -->
    <AuthButton :loading="loading" class="login-form__submit">
      <span>{{
        loading ? t("auth.login.submitting") : t("auth.login.submit")
      }}</span>
      <ArrowRight v-if="!loading" class="login-form__submit-icon" />
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

    <!-- Sign Up Link -->
    <div class="login-form__signup">
      {{ t("auth.login.noAccount") }}
      <a href="/register">{{ t("auth.login.signUp") }}</a>
    </div>
  </form>
</template>

<style scoped>
.login-form {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.login-form__header {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
  padding-bottom: 1rem;
  border-bottom: 1px solid var(--silver-100);
}

.dark .login-form__header {
  border-bottom-color: var(--silver-300);
}

.login-form__title {
  font-size: 1.75rem;
  font-weight: 600;
  letter-spacing: -0.03em;
  color: var(--foreground);
  line-height: 1.1;
}

.login-form__subtitle {
  font-size: 0.875rem;
  color: var(--silver-500);
}

.login-form__error {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.875rem 1rem;
  border-left: 3px solid var(--terminal-red);
  border-radius: 0;
  background: color-mix(in oklch, var(--terminal-red) 8%, transparent);
  color: var(--status-error);
  font-size: 0.875rem;
  font-family: "JetBrains Mono", "Fira Code", ui-monospace, monospace;
}

.dark .login-form__error {
  background: color-mix(in oklch, var(--terminal-red) 15%, transparent);
  border-left-color: var(--terminal-red);
}

.login-form__error-prefix {
  font-weight: 600;
  opacity: 0.9;
}

.login-form__forgot {
  font-size: 0.8125rem;
  text-align: right;
}

.login-form__forgot a {
  color: var(--accent-primary);
  text-decoration: none;
}

.login-form__forgot a:hover {
  text-decoration: underline;
}

.login-form__submit {
  margin-top: 0.5rem;
}

.login-form__submit-icon {
  width: 1.125rem;
  height: 1.125rem;
}

.login-form__signup {
  font-size: 0.8125rem;
  color: var(--silver-500);
  text-align: center;
}

.login-form__signup a {
  color: var(--accent-primary);
  text-decoration: none;
  font-weight: 500;
}

.login-form__signup a:hover {
  text-decoration: underline;
}
</style>
