<script setup lang="ts">
import { computed } from "vue";
import { getRatingTitle, getRatingColor } from "@/types/contest";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  rating: number;
  showTitle?: boolean;
  size?: "sm" | "md" | "lg";
}>();

const { t } = useI18n();

const title = computed(() => getRatingTitle(props.rating));
const color = computed(() => getRatingColor(props.rating));

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
      class="font-bold rounded"
      :class="sizeClasses"
      :style="{ color: color }"
    >
      {{ rating }}
    </span>
    <span v-if="showTitle" class="text-xs text-muted-foreground font-medium">
      {{ displayName }}
    </span>
  </div>
</template>
