<script setup lang="ts">
import { useI18n } from "vue-i18n";

const { t } = useI18n();

// Visual brand typography: short uppercase words cycle the UltiCode loop.
// Values are intentionally identical across locales (brand terms), but keyed
// through i18n so the structure stays locale-driven and auditable.
const words = ["code", "judge", "compete", "learn"] as const;

// Editorial index labels — purely cosmetic, no i18n needed because the brand
// language stays numeric across locales.
const indexes = ["00", "01", "02", "03"] as const;
</script>

<template>
  <h1 class="luca-wordstack" aria-label="UltiCode — Code, Judge, Compete, Learn">
    <span
      v-for="(word, i) in words"
      :key="word"
      class="luca-word"
      :data-index="indexes[i]"
    >
      <span class="luca-word-index" aria-hidden="true">{{ indexes[i] }}</span>
      <span class="luca-word-text">{{ t(`landingLuca.hero.words.${word}`) }}</span>
    </span>
  </h1>
</template>