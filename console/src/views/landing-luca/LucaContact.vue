<script setup lang="ts">
import { useI18n } from "vue-i18n";

const { t } = useI18n();

interface Emits {
  primary: [];
}
defineEmits<Emits>();
</script>

<template>
  <section class="luca-section luca-contact" aria-labelledby="luca-contact-title">
    <p class="luca-eyebrow">{{ t("landingLuca.contact.eyebrow") }}</p>
    <h2
      id="luca-contact-title"
      class="luca-contact-title"
      data-luca-reveal
    >
      {{ t("landingLuca.contact.title") }}
    </h2>
    <p class="luca-contact-desc" data-luca-reveal>
      {{ t("landingLuca.contact.desc") }}
    </p>
    <div data-luca-reveal>
      <button type="button" class="luca-contact-cta" @click="$emit('primary')">
        {{ t("landingLuca.contact.cta") }} →
      </button>
    </div>
    <div>
      <RouterLink :to="{ name: 'problemset' }" class="luca-contact-secondary">
        {{ t("landingLuca.contact.ctaSecondary") }} →
      </RouterLink>
    </div>
  </section>
</template>
