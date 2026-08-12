<script setup lang="ts">
/**
 * ResetPasswordView - Password reset with token (from email link)
 *
 * Console-only surface (management does not implement password reset).
 * Uses the shared `AuthLayout` shell + primitives; the inline form is
 * local because it owns token-from-query parsing + custom validation.
 */
import { ref, computed, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { toast } from "vue-sonner";
import { useI18n } from "vue-i18n";
import { authApi } from "@/api/auth";
import AuthCard from "@/shared/auth-ui/src/components/AuthCard.vue";
import AuthLayout from "@/shared/auth-ui/src/layouts/AuthLayout.vue";
import AuthPatternBackground from "@/shared/auth-ui/src/layouts/AuthPatternBackground.vue";
import AuthInput from "@/shared/auth-ui/src/components/AuthInput.vue";
import AuthButton from "@/shared/auth-ui/src/components/AuthButton.vue";

const router = useRouter();
const route = useRoute();
const { t } = useI18n();

const newPassword = ref("");
const confirmPassword = ref("");
const loading = ref(false);
const token = ref("");

onMounted(() => {
  token.value = (route.query.token as string) || "";
  if (!token.value) {
    toast.error(t("auth.messages.passwordResetFailed"));
    router.push("/forgot-password");
  }
});

const passwordsMatch = computed(() => newPassword.value === confirmPassword.value);

const isFormValid = computed(
  () =>
    newPassword.value.length >= 6 &&
    confirmPassword.value.length >= 6 &&
    passwordsMatch.value,
);

async function handleReset(e: Event) {
  e.preventDefault();

  if (!passwordsMatch.value) {
    toast.error(t("auth.validation.passwordMismatch"));
    return;
  }

  loading.value = true;
  try {
    await authApi.resetPassword({
      token: token.value,
      newPassword: newPassword.value,
    });
    toast.success(t("auth.resetPassword.successMessage"));
    router.push("/login");
  } catch {
    toast.error(t("auth.messages.passwordResetFailed"));
  } finally {
    loading.value = false;
  }
}

defineOptions({
  name: "ResetPasswordView",
});
</script>

<template>
  <AuthLayout badge="CODE" version="v2.0.0">
    <template #form>
      <AuthCard :title="t('auth.resetPassword.terminal')">
        <form class="reset-form" @submit="handleReset">
          <div class="reset-form__header">
            <h1 class="reset-form__title">{{ t("auth.resetPassword.title") }}</h1>
            <p class="reset-form__subtitle">{{ t("auth.resetPassword.subtitle") }}</p>
          </div>

          <AuthInput
            v-model="newPassword"
            :label="t('auth.resetPassword.newPassword')"
            type="password"
            autocomplete="new-password"
            :placeholder="t('auth.resetPassword.newPasswordPlaceholder')"
            :disabled="loading"
            :error="
              confirmPassword && !passwordsMatch
                ? t('auth.validation.passwordMismatch')
                : undefined
            "
          />

          <AuthInput
            v-model="confirmPassword"
            :label="t('auth.resetPassword.confirmPassword')"
            type="password"
            autocomplete="new-password"
            :placeholder="t('auth.resetPassword.confirmPasswordPlaceholder')"
            :disabled="loading"
          />

          <AuthButton :loading="loading" :disabled="!isFormValid">
            {{
              loading
                ? t("auth.resetPassword.submitting")
                : t("auth.resetPassword.submit")
            }}
          </AuthButton>

          <div class="reset-form__back">
            {{ t("auth.forgotPassword.rememberPassword") }}
            <a href="/login">{{ t("auth.register.login") }}</a>
          </div>
        </form>
      </AuthCard>
    </template>
    <template #pattern>
      <AuthPatternBackground
        :title="t('auth.layout.codingConsole')"
        :subtitle="t('auth.layout.codingConsoleSubtitle')"
      />
    </template>
  </AuthLayout>
</template>

<style scoped>
.reset-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.reset-form__header {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.25rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--border);
}

.reset-form__title {
  font-size: var(--uc-text-2xl);
  font-weight: var(--uc-font-weight-bold);
  letter-spacing: var(--uc-tracking-normal);
  color: var(--foreground-strong);
  line-height: 1.1;
}

.dark .reset-form__title {
  color: var(--foreground-strong);
}

.reset-form__subtitle {
  font-size: var(--uc-text-md);
  color: var(--foreground);
}

.dark .reset-form__subtitle {
  color: var(--foreground-muted);
}

.reset-form__back {
  font-size: var(--uc-text-md);
  color: var(--foreground);
  text-align: center;
  font-family: var(--uc-font-code);
}

.dark .reset-form__back {
  color: var(--foreground-muted);
}

.reset-form__back a {
  color: var(--primary);
  text-decoration: none;
  font-weight: var(--uc-font-weight-bold);
}

.reset-form__back a:hover {
  text-decoration: underline;
}
</style>