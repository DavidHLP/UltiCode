<script setup lang="ts">
/**
 * RegisterForm - Username + email + password registration form
 *
 * Decoupled from any specific auth store: the parent supplies an `onSubmit`
 * callback. Console's `useAuthStore` calls `/auth/register` and redirects
 * to `/`; management passes a placeholder handler that rejects with
 * "auth.messages.contactAdmin" so the same form acts as a "not available"
 * notice without diverging from the shared visual.
 *
 * i18n keys (must exist in the consumer's `auth.register.*` namespace):
 *   title, subtitle, username, usernamePlaceholder, email, emailPlaceholder,
 *   password, passwordPlaceholder, confirmPassword,
 *   confirmPasswordPlaceholder, submit, submitting, alreadyHaveAccount,
 *   signIn, termsAgreement, termsOfService, privacyPolicy
 *
 * Required additional key for password mismatch:
 *   auth.messages.passwordsDoNotMatch
 * Required additional key for submit failure:
 *   auth.messages.registerFailed
 */
import type { HTMLAttributes } from "vue";
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { cn } from "./cn";
import AuthInput from "./AuthInput.vue";
import AuthButton from "./AuthButton.vue";
import AuthDivider from "./AuthDivider.vue";
import OAuthButton from "./OAuthButton.vue";

export interface RegisterPayload {
  username: string;
  password: string;
  email?: string;
  /**
   * Display name. Optional in the payload; the form renders a Name
   * input that the user can leave blank. If blank, `name` is omitted
   * from the callback (we do NOT silently default it to the username
   * — see review M2; consumers that need a non-empty name should
   * enforce that themselves).
   */
  name?: string;
}

/**
 * Per-field server validation error. Surfaced via the optional
 * `fieldErrors` prop so a parent that calls a real backend can show
 * "username already taken" inline next to the field instead of
 * forcing the user to read the global error alert.
 *
 * Pass-through: keys match the input labels (username / email /
 * password / name). Unknown keys are ignored.
 */
export interface RegisterFieldErrors {
  username?: string;
  email?: string;
  password?: string;
  name?: string;
}

const props = withDefaults(
  defineProps<{
    class?: HTMLAttributes["class"];
    /**
     * Async submit handler. Resolves on successful registration; throws
     * on failure. The form does NOT call any auth store directly.
     */
    onSubmit?: (payload: RegisterPayload) => Promise<void>;
    /** Redirect target after successful registration. Defaults to `/`. */
    redirectAfter?: string;
    /** Hide OAuth buttons (e.g. when only password registration is allowed) */
    hideOAuth?: boolean;
    /**
     * Show the optional Name input. Some apps treat name as redundant
     * with username; set `showName={false}` to hide it.
     */
    showName?: boolean;
    /**
     * Per-field errors from the server (e.g. "username taken"). These
     * render inline next to each field, complementing the global
     * `error` ref that catches thrown exceptions.
     */
    fieldErrors?: RegisterFieldErrors;
  }>(),
  {
    redirectAfter: "/",
    hideOAuth: false,
    showName: true,
    fieldErrors: () => ({}),
  },
);

const { t } = useI18n();
const router = useRouter();

const username = ref("");
const name = ref("");
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
    if (props.onSubmit) {
      // Pass the Name field only if the user actually entered one;
      // we don't silently default to username (see review M2).
      const payload: RegisterPayload = {
        username: username.value,
        password: password.value,
      };
      if (email.value) payload.email = email.value;
      if (name.value) payload.name = name.value;
      await props.onSubmit(payload);
    }
    await router.push(props.redirectAfter);
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
      :error="props.fieldErrors.username"
    />

    <!-- Name Field (optional; consumers may hide via :show-name="false") -->
    <AuthInput
      v-if="props.showName"
      v-model="name"
      :label="t('auth.register.name')"
      type="text"
      autocomplete="name"
      :placeholder="t('auth.register.namePlaceholder')"
      :disabled="loading"
      :error="props.fieldErrors.name"
    />

    <!-- Email Field -->
    <AuthInput
      v-model="email"
      :label="t('auth.register.email')"
      type="email"
      autocomplete="email"
      :placeholder="t('auth.register.emailPlaceholder')"
      :disabled="loading"
      :error="props.fieldErrors.email"
    />

    <!-- Password Field -->
    <AuthInput
      v-model="password"
      :label="t('auth.register.password')"
      type="password"
      autocomplete="new-password"
      :placeholder="t('auth.register.passwordPlaceholder')"
      :disabled="loading"
      :error="props.fieldErrors.password"
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

    <!-- Divider + OAuth (optional) -->
    <template v-if="!hideOAuth">
      <AuthDivider />
      <div class="register-form__oauth-grid">
        <OAuthButton provider="github">GitHub</OAuthButton>
        <OAuthButton provider="google">Google</OAuthButton>
      </div>
    </template>

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
  gap: 0.75rem;
}

.register-form__header {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.25rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--border);
}

.register-form__title {
  font-size: var(--uc-text-2xl);
  font-weight: var(--uc-font-weight-bold);
  letter-spacing: var(--uc-tracking-normal);
  color: var(--foreground-strong);
  line-height: 1.1;
}

.dark .register-form__title {
  color: var(--foreground-strong);
}

.register-form__subtitle {
  font-size: var(--uc-text-md);
  color: var(--foreground);
}

.dark .register-form__subtitle {
  color: var(--foreground-muted);
}

.register-form__error {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.875rem 1rem;
  border-left: 3px solid var(--status-error-mark);
  border-radius: 0;
  background: color-mix(in oklch, var(--status-error-mark) 8%, transparent);
  color: var(--foreground-strong);
  font-size: var(--uc-text-sm);
  font-family: var(--uc-font-code);
}

.dark .register-form__error {
  background: color-mix(in oklch, var(--status-error-mark) 15%, transparent);
  border-left-color: var(--status-error-mark);
}

.register-form__error-prefix {
  color: var(--status-error-mark);
  font-weight: var(--uc-font-weight-semibold);
}

.register-form__submit {
  margin-top: 0.5rem;
}

.register-form__signin {
  font-size: var(--uc-text-md);
  color: var(--foreground);
  text-align: center;
  font-family: var(--uc-font-code);
}

.dark .register-form__signin {
  color: var(--foreground-muted);
}

.register-form__signin a {
  color: var(--link-foreground);
  text-decoration-color: var(--link-decoration);
  text-decoration: underline;
  text-underline-offset: 0.18em;
  font-weight: var(--uc-font-weight-bold);
}

.register-form__signin a:hover {
  text-decoration: underline;
}

.register-form__oauth-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}
</style>