<script setup lang="ts">
import { Lightbulb } from 'lucide-vue-next'
import { Card, CardContent } from '@/components/ui/card'

defineProps<{
  problemId: string
}>()
</script>

<template>
  <div class="flex items-center justify-center py-16">
    <Card class="max-w-md border-dashed">
      <CardContent class="flex flex-col items-center justify-center py-12 px-6 text-center">
        <div class="w-16 h-16 rounded-full bg-amber-500/10 flex items-center justify-center mb-4">
          <Lightbulb :size="32" class="text-amber-600 dark:text-amber-400" />
        </div>
        <h3 class="text-base font-semibold mb-2">Solutions View Coming Soon</h3>
        <p class="text-sm text-muted-foreground mb-4">
          The solutions viewer will be available once the backend endpoint is implemented.
        </p>
        <div class="text-xs text-muted-foreground space-y-1 bg-muted/50 p-3 rounded-lg text-left">
          <p class="font-medium">Required endpoint:</p>
          <code class="block font-mono bg-background px-2 py-1 rounded mt-1">
            GET /api/admin/problems/:id/solutions
          </code>
        </div>
      </CardContent>
    </Card>
  </div>
</template>
