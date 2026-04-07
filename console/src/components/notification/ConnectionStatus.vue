<script setup lang="ts">
import { computed } from "vue";
import { useNotificationStore } from "@/stores/notification";
import { cn } from "@/lib/utils";

const props = defineProps<{
  class?: string;
}>();

const notificationStore = useNotificationStore();

const statusColor = computed(() => {
  return notificationStore.realtimeConnected ? "bg-[var(--terminal-green)]" : "bg-[var(--terminal-amber)]";
});

const statusText = computed(() => {
  return notificationStore.realtimeConnected ? "Connected" : "Disconnected";
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
