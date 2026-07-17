<script setup lang="ts">
/**
 * Top-edge scroll progress indicator.
 *
 * Renders a single hairline bar whose width tracks `useScrollProgress`. The
 * bar is purely decorative (aria-hidden) and collapses to opacity 0 under
 * `prefers-reduced-motion: reduce` so it never introduces motion users have
 * opted out of.
 */

import { computed } from "vue";
import { useScrollProgress } from "@/composables/landing/useScrollProgress";

const progress = useScrollProgress();
const scale = computed(() => Math.max(0, Math.min(1, progress.value)));
</script>

<template>
  <div
    class="luca-progress"
    aria-hidden="true"
    :style="{ transform: `scaleX(${scale})` }"
  ></div>
</template>