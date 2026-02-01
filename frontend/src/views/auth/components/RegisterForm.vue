<script setup lang="ts">
import type { HTMLAttributes } from "vue";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import {
  Field,
  FieldDescription,
  FieldGroup,
  FieldLabel,
  FieldSeparator,
} from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ref } from "vue";
import { useRouter } from "vue-router";
import { authApi } from "@/api/auth";
import { setToken, setUserId } from "@/utils/auth";
import { toast } from "vue-sonner";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  class?: HTMLAttributes["class"];
}>();

const { t } = useI18n();
const router = useRouter();
const username = ref("");
const email = ref("");
const password = ref("");
const loading = ref(false);

async function handleSubmit(e: Event) {
  e.preventDefault();
  loading.value = true;
  try {
    const res = await authApi.register({
      username: username.value,
      email: email.value,
      password: password.value,
    });
    setToken(res.access_token);
    setUserId(res.user.id);
    toast.success(t("auth.messages.registerSuccess"));
    router.push("/");
  } catch (error) {
    console.error(error);
    toast.error(t("auth.messages.registerFailed"));
  } finally {
    loading.value = false;
  }
}

function handleGithubLogin() {
  window.location.href = `${import.meta.env.VITE_API_BASE_URL || "http://localhost:9001"}/auth/github`;
}
</script>

<template>
  <form :class="cn('flex flex-col gap-6', props.class)" @submit="handleSubmit">
    <FieldGroup>
      <div class="flex flex-col items-center gap-1 text-center">
        <h1 class="text-2xl font-bold">{{ t("auth.register.title") }}</h1>
        <p class="text-muted-foreground text-sm text-balance">
          {{ t("auth.register.subtitle") }}
        </p>
      </div>
      <Field>
        <FieldLabel for="username">
          {{ t("auth.register.username") }}
        </FieldLabel>
        <Input
          id="username"
          type="text"
          v-model="username"
          :placeholder="t('auth.register.usernamePlaceholder')"
          autocomplete="username"
          required
        />
      </Field>
      <Field>
        <FieldLabel for="email"> {{ t("auth.register.email") }} </FieldLabel>
        <Input
          id="email"
          type="email"
          v-model="email"
          :placeholder="t('auth.register.emailPlaceholder')"
          autocomplete="email"
          required
        />
      </Field>
      <Field>
        <FieldLabel for="password">
          {{ t("auth.register.password") }}
        </FieldLabel>
        <Input
          id="password"
          type="password"
          v-model="password"
          :placeholder="t('auth.register.passwordPlaceholder')"
          autocomplete="new-password"
          required
        />
      </Field>
      <Field>
        <Button type="submit" :disabled="loading">
          {{
            loading ? t("auth.register.submitting") : t("auth.register.submit")
          }}
        </Button>
      </Field>
      <FieldSeparator>{{ t("auth.login.orContinueWith") }}</FieldSeparator>
      <Field>
        <Button variant="outline" type="button" @click="handleGithubLogin">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
            <path
              d="M12 .297c-6.63 0-12 5.373-12 12 0 5.303 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61C4.422 18.07 3.633 17.7 3.633 17.7c-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.606-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 22.092 24 17.592 24 12.297c0-6.627-5.373-12-12-12"
              fill="currentColor"
            />
          </svg>
          {{ t("auth.login.loginWithGithub") }}
        </Button>
        <FieldDescription class="text-center">
          {{ t("auth.register.hasAccount") }}
          <a href="/login">{{ t("auth.register.login") }}</a>
        </FieldDescription>
      </Field>
    </FieldGroup>
  </form>
</template>
