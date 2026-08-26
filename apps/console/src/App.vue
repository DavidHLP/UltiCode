<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
import { Toaster } from "vue-sonner";
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
</script>

<template>
  <!-- Garden paper grain (shared design system; see GARDEN_DESIGN_SPEC.md §5) -->
  <div class="paper-texture-overlay" aria-hidden="true" />
  <RouterView />
  <PWAUpdatePrompt />
  <Toaster position="top-right" rich-colors close-button />
</template>

<style scoped></style>
