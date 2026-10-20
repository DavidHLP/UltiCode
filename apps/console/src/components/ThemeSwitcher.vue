<script setup lang="ts">
import { DropdownMenuItem } from "@ulticode/design-system/ui/dropdown-menu";
import { openAppearanceSettings } from "@ulticode/theme";
import { useI18n } from "vue-i18n";
import { Paintbrush } from "@lucide/vue";
const { locale } = useI18n();
</script>
<template>
  <DropdownMenuItem @select="openAppearanceSettings"
    ><Paintbrush />{{
      locale === "zh-CN" ? "外观设置" : "Appearance"
    }}</DropdownMenuItem
  >
</template>
