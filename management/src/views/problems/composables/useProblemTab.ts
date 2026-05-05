import { ref, computed, watch, onScopeDispose, type ComputedRef } from 'vue'
import { useRoute } from 'vue-router'
import { useProblemsStore, type ProblemEditTab } from '@/stores/admin/problems'

export interface UseProblemTabReturn<T> {
  problemId: ComputedRef<string>
  data: ComputedRef<T | null>
  loading: ComputedRef<boolean>
  error: ComputedRef<string | null>
  isReady: ComputedRef<boolean>
  loadData: () => Promise<void>
}

export function useProblemTab<T>(
  tabKey: ProblemEditTab,
  fetchFn: (id: string) => Promise<T | null>,
): UseProblemTabReturn<T> {
  const route = useRoute()
  const store = useProblemsStore()

  const problemId = computed(() => route.params.id as string)
  const isActive = ref(true)

  const tabState = computed(() => store.getRawTabState<T>(tabKey))

  const data = computed(() => tabState.value.data)
  const loading = computed(() => tabState.value.loading)
  const error = computed(() => tabState.value.error)
  const isReady = ref(false)

  async function loadData(): Promise<void> {
    if (!problemId.value || !isActive.value) return

    isReady.value = false

    try {
      await fetchFn(problemId.value)
    } catch (err) {
      if (isActive.value) {
        console.error(`[useProblemTab:${tabKey}] Failed to load data:`, err)
      }
    } finally {
      if (isActive.value) {
        isReady.value = true
      }
    }
  }

  watch(
    () => route.params.id,
    (newId, oldId) => {
      if (newId && newId !== oldId) {
        loadData()
      }
    },
    { immediate: true },
  )

  onScopeDispose(() => {
    isActive.value = false
  })

  return {
    problemId,
    data,
    loading,
    error,
    isReady: computed(() => isReady.value),
    loadData,
  }
}
