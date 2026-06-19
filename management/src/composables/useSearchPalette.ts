/**
 * useSearchPalette - shared open/close state for the global command
 * palette. Lifted out of SiteHeader so other surfaces (the sidebar
 * search link, future quick-search buttons, etc.) can open the same
 * modal without prop-drilling or event buses.
 *
 * Backed by a module-level ref so the singleton survives route changes
 * and the palette stays mounted at its single SiteHeader host.
 */
import { ref } from 'vue'

const isOpen = ref(false)

export function useSearchPalette() {
  return {
    isOpen,
    open: () => {
      isOpen.value = true
    },
    close: () => {
      isOpen.value = false
    },
    toggle: () => {
      isOpen.value = !isOpen.value
    },
  }
}
