<script setup lang="ts">
/**
 * RegisterView - Console registration page
 *
 * Thin shell over the shared AuthLayout/AuthPatternBackground. The form's
 * submit handler delegates to the local `useAuthStore.register`. The
 * shared `RegisterForm` performs the password-match validation client-side
 * before invoking this callback.
 */
import { useI18n } from "vue-i18n";
import { useAuthStore } from "@/stores/auth";
import type { RegisterRequest } from "@ulticode/auth-core";
import AuthCard from "@ulticode/auth-ui/components/AuthCard.vue";
import AuthLayout from "@ulticode/auth-ui/layouts/AuthLayout.vue";
import AuthPatternBackground from "@ulticode/auth-ui/layouts/AuthPatternBackground.vue";
import RegisterForm from "@ulticode/auth-ui/components/RegisterForm.vue";

const { t } = useI18n();
const authStore = useAuthStore();

async function handleRegister(payload: RegisterRequest) {
  await authStore.register(payload);
}

defineOptions({
  name: "RegisterView",
});
</script>

<template>
  <AuthLayout badge="CODE" version="v2.0.0">
    <template #form>
      <AuthCard>
        <RegisterForm :on-submit="handleRegister" />
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
