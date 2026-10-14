<script setup lang="ts">
/**
 * LoginView - Management login page
 *
 * Thin shell over the shared `AuthLayout` + `AuthPatternBackground`.
 * Management login omits the "forgot password" and "no account / sign up"
 * affordances because admin accounts are provisioned out-of-band.
 */
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/stores/auth'
import AuthCard from '@ulticode/auth-ui/components/AuthCard.vue'
import AuthLayout from '@ulticode/auth-ui/layouts/AuthLayout.vue'
import AuthPatternBackground from '@ulticode/auth-ui/layouts/AuthPatternBackground.vue'
import LoginForm from '@ulticode/auth-ui/components/LoginForm.vue'

const { t } = useI18n()
const authStore = useAuthStore()

async function handleLogin(creds: { username: string; password: string }) {
  await authStore.login(creds)
}

defineOptions({
  name: 'LoginView',
})
</script>

<template>
  <AuthLayout badge="ADMIN" version="v2.0.0">
    <template #form>
      <AuthCard>
        <LoginForm :on-submit="handleLogin" hide-forgot hide-sign-up hide-oauth />
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
