<script setup lang="ts">
/**
 * LoginForm - Username + password login form
 *
 * Decoupled from any specific auth store: the parent supplies an `onSubmit`
 * callback that resolves on success and throws on failure. This lets the
 * same form back console's `useAuthStore` (real Pinia store) and any future
 * 2FA / SSO flow without coupling the shared UI to app state.
 *
 * i18n keys (must exist in the consumer's `auth.login.*` namespace):
 *   title, subtitle, username, password, usernamePlaceholder,
 *   passwordPlaceholder, forgotPassword, submit, submitting, loginFailed,
 *   noAccount, signUp
 */
import type { HTMLAttributes } from "vue";
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { cn } from "./cn";
import AuthInput from "./AuthInput.vue";
import AuthButton from "./AuthButton.vue";
import AuthDivider from "./AuthDivider.vue";
import OAuthButton from "./OAuthButton.vue";
import { ArrowRight } from "lucide-vue-next";

const props = withDefaults(
  defineProps<{
    class?: HTMLAttributes["class"];
    /**
     * Async submit handler. Resolves on successful login; throws on failure
     * (error message is extracted from the thrown value and shown).
     */
    onSubmit?: (credentials: {
      username: string;
      password: string;
    }) => Promise<void>;
    /**
     * Redirect target after successful login. Defaults to the current
     * route's `?redirect=` query string, falling back to `/`.
     */
    redirectAfter?: string;
    /** Hide the "no account / sign up" footer (management uses this) */
    hideSignUp?: boolean;
    /** Hide the OAuth buttons (set when only password auth is allowed) */
    hideOAuth?: boolean;
    /** Hide the "forgot password" link (management does not implement reset) */
    hideForgot?: boolean;
  }>(),
  {
    redirectAfter: "",
    hideSignUp: false,
    hideOAuth: false,
    hideForgot: false,
  },
);

const { t } = useI18n();
const router = useRouter();
const route = useRoute();

const username = ref("");
const password = ref("");
const error = ref("");
const loading = ref(false);

async function handleSubmit(event: Event) {
  event.preventDefault();
  error.value = "";
  loading.value = true;

  try {
    if (props.onSubmit) {
      await props.onSubmit({
        username: username.value,
        password: password.value,
      });
    }
    const target =
      props.redirectAfter || (route.query.redirect as string) || "/";
    await router.push(target);
  } catch (err: unknown) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data
        ?.message || t("auth.messages.loginFailed");
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
    <div v-if="!hideForgot" class="login-form__forgot">
      <a href="/forgot-password">{{ t("auth.login.forgotPassword") }}</a>
    </div>

    <!-- Submit Button -->
    <AuthButton :loading="loading" class="login-form__submit">
      <span>{{
        loading ? t("auth.login.submitting") : t("auth.login.submit")
      }}</span>
      <ArrowRight v-if="!loading" class="login-form__submit-icon" />
    </AuthButton>

    <!-- Divider + OAuth (optional) -->
    <template v-if="!hideOAuth">
      <AuthDivider />
      <div class="login-form__oauth-grid">
        <OAuthButton provider="github">GitHub</OAuthButton>
        <OAuthButton provider="google">Google</OAuthButton>
      </div>
    </template>

    <!-- Sign Up Link -->
    <div v-if="!hideSignUp" class="login-form__signup">
      {{ t("auth.login.noAccount") }}
      <a href="/register">{{ t("auth.login.signUp") }}</a>
    </div>
  </form>
</template>

<style scoped>
.login-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.login-form__header {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.25rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--border);
}

.login-form__title {
  font-size: var(--uc-text-2xl);
  font-weight: var(--uc-font-weight-bold);
  letter-spacing: var(--uc-tracking-normal);
  color: var(--solarized-base03);
  line-height: 1.1;
}

.dark .login-form__title {
  color: var(--silver-900);
}

.login-form__subtitle {
  font-size: var(--uc-text-md);
  color: var(--solarized-base01);
}

.dark .login-form__subtitle {
  color: var(--silver-400);
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
  font-size: var(--uc-text-sm);
  font-family: var(--uc-font-code);
}

.dark .login-form__error {
  background: color-mix(in oklch, var(--terminal-red) 15%, transparent);
  border-left-color: var(--terminal-red);
}

.login-form__error-prefix {
  font-weight: var(--uc-font-weight-semibold);
  opacity: 0.9;
}

.login-form__forgot {
  font-size: var(--uc-text-md);
  text-align: right;
  font-family: var(--uc-font-code);
}

.login-form__forgot a {
  color: var(--accent-electric);
  text-decoration: none;
  font-weight: var(--uc-font-weight-bold);
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
  font-size: var(--uc-text-md);
  color: var(--solarized-base01);
  text-align: center;
  font-family: var(--uc-font-code);
}

.dark .login-form__signup {
  color: var(--silver-400);
}

.login-form__signup a {
  color: var(--accent-electric);
  text-decoration: none;
  font-weight: var(--uc-font-weight-bold);
}

.login-form__signup a:hover {
  text-decoration: underline;
}

.login-form__oauth-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}
</style>