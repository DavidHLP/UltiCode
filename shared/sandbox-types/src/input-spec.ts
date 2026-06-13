import { isSupportedOJDataType, type OJDataType } from './oj-type.js';

/**
 * One positional argument in a D-form {@code input.json} case.
 *
 * <p>Shape matches what the backend ships to the sandbox harness
 * ({@code docker/sandbox/harness/{java,python}/}) and what the harness
 * reads via {@code input.value} (JSON-encoded literal) and the optional
 * {@code input.type} (OJ data-type hint used to materialize LeetCode
 * structures from raw lists).
 */
export interface DFormInputSpec {
  /** Logical parameter name; matches the Solution method's arg name. */
  name: string;
  /**
   * JSON-encoded literal of the value (e.g. {@code "[1,2,3]"} or
   * {@code "42"} or {@code "\"abc\""}). Backend stores the JSON literal
   * verbatim and the harness parses it with its own JSON parser.
   */
  value: string;
  /**
   * Optional OJ data-type hint. When present, the harness prefers this
   * over the Solution method's Java annotation or Python type hint.
   */
  type?: OJDataType;
}

/**
 * Strict type guard. Use this at any trust boundary (form input,
 * URL-decoded JSON, IPC payload) where untrusted data crosses into
 * the D-form submission flow.
 *
 * <p>Rejects:
 * <ul>
 *   <li>non-objects / {@code null}
 *   <li>missing or non-string {@code name} / {@code value}
 *   <li>blank {@code name} (matches {@code RunInputSpec} record's
 *       constructor assertion in the backend)
 *   <li>{@code type} that is present but is not a supported
 *       {@link OJDataType} (the backend silently drops unknown types
 *       to avoid a runtime mismatch between the wire and the harness)
 * </ul>
 */
export function isDFormInputSpec(x: unknown): x is DFormInputSpec {
  if (typeof x !== 'object' || x === null) return false;
  const o = x as Record<string, unknown>;
  if (typeof o.name !== 'string' || o.name.trim().length === 0) return false;
  if (typeof o.value !== 'string') return false;
  if (o.type === undefined) return true;
  // type is optional; if present, it MUST be a supported OJDataType
  return isSupportedOJDataType(o.type);
}
