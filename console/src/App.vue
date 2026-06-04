<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
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
  <RouterView />
  <PWAUpdatePrompt />
</template>

<style scoped></style>
