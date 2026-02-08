/**
 * Problem example type definitions
 *
 * Provides type-safe definitions for problem examples with input/output structures.
 */

/**
 * Individual input field for a problem example
 */
export interface ProblemExampleInput {
  name: string;
  value: string;
}

/**
 * Complete problem example with inputs and expected output (backend format)
 */
export interface ProblemExample {
  id: string;
  explanation: string;
  inputs?: ProblemExampleInput[];
  inputText?: string;
  outputText?: string;
}

/**
 * Simplified example format used in DescriptionView
 */
export interface DescriptionExample {
  input: string;
  output: string;
  explanation?: string;
}

/**
 * Problem examples from API (backend format)
 */
export type ProblemExamples = ProblemExample[];

/**
 * Type guard for ProblemExampleInput
 */
export function isProblemExampleInput(
  value: unknown,
): value is ProblemExampleInput {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const input = value as Record<string, unknown>;

  return typeof input.name === "string" && typeof input.value === "string";
}

/**
 * Type guard for ProblemExample
 */
export function isProblemExample(value: unknown): value is ProblemExample {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const example = value as Record<string, unknown>;

  return (
    typeof example.id === "string" && typeof example.explanation === "string"
  );
}

/**
 * Type guard for ProblemExample array
 */
export function isProblemExamples(value: unknown): value is ProblemExample[] {
  return Array.isArray(value) && value.every(isProblemExample);
}

/**
 * Type guard for DescriptionExample
 */
export function isDescriptionExample(
  value: unknown,
): value is DescriptionExample {
  if (typeof value !== "object" || value === null) {
    return false;
  }

  const example = value as Record<string, unknown>;

  return (
    typeof example.input === "string" && typeof example.output === "string"
  );
}
