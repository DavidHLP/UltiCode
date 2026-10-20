<script setup lang="ts">
import { Button } from '@ulticode/design-system/ui/button'
import { openAppearanceSettings } from '@ulticode/theme'
import { useI18n } from 'vue-i18n'
import { Paintbrush } from '@lucide/vue'
const { locale } = useI18n()
</script>
<template>
  <Button
    variant="ghost"
    size="icon"
    :aria-label="locale === 'zh-CN' ? '外观设置' : 'Appearance'"
    @click="openAppearanceSettings"
    ><Paintbrush
  /></Button>
</template>
