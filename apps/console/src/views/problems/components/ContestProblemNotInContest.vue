<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { AlertTriangle } from "@lucide/vue";
import { Button } from "@ulticode/design-system/ui/button";
import type { ContestDetail } from "@/types/contest";

/**
 * Inline error state shown inside the problem page when the URL
 * carries a `?contestId=...` that doesn't include the current problem.
 *
 * Renders a compact banner; the back button is the only interaction.
 * We deliberately do NOT auto-redirect — the user might have a deep
 * link to a sibling problem in a different contest and we want them
 * to know we caught it, not silently bounce them to a different page.
 */
const props = defineProps<{
  contest: ContestDetail;
}>();

const { t } = useI18n();

const backHref = computed(() => `/contest/${props.contest.slug}`);
</script>

<template>
  <div
    class="flex items-center gap-3 border border-status-warning-mark/40 bg-status-warning-mark/10 px-3 py-2 font-mono text-xs uppercase tracking-wider text-foreground"
    role="alert"
    data-testid="contest-problem-not-in-contest"
  >
    <AlertTriangle class="h-4 w-4 shrink-0" :stroke-width="2.5" />
    <div class="flex flex-1 items-center justify-between gap-3 min-w-0">
      <div class="truncate">
        <span class="font-black">{{
          t("contest.detail.problemNotInContest.title")
        }}</span>
        <span
          class="ml-2 text-foreground/70 normal-case font-normal tracking-normal"
        >
          {{ t("contest.detail.problemNotInContest.description") }}
        </span>
      </div>
      <Button
        variant="outline"
        size="sm"
        class="px-3"
        :data-testid="'back-to-contest-from-not-in-contest'"
        @click="$router.push(backHref)"
      >
        {{ t("contest.detail.problemNotInContest.action") }}
      </Button>
    </div>
  </div>
</template>
