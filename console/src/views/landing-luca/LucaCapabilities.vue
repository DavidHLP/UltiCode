<script setup lang="ts">
import { useI18n } from "vue-i18n";

const { t } = useI18n();

const pillars = ["editor", "judge", "contest", "community"] as const;
</script>

<template>
  <section class="luca-section luca-beat" aria-labelledby="luca-capabilities-title">
    <p class="luca-eyebrow">{{ t("landingLuca.capabilities.eyebrow") }}</p>
    <h2
      id="luca-capabilities-title"
      class="luca-section-title"
      style="max-width: 16ch"
      data-luca-reveal
    >
      {{ t("landingLuca.capabilities.title") }}
    </h2>

    <div class="luca-pillars" style="margin-top: 3rem">
      <div
        v-for="(pillar, index) in pillars"
        :key="pillar"
        class="luca-pillar"
        data-luca-reveal
      >
        <span class="luca-pillar-index">{{
          String(index + 1).padStart(2, "0")
        }}</span>
        <span class="luca-pillar-label">{{
          t(`landingLuca.capabilities.pillars.${pillar}.label`)
        }}</span>
        <span class="luca-pillar-desc">{{
          t(`landingLuca.capabilities.pillars.${pillar}.desc`)
        }}</span>
      </div>
    </div>
  </section>
</template>
