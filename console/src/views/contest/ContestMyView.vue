<script setup lang="ts">
import { onMounted } from "vue";
import { Trophy } from "lucide-vue-next";
import { useContestRankingStore } from "@/stores/contestRanking";
import { Separator } from "@/components/ui/separator";
import MyContests from "./components/MyContests.vue";
import { useI18n } from "vue-i18n";

const contestStore = useContestRankingStore();
const { t } = useI18n();

onMounted(async () => {
  try {
    await contestStore.loadUserContests("registered");
  } catch (error) {
    console.error("Failed to load user contests:", error);
  }
});
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
          <Trophy class="h-8 w-8 text-[var(--terminal-amber)]" />
          <h1
            class="text-3xl font-bold tracking-tight text-[var(--terminal-amber)]"
          >
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
