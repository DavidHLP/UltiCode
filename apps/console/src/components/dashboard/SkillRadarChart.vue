<script setup lang="ts">
import { computed, ref } from "vue";
import { useResizeObserver } from "@vueuse/core";
import { useI18n } from "vue-i18n";
import type { UserSkill } from "@/types/userStats";
import { problemTagLabelKey } from "@/i18n/utils/problemTagLabel";

const { t } = useI18n();

const props = defineProps<{
  skills: UserSkill[];
  title?: string;
  maxDisplay?: number;
}>();

const skillLabel = (skill: UserSkill) => {
  const key = problemTagLabelKey(skill.tagSlug);
  return key ? t(key) : skill.tagName;
};

const containerRef = ref<HTMLElement | null>(null);
const size = ref(300);

useResizeObserver(containerRef, () => {
  if (containerRef.value) {
    size.value = Math.max(
      0,
      Math.min(containerRef.value.clientWidth - 64, 300),
    );
  }
});

const displaySkills = computed(() => {
  const max = props.maxDisplay || 8;
  return props.skills.slice(0, max);
});

const maxValue = computed(() => {
  return Math.max(...displaySkills.value.map((s) => s.count), 1);
});

const centerX = computed(() => size.value / 2);
const centerY = computed(() => size.value / 2);
const radius = computed(() => (size.value / 2) * 0.7);

const angleStep = computed(() => (2 * Math.PI) / displaySkills.value.length);

const points = computed(() => {
  return displaySkills.value.map((skill, i) => {
    const angle = i * angleStep.value - Math.PI / 2;
    const value = skill.count / maxValue.value;
    return {
      x: centerX.value + radius.value * value * Math.cos(angle),
      y: centerY.value + radius.value * value * Math.sin(angle),
    };
  });
});

const pathD = computed(() => {
  if (points.value.length === 0) return "";
  const first = points.value[0];
  if (!first) return "";
  const rest = points.value.slice(1);
  return `M ${first.x} ${first.y} ${rest.map((p) => `L ${p.x} ${p.y}`).join(" ")} Z`;
});

const gridLines = computed(() => {
  return [0.2, 0.4, 0.6, 0.8, 1].map((scale) => {
    const r = radius.value * scale;
    const gridPoints = displaySkills.value.map((_, i) => {
      const angle = i * angleStep.value - Math.PI / 2;
      return {
        x: centerX.value + r * Math.cos(angle),
        y: centerY.value + r * Math.sin(angle),
      };
    });
    if (gridPoints.length === 0) return "";
    const first = gridPoints[0];
    if (!first) return "";
    const rest = gridPoints.slice(1);
    return `M ${first.x} ${first.y} ${rest.map((p) => `L ${p.x} ${p.y}`).join(" ")} Z`;
  });
});

const labelPositions = computed(() => {
  return displaySkills.value.map((skill, i) => {
    const angle = i * angleStep.value - Math.PI / 2;
    const labelRadius = radius.value + 20;
    return {
      name: skillLabel(skill),
      x: centerX.value + labelRadius * Math.cos(angle),
      y: centerY.value + labelRadius * Math.sin(angle),
      textAnchor:
        Math.abs(Math.cos(angle)) < 0.1
          ? "middle"
          : Math.cos(angle) > 0
            ? "start"
            : "end",
    };
  });
});
</script>

<template>
  <div class="space-y-2">
    <h3 v-if="title" class="text-sm font-medium">{{ title }}</h3>

    <div ref="containerRef" class="flex justify-center">
      <svg
        v-if="displaySkills.length > 0"
        :width="size"
        :height="size"
        class="overflow-visible"
      >
        <!-- Grid lines -->
        <path
          v-for="(d, i) in gridLines"
          :key="i"
          :d="d"
          fill="none"
          stroke="currentColor"
          stroke-width="1"
          class="text-muted opacity-30"
        />

        <!-- Axis lines -->
        <line
          v-for="(_, i) in displaySkills"
          :key="`axis-${i}`"
          :x1="centerX"
          :y1="centerY"
          :x2="points[i]?.x || centerX"
          :y2="points[i]?.y || centerY"
          stroke="currentColor"
          stroke-width="1"
          class="text-muted opacity-30"
        />

        <!-- Data polygon -->
        <path
          :d="pathD"
          fill="currentColor"
          fill-opacity="0.2"
          stroke="currentColor"
          stroke-width="2"
          class="text-primary"
        />

        <!-- Data points -->
        <circle
          v-for="(point, i) in points"
          :key="`point-${i}`"
          :cx="point.x"
          :cy="point.y"
          r="4"
          fill="currentColor"
          class="text-primary"
        />

        <!-- Labels -->
        <text
          v-for="(label, i) in labelPositions"
          :key="`label-${i}`"
          :x="label.x"
          :y="label.y"
          :text-anchor="label.textAnchor"
          dominant-baseline="middle"
          class="fill-current text-xs text-muted-foreground"
        >
          {{ label.name }}
        </text>
      </svg>

      <!-- Empty state -->
      <div
        v-else
        class="flex h-48 w-full items-center justify-center text-center text-sm text-muted-foreground"
      >
        {{ t("personal.skills.noSkills") }}
      </div>
    </div>
  </div>
</template>
