<script setup lang="ts">
import { useI18n } from "vue-i18n";

const { t } = useI18n();
const orbits = ["editor", "judge", "community"] as const;
</script>

<template>
  <section class="luca-section luca-solution luca-beat" aria-labelledby="luca-solution-title">
    <p class="luca-eyebrow">{{ t("landingLuca.solution.eyebrow") }}</p>
    <h2 id="luca-solution-title" class="luca-section-title" data-luca-reveal>
      {{ t("landingLuca.solution.title") }}
    </h2>
    <p class="luca-manifesto-body" data-luca-reveal>
      {{ t("landingLuca.solution.body") }}
    </p>
    <div class="luca-solution-diagram" data-luca-reveal>
      <span class="luca-solution-core">{{ t("landingLuca.solution.core") }}</span>
      <div class="luca-solution-orbits luca-stagger">
        <div v-for="orbit in orbits" :key="orbit" class="luca-solution-orbit">
          <span class="luca-solution-orbit-label">{{
            t(`landingLuca.solution.orbits.${orbit}.label`)
          }}</span>
          <span class="luca-solution-orbit-desc">{{
            t(`landingLuca.solution.orbits.${orbit}.desc`)
          }}</span>
        </div>
      </div>
    </div>
  </section>
</template>
