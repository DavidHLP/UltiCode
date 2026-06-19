<script setup lang="ts">
/**
 * ForgotPasswordView - Password reset request flow
 *
 * Console-only surface (management does not implement password reset).
 * Uses the shared `AuthLayout` shell + `AuthCard` + `AuthInput` + `AuthButton`
 * primitives; the inline form is local because it's small and tightly
 * coupled to `authApi.forgotPassword`.
 */
import { ref } from "vue";
import { useRouter } from "vue-router";
import { useI18n } from "vue-i18n";
import { toast } from "vue-sonner";
import { authApi } from "@/api/auth";
import AuthCard from "@/shared/auth-ui/src/components/AuthCard.vue";
import AuthLayout from "@/shared/auth-ui/src/layouts/AuthLayout.vue";
import AuthPatternBackground from "@/shared/auth-ui/src/layouts/AuthPatternBackground.vue";
import AuthInput from "@/shared/auth-ui/src/components/AuthInput.vue";
import AuthButton from "@/shared/auth-ui/src/components/AuthButton.vue";

const router = useRouter();
const { t } = useI18n();

const email = ref("");
const loading = ref(false);

async function handleSubmit(e: Event) {
  e.preventDefault();
  loading.value = true;
  try {
    await authApi.forgotPassword({ email: email.value });
    toast.success(t("auth.forgotPassword.successMessage"));
    router.push("/login");
  } catch {
    toast.error(t("auth.messages.requestFailed"));
  } finally {
    loading.value = false;
  }
}

defineOptions({
  name: "ForgotPasswordView",
});
</script>

<template>
  <AuthLayout badge="CODE" version="v2.0.0">
    <template #form>
      <AuthCard :title="t('auth.forgotPassword.terminal')">
        <form class="forgot-form" @submit="handleSubmit">
          <div class="forgot-form__header">
            <h1 class="forgot-form__title">{{ t("auth.forgotPassword.title") }}</h1>
            <p class="forgot-form__subtitle">{{ t("auth.forgotPassword.subtitle") }}</p>
          </div>

          <AuthInput
            v-model="email"
            :label="t('auth.forgotPassword.email')"
            type="email"
            autocomplete="email"
            :placeholder="t('auth.forgotPassword.emailPlaceholder')"
            :disabled="loading"
          />

          <AuthButton :loading="loading">
            {{
              loading
                ? t("auth.forgotPassword.submitting")
                : t("auth.forgotPassword.submit")
            }}
          </AuthButton>

          <div class="forgot-form__back">
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
.forgot-form {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.forgot-form__header {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-bottom: 0.25rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--border);
}

.forgot-form__title {
  font-size: var(--uc-text-2xl);
  font-weight: var(--uc-font-weight-bold);
  letter-spacing: var(--uc-tracking-normal);
  color: var(--solarized-base03);
  line-height: 1.1;
}

.dark .forgot-form__title {
  color: var(--silver-900);
}

.forgot-form__subtitle {
  font-size: var(--uc-text-md);
  color: var(--solarized-base01);
}

.dark .forgot-form__subtitle {
  color: var(--silver-400);
}

.forgot-form__back {
  font-size: var(--uc-text-md);
  color: var(--solarized-base01);
  text-align: center;
  font-family: var(--uc-font-code);
}

.dark .forgot-form__back {
  color: var(--silver-400);
}

.forgot-form__back a {
  color: var(--accent-electric);
  text-decoration: none;
  font-weight: var(--uc-font-weight-bold);
}

.forgot-form__back a:hover {
  text-decoration: underline;
}
</style>