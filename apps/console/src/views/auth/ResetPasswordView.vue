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
import AuthCard from "@ulticode/auth-ui/components/AuthCard.vue";
import AuthLayout from "@ulticode/auth-ui/layouts/AuthLayout.vue";
import AuthPatternBackground from "@ulticode/auth-ui/layouts/AuthPatternBackground.vue";
import AuthInput from "@ulticode/auth-ui/components/AuthInput.vue";
import AuthButton from "@ulticode/auth-ui/components/AuthButton.vue";

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

const passwordsMatch = computed(
  () => newPassword.value === confirmPassword.value,
);

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
      <AuthCard>
        <form class="flex flex-col gap-4" @submit="handleReset">
          <div class="flex flex-col gap-2">
            <h1 class="text-2xl font-semibold">
              {{ t("auth.resetPassword.title") }}
            </h1>
            <p class="text-sm text-muted-foreground">
              {{ t("auth.resetPassword.subtitle") }}
            </p>
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

          <div class="text-center text-sm text-muted-foreground">
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
