import { computed, type ComputedRef } from 'vue'
import { storeToRefs } from 'pinia'
import { useProblemsStore } from '@/stores/admin/problems'

/**
 * Deep workspace facade for the Problem administration page.
 *
 * <p>Owns the cross-cutting workspace concerns that previously leaked
 * into {@code ProblemsListView} and {@code useProblemActions}:
 *
 * <ul>
 *   <li><b>Reload policy</b> &mdash; a single {@link #reload} entry point
 *       with the store's seq-guard stale-response drop. Every refresh
 *       source (manual button, post-action refresh, route change) must
 *       funnel through this function so reloads are serialized.</li>
 *   <li><b>Selection</b> &mdash; selected row ids are the single source
 *       for the bulk-action dialog; this facade exposes setters the
 *       action composables call when they mutate selection.</li>
 *   <li><b>Workspace stats</b> &mdash; total / selected counts derived
 *       from live store state. Views no longer compute these from raw
 *       arrays.</li>
 * </ul>
 *
 * <p>Single-item action composition (confirm-delete, flag, publish,
 * audit drawer) stays in {@code useProblemActions}; this facade is the
 * query / reload / stats seam, not a re-implementation of every
 * action. Action handlers that need a reload call {@link #reload}
 * directly so the stale-response guard catches every refresh.
 *
 * <p>Architecture review candidate #2 (Problem administration
 * workspace).
 */
export function useProblemWorkspace() {
  const problemsStore = useProblemsStore()
  const { problems, total, loading, error, selectedIds } = storeToRefs(problemsStore)

  /** Single reload entry point — store's seq-guard drops stale responses. */
  async function reload(): Promise<void> {
    await problemsStore.fetchProblems()
  }

  const isLoading: ComputedRef<boolean> = computed(() => loading.value)
  const errorMessage: ComputedRef<string | null> = computed(() => error.value)
  const itemCount: ComputedRef<number> = computed(() => problems.value.length)
  const selectedCount: ComputedRef<number> = computed(() => selectedIds.value.length)
  const hasSelection: ComputedRef<boolean> = computed(() => selectedIds.value.length > 0)

  function clearSelection(): void {
    problemsStore.clearSelectedIds()
  }

  function setSelection(ids: string[]): void {
    problemsStore.setSelectedIds(ids)
  }

  return {
    // live store refs (read-only for the view; mutators below)
    problems,
    total,
    selectedIds,
    isLoading,
    errorMessage,
    itemCount,
    selectedCount,
    hasSelection,
    // actions
    reload,
    clearSelection,
    setSelection,
  }
}
