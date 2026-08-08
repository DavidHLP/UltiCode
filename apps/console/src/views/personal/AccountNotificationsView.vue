<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { toast } from "vue-sonner";
import { Bell, Mail, ShieldCheck, Loader2 } from "lucide-vue-next";
import {
  fetchNotificationPreferences,
  updateNotificationPreferences,
} from "@/api/notification";
import type { NotificationPreferences } from "@/types/notification";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";

const { t } = useI18n();
const loading = ref(true);
const saving = ref(false);
const preferences = ref<NotificationPreferences | null>(null);

async function handlePreferenceChange(
  key: keyof NotificationPreferences,
  value: boolean,
) {
  if (!preferences.value || saving.value) return;
  const previous = preferences.value[key];
  preferences.value = { ...preferences.value, [key]: value };
  saving.value = true;
  try {
    preferences.value = await updateNotificationPreferences({ [key]: value });
    toast.success(t("personal.messages.notificationsUpdated"));
  } catch (error) {
    console.error("Failed to update notification preferences", error);
    preferences.value = { ...preferences.value, [key]: previous };
    toast.error(t("common.status.error"));
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  try {
    preferences.value = await fetchNotificationPreferences();
  } catch (error) {
    console.error("Failed to load notification preferences", error);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <div class="space-y-6 animate-in fade-in slide-in-from-right-4 duration-300">
    <div
      v-if="loading"
      class="flex flex-col items-center justify-center py-20 gap-4"
    >
      <Loader2 class="h-10 w-10 animate-spin text-primary" />
      <p class="text-sm text-muted-foreground">
        {{ t("personal.account.loadingSettings") }}
      </p>
    </div>

    <Card
      v-else
      class="border shadow-[var(--shadow-float)] bg-card rounded-none"
    >
      <CardHeader>
        <CardTitle class="text-lg">
          {{ t("personal.account.sections.notifications") }}
        </CardTitle>
        <CardDescription>
          {{ t("personal.account.sections.notificationsDesc") }}
        </CardDescription>
      </CardHeader>
      <CardContent class="space-y-4">
        <div class="space-y-4">
          <div
            class="flex items-center justify-between space-x-4 rounded-none border bg-background p-4 transition-all"
          >
            <div class="flex gap-4 items-center">
              <div
                class="h-10 w-10 rounded-full bg-primary/10 flex items-center justify-center"
              >
                <Mail class="h-5 w-5 text-primary" />
              </div>
              <div class="space-y-0.5">
                <Label class="text-base font-bold">
                  {{ t("personal.account.notificationTypes.communication") }}
                </Label>
                <p class="text-sm text-muted-foreground">
                  {{ t("personal.account.notificationTypes.communicationDesc") }}
                </p>
              </div>
            </div>
            <Switch
              :model-value="preferences?.communication ?? true"
              :disabled="saving || !preferences"
              @update:model-value="
                (value: boolean) =>
                  handlePreferenceChange('communication', value)
              "
            />
          </div>

          <div
            class="flex items-center justify-between space-x-4 rounded-none border bg-background p-4 transition-all"
          >
            <div class="flex gap-4 items-center">
              <div
                class="h-10 w-10 rounded-full bg-[var(--terminal-amber)]/10 flex items-center justify-center"
              >
                <Bell class="h-5 w-5 text-[var(--terminal-amber)]" />
              </div>
              <div class="space-y-0.5">
                <Label class="text-base font-bold">
                  {{ t("personal.account.notificationTypes.marketing") }}
                </Label>
                <p class="text-sm text-muted-foreground">
                  {{ t("personal.account.notificationTypes.marketingDesc") }}
                </p>
              </div>
            </div>
            <Switch
              :model-value="preferences?.marketing ?? false"
              :disabled="saving || !preferences"
              @update:model-value="
                (value: boolean) => handlePreferenceChange('marketing', value)
              "
            />
          </div>

          <div
            class="flex items-center justify-between space-x-4 rounded-none border bg-background p-4 opacity-70"
          >
            <div class="flex gap-4 items-center">
              <div
                class="h-10 w-10 rounded-full bg-[var(--terminal-green)]/10 flex items-center justify-center"
              >
                <ShieldCheck class="h-5 w-5 text-[var(--terminal-green)]" />
              </div>
              <div class="space-y-0.5">
                <Label class="text-base font-bold">
                  {{ t("personal.account.notificationTypes.security") }}
                </Label>
                <p class="text-sm text-muted-foreground">
                  {{ t("personal.account.notificationTypes.securityDesc") }}
                </p>
              </div>
            </div>
            <Switch
              :model-value="preferences?.security ?? true"
              disabled
            />
          </div>
        </div>
      </CardContent>
    </Card>
  </div>
</template>