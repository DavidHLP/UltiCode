<script setup lang="ts">
/**
 * GrowthSection — a submission is not an isolated event. The visual focus is
 * accumulation: an illustrative trajectory curve with submission nodes, plus
 * the product surfaces that record it (history, lists, review, trends,
 * achievements). No invented metrics — shape, not numbers.
 */
import { computed } from "vue";
import { useI18n } from "vue-i18n";

const { t } = useI18n();

// Explicit literal keys: the i18n static checker cannot resolve dynamic
// key interpolation in templates.
const points = computed(() => [
  t("landing.growth.points.history"),
  t("landing.growth.points.lists"),
  t("landing.growth.points.review"),
  t("landing.growth.points.trend"),
  t("landing.growth.points.achievements"),
]);

defineOptions({ name: "GrowthSection" });
</script>

<template>
  <section class="landing-section" aria-labelledby="landing-growth-title">
    <div class="landing-container">
      <div class="grid items-center gap-10 lg:grid-cols-2">
        <div class="landing-track" aria-hidden="true">
          <svg viewBox="0 0 320 200" class="landing-track-svg">
            <path
              d="M16 176 C 80 160, 96 128, 144 116 S 224 88, 244 60 S 296 36, 304 24"
              fill="none"
              stroke="var(--solarized-green)"
              stroke-opacity="0.7"
              stroke-width="1.5"
            />
            <g fill="var(--solarized-green)">
              <circle cx="16" cy="176" r="3" fill-opacity="0.45" />
              <circle cx="76" cy="156" r="3" fill-opacity="0.55" />
              <circle cx="144" cy="116" r="3" fill-opacity="0.7" />
              <circle cx="204" cy="96" r="3" fill-opacity="0.8" />
              <circle cx="244" cy="60" r="3.5" fill-opacity="0.9" />
              <circle cx="304" cy="24" r="4" />
            </g>
          </svg>
        </div>

        <div class="landing-block">
          <p class="landing-eyebrow">{{ t("landing.growth.eyebrow") }}</p>
          <h2 id="landing-growth-title" class="landing-heading">
            {{ t("landing.growth.title") }}
          </h2>
          <p class="landing-body">{{ t("landing.growth.body") }}</p>
          <ul class="landing-points">
            <li v-for="point in points" :key="point">
              {{ point }}
            </li>
          </ul>
        </div>
      </div>
    </div>
  </section>
</template>
