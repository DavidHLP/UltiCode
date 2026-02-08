import type { App } from 'vue'
import { toast } from 'vue-sonner'

export function setupGlobalErrorHandler(app: App) {
  app.config.errorHandler = (err, instance, info) => {
    // Log error
    console.error('Vue error:', err, info)

    // Show user-friendly message
    const message = err instanceof Error ? err.message : 'An unexpected error occurred'
    toast.error(message)
  }

  // Handle unhandled promise rejections
  window.addEventListener('unhandledrejection', (event) => {
    console.error('Unhandled promise rejection:', event.reason)
    toast.error('An unexpected error occurred')
    event.preventDefault()
  })
}
