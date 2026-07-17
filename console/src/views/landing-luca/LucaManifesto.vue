<script setup lang="ts">
import { useI18n } from "vue-i18n";

const { t } = useI18n();
</script>

<template>
  <section class="luca-section luca-beat" aria-labelledby="luca-manifesto-title">
    <p class="luca-eyebrow">{{ t("landingLuca.manifesto.eyebrow") }}</p>
    <h2
      id="luca-manifesto-title"
      class="luca-manifesto-lead"
      data-luca-reveal
    >
      <span class="luca-line"><span class="luca-line-inner">{{ t("landingLuca.manifesto.lead") }}</span></span>
    </h2>
    <div class="luca-manifesto-body" data-luca-reveal>
      <p>{{ t("landingLuca.manifesto.body1") }}</p>
      <p class="mt-4">{{ t("landingLuca.manifesto.body2") }}</p>
    </div>
    <p class="luca-manifesto-signal" data-luca-reveal>
      {{ t("landingLuca.manifesto.signal") }}
    </p>
  </section>
</template>
