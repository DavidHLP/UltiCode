<script setup lang="ts">
import { useI18n } from "vue-i18n";

const { t } = useI18n();
const principles = ["one", "two", "three"] as const;
</script>

<template>
  <section class="luca-section luca-about" aria-labelledby="luca-about-title">
    <p class="luca-eyebrow">{{ t("landingLuca.about.eyebrow") }}</p>
    <div class="luca-about-grid">
      <h2 id="luca-about-title" class="luca-about-statement">
        <span class="luca-line"><span class="luca-line-inner">{{ t("landingLuca.about.statement") }}</span></span>
      </h2>
      <div class="luca-about-body">
        <p class="luca-manifesto-body" data-luca-reveal>
          {{ t("landingLuca.about.body1") }}
        </p>
        <p class="luca-manifesto-body" data-luca-reveal>
          {{ t("landingLuca.about.body2") }}
        </p>
      </div>
    </div>
    <ul class="luca-about-principles luca-stagger">
      <li v-for="principle in principles" :key="principle" class="luca-about-principle">
        {{ t(`landingLuca.about.principles.${principle}`) }}
      </li>
    </ul>
  </section>
</template>
