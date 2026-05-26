import {
  computed,
  ref,
  watch,
  onMounted,
  onBeforeUnmount,
  nextTick,
  unref,
  type ComputedRef,
  type Ref,
} from 'vue'
import { useRoute } from 'vue-router'
import { useProblemsStore } from '@/stores/admin/problems'
import type { HeaderData, DescriptionData, CodeData, CasesData } from '@/api/admin/problems'

export type ProblemTab = 'description' | 'code' | 'cases' | 'audit'

const VALID_TABS: ProblemTab[] = ['description', 'code', 'cases', 'audit']

export interface UseProblemTabDataReturn {
  currentTab: ComputedRef<ProblemTab>
  headerData: ComputedRef<HeaderData | null>
  descriptionData: ComputedRef<DescriptionData | null>
  codeData: ComputedRef<CodeData | null>
  casesData: ComputedRef<CasesData | null>
  isLoading: ComputedRef<boolean>
  error: ComputedRef<string | null>
  loadTabData: (tab?: ProblemTab) => Promise<void>
  isReady: Ref<boolean>
}

/**
 * Composable for managing problem detail page tab data lifecycle.
 *
 * Handles:
 * - Tab detection from route params
 * - Lazy data loading per tab with caching awareness
 * - Request cancellation on component unmount
 * - Loading/error state aggregation
 * - Ready state for animations (uses nextTick instead of setTimeout)
 */
export function useProblemTabData(problemId: ComputedRef<string>): UseProblemTabDataReturn {
  const route = useRoute()
  const store = useProblemsStore()

  // Track animation ready state
  const isReady = ref(false)
  // Local loading state to prevent flashing "not found" before first load completes
  const isInitializing = ref(true)

  /**
   * Derive current tab from route params with type safety.
   * Falls back to 'description' for invalid/missing tab values.
   */
  const currentTab = computed<ProblemTab>(() => {
    const tab = route.params.tab as string
    return VALID_TABS.includes(tab as ProblemTab) ? (tab as ProblemTab) : 'description'
  })

  /**
   * Load data for the specified tab.
   * Always fetches header data first, then tab-specific data.
   * Uses caching in the store to avoid redundant requests.
   */
  // Prevent concurrent loadTabData calls
  let isLoadingData = false

  async function loadTabData(tab: ProblemTab = currentTab.value): Promise<void> {
    if (!problemId.value || isLoadingData) return

    isLoadingData = true
    isReady.value = false
    isInitializing.value = true

    try {
      // Always fetch header data (needed for title, badges, etc.)
      const headerPromise = store.fetchHeader(problemId.value)

      // Fetch tab-specific data in parallel with header
      switch (tab) {
        case 'description':
          await Promise.all([headerPromise, store.fetchDescription(problemId.value)])
          break
        case 'code':
          await Promise.all([headerPromise, store.fetchCode(problemId.value)])
          break
        case 'cases':
          await Promise.all([headerPromise, store.fetchCases(problemId.value)])
          break
        case 'audit':
          await headerPromise
          break
      }
    } finally {
      isInitializing.value = false
      isLoadingData = false
    }

    await nextTick()
    isReady.value = true
  }

  /**
   * Watch for tab changes and load appropriate data.
   * This handles both initial load (when route params are first resolved)
   * and subsequent tab switches.
   */
  watch(currentTab, (newTab, oldTab) => {
    if (newTab === oldTab) return
    loadTabData(newTab)
  })

  onMounted(() => {
    loadTabData()
  })

  onBeforeUnmount(() => {
    store.abortAllRequests()
  })

  // Wrap store data in computed to provide clean reactive types.
  // Pinia exposes refs as Ref<T> in types but unwraps at runtime.
  // unref() handles both cases safely.
  const headerData = computed(() => unref(store.headerData))
  const descriptionData = computed(() => unref(store.descriptionData))
  const codeData = computed(() => unref(store.codeData))
  const casesData = computed(() => unref(store.casesData))

  const isLoading = computed(() => {
    if (isInitializing.value) return true

    switch (currentTab.value) {
      case 'description':
        return unref(store.descriptionLoading) || unref(store.headerLoading)
      case 'code':
        return unref(store.codeLoading) || unref(store.headerLoading)
      case 'cases':
        return unref(store.casesLoading) || unref(store.headerLoading)
      case 'audit':
        return unref(store.headerLoading)
      default:
        return false
    }
  })

  const error = computed(() => {
    switch (currentTab.value) {
      case 'description':
        return unref(store.descriptionError) || unref(store.headerError)
      case 'code':
        return unref(store.codeError) || unref(store.headerError)
      case 'cases':
        return unref(store.casesError) || unref(store.headerError)
      case 'audit':
        return unref(store.headerError)
      default:
        return unref(store.headerError)
    }
  })

  return {
    currentTab,
    headerData,
    descriptionData,
    codeData,
    casesData,
    isLoading,
    error,
    loadTabData,
    isReady,
  }
}
