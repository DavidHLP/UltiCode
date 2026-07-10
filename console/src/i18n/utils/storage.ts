/**
 * Re-export from shared/locale-preference (arch review candidate #3).
 * Registers vue-sonner toast as the storage notifier on first import.
 */
import { toast } from 'vue-sonner'
import { setStorageNotifier } from '@/shared/locale-preference/src'

// Wire the shared storage module's notifier to this app's toast
setStorageNotifier((level, message) => {
  if (level === 'warning') toast.warning(message)
  else if (level === 'info') toast.info(message)
  else if (level === 'success') toast.success(message)
})

export { getStoredLocale, setStoredLocale, type SupportedLocale } from '@/shared/locale-preference/src'
