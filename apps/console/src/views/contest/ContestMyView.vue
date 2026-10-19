<script setup lang="ts">
import { Trophy } from "@lucide/vue";
import { Separator } from "@ulticode/design-system/ui/separator";
import MyContests from "./components/MyContests.vue";
import { useI18n } from "vue-i18n";

const { t } = useI18n();
</script>

<template>
  <div
    class="max-w-7xl mx-auto w-full space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-10"
  >
    <div
      class="flex flex-col md:flex-row md:items-center justify-between gap-4"
    >
      <div class="space-y-1">
        <div class="flex items-center gap-3">
          <Trophy class="h-8 w-8 text-status-warning-mark" />
          <h1 class="text-3xl font-bold tracking-tight text-foreground">
            {{ t("contest.my.title") }}
          </h1>
        </div>
        <p class="text-muted-foreground">
          {{ t("contest.my.subtitle") }}
        </p>
      </div>
    </div>

    <Separator />

    <MyContests />
  </div>
</template>
