<script setup lang="ts">
import { useI18n } from "vue-i18n";

interface Props {
  progress: number;
  ready: boolean;
  leaving: boolean;
}
defineProps<Props>();
defineEmits<{ enter: []; skip: [] }>();

const { t } = useI18n();
</script>

<template>
  <div
    class="luca-portal"
    :class="{ 'is-leaving': leaving }"
    role="dialog"
    aria-modal="false"
    :aria-label="t('landingLuca.portalHint')"
  >
    <p class="luca-portal-status" aria-live="polite">
      {{ t("landingLuca.portalStatus") }}
    </p>
    <p class="luca-portal-counter" aria-hidden="true">
      {{ String(progress).padStart(3, "0") }}
    </p>
    <p class="luca-portal-hint">{{ t("landingLuca.portalHint") }}</p>
    <button
      type="button"
      class="luca-portal-enter"
      :class="{ 'is-ready': ready }"
      :tabindex="ready ? 0 : -1"
      @click="$emit('enter')"
    >
      {{ t("landingLuca.enter") }} →
    </button>
    <button type="button" class="luca-portal-skip" @click="$emit('skip')">
      {{ t("landingLuca.skipLoader") }}
    </button>
  </div>
</template>
