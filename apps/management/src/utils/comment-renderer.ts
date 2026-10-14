import { h } from 'vue'
import type { VNode } from 'vue'

/**
 * Parses a plain text/markdown string and returns an array of Vue VNodes/strings,
 * rendering user mentions (@username) and simple inline markdown (bold, inline code)
 * with shared semantic CSS classes.
 *
 * @param text - The comment content to render inline.
 * @returns Array of strings and VNodes.
 */
export function renderInlineContent(text: string): (string | VNode)[] {
  if (!text) return []

  // Tokenizer pattern:
  // - Match **@username**: `\*\*@(?:[a-zA-Z0-9_-]+)\*\*`
  // - Match @username: `@(?:[a-zA-Z0-9_-]+)`
  // - Match **bold**: `\*\*(?:[^*]+)\*\*`
  // - Match `code`: `(?:[^`]+)`
  const regex = /(\*\*@(?:[a-zA-Z0-9_-]+)\*\*|@(?:[a-zA-Z0-9_-]+)|\*\*(?:[^*]+)\*\*|`(?:[^`]+)`)/g

  const parts = text.split(regex)
  return parts
    .map((part) => {
      if (!part) return ''

      // Check if it's bold mention: **@username**
      if (part.startsWith('**@') && part.endsWith('**')) {
        const username = part.slice(3, -2)
        return h(
          'span',
          {
            class:
              'font-mono text-xs px-1.5 py-0.5 border bg-[color-mix(in_oklch,var(--primary)_12%,transparent)] text-[var(--primary)] border-[color-mix(in_oklch,var(--primary)_25%,transparent)] rounded-lg inline-flex items-center gap-0.5 align-baseline font-bold mr-1 select-all',
          },
          `@${username}`,
        )
      }

      // Check if it's plain mention: @username
      if (part.startsWith('@') && /^[a-zA-Z0-9_-]+$/.test(part.slice(1))) {
        const username = part.slice(1)
        return h(
          'span',
          {
            class:
              'font-mono text-xs px-1.5 py-0.5 border bg-[color-mix(in_oklch,var(--primary)_12%,transparent)] text-[var(--primary)] border-[color-mix(in_oklch,var(--primary)_25%,transparent)] rounded-lg inline-flex items-center gap-0.5 align-baseline mr-1 select-all',
          },
          `@${username}`,
        )
      }

      // Check if it's general bold text: **bold**
      if (part.startsWith('**') && part.endsWith('**')) {
        const boldText = part.slice(2, -2)
        return h('strong', { class: 'font-semibold text-[var(--foreground)] font-mono' }, boldText)
      }

      // Check if it's inline code: `code`
      if (part.startsWith('`') && part.endsWith('`')) {
        const codeText = part.slice(1, -1)
        return h(
          'code',
          {
            class:
              'font-mono text-xs px-1 py-0.5 bg-[color-mix(in_oklch,_var(--status-info-mark)_7%,_var(--muted))] dark:bg-[color-mix(in_oklch,_var(--status-info-mark)_10%,_var(--muted))] border border-[color-mix(in_oklch,_var(--status-info-mark)_24%,_var(--border))] dark:border-[var(--border)] text-foreground rounded-lg mx-0.5 font-bold',
          },
          codeText,
        )
      }

      return part
    })
    .filter(Boolean)
}
