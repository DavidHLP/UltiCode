<script setup lang="ts">
import { useI18n } from "vue-i18n";

const { t } = useI18n();

// Visual brand typography: short uppercase words cycle the UltiCode loop.
// Values are intentionally identical across locales (brand terms), but keyed
// through i18n so the structure stays locale-driven and auditable.
const words = ["code", "judge", "compete", "learn"] as const;
</script>

<template>
  <h1 class="luca-wordstack" aria-label="UltiCode — Code, Judge, Compete, Learn">
    <span v-for="word in words" :key="word" class="luca-word">{{
      t(`landingLuca.hero.words.${word}`)
    }}</span>
  </h1>
</template>
