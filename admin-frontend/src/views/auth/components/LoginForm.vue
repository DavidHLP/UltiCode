<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { cn } from '@/lib/utils'
import { useAuthStore } from '@/stores/admin/auth'
import { Button } from '@/components/ui/button'
import { Field, FieldGroup, FieldLabel, FieldSeparator } from '@/components/ui/field'
import { Input } from '@/components/ui/input'

const props = defineProps<{
  class?: HTMLAttributes['class']
}>()

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
      error.value = 'Invalid username or password'
    }
  } catch (err: unknown) {
    error.value =
      (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
      'Login failed. Please try again.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <form :class="cn('flex flex-col gap-6', props.class)" @submit="handleSubmit">
    <FieldGroup>
      <div class="flex flex-col items-center gap-1 text-center">
        <h1 class="text-2xl font-semibold tracking-tight">Sign in</h1>
        <p class="text-muted-foreground text-sm text-balance">
          Enter your credentials to access the admin panel
        </p>
      </div>
      <Field v-if="error">
        <div class="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-600">
          {{ error }}
        </div>
      </Field>
      <Field>
        <FieldLabel for="username">Username</FieldLabel>
        <Input
          id="username"
          v-model="username"
          type="text"
          placeholder="admin"
          required
          :disabled="loading"
        />
      </Field>
      <Field>
        <FieldLabel for="password">Password</FieldLabel>
        <Input
          id="password"
          v-model="password"
          type="password"
          placeholder="••••••••"
          required
          :disabled="loading"
        />
      </Field>
      <Field>
        <Button type="submit" class="w-full" :disabled="loading">
          {{ loading ? 'Signing in...' : 'Sign in' }}
        </Button>
      </Field>
      <FieldSeparator>Demo Accounts</FieldSeparator>
      <Field>
        <div class="space-y-1 text-sm text-muted-foreground">
          <p class="font-medium">Use these credentials:</p>
          <p>• admin / admin123 (Super Admin)</p>
          <p>• moderator / mod123 (Moderator)</p>
        </div>
      </Field>
    </FieldGroup>
  </form>
</template>
