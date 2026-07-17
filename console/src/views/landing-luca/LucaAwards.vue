<script setup lang="ts">
/**
 * Self-contained awards marquee.
 *
 * Mirrors the "honors strip" rhythm from the reference site without pulling
 * any third-party brand marks: every badge is generated from inline SVG and
 * localized text. The two-row infinite loop is a pure CSS animation that
 * pauses under `prefers-reduced-motion: reduce` (see landing-luca.css).
 *
 * The set of badges is driven by i18n so the loop length stays in sync with
 * either locale's copy.
 */

import { computed } from "vue";
import { useI18n } from "vue-i18n";

interface AwardEntry {
  readonly key: string;
}

const AWARD_KEYS: ReadonlyArray<AwardEntry> = [
  { key: "siteOfDay2024" },
  { key: "siteOfDay2023" },
  { key: "honorable2023a" },
  { key: "honorable2023b" },
  { key: "innovation2022" },
  { key: "kudos2022" },
];

const { t } = useI18n();

// Tripled so the linear-gradient mask never reveals an empty edge even on
// ultra-wide viewports. The animation translates by exactly one third per
// cycle.
const loop = computed(() => [...AWARD_KEYS, ...AWARD_KEYS, ...AWARD_KEYS]);
</script>

<template>
  <section
    class="luca-section luca-awards luca-beat"
    aria-labelledby="luca-awards-title"
  >
    <p class="luca-eyebrow">{{ t("landingLuca.awards.eyebrow") }}</p>
    <h2
      id="luca-awards-title"
      class="luca-section-title"
      style="max-width: 18ch"
      data-luca-reveal
    >
      {{ t("landingLuca.awards.title") }}
    </h2>

    <div
      class="luca-awards-track"
      data-luca-reveal
      :aria-label="t('landingLuca.awards.label')"
      role="list"
    >
      <div class="luca-awards-row">
        <div
          v-for="(award, index) in loop"
          :key="`a-${index}`"
          class="luca-award"
          role="listitem"
        >
          <svg
            class="luca-award-badge"
            viewBox="0 0 64 64"
            aria-hidden="true"
            focusable="false"
          >
            <circle
              cx="32"
              cy="32"
              r="29"
              fill="none"
              stroke="currentColor"
              stroke-width="1.25"
            />
            <circle cx="32" cy="32" r="3" fill="currentColor" />
            <path
              d="M32 8 L34.6 14 L41 14.6 L36.2 18.8 L37.8 25 L32 21.6 L26.2 25 L27.8 18.8 L23 14.6 L29.4 14 Z"
              fill="currentColor"
              opacity="0.78"
            />
          </svg>
          <span class="luca-award-label">{{
            t(`landingLuca.awards.items.${award.key}`)
          }}</span>
        </div>
      </div>
      <div class="luca-awards-row luca-awards-row--reverse" aria-hidden="true">
        <div
          v-for="(award, index) in loop"
          :key="`b-${index}`"
          class="luca-award luca-award--ghost"
          aria-hidden="true"
        >
          <svg
            class="luca-award-badge"
            viewBox="0 0 64 64"
            focusable="false"
          >
            <circle
              cx="32"
              cy="32"
              r="29"
              fill="none"
              stroke="currentColor"
              stroke-width="1.25"
            />
            <circle cx="32" cy="32" r="3" fill="currentColor" />
          </svg>
          <span class="luca-award-label">{{
            t(`landingLuca.awards.items.${award.key}`)
          }}</span>
        </div>
      </div>
    </div>
  </section>
</template>