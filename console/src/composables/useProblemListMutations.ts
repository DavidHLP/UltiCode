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
 * {@link MutationDescriptor} describing the call, the success text, the
 * error text, and a reload callback. The toast + reload + try/catch shape
 * lives in one place.
 *
 * <p>Callers pass <strong>pre-resolved</strong> strings (i18n keys already
 * passed through {@code t()}, or literal English for screens that have
 * not been internationalised). The helper only knows about toast options,
 * reload ordering, and error logging.
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
  /** Pre-resolved success message; toast suppressed when blank. */
  successMessage?: string;
  /** Optional description paired with the success toast. */
  successDescription?: string;
  /** Pre-resolved error message; falls back to a generic Problem List key. */
  errorMessage?: string;
  /** Optional description paired with the error toast. */
  errorDescription?: string;
  /** Reload callback fired after every successful mutation. */
  reload?: () => Promise<void> | void;
  /** Optional custom failure label (e.g. "create list"). */
  failureLabel?: string;
}

/** Generic Problem List fallback for the error toast. */
export const DEFAULT_PROBLEM_LIST_ERROR_MESSAGE =
  "personal.messages.mutationFailed";

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
      if (descriptor.successMessage) {
        if (descriptor.successDescription) {
          toast.success(descriptor.successMessage, {
            description: descriptor.successDescription,
          });
        } else {
          toast.success(descriptor.successMessage);
        }
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
      const errorMessage =
        descriptor.errorMessage ?? t(DEFAULT_PROBLEM_LIST_ERROR_MESSAGE);
      if (descriptor.errorDescription) {
        toast.error(errorMessage, {
          description: descriptor.errorDescription,
        });
      } else {
        toast.error(errorMessage);
      }
      return null;
    }
  }

  return { run };
}