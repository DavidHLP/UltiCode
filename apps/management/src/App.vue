<script setup lang="ts">
import 'vue-sonner/style.css'
import { RouterView } from 'vue-router'
import { Toaster } from '@/components/ui/sonner'
</script>

<template>
  <!-- Garden paper grain (shared design system; see GARDEN_DESIGN_SPEC.md §5) -->
  <div class="paper-texture-overlay" aria-hidden="true" />
  <RouterView />
  <Toaster />
</template>

<style scoped></style>
