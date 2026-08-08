<script setup lang="ts">
import 'vue-sonner/style.css'
import { RouterView } from 'vue-router'
import { Toaster } from '@/components/ui/sonner'
</script>

<template>
  <RouterView />
  <Toaster />
</template>

<style scoped></style>
