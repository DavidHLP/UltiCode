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
import { useI18n } from 'vue-i18n'
import AuthCard from '@ulticode/auth-ui/components/AuthCard.vue'
import AuthLayout from '@ulticode/auth-ui/layouts/AuthLayout.vue'
import AuthPatternBackground from '@ulticode/auth-ui/layouts/AuthPatternBackground.vue'
import RegisterForm from '@ulticode/auth-ui/components/RegisterForm.vue'

const { t } = useI18n()

async function blockRegistration() {
  throw new Error(t('auth.messages.contactAdmin'))
}

defineOptions({
  name: 'SignupView',
})
</script>

<template>
  <AuthLayout badge="ADMIN" version="v2.0.0">
    <template #form>
      <AuthCard>
        <RegisterForm :on-submit="blockRegistration" hide-oauth />
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
