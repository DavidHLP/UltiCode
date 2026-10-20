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
import AuthCard from "@ulticode/auth-ui/components/AuthCard.vue";
import AuthLayout from "@ulticode/auth-ui/layouts/AuthLayout.vue";
import AuthPatternBackground from "@ulticode/auth-ui/layouts/AuthPatternBackground.vue";
import AuthInput from "@ulticode/auth-ui/components/AuthInput.vue";
import AuthButton from "@ulticode/auth-ui/components/AuthButton.vue";

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
      <AuthCard>
        <form class="flex flex-col gap-4" @submit="handleSubmit">
          <div class="flex flex-col gap-2">
            <h1 class="text-2xl font-semibold">
              {{ t("auth.forgotPassword.title") }}
            </h1>
            <p class="text-sm text-muted-foreground">
              {{ t("auth.forgotPassword.subtitle") }}
            </p>
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
