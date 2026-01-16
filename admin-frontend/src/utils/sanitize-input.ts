/**
 * Sanitize plain text input for language names and other user-provided strings.
 * This is NOT for HTML content (use sanitize-markdown.ts for that).
 *
 * @param input - The raw user input string
 * @param maxLength - Maximum allowed length (default 50)
 * @returns Sanitized string safe for storage and display
 */
export function sanitizeTextInput(input: string, maxLength: number = 50): string {
  // 1. Trim whitespace
  let sanitized = input.trim()

  // 2. Remove null bytes and control characters (except tab, newline)
  sanitized = sanitized.replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '')

  // 3. Limit length to prevent DoS
  if (sanitized.length > maxLength) {
    sanitized = sanitized.substring(0, maxLength)
  }

  // 4. Validate: only allow alphanumeric, spaces, hyphens, underscores, plus, dot, hash
  const allowedPattern = /^[a-zA-Z0-9\s\-_+#.]+$/
  if (!allowedPattern.test(sanitized)) {
    // Remove any characters that don't match the pattern
    sanitized = sanitized.replace(/[^a-zA-Z0-9\s\-_+#.]/g, '')
  }

  return sanitized
}
