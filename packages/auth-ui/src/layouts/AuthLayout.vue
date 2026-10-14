<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { Code } from "@lucide/vue";
import { Badge } from "@ulticode/design-system/ui/badge";
import AuthThemeToggle from "../components/AuthThemeToggle.vue";
import type { AuthLayoutProps } from "./useAuthLayout";
const props = withDefaults(defineProps<AuthLayoutProps>(), {
  badge: "CODE",
  version: "v2.0.0",
  statusText: "",
  hidePattern: false,
  homeHref: "/",
});
const { t } = useI18n();
const statusText = computed(
  () => props.statusText || t("auth.layout.systemOnline"),
);
</script>
<template>
  <div class="grid min-h-svh bg-background lg:grid-cols-2">
    <div class="flex min-w-0 flex-col gap-8 p-4 sm:p-8">
      <header class="flex items-center justify-between gap-4">
        <RouterLink :to="homeHref" class="flex items-center gap-2 font-semibold"
          ><Code aria-hidden="true" /><span>UltiCode</span
          ><Badge variant="secondary">{{ badge }}</Badge></RouterLink
        >
        <AuthThemeToggle />
      </header>
      <main class="flex flex-1 items-center justify-center py-8">
        <slot name="form" />
      </main>
      <footer class="flex justify-between gap-4 text-sm text-muted-foreground">
        <span>{{ version }}</span
        ><span>{{ statusText }}</span>
      </footer>
    </div>
    <slot v-if="!hidePattern" name="pattern" />
  </div>
</template>
