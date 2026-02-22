<script setup lang="ts">
import { ref, onErrorCaptured, type ComponentPublicInstance } from 'vue'
import { Button } from '@/components/ui/button'
import { IconAlertTriangle, IconRefresh } from '@tabler/icons-vue'

const emit = defineEmits<{
  (e: 'error', error: Error): void
}>()

const error = ref<Error | null>(null)
const errorInfo = ref<string | null>(null)

onErrorCaptured(
  (err: Error, instance: ComponentPublicInstance | null, info: string) => {
    error.value = err
    errorInfo.value = info
    emit('error', err)
    return false // Prevent the error from propagating further
  },
)

function retry() {
  error.value = null
  errorInfo.value = null
}

function reload() {
  window.location.reload()
}
</script>

<template>
  <slot v-if="!error" />

  <div
    v-else
    class="flex flex-col items-center justify-center min-h-[300px] p-8 text-center"
  >
    <div class="rounded-full bg-destructive/10 p-4 mb-4">
      <IconAlertTriangle class="h-8 w-8 text-destructive" />
    </div>

    <h2 class="text-xl font-semibold mb-2">Something went wrong</h2>

    <p class="text-muted-foreground mb-4 max-w-md">
      {{ error.message || 'An unexpected error occurred. Please try again.' }}
    </p>

    <details v-if="errorInfo" class="mb-4 text-left w-full max-w-md">
      <summary class="cursor-pointer text-sm text-muted-foreground hover:text-foreground">
        View error details
      </summary>
      <pre class="mt-2 p-4 bg-muted rounded-lg text-xs overflow-auto">{{ error.stack }}</pre>
    </details>

    <div class="flex gap-2">
      <Button variant="outline" @click="retry">
        <IconRefresh class="h-4 w-4 mr-1" />
        Try Again
      </Button>
      <Button @click="reload"> Reload Page </Button>
    </div>
  </div>
</template>
