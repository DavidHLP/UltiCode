<script setup lang="ts">
import { computed } from "vue";
import { getRatingTitle, getRatingColor } from "@/types/contest";
import { useI18n } from "vue-i18n";

const props = withDefaults(
  defineProps<{
    rating: number | null;
    showTitle?: boolean;
    size?: "sm" | "md" | "lg";
  }>(),
  {
    rating: null,
  },
);

const { t } = useI18n();

const title = computed(() =>
  props.rating != null ? getRatingTitle(props.rating) : "NEWBIE",
);
const color = computed(() =>
  props.rating != null ? getRatingColor(props.rating) : "var(--muted-foreground)",
);

const displayName = computed(() => {
  const map: Record<string, string> = {
    NEWBIE: "newbie",
    PUPIL: "pupil",
    SPECIALIST: "specialist",
    EXPERT: "expert",
    CANDIDATE_MASTER: "candidateMaster",
    MASTER: "master",
    INTERNATIONAL_MASTER: "internationalMaster",
    GRANDMASTER: "grandmaster",
    INTERNATIONAL_GRANDMASTER: "internationalGrandmaster",
    LEGENDARY_GRANDMASTER: "legendaryGrandmaster",
  };
  return t(`contest.rating.${map[title.value]}`);
});

const sizeClasses = computed(() => {
  switch (props.size) {
    case "sm":
      return "text-xs px-1.5 py-0.5";
    case "lg":
      return "text-base px-3 py-1.5";
    default:
      return "text-sm px-2 py-1";
  }
});
</script>

<template>
  <div class="inline-flex items-center gap-1.5">
    <span
      class="font-bold rounded-none border border-transparent text-foreground-strong"
      :class="sizeClasses"
      :style="{ borderColor: color }"
    >
      {{ rating ?? "—" }}
    </span>
    <span v-if="showTitle" class="text-xs text-muted-foreground font-medium">
      {{ displayName }}
    </span>
  </div>
</template>
