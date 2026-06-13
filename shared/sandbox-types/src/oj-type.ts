/**
 * OJ-style data types that the D-form harness knows how to materialize
 * from a JSON literal. Mirrors the union accepted by
 * {@code docker/sandbox/harness/java/src/main/java/Harness.java} (Java
 * dispatch via reflection on the Solution method's parameter type) and
 * {@code docker/sandbox/harness/python/harness.py} (Python dispatch via
 * {@code adapt_arg(value, hint, type_override)}).
 *
 * <p>Add a new entry here ONLY when the harness implementation actually
 * recognizes it — adding unhandled strings just gets the per-case path
 * a Runtime Error.
 */
export type OJDataType =
  | 'int'
  | 'long'
  | 'double'
  | 'boolean'
  | 'String'
  | 'int[]'
  | 'int[][]'
  | 'long[]'
  | 'String[]'
  | 'ListNode'
  | 'ListNode[]'
  | 'TreeNode'
  | 'TreeNode[]';

export const SUPPORTED_OJ_DATA_TYPES: readonly OJDataType[] = [
  'int',
  'long',
  'double',
  'boolean',
  'String',
  'int[]',
  'int[][]',
  'long[]',
  'String[]',
  'ListNode',
  'ListNode[]',
  'TreeNode',
  'TreeNode[]',
] as const;

export function isSupportedOJDataType(t: unknown): t is OJDataType {
  return typeof t === 'string' && (SUPPORTED_OJ_DATA_TYPES as readonly string[]).includes(t);
}
