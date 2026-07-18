<script setup lang="ts">
/**
 * One narrative beat — a full-viewport section whose Chinese headline scrolls
 * over the fixed 3D canvas while the polyhedron acts out the beat's meaning.
 *
 * The shell renders the 01/09 counter, eyebrow, headline, English sub-line,
 * and a slot for beat-specific content (the entrance CTA, the anatomy pillar
 * grid, the records log, the broken dual CTA). The `state` class is what the
 * stage bus queries to pin the beat and publish its polyhedron state.
 */
import type { LucaState } from "@/composables/landing/useLucaStage";

interface Props {
  state: LucaState;
  n: number;
  total: number;
  eyebrow: string;
  title: string;
  subline: string;
  align?: "left" | "right";
}
const props = withDefaults(defineProps<Props>(), { align: "left" });

const pad = (v: number) => String(v).padStart(2, "0");
</script>

<template>
  <section
    :class="['luca-beat', `luca-beat-${props.state}`, `luca-align-${props.align}`]"
    :aria-label="title"
  >
    <p class="luca-beat-counter" data-luca-reveal>
      {{ pad(n) }} / {{ pad(total) }}
    </p>
    <p class="luca-eyebrow" data-luca-reveal>{{ eyebrow }}</p>
    <h2 class="luca-beat-title" data-luca-reveal>{{ title }}</h2>
    <p class="luca-beat-subline" data-luca-reveal>{{ subline }}</p>
    <slot />
  </section>
</template>
