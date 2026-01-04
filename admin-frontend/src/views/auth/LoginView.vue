<script setup lang="ts">
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/admin/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Field, FieldGroup, FieldLabel, FieldSeparator } from '@/components/ui/field'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleSubmit(e: Event) {
  e.preventDefault()
  error.value = ''
  loading.value = true

  try {
    const success = await authStore.login({
      username: username.value,
      password: password.value,
    })

    if (success) {
      // Redirect to the page they were trying to access, or dashboard
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
  <div class="flex min-h-screen w-full items-center justify-center px-4">
    <div class="w-full max-w-sm">
      <div class="flex flex-col space-y-2 text-center mb-6">
        <h1 class="text-2xl font-semibold tracking-tight">UltiCode Admin</h1>
        <p class="text-sm text-muted-foreground">
          Enter your credentials to access the admin panel
        </p>
      </div>

      <form @submit="handleSubmit" class="flex flex-col gap-6">
        <FieldGroup>
          <Field v-if="error">
            <div class="p-3 text-sm text-red-600 bg-red-50 border border-red-200 rounded-md">
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

          <FieldSeparator />

          <Field>
            <div class="text-sm text-muted-foreground space-y-1">
              <p class="font-medium">Demo Accounts:</p>
              <p>• admin / admin123 (Super Admin)</p>
              <p>• moderator / mod123 (Moderator)</p>
            </div>
          </Field>
        </FieldGroup>
      </form>
    </div>
  </div>
</template>
