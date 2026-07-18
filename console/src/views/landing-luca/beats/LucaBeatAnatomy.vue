<script setup lang="ts">
/**
 * Beat 06 — "quarteted". The scene flings four icosahedra to the canvas
 * corners; the DOM beat lays out a 2×2 pillar grid of the platform's four
 * controlled surfaces. Hovering or focusing a pillar publishes its key on
 * the stage bus so the scene can flare the matching fragment. Each pillar
 * is also a RouterLink into the surface it names.
 */
import { useI18n } from "vue-i18n";
import type { RouteLocationRaw } from "vue-router";
import LucaBeat from "./LucaBeat.vue";
import { useLucaStageConsumer } from "@/composables/landing/useLucaStage";

interface Props {
  n: number;
  total: number;
  align?: "left" | "right";
}
const props = withDefaults(defineProps<Props>(), { align: "left" });

const { t } = useI18n();
const stage = useLucaStageConsumer();

type PillarKey = "editor" | "judge" | "contest" | "community";

interface PillarDef {
  key: PillarKey;
  route: RouteLocationRaw;
}

// Stable domain keys (NOT array index) — order is the visual reading order.
const PILLARS: readonly PillarDef[] = [
  { key: "editor", route: { name: "problemset" } },
  { key: "judge", route: { name: "problemset" } },
  { key: "contest", route: { name: "contest-list" } },
  { key: "community", route: { name: "forum-home" } },
];

const pillarLabel = (key: PillarKey) =>
  t(`landingLuca.beats.quarteted.pillars.${key}.label`);
const pillarDesc = (key: PillarKey) =>
  t(`landingLuca.beats.quarteted.pillars.${key}.desc`);
const isActive = (key: PillarKey) => stage.activeFragment.value === key;
const indexLabel = (i: number) => String(i + 1).padStart(2, "0");
</script>

<template>
  <LucaBeat
    state="quarteted"
    :n="props.n"
    :total="props.total"
    :align="props.align"
    :eyebrow="t('landingLuca.beats.quarteted.eyebrow')"
    :title="t('landingLuca.beats.quarteted.title')"
    :subline="t('landingLuca.beats.quarteted.subline')"
  >
    <div class="luca-pillars" data-luca-reveal>
      <RouterLink
        v-for="(pillar, i) in PILLARS"
        :key="pillar.key"
        :to="pillar.route"
        class="luca-pillar"
        :class="{ 'is-active': isActive(pillar.key) }"
        @mouseenter="stage.setFragment(pillar.key)"
        @focus="stage.setFragment(pillar.key)"
        @mouseleave="stage.setFragment(null)"
        @blur="stage.setFragment(null)"
      >
        <span class="luca-pillar-index">{{ indexLabel(i) }}</span>
        <span class="luca-pillar-label">{{ pillarLabel(pillar.key) }}</span>
        <span class="luca-pillar-desc">{{ pillarDesc(pillar.key) }}</span>
      </RouterLink>
    </div>
  </LucaBeat>
</template>
