<script setup lang="ts">
/**
 * PWA Update Prompt Component
 *
 * Displays a notification when a new version of the app is available.
 * Allows users to update immediately or dismiss the prompt.
 */

import { usePWA } from "@/composables/usePWA";
import { RefreshCw, X } from "lucide-vue-next";
import { useI18n } from "vue-i18n";

const { needRefresh, updateServiceWorker, close } = usePWA();
const { t } = useI18n();
</script>

<template>
  <Transition name="slide-up">
    <div
      v-if="needRefresh"
      class="fixed bottom-4 right-4 z-50 flex items-center gap-3 rounded-none border border-border bg-card p-4 shadow-[var(--shadow-float)]"
      role="alert"
    >
      <RefreshCw class="h-5 w-5 text-primary" />
      <div class="flex-1">
        <p class="text-sm font-medium">
          {{ t("pwa.updateAvailable") }}
        </p>
        <p class="text-xs text-muted-foreground">
          {{ t("pwa.updateDescription") }}
        </p>
      </div>
      <div class="flex gap-2">
        <button
          class="rounded-none bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground transition-colors hover:bg-primary/90"
          @click="updateServiceWorker"
        >
          {{ t("pwa.update") }}
        </button>
        <button
          class="rounded-none px-2 py-1.5 text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
          :aria-label="t('common.dismiss')"
          @click="close"
        >
          <X class="h-4 w-4" />
        </button>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s ease;
}

.slide-up-enter-from,
.slide-up-leave-to {
  opacity: 0;
  transform: translateY(1rem);
}
</style>
