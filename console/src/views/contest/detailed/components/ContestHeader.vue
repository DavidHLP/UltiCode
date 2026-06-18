<script setup lang="ts">
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { ArrowLeft } from "lucide-vue-next";
import { useI18n } from "vue-i18n";
import type { ContestDetail } from "@/types/contest";

defineProps<{
  contest: ContestDetail;
}>();

const { t } = useI18n();
</script>

<template>
  <div class="space-y-6">
    <Button
      variant="ghost"
      size="sm"
      class="gap-2 cursor-pointer hover:bg-[var(--silver-200)]/30 rounded-none border border-transparent hover:border-border transition-all h-8 px-3"
      @click="$router.push({ name: 'contest-list' })"
    >
      <ArrowLeft class="h-4 w-4" />
      {{ t("contest.detail.backToList") }}
    </Button>

    <div class="space-y-2">
      <div class="flex items-center gap-3">
        <h1
          class="text-3xl font-black tracking-tight text-[var(--solarized-base02)] dark:text-[var(--solarized-base1)]"
        >
          {{ contest.title }}
        </h1>
        <Badge
          :variant="
            contest.status === 'RUNNING'
              ? 'destructive'
              : contest.status === 'UPCOMING'
                ? 'default'
                : 'secondary'
          "
          class="rounded-none px-3 h-5 font-bold uppercase text-2xs tracking-widest border border-current/25 bg-current/5"
          :class="
            contest.status === 'RUNNING'
              ? 'text-[var(--terminal-red)] border-[var(--terminal-red)]'
              : contest.status === 'UPCOMING'
                ? 'text-[var(--terminal-green)] border-[var(--terminal-green)]'
                : 'text-muted-foreground border-border'
          "
        >
          {{
            contest.status === "UPCOMING"
              ? t("contest.status.upcoming")
              : contest.status === "RUNNING"
                ? t("contest.list.liveBadge")
                : t("contest.status.finished")
          }}
        </Badge>
      </div>
      <p
        v-if="contest.description"
        class="text-base text-muted-foreground max-w-3xl leading-relaxed"
      >
        {{ contest.description }}
      </p>
    </div>
  </div>
</template>
