<script setup lang="ts">
import { FieldSeparator } from "@ulticode/design-system/ui/field";
import { useI18n } from "vue-i18n";
const { t } = useI18n();
</script>
<template>
  <FieldSeparator>{{ t("auth.login.orContinueWith") }}</FieldSeparator>
</template>
