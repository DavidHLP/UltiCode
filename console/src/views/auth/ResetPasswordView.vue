<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { ref, computed, onMounted } from "vue";
import { useRouter, useRoute } from "vue-router";
import { authApi } from "@/api/auth";
import { toast } from "vue-sonner";
import { GalleryVerticalEnd } from "lucide-vue-next";
import { useI18n } from "vue-i18n";

const router = useRouter();
const route = useRoute();
const { t } = useI18n();

const newPassword = ref("");
const confirmPassword = ref("");
const loading = ref(false);
const token = ref("");

onMounted(() => {
  token.value = (route.query.token as string) || "";
  if (!token.value) {
    toast.error(t("auth.messages.passwordResetFailed"));
    router.push("/forgot-password");
  }
});

const passwordsMatch = computed(() => {
  return newPassword.value === confirmPassword.value;
});

const isFormValid = computed(() => {
  return (
    newPassword.value.length >= 6 &&
    confirmPassword.value.length >= 6 &&
    passwordsMatch.value
  );
});

async function handleReset(e: Event) {
  e.preventDefault();

  if (!passwordsMatch.value) {
    toast.error(t("auth.validation.passwordMismatch"));
    return;
  }

  if (newPassword.value.length < 6) {
    toast.error(t("auth.validation.passwordMinLength"));
    return;
  }

  loading.value = true;
  try {
    await authApi.resetPassword(token.value, newPassword.value);
    toast.success(t("auth.resetPassword.successMessage"));
    router.push("/login");
  } catch (error) {
    console.error(error);
    toast.error(t("auth.messages.passwordResetFailed"));
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="grid min-h-svh lg:grid-cols-2">
    <div class="flex flex-col gap-4 p-6 md:p-10">
      <div class="flex justify-center gap-2 md:justify-start">
        <a href="#" class="flex items-center gap-2 font-medium">
          <div
            class="bg-primary text-primary-foreground flex size-6 items-center justify-center rounded-md"
          >
            <GalleryVerticalEnd class="size-4" />
          </div>
          UltiCode
        </a>
      </div>
      <div class="flex flex-1 items-center justify-center">
        <div class="w-full max-w-xs">
          <form class="flex flex-col gap-6" @submit="handleReset">
            <div class="flex flex-col items-center gap-1 text-center">
              <h1 class="text-2xl font-bold">
                {{ t("auth.resetPassword.title") }}
              </h1>
              <p class="text-muted-foreground text-sm text-balance">
                {{ t("auth.resetPassword.subtitle") }}
              </p>
            </div>
            <div class="grid gap-4">
              <div class="grid gap-2">
                <Label for="newPassword">{{
                  t("auth.resetPassword.newPassword")
                }}</Label>
                <Input
                  id="newPassword"
                  type="password"
                  v-model="newPassword"
                  :placeholder="t('auth.resetPassword.newPasswordPlaceholder')"
                  autocomplete="new-password"
                  required
                  minlength="6"
                />
              </div>
              <div class="grid gap-2">
                <Label for="confirmPassword">{{
                  t("auth.resetPassword.confirmPassword")
                }}</Label>
                <Input
                  id="confirmPassword"
                  type="password"
                  v-model="confirmPassword"
                  :placeholder="
                    t('auth.resetPassword.confirmPasswordPlaceholder')
                  "
                  autocomplete="new-password"
                  required
                  minlength="6"
                  :class="{
                    'border-destructive': confirmPassword && !passwordsMatch,
                  }"
                />
                <p
                  v-if="confirmPassword && !passwordsMatch"
                  class="text-destructive text-sm"
                >
                  {{ t("auth.validation.passwordMismatch") }}
                </p>
              </div>
            </div>
            <Button type="submit" :disabled="loading || !isFormValid">
              {{
                loading
                  ? t("auth.resetPassword.submitting")
                  : t("auth.resetPassword.submit")
              }}
            </Button>
            <div class="text-center text-sm">
              {{ t("auth.forgotPassword.rememberPassword") }}
              <a href="/login" class="underline underline-offset-4">{{
                t("auth.register.login")
              }}</a>
            </div>
          </form>
        </div>
      </div>
    </div>
    <div class="bg-muted relative hidden lg:block">
      <img
        src="https://images.unsplash.com/photo-1590069261209-f8e9b8642343?ixlib=rb-4.0.3&auto=format&fit=crop&w=1376&q=80"
        alt="Image"
        class="absolute inset-0 h-full w-full object-cover dark:brightness-[0.2] dark:grayscale"
      />
    </div>
  </div>
</template>
