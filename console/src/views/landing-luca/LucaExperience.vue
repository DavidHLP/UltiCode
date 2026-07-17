<script setup lang="ts">
import { useI18n } from "vue-i18n";

interface FlowStep {
  key: string;
  index: string;
  to?: { name: string };
  link?: boolean;
}

interface Emits {
  primary: [];
}

const { t } = useI18n();
defineEmits<Emits>();

const steps: ReadonlyArray<FlowStep> = [
  { key: "read", index: "01", to: { name: "problemset" }, link: true },
  { key: "code", index: "02", to: { name: "problemset" }, link: true },
  { key: "submit", index: "03" },
  { key: "judge", index: "04" },
  { key: "review", index: "05", to: { name: "forum-home" }, link: true },
];
</script>

<template>
  <section class="luca-section luca-experience" aria-labelledby="luca-experience-title">
    <p class="luca-eyebrow">{{ t("landingLuca.experience.eyebrow") }}</p>
    <h2 id="luca-experience-title" class="luca-section-title" data-luca-reveal>
      {{ t("landingLuca.experience.title") }}
    </h2>
    <p class="luca-manifesto-body" data-luca-reveal>
      {{ t("landingLuca.experience.body") }}
    </p>
    <ol class="luca-flow">
      <li v-for="step in steps" :key="step.key" class="luca-flow-step" data-luca-reveal>
        <span class="luca-flow-index">{{ step.index }}</span>
        <div class="luca-flow-content">
          <span class="luca-flow-label">{{
            t(`landingLuca.experience.steps.${step.key}.label`)
          }}</span>
          <span class="luca-flow-desc">{{
            t(`landingLuca.experience.steps.${step.key}.desc`)
          }}</span>
        </div>
        <RouterLink
          v-if="step.link && step.to"
          :to="step.to"
          class="luca-flow-link"
          aria-label="open"
          >→</RouterLink
        >
      </li>
    </ol>
    <div data-luca-reveal>
      <button type="button" class="luca-flow-cta" @click="$emit('primary')">
        {{ t("landingLuca.experience.cta") }} →
      </button>
    </div>
  </section>
</template>
