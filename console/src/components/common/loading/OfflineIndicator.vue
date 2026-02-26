<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useNetworkStatus } from "@/composables/useNetworkStatus";
import { useI18n } from "vue-i18n";
import { WifiOff, Wifi, RefreshCw } from "lucide-vue-next";
import { Button } from "@/components/ui/button";

const props = defineProps<{
  /** Position of the indicator */
  position?: "top" | "bottom";
  /** Show reconnect button */
  showReconnect?: boolean;
  /** Auto-hide duration in ms when back online (0 = no auto-hide) */
  autoHideDelay?: number;
  /** Additional CSS classes */
  class?: string;
}>();

const emit = defineEmits<{
  /** Emitted when user clicks reconnect */
  reconnect: [];
  /** Emitted when online status changes */
  "status-change": [online: boolean];
}>();

const { t } = useI18n();
const { isOnline, offline, formattedOfflineDuration, subscribe } =
  useNetworkStatus();

const isReconnecting = ref(false);
const showOnlineNotification = ref(false);
let onlineTimeout: ReturnType<typeof setTimeout> | null = null;

// Subscribe to status changes
const unsubscribe = subscribe((online) => {
  emit("status-change", online);

  if (online) {
    showOnlineNotification.value = true;

    // Auto-hide online notification
    const delay = props.autoHideDelay ?? 3000;
    if (delay > 0) {
      if (onlineTimeout) clearTimeout(onlineTimeout);
      onlineTimeout = setTimeout(() => {
        showOnlineNotification.value = false;
      }, delay);
    }
  }
});

// Cleanup
watch(
  () => null,
  () => {
    if (onlineTimeout) clearTimeout(onlineTimeout);
    unsubscribe();
  },
  { immediate: true },
);

// Handle reconnect click
async function handleReconnect() {
  isReconnecting.value = true;
  emit("reconnect");

  // Simulate checking connection
  await new Promise((resolve) => setTimeout(resolve, 1000));

  // Force page reload to re-fetch data
  if (isOnline.value) {
    window.location.reload();
  }

  isReconnecting.value = false;
}

// Position classes
const positionClasses = computed(() => {
  const pos = props.position ?? "top";
  return pos === "top" ? "top-0" : "bottom-0";
});

// Banner visibility
const showOfflineBanner = computed(() => offline.value);
const showOnlineBanner = computed(
  () => showOnlineNotification.value && isOnline.value,
);
</script>

<template>
  <Teleport to="body">
    <!-- Offline Banner -->
    <Transition
      enter-active-class="transition-transform duration-300"
      :enter-from-class="
        position === 'bottom' ? 'translate-y-full' : '-translate-y-full'
      "
      enter-to-class="translate-y-0"
      leave-active-class="transition-transform duration-300"
      leave-from-class="translate-y-0"
      :leave-to-class="
        position === 'bottom' ? 'translate-y-full' : '-translate-y-full'
      "
    >
      <div
        v-if="showOfflineBanner"
        :class="[
          'fixed left-0 right-0 z-50',
          positionClasses,
          'bg-destructive text-destructive-foreground',
          'px-4 py-2',
          'flex items-center justify-center gap-3',
          'shadow-lg',
          props.class,
        ]"
        role="alert"
        aria-live="assertive"
      >
        <WifiOff class="size-4 flex-shrink-0" />
        <span class="text-sm font-medium">
          {{ t("common.network.offline") }}
          <span v-if="formattedOfflineDuration" class="opacity-80">
            ({{
              t("common.network.offlineFor", {
                duration: formattedOfflineDuration,
              })
            }})
          </span>
        </span>
        <Button
          v-if="showReconnect"
          variant="outline"
          size="sm"
          class="h-7 bg-transparent border-white/30 hover:bg-white/20 text-white"
          :disabled="isReconnecting"
          @click="handleReconnect"
        >
          <RefreshCw
            :class="['size-3 mr-1', isReconnecting && 'animate-spin']"
          />
          {{ t("common.network.reconnect") }}
        </Button>
      </div>
    </Transition>

    <!-- Back Online Banner -->
    <Transition
      enter-active-class="transition-transform duration-300"
      :enter-from-class="
        position === 'bottom' ? 'translate-y-full' : '-translate-y-full'
      "
      enter-to-class="translate-y-0"
      leave-active-class="transition-transform duration-300"
      leave-from-class="translate-y-0"
      :leave-to-class="
        position === 'bottom' ? 'translate-y-full' : '-translate-y-full'
      "
    >
      <div
        v-if="showOnlineBanner"
        :class="[
          'fixed left-0 right-0 z-50',
          positionClasses,
          'bg-green-600 text-white',
          'px-4 py-2',
          'flex items-center justify-center gap-2',
          'shadow-lg',
          props.class,
        ]"
        role="status"
        aria-live="polite"
      >
        <Wifi class="size-4 flex-shrink-0" />
        <span class="text-sm font-medium">
          {{ t("common.network.backOnline") }}
        </span>
      </div>
    </Transition>
  </Teleport>
</template>
