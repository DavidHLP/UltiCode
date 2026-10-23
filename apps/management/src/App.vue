<script setup lang="ts">
import { watch } from 'vue'
import { setAppearanceAccount } from '@ulticode/theme'
import { useAuthStore } from '@/stores/auth'
import AppearanceSettings from '@ulticode/design-system/AppearanceSettings.vue'
import { useI18n } from 'vue-i18n'
import 'vue-sonner/style.css'
import { RouterView } from 'vue-router'
import { Toaster } from '@ulticode/design-system/ui/sonner'
const { locale } = useI18n()
const auth = useAuthStore()
watch(() => auth.user?.id ?? null, setAppearanceAccount, { immediate: true, flush: 'sync' })
</script>

<template>
  <RouterView />
  <AppearanceSettings :locale="locale" />
  <Toaster />
</template>
