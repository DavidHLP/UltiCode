import DOMPurify from 'dompurify'

/**
 * DOMPurify configuration for sanitizing HTML with i18n formatting.
 *
 * Allows only safe inline formatting tags used in translated strings.
 */
const i18nPurifyConfig = {
  ALLOWED_TAGS: ['strong', 'em', 'b', 'i', 'br', 'span', 'a'],
  ALLOWED_ATTR: ['href', 'target', 'rel'],
  ALLOW_DATA_ATTR: false,
}

/**
 * DOMPurify configuration for sanitizing highlight.js output.
 *
 * highlight.js produces span elements with class attributes for syntax tokens.
 * We allow those specific attributes while blocking everything else.
 */
const codePurifyConfig = {
  ALLOWED_TAGS: ['span', 'br'],
  ALLOWED_ATTR: ['class'],
  ALLOW_DATA_ATTR: false,
  FORBID_TAGS: ['script', 'style', 'object', 'embed', 'iframe'],
  FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onfocus', 'onblur'],
}

/**
 * Sanitize HTML string containing i18n-formatted content (e.g., <strong>, <em>).
 *
 * @param html - Raw HTML string from i18n translations
 * @returns Sanitized HTML string safe for DOM insertion
 */
export function sanitizeI18nHtml(html: string): string {
  return DOMPurify.sanitize(html || '', i18nPurifyConfig)
}

/**
 * Sanitize highlight.js syntax-highlighted code output.
 *
 * highlight.js wraps tokens in <span class="hljs-..."> elements.
 * This sanitizer preserves those while blocking any injected content.
 *
 * @param highlightedHtml - HTML output from hljs.highlight()
 * @returns Sanitized HTML string safe for DOM insertion
 */
export function sanitizeCodeHtml(highlightedHtml: string): string {
  return DOMPurify.sanitize(highlightedHtml || '', codePurifyConfig)
}
