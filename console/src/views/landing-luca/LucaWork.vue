<script setup lang="ts">
import { useI18n } from "vue-i18n";

const { t } = useI18n();

interface WorkItem {
  key: "twosum" | "editor" | "judge" | "contest";
  to: { name: string; params?: Record<string, string> };
}

const TWO_SUM_SLUG = "two-sum";

const items: ReadonlyArray<WorkItem> = [
  { key: "twosum", to: { name: "problem-detail", params: { slug: TWO_SUM_SLUG } } },
  { key: "editor", to: { name: "problemset" } },
  { key: "judge", to: { name: "problemset" } },
  { key: "contest", to: { name: "contest-list" } },
];
</script>

<template>
  <section class="luca-section" aria-labelledby="luca-work-title">
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

    <div class="luca-work-grid luca-stagger" style="margin-top: 3rem">
      <RouterLink
        v-for="item in items"
        :key="item.key"
        :to="item.to"
        class="luca-work-card"
      >
        <span class="luca-work-tag">{{
          t(`landingLuca.work.items.${item.key}.tag`)
        }}</span>
        <span>
          <span class="luca-work-title">{{
            t(`landingLuca.work.items.${item.key}.title`)
          }}</span>
          <span class="luca-work-desc">{{
            t(`landingLuca.work.items.${item.key}.desc`)
          }}</span>
        </span>
        <span class="luca-work-arrow" aria-hidden="true">→</span>
      </RouterLink>
    </div>
  </section>
</template>
