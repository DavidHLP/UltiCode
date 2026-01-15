import * as DOMPurifyModule from 'dompurify'
import { renderMarkdown } from './markdown'

// Handle both ESM and CommonJS exports
 
const DOMPurify = ('default' in DOMPurifyModule ? DOMPurifyModule.default : DOMPurifyModule) as {
  sanitize: (html: string, config?: object) => string
}

/**
 * DOMPurify configuration for sanitizing markdown-rendered HTML.
 *
 * Security considerations:
 * - Allows safe HTML tags needed for proper markdown rendering
 * - Blocks dangerous protocols in links (javascript:, data:, vbscript:)
 * - Blocks dangerous event handler attributes
 * - Preserves data attributes used by custom markdown features (code tabs, Katex)
 */
const purifyConfig = {
  // HTML tags needed for markdown rendering
  ALLOWED_TAGS: [
    // Text formatting
    'p',
    'br',
    'strong',
    'em',
    'u',
    's',
    'code',
    'pre',
    // Headers
    'h1',
    'h2',
    'h3',
    'h4',
    'h5',
    'h6',
    // Lists
    'ul',
    'ol',
    'li',
    // Block elements
    'blockquote',
    'hr',
    'div',
    'span',
    // Links and images
    'a',
    'img',
    // Tables
    'table',
    'thead',
    'tbody',
    'tfoot',
    'tr',
    'th',
    'td',
    // Code blocks with syntax highlighting
    'figure',
    'figcaption',
    // Katex math elements (used by markdown-it-katex)
    'math',
    'semantics',
    'mrow',
    'mi',
    'mo',
    'mn',
    'msup',
    'annotation',
    'mspace',
    'mfrac',
    'msqrt',
    'mtext',
  ],
  // Safe attributes needed for markdown features
  ALLOWED_ATTR: [
    'href',
    'src',
    'alt',
    'title',
    'class',
    'id',
    // Data attributes for code tabs functionality
    'data-index',
    'data-language',
    // Accessibility attributes
    'role',
    'aria-label',
    // Inline styles (Katex uses these for math rendering)
    'style',
  ],
  // Block generic data-* attributes unless explicitly allowed
  ALLOW_DATA_ATTR: false,
  // Dangerous tags that should never be allowed
  FORBID_TAGS: ['script', 'object', 'embed', 'iframe', 'form', 'input', 'button'],
  // Dangerous event handler attributes
  FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onfocus', 'onblur'],
  // Custom sanitizer to block dangerous protocols
  // Note: This runs in browser context where we have DOM API
  FORBID_CONTENT: (node: HTMLElement | null) => {
    if (!node) return false
    // Check for dangerous href/src attributes
    const href = node.getAttribute?.('href')
    const src = node.getAttribute?.('src')

    const dangerousProtocols = ['javascript:', 'data:', 'vbscript:', 'file:']

    if (href && dangerousProtocols.some((p) => href.toLowerCase().startsWith(p))) {
      return true // Remove this node
    }

    if (src && dangerousProtocols.some((p) => src.toLowerCase().startsWith(p))) {
      return true // Remove this node
    }

    return false
  },
}

/**
 * Safely render markdown to HTML with XSS protection.
 *
 * This function:
 * 1. Renders markdown to HTML using markdown-it
 * 2. Sanitizes the HTML using DOMPurify to remove XSS vectors
 * 3. Returns safe HTML that can be used with v-html
 *
 * @param rawMarkdown - Raw markdown string to render
 * @returns Sanitized HTML string safe for DOM insertion
 *
 * @example
 * ```vue
 * <template>
 *   <div v-html="renderSafeMarkdown(userInput)" />
 * </template>
 * ```
 */
export function renderSafeMarkdown(rawMarkdown: string): string {
  const html = renderMarkdown(rawMarkdown || '')
  return DOMPurify.sanitize(html, purifyConfig)
}
