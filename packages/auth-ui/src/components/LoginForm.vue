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
import { Alert, AlertDescription } from "@ulticode/design-system/ui/alert";
import { FieldGroup } from "@ulticode/design-system/ui/field";
import type { HTMLAttributes } from "vue";
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { cn } from "./cn";
import AuthInput from "./AuthInput.vue";
import AuthButton from "./AuthButton.vue";
import AuthDivider from "./AuthDivider.vue";
import OAuthButton from "./OAuthButton.vue";
import { ArrowRight } from "@lucide/vue";

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
    /** Fallback redirect when neither an explicit nor query redirect is valid. */
    fallbackRedirect?: string;
    /** Hide the "no account / sign up" footer (management uses this) */
    hideSignUp?: boolean;
    /** Hide the OAuth buttons (set when only password auth is allowed) */
    hideOAuth?: boolean;
    /** Hide the "forgot password" link (management does not implement reset) */
    hideForgot?: boolean;
  }>(),
  {
    redirectAfter: "",
    fallbackRedirect: "/",
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

function resolveInternalRedirect(value: unknown): string | null {
  const candidate = Array.isArray(value) ? value[0] : value;
  if (typeof candidate !== "string") return null;
  if (!candidate.startsWith("/")) return null;
  if (candidate.startsWith("//")) return null;
  if (candidate.includes("\\")) return null;
  return candidate;
}

async function handleSubmit(event: Event) {
  event.preventDefault();
  if (loading.value) return;
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
      resolveInternalRedirect(props.redirectAfter) ??
      resolveInternalRedirect(route.query.redirect) ??
      resolveInternalRedirect(props.fallbackRedirect) ??
      "/";
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
  <form :class="cn('flex flex-col gap-5', props.class)" @submit="handleSubmit">
    <!-- Header -->
    <div class="flex flex-col gap-2">
      <h1 class="text-2xl font-semibold">{{ t("auth.login.title") }}</h1>
      <p class="text-sm text-muted-foreground">
        {{ t("auth.login.subtitle") }}
      </p>
    </div>

    <!-- Error Alert -->
    <Alert v-if="error" variant="destructive" role="alert"
      ><AlertDescription>{{ error }}</AlertDescription></Alert
    >

    <FieldGroup>
      <!-- Username Field -->
      <AuthInput
        required
        v-model="username"
        :label="t('auth.login.username')"
        type="text"
        autocomplete="username"
        :placeholder="t('auth.login.usernamePlaceholder')"
        :disabled="loading"
      />

      <!-- Password Field -->
      <AuthInput
        required
        v-model="password"
        :label="t('auth.login.password')"
        type="password"
        autocomplete="current-password"
        :placeholder="t('auth.login.passwordPlaceholder')"
        :disabled="loading"
      />

      <!-- Forgot Password -->
      <div v-if="!hideForgot" class="text-end text-sm">
        <a href="/forgot-password">{{ t("auth.login.forgotPassword") }}</a>
      </div>
    </FieldGroup>
    <!-- Submit Button -->
    <AuthButton :loading="loading" class="">
      <span>{{
        loading ? t("auth.login.submitting") : t("auth.login.submit")
      }}</span>
      <ArrowRight v-if="!loading" class="" />
    </AuthButton>

    <!-- Divider + OAuth (optional) -->
    <template v-if="!hideOAuth">
      <AuthDivider />
      <div class="grid grid-cols-2 gap-3">
        <OAuthButton provider="github">GitHub</OAuthButton>
        <OAuthButton provider="google">Google</OAuthButton>
      </div>
    </template>

    <!-- Sign Up Link -->
    <div v-if="!hideSignUp" class="text-center text-sm text-muted-foreground">
      {{ t("auth.login.noAccount") }}
      <a href="/register">{{ t("auth.login.signUp") }}</a>
    </div>
  </form>
</template>
