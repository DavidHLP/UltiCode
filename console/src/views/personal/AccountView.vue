<script setup lang="ts">
import { useI18n } from "vue-i18n";
import PersonalPageShell from "./components/PersonalPageShell.vue";
import PersonalPageHeader from "./components/PersonalPageHeader.vue";

const { t } = useI18n();
</script>

<template>
  <PersonalPageShell>
    <PersonalPageHeader
      :title="t('personal.account.title')"
      :description="t('personal.account.subtitle')"
    />
    <RouterView />
  </PersonalPageShell>
</template>