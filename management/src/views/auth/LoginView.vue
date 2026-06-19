<script setup lang="ts">
/**
 * LoginView - Management login page
 *
 * Thin shell over the shared `AuthLayout` + `AuthPatternBackground`.
 * Management login omits the "forgot password" and "no account / sign up"
 * affordances because admin accounts are provisioned out-of-band.
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
  await authStore.login(creds)
}

defineOptions({
  name: "LoginView",
});
</script>

<template>
  <AuthLayout badge="ADMIN" version="v2.0.0">
    <template #form>
      <AuthCard :title="t('auth.login.terminal')">
        <LoginForm
          :on-submit="handleLogin"
          hide-forgot
          hide-sign-up
          hide-oauth
        />
      </AuthCard>
    </template>
    <template #pattern>
      <AuthPatternBackground
        :title="t('auth.layout.codingConsole')"
        :subtitle="t('auth.layout.codingConsoleSubtitle')"
        :spec="[
          { prompt: 'systemctl status ulticode.service', output: { text: '● ulticode.service - UltiCode Platform', tone: 'success' } },
          { output: { text: 'Active: active (running) since Jun 2026', tone: 'muted' } },
          { output: { text: 'PID: 9003 (vite-management)', tone: 'muted' } },
          { prompt: 'npx vitest run --coverage', output: { text: '✓ tests passed', tone: 'success' } },
          { prompt: 'check_db_connection', output: { text: 'Database: mysql@localhost (CONNECTED)', tone: 'success' } },
          { output: { text: 'Server Port: 9003 (Vite Management)', tone: 'accent' } },
        ]"
      />
    </template>
  </AuthLayout>
</template>