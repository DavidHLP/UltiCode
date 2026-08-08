import { ref, computed, onMounted, onBeforeUnmount, nextTick, type ComputedRef } from 'vue'
import { useRoute } from 'vue-router'
import { useProblemsStore } from '@/stores/admin/problems'
import type { HeaderData } from '@/api/admin/problems'

export interface UseProblemEditReturn<T> {
  problemId: ComputedRef<string>
  data: ComputedRef<T | null>
  loading: ComputedRef<boolean>
  title: ComputedRef<string>
  isReady: ComputedRef<boolean>
  loadData: () => Promise<void>
}

/**
 * Composable for managing problem edit page data lifecycle.
 *
 * Provides a standardized pattern for edit views that:
 * - Loads problem data on mount
 * - Optionally fetches header data for the page title
 * - Uses nextTick for reliable animation ready state
 * - Cancels pending requests on unmount
 * - Exposes a reload function for refreshing after saves
 *
 * @param fetchFn - Function to fetch the main edit data
 * @param options - Configuration options
 */
export function useProblemEdit<T>(
  fetchFn: (id: string) => Promise<T | null>,
  options: {
    /** Whether to also fetch header data for the page title */
    fetchTitle?: boolean
  } = {},
): UseProblemEditReturn<T> {
  const route = useRoute()
  const store = useProblemsStore()
  const { fetchTitle = false } = options

  const problemId = computed(() => route.params.id as string)

  // Local state
  const data = ref<T | null>(null)
  const loading = ref(true)
  const title = ref('')
  const isReady = ref(false)

  /**
   * Load data for the edit page.
   * Fetches both main data and optionally header data in parallel.
   */
  async function loadData(): Promise<void> {
    if (!problemId.value) {
      loading.value = false
      isReady.value = true
      return
    }

    loading.value = true
    isReady.value = false

    try {
      const promises: [Promise<T | null>, Promise<HeaderData | null>?] = [
        fetchFn(problemId.value),
        fetchTitle ? store.fetchHeader(problemId.value) : undefined,
      ]

      const [result, header] = await Promise.all(promises)

      if (result) {
        data.value = result
      }
      if (header) {
        title.value = header.title
      }
    } catch (err) {
      console.error('[useProblemEdit] Failed to load data:', err)
    } finally {
      loading.value = false
      // Use nextTick instead of setTimeout for reliable DOM-ready state
      await nextTick()
      isReady.value = true
    }
  }

  // Load data on component mount
  onMounted(() => {
    loadData()
  })

  // Cleanup: abort all pending requests when component unmounts
  onBeforeUnmount(() => {
    store.abortAllRequests()
  })

  return {
    problemId,
    data: computed(() => data.value),
    loading: computed(() => loading.value),
    title: computed(() => title.value),
    isReady: computed(() => isReady.value),
    loadData,
  }
}
