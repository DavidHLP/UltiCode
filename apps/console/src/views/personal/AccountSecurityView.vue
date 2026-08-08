<script setup lang="ts">
import { ref, computed } from "vue";
import { useI18n } from "vue-i18n";
import { toast } from "vue-sonner";
import { Loader2 } from "lucide-vue-next";
import { changePassword } from "@/api/user";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";

const { t } = useI18n();

const currentPassword = ref("");
const newPassword = ref("");
const confirmPassword = ref("");
const loading = ref(false);

const passwordsMismatch = computed(
  () =>
    confirmPassword.value.length > 0 &&
    newPassword.value !== confirmPassword.value,
);

const canSubmit = computed(() => {
  return (
    currentPassword.value.length >= 6 &&
    newPassword.value.length >= 6 &&
    confirmPassword.value.length > 0 &&
    !passwordsMismatch.value
  );
});

async function handleSubmit() {
  if (!canSubmit.value || loading.value) return;

  loading.value = true;
  try {
    await changePassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
      confirmPassword: confirmPassword.value,
    });
    toast.success(t("personal.messages.passwordChanged"));
    currentPassword.value = "";
    newPassword.value = "";
    confirmPassword.value = "";
  } catch (error: unknown) {
    const message =
      error instanceof Error ? error.message : t("common.status.error");
    toast.error(message);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="space-y-6 animate-in fade-in slide-in-from-right-4 duration-300">
    <Card class="border shadow-[var(--shadow-float)] bg-card rounded-none">
      <CardHeader>
        <CardTitle class="text-lg">
          {{ t("personal.account.changePassword") }}
        </CardTitle>
        <CardDescription>
          {{ t("personal.account.changePasswordDesc") }}
        </CardDescription>
      </CardHeader>

      <CardContent class="space-y-4">
        <div class="space-y-2">
          <Label htmlFor="current" class="text-xs font-bold">
            {{ t("personal.account.currentPassword") }}
          </Label>
          <Input
            id="current"
            v-model="currentPassword"
            type="password"
            autocomplete="current-password"
          />
        </div>

        <div class="space-y-2">
          <Label htmlFor="new" class="text-xs font-bold">
            {{ t("personal.account.newPassword") }}
          </Label>
          <Input
            id="new"
            v-model="newPassword"
            type="password"
            autocomplete="new-password"
          />
          <p
            v-if="newPassword.length > 0 && newPassword.length < 6"
            class="text-xs text-destructive"
          >
            {{ t("personal.account.passwordMinLength") }}
          </p>
        </div>

        <div class="space-y-2">
          <Label htmlFor="confirm" class="text-xs font-bold">
            {{ t("personal.account.confirmNewPassword") }}
          </Label>
          <Input
            id="confirm"
            v-model="confirmPassword"
            type="password"
            autocomplete="new-password"
          />
          <p
            v-if="passwordsMismatch"
            class="text-xs text-destructive"
          >
            {{ t("personal.account.passwordMismatch") }}
          </p>
        </div>
      </CardContent>

      <CardFooter class="bg-muted/5 border-t justify-end py-4">
        <Button
          class="rounded-none px-8"
          :disabled="!canSubmit || loading"
          @click="handleSubmit"
        >
          <Loader2
            v-if="loading"
            class="mr-2 h-4 w-4 animate-spin"
          />
          {{ t("personal.account.updatePassword") }}
        </Button>
      </CardFooter>
    </Card>

    <Card class="border-destructive/20 bg-destructive/5 rounded-none">
      <CardHeader>
        <CardTitle class="text-lg text-destructive">
          {{ t("personal.account.dangerZone") }}
        </CardTitle>
        <CardDescription>
          {{ t("personal.account.dangerZoneDesc") }}
        </CardDescription>
      </CardHeader>

      <CardContent>
        <p class="text-sm text-muted-foreground mb-4">
          {{ t("personal.account.deleteWarning") }}
        </p>
        <Button variant="destructive" class="rounded-none" disabled>
          {{ t("personal.account.deleteAccount") }}
        </Button>
        <p class="text-xs text-muted-foreground mt-2">
          {{ t("personal.account.deleteNotAvailable") }}
        </p>
      </CardContent>
    </Card>
  </div>
</template>