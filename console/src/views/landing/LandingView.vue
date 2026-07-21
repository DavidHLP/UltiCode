<script setup lang="ts">
import { useI18n } from "vue-i18n";

const { t } = useI18n();
</script>

<template>
  <div class="landing-frame-host">
    <iframe
      class="landing-frame"
      src="/landing/index.html"
      :title="t('common.landingFrameTitle')"
      allow="autoplay"
    ></iframe>
  </div>
</template>

<style scoped>
.landing-frame-host {
  position: fixed;
  inset: 0;
  overflow: hidden;
  background: #101010;
}

.landing-frame {
  display: block;
  width: 100%;
  height: 100%;
  border: 0;
}
</style>
