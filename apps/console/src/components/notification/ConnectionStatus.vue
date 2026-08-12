<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { useNotificationStore } from "@/stores/notification";
import { cn } from "@/lib/utils";

const { t } = useI18n();

const props = defineProps<{
  class?: string;
}>();

const notificationStore = useNotificationStore();

const statusColor = computed(() => {
  return notificationStore.realtimeConnected
    ? "bg-[var(--status-success-mark)]"
    : "bg-[var(--status-warning-mark)]";
});

const statusText = computed(() => {
  return notificationStore.realtimeConnected
    ? t("notification.connected")
    : t("notification.disconnected");
});
</script>

<template>
  <div
    :class="cn('flex items-center gap-1.5', props.class)"
    :title="statusText"
  >
    <span
      :class="
        cn(
          'h-2 w-2 rounded-full transition-colors duration-300',
          statusColor,
          notificationStore.realtimeConnected && 'animate-pulse',
        )
      "
    />
    <span class="sr-only">{{ statusText }}</span>
  </div>
</template>
