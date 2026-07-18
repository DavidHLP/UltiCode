<script setup lang="ts">
import { useI18n } from "vue-i18n";

const { t } = useI18n();

interface WorkItem {
  key: "twosum" | "editor" | "judge" | "contest";
  index: string;
  to: { name: string; params?: Record<string, string> };
  previewPalette: readonly [string, string, string];
}

const TWO_SUM_SLUG = "two-sum";

const items: ReadonlyArray<WorkItem> = [
  {
    key: "twosum",
    index: "001",
    to: { name: "problem-detail", params: { slug: TWO_SUM_SLUG } },
    // Monochrome specimen swatches — neutral ramps keep the white glyph
    // legible and obey the brutalist field (no hue).
    previewPalette: ["#3f3f3f", "#1c1c1c", "#000000"],
  },
  {
    key: "editor",
    index: "002",
    to: { name: "problemset" },
    previewPalette: ["#2e2e2e", "#141414", "#000000"],
  },
  {
    key: "judge",
    index: "003",
    to: { name: "problemset" },
    previewPalette: ["#4a4a4a", "#222222", "#000000"],
  },
  {
    key: "contest",
    index: "004",
    to: { name: "contest-list" },
    previewPalette: ["#353535", "#0f0f0f", "#000000"],
  },
];
</script>

<template>
  <section class="luca-section luca-beat" aria-labelledby="luca-work-title">
    <p class="luca-eyebrow">{{ t("landingLuca.work.eyebrow") }}</p>
    <h2
      id="luca-work-title"
      class="luca-section-title"
      style="max-width: 18ch"
      data-luca-reveal
    >
      {{ t("landingLuca.work.title") }}
    </h2>
    <p class="luca-manifesto-body" data-luca-reveal>
      {{ t("landingLuca.work.subtitle") }}
    </p>

    <ol class="luca-work-grid luca-stagger" style="margin-top: 3rem">
      <li v-for="item in items" :key="item.key" class="luca-work-row-wrap">
        <RouterLink :to="item.to" class="luca-work-card">
          <span class="luca-work-index" aria-hidden="true">{{ item.index }}</span>
          <span
            class="luca-work-preview"
            :style="{
              backgroundImage: `linear-gradient(135deg, ${item.previewPalette[0]} 0%, ${item.previewPalette[1]} 55%, ${item.previewPalette[2]} 100%)`,
            }"
            aria-hidden="true"
          >
            <span class="luca-work-preview-glyph">{{
              t(`landingLuca.work.items.${item.key}.glyph`)
            }}</span>
          </span>
          <span class="luca-work-meta">
            <span class="luca-work-tag">{{
              t(`landingLuca.work.items.${item.key}.tag`)
            }}</span>
            <span class="luca-work-title">{{
              t(`landingLuca.work.items.${item.key}.title`)
            }}</span>
            <span class="luca-work-desc">{{
              t(`landingLuca.work.items.${item.key}.desc`)
            }}</span>
          </span>
          <span class="luca-work-arrow" aria-hidden="true">→</span>
        </RouterLink>
      </li>
    </ol>
  </section>
</template>