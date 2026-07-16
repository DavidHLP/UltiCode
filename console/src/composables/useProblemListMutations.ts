/**
 * Shared Problem List mutation helper (architecture-review candidate #2).
 *
 * <p>Before the deepening, three console composables
 * ({@code useProblemLists}, {@code useSidebarLists},
 * {@code useProblemListOperations}) each inlined the same mutation policy:
 * HTTP call → success toast → {@code loadData()} reload → error toast.
 * Drift between the three copies meant "create failed" surfaced as
 * different toasts depending on which screen the user clicked from.
 *
 * <p>This module concentrates the policy so each caller passes one
 * {@link MutationDescriptor} describing the call, the success key, the
 * error key, and a reload callback. The toast + reload + try/catch shape
 * lives in one place.
 *
 * <p>The composables retain their screen-specific data shape (sorted
 * lists, search query, drag-and-drop ordering); only the mutation
 * boilerplate moves.
 */
import { toast } from "vue-sonner";
import { useI18n } from "vue-i18n";

export interface MutationDescriptor<T = unknown> {
  /** Async HTTP/mutator call. */
  call: () => Promise<T>;
  /**
   * Optional return-value handler invoked only on success. Used to push a
   * new id into a caller-managed list, jump to a route, etc.
   */
  onSuccess?: (value: T) => void;
  /** i18n key for the success toast (skipped when blank). */
  successKey?: string;
  /** i18n key for the error toast (defaults to a generic failure). */
  errorKey?: string;
  /** Reload callback fired after every successful mutation. */
  reload?: () => Promise<void> | void;
  /** Optional custom failure label (e.g. "create list"). */
  failureLabel?: string;
}

/**
 * Run a Problem List mutation through the shared toast + reload policy.
 *
 * <p>Returns the success value (if any) or {@code null} on failure so
 * callers can chain without a try/catch.
 */
export function useProblemListMutations() {
  const { t } = useI18n();

  async function run<T>(descriptor: MutationDescriptor<T>): Promise<T | null> {
    try {
      const value = await descriptor.call();
      if (descriptor.successKey) {
        toast.success(t(descriptor.successKey));
      }
      descriptor.onSuccess?.(value);
      if (descriptor.reload) {
        await descriptor.reload();
      }
      return value;
    } catch (e) {
      console.error(
        `Problem List mutation failed (${descriptor.failureLabel ?? "unknown"})`,
        e,
      );
      toast.error(t(descriptor.errorKey ?? "personal.messages.saveFailed"));
      return null;
    }
  }

  return { run };
}