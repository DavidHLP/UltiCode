import { ref, onMounted, toValue, type Ref, type MaybeRefOrGetter } from 'vue'

/**
 * Deep detail-workspace module — owns the route/load/animation lifecycle
 * shared by Management entity-detail views. Replaces the byte-identical
 * {@code isInitialLoad} / {@code isLoaded}-setTimeout / {@code onMounted}
 * -loadData blocks that ForumPostDetailView, CommentDetailView (and future
 * detail views) each inlined.
 *
 * The composable owns: first-load skeleton gating ({@link isInitialLoad}),
 * the staggered-reveal animation flag ({@link isLoaded}), the initial mount
 * fetch, and a {@link refresh} helper for action handlers to call after a
 * domain mutation. Each view stays an adapter: it supplies its entity id +
 * fetch (+ optional secondary refresh such as audit history), and keeps its
 * domain tabs, actions, permissions and dialog state.
 *
 * Arch review 2026-07-10, candidate #5 ("Deepen the Management entity-detail
 * workspace").
 */
export interface UseDetailWorkspaceOptions {
  /** The entity id resolved from the route (ref / computed / getter). */
  entityId: MaybeRefOrGetter<string>
  /** Fetch the entity detail into the store (store owns loading/error). */
  fetch: (id: string) => Promise<void>
  /**
   * Optional secondary refresh fired only on a successful fetch (e.g.
   * reloading audit history). Fire-and-forget, matching the previous
   * inline {@code loadAuditHistory()} non-awaited call.
   */
  onRefreshed?: () => void | Promise<void>
}

export interface UseDetailWorkspaceReturn {
  /** True until the first fetch completes — gates the first-load skeleton. */
  isInitialLoad: Ref<boolean>
  /** Staggered-reveal animation flag, flipped 100ms after mount. */
  isLoaded: Ref<boolean>
  /** Re-fetch the entity (and run the secondary refresh on success). */
  refresh: () => Promise<void>
}

export function useDetailWorkspace(options: UseDetailWorkspaceOptions): UseDetailWorkspaceReturn {
  const { entityId, fetch, onRefreshed } = options

  const isInitialLoad = ref(true)
  const isLoaded = ref(false)

  async function refresh(): Promise<void> {
    const id = toValue(entityId)
    if (!id) return
    let ok = false
    try {
      await fetch(id)
      ok = true
    } catch {
      // Error state is owned by the store; detail views read store.error.
    } finally {
      if (isInitialLoad.value) {
        isInitialLoad.value = false
      }
    }
    if (ok && onRefreshed) {
      // Fire-and-forget: secondary data must not block the detail resolve.
      void onRefreshed()
    }
  }

  onMounted(() => {
    // Staggered-reveal animation flag (shared detail-view UX).
    setTimeout(() => {
      isLoaded.value = true
    }, 100)
    refresh()
  })

  return { isInitialLoad, isLoaded, refresh }
}
