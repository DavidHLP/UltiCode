<script setup lang="ts">
import { onMounted } from "vue";
import { useRouter } from "vue-router";
import PWAUpdatePrompt from "@/components/common/PWAUpdatePrompt.vue";

const router = useRouter();
const isDevelopment = import.meta.env.DEV;

onMounted(() => {
  if (isDevelopment) {
    console.log("[App.vue] App mounted");
    console.log("[App.vue] Current route:", router.currentRoute.value);
  }

  // Global error handler
  window.addEventListener("error", (event) => {
    console.error("[App.vue] Global error:", event.error);
  });

  // Global unhandled promise rejection handler
  window.addEventListener("unhandledrejection", (event) => {
    console.error("[App.vue] Unhandled promise rejection:", event.reason);
  });
});
</script>

<template>
  <RouterView />
  <PWAUpdatePrompt />
</template>

<style scoped></style>
