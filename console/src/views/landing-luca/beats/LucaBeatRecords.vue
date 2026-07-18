<script setup lang="ts">
/**
 * Beat 07 — "timed". The scene lights a ring of year ticks as the local
 * scrub advances; the DOM beat mirrors that as a vertical specification log
 * (2021 → 2026). Row activation is driven by an IntersectionObserver over
 * each row, so the log lights on every viewport — the stage bus creates no
 * ScrollTrigger under (max-width: 768px) or prefers-reduced-motion, so a
 * progress-based test never lights any row on phones or reduced-motion.
 */
import { onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useI18n } from "vue-i18n";
import LucaBeat from "./LucaBeat.vue";

interface Props {
  n: number;
  total: number;
  align?: "left" | "right";
}
const props = withDefaults(defineProps<Props>(), { align: "right" });

const { t } = useI18n();

const YEARS = ["2021", "2022", "2023", "2024", "2025", "2026"] as const;

// Per-row active flags keyed by stable year string. Set true by the
// IntersectionObserver when the row scrolls into the focused viewport band.
const activeYears = reactive<Record<string, boolean>>({});
const listRef = ref<HTMLOListElement | null>(null);
let observer: IntersectionObserver | null = null;

const yearNote = (year: string) => t(`landingLuca.beats.timed.years.${year}`);
const isActive = (year: string) => Boolean(activeYears[year]);

onMounted(() => {
  if (
    typeof window === "undefined" ||
    typeof IntersectionObserver === "undefined"
  ) {
    // jsdom / no IO support: light every row so the log still reads.
    YEARS.forEach((y) => {
      activeYears[y] = true;
    });
    return;
  }
  const list = listRef.value;
  if (!list) return;
  const rows = Array.from(list.querySelectorAll<HTMLElement>("[data-year]"));
  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        const year = (entry.target as HTMLElement).dataset.year;
        if (!year) continue;
        if (entry.isIntersecting) activeYears[year] = true;
      }
    },
    { rootMargin: "-40% 0px -40% 0px", threshold: 0 },
  );
  rows.forEach((row) => {
    if (observer) observer.observe(row);
  });
});

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect();
    observer = null;
  }
});
</script>

<template>
  <LucaBeat
    state="timed"
    :n="props.n"
    :total="props.total"
    :align="props.align"
    :eyebrow="t('landingLuca.beats.timed.eyebrow')"
    :title="t('landingLuca.beats.timed.title')"
    :subline="t('landingLuca.beats.timed.subline')"
  >
    <div class="luca-record-log" data-luca-reveal>
      <p class="luca-record-log-label">{{ t("landingLuca.beats.timed.logLabel") }}</p>
      <ol ref="listRef" class="luca-record-list">
        <li
          v-for="year in YEARS"
          :key="year"
          :data-year="year"
          class="luca-record-row"
          :class="{ 'is-active': isActive(year) }"
        >
          <span class="luca-record-year">{{ year }}</span>
          <span class="luca-record-note">{{ yearNote(year) }}</span>
        </li>
      </ol>
    </div>
  </LucaBeat>
</template>
