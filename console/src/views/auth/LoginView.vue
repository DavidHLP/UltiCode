<script setup lang="ts">
/**
 * LoginView - Console login page
 *
 * Thin shell over the shared `AuthLayout` + `AuthPatternBackground`
 * components. The form's submit handler delegates to the local
 * `useAuthStore.login` so the store continues to own CSRF + redirect.
 */
import { useI18n } from "vue-i18n";
import { useAuthStore } from "@/stores/auth";
import AuthCard from "@/shared/auth-ui/src/components/AuthCard.vue";
import AuthLayout from "@/shared/auth-ui/src/layouts/AuthLayout.vue";
import AuthPatternBackground from "@/shared/auth-ui/src/layouts/AuthPatternBackground.vue";
import LoginForm from "@/shared/auth-ui/src/components/LoginForm.vue";

const { t } = useI18n();
const authStore = useAuthStore();

async function handleLogin(creds: { username: string; password: string }) {
  await authStore.login(creds);
}

defineOptions({
  name: "LoginView",
});
</script>

<template>
  <AuthLayout badge="CODE" version="v2.0.0">
    <template #form>
      <AuthCard :title="t('auth.login.terminal')">
        <LoginForm :on-submit="handleLogin" />
      </AuthCard>
    </template>
    <template #pattern>
      <AuthPatternBackground
        :title="t('auth.layout.codingConsole')"
        :subtitle="t('auth.layout.codingConsoleSubtitle')"
        :spec="[
          { prompt: 'systemctl status ulticode.service', output: { text: '● ulticode.service - UltiCode Platform', tone: 'success' } },
          { output: { text: 'Active: active (running) since Jun 2026', tone: 'muted' } },
          { output: { text: 'PID: 9002 (vite-console)', tone: 'muted' } },
          { prompt: 'npx vitest run --coverage', output: { text: '✓ tests passed (100% coverage)', tone: 'success' } },
          { prompt: 'check_db_connection', output: { text: 'Database: mysql@localhost (CONNECTED)', tone: 'success' } },
          { output: { text: 'Server Port: 9002 (Vite Console)', tone: 'accent' } },
        ]"
      />
    </template>
  </AuthLayout>
</template>