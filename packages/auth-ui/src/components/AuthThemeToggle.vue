<script setup lang="ts">
import { openAppearanceSettings } from "@ulticode/theme";
import { Button } from "@ulticode/design-system/ui/button";
import { Paintbrush } from "@lucide/vue";
import { useI18n } from "vue-i18n";
const { locale } = useI18n();
</script>
<template>
  <Button
    type="button"
    variant="ghost"
    size="icon"
    :aria-label="locale === 'zh-CN' ? '外观设置' : 'Appearance'"
    @click="openAppearanceSettings"
    ><Paintbrush
  /></Button>
</template>
