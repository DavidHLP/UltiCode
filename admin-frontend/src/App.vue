<script setup lang="ts">
import { RouterView } from 'vue-router'
import { onMounted } from 'vue'
import { useAuthStore } from '@/stores/admin/auth'
import { Toaster } from '@/components/ui/sonner'

const authStore = useAuthStore()

onMounted(() => {
  authStore.initialize()
})
</script>

<template>
  <Toaster />
  <RouterView />
</template>

<style scoped></style>
