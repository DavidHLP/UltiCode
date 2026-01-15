<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/stores/admin/auth'
import { Button } from '@/components/ui/button'
import { Field, FieldGroup, FieldLabel, FieldSeparator } from '@/components/ui/field'
import { Input } from '@/components/ui/input'

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleSubmit(event: Event) {
  event.preventDefault()
  error.value = ''
  loading.value = true

  try {
    const success = await authStore.login({
      username: username.value,
      password: password.value,
    })

    if (success) {
      const redirect = (route.query.redirect as string) || '/'
      await router.push(redirect)
    } else {
      error.value = t('auth.login.invalidCredentials')
    }
  } catch (err: unknown) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      t('auth.login.loginFailed')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <form :class="cn('flex flex-col gap-6', props.class)" @submit="handleSubmit">
    <FieldGroup>
      <div class="flex flex-col items-center gap-1 text-center">
        <h1 class="text-2xl font-semibold tracking-tight">{{ t('auth.login.title') }}</h1>
        <p class="text-muted-foreground text-sm text-balance">
          {{ t('auth.login.subtitle') }}
        </p>
      </div>
      <Field v-if="error">
        <div class="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-600">
          {{ error }}
        </div>
      </Field>
      <Field>
        <FieldLabel for="username">{{ t('auth.login.username') }}</FieldLabel>
        <Input
          id="username"
          v-model="username"
          type="text"
          autocomplete="username"
          :placeholder="t('auth.login.usernamePlaceholder')"
          required
          :disabled="loading"
        />
      </Field>
      <Field>
        <FieldLabel for="password">{{ t('auth.login.password') }}</FieldLabel>
        <Input
          id="password"
          v-model="password"
          type="password"
          autocomplete="current-password"
          :placeholder="t('auth.login.passwordPlaceholder')"
          required
          :disabled="loading"
        />
      </Field>
      <Field>
        <Button type="submit" class="w-full" :disabled="loading">
          {{ loading ? t('auth.login.submitting') : t('auth.login.submit') }}
        </Button>
      </Field>
      <FieldSeparator>{{ t('auth.login.demoAccounts') }}</FieldSeparator>
      <Field>
        <div class="space-y-1 text-sm text-muted-foreground">
          <p class="font-medium">{{ t('auth.login.demoAccountsTitle') }}</p>
          <p>{{ t('auth.login.demoAdmin') }}</p>
          <p>{{ t('auth.login.demoModerator') }}</p>
        </div>
      </Field>
    </FieldGroup>
  </form>
</template>
