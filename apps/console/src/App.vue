<script setup lang="ts">
import { watch } from "vue";
import { setAppearanceAccount } from "@ulticode/theme";
import { useAuthStore } from "@/stores/auth";
import AppearanceSettings from "@ulticode/design-system/AppearanceSettings.vue";
import { useI18n } from "vue-i18n";
import { onMounted, onUnmounted } from "vue";
import { TooltipProvider } from "@ulticode/design-system/ui/tooltip";
import { Toaster } from "@ulticode/design-system/ui/sonner";
import "vue-sonner/style.css";
import PWAUpdatePrompt from "@/components/common/PWAUpdatePrompt.vue";

const handleError = (event: ErrorEvent) => {
  console.error("[App.vue] Global error:", event.error);
};

const handleRejection = (event: PromiseRejectionEvent) => {
  console.error("[App.vue] Unhandled promise rejection:", event.reason);
};

onMounted(() => {
  window.addEventListener("error", handleError);
  window.addEventListener("unhandledrejection", handleRejection);
});

onUnmounted(() => {
  window.removeEventListener("error", handleError);
  window.removeEventListener("unhandledrejection", handleRejection);
});
const { locale } = useI18n();
const auth = useAuthStore();
watch(() => auth.user?.id ?? null, setAppearanceAccount, {
  immediate: true,
  flush: "sync",
});
</script>

<template>
  <TooltipProvider :delay-duration="200">
    <RouterView />
    <AppearanceSettings :locale="locale" />
    <PWAUpdatePrompt />
    <Toaster position="top-right" rich-colors close-button />
  </TooltipProvider>
</template>
