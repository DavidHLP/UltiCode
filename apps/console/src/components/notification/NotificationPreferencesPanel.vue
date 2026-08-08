<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { toast } from "vue-sonner";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { Separator } from "@/components/ui/separator";
import {
  fetchNotificationPreferences,
  updateNotificationPreferences,
} from "@/api/notification";
import type { NotificationPreferences } from "@/types/notification";
import { MessageSquare, Megaphone, ShieldAlert, Bell } from "lucide-vue-next";

const { t } = useI18n();
const loading = ref(false);
const preferences = ref<NotificationPreferences>({
  communication: true,
  marketing: true,
  security: true,
  system: true,
});

const prefItems: {
  key: keyof NotificationPreferences;
  icon: typeof Bell;
}[] = [
  { key: "communication", icon: MessageSquare },
  { key: "marketing", icon: Megaphone },
  { key: "security", icon: ShieldAlert },
  { key: "system", icon: Bell },
];

onMounted(async () => {
  loading.value = true;
  try {
    preferences.value = await fetchNotificationPreferences();
  } catch {
    // silent — preferences are non-critical
  } finally {
    loading.value = false;
  }
});

async function togglePreference(
  key: keyof NotificationPreferences,
  value: boolean,
) {
  const previous = preferences.value[key];
  preferences.value = { ...preferences.value, [key]: value };
  try {
    await updateNotificationPreferences({ [key]: value });
    toast.success(t("personal.notifications.preferencesSaved"));
  } catch {
    preferences.value = { ...preferences.value, [key]: previous };
    toast.error(t("personal.notifications.preferencesError"));
  }
}
</script>

<template>
  <div class="space-y-4">
    <div v-for="(item, index) in prefItems" :key="item.key">
      <Separator v-if="index > 0" class="mb-4" />
      <div class="flex items-center justify-between gap-4">
        <div class="flex items-center gap-3">
          <div
            class="flex h-8 w-8 items-center justify-center rounded-full border bg-background"
          >
            <component :is="item.icon" class="h-4 w-4 text-muted-foreground" />
          </div>
          <Label :for="`pref-${item.key}`" class="cursor-pointer text-sm">
            {{ t(`personal.notifications.prefCategories.${item.key}`) }}
          </Label>
        </div>
        <Switch
          :id="`pref-${item.key}`"
          :model-value="preferences[item.key]"
          :disabled="loading"
          @update:model-value="(v: boolean) => togglePreference(item.key, v)"
        />
      </div>
    </div>
  </div>
</template>
