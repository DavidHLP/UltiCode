<script setup lang="ts">
/**
 * SignupView - Management registration placeholder
 *
 * Management surfaces do not allow open self-registration; accounts are
 * provisioned through the opt-in `AdminBootstrapRunner` or by an existing
 * administrator. The shared `RegisterForm` UI is reused for visual
 * consistency, but the submit handler always rejects with a "contact
 * admin" message so the form behaves as a placeholder.
 */
import { useI18n } from "vue-i18n";
import AuthCard from "@/shared/auth-ui/src/components/AuthCard.vue";
import AuthLayout from "@/shared/auth-ui/src/layouts/AuthLayout.vue";
import AuthPatternBackground from "@/shared/auth-ui/src/layouts/AuthPatternBackground.vue";
import RegisterForm from "@/shared/auth-ui/src/components/RegisterForm.vue";

const { t } = useI18n();

async function blockRegistration() {
  throw new Error(t("auth.messages.contactAdmin"))
}

defineOptions({
  name: "SignupView",
});
</script>

<template>
  <AuthLayout badge="ADMIN" version="v2.0.0">
    <template #form>
      <AuthCard :title="t('auth.register.terminal')">
        <RegisterForm :on-submit="blockRegistration" hide-oauth />
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