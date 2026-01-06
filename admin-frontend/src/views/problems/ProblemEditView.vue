<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

onMounted(() => {
  // Redirect to the description edit view
  router.replace({ name: 'problem-edit-description', params: { id: route.params.id } })
})
</script>

<template>
  <div class="flex items-center justify-center py-12">
    <div class="text-center">
      <div
        class="inline-block h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"
      ></div>
      <p class="mt-2 text-muted-foreground">Loading...</p>
    </div>
  </div>
</template>
