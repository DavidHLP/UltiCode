<script setup lang="ts">
import AppearanceSettings from "@ulticode/design-system/AppearanceSettings.vue";
import { useI18n } from "vue-i18n";
import LandingView from "./views/landing/LandingView.vue";
const { locale } = useI18n();
</script>

<template>
  <LandingView />
  <AppearanceSettings :locale="locale" />
</template>
