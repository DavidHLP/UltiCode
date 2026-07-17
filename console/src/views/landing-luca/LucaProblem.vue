<script setup lang="ts">
import { useI18n } from "vue-i18n";

const { t } = useI18n();
const fragments = ["one", "two", "three", "four"] as const;
</script>

<template>
  <section class="luca-section luca-problem" aria-labelledby="luca-problem-title">
    <p class="luca-eyebrow">{{ t("landingLuca.problem.eyebrow") }}</p>
    <h2 id="luca-problem-title" class="luca-section-title" data-luca-reveal>
      {{ t("landingLuca.problem.title") }}
    </h2>
    <p class="luca-manifesto-body" data-luca-reveal>
      {{ t("landingLuca.problem.body") }}
    </p>
    <div class="luca-problem-field luca-stagger">
      <span
        v-for="(fragment, i) in fragments"
        :key="fragment"
        class="luca-problem-fragment"
        :class="{ 'is-accent': i === 0 }"
      >
        {{ t(`landingLuca.problem.fragments.${fragment}`) }}
      </span>
    </div>
  </section>
</template>
