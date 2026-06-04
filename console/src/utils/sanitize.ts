import DOMPurify from "dompurify";

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
    "p",
    "br",
    "strong",
    "em",
    "u",
    "s",
    "code",
    "pre",
    // Headers
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    // Lists
    "ul",
    "ol",
    "li",
    // Block elements
    "blockquote",
    "hr",
    "div",
    "span",
    // Links and images
    "a",
    "img",
    // Tables
    "table",
    "thead",
    "tbody",
    "tfoot",
    "tr",
    "th",
    "td",
    // Code blocks with syntax highlighting
    "figure",
    "figcaption",
    // Katex math elements (used by markdown-it-katex)
    "math",
    "semantics",
    "mrow",
    "mi",
    "mo",
    "mn",
    "msup",
    "annotation",
    "mspace",
    "mfrac",
    "msqrt",
    "mtext",
  ],
  // Safe attributes needed for markdown features
  ALLOWED_ATTR: [
    "href",
    "src",
    "alt",
    "title",
    "class",
    "id",
    // Data attributes for code tabs functionality
    "data-index",
    "data-language",
    "data-code",
    // Accessibility attributes
    "role",
    "aria-label",
    // Inline styles (Katex uses these for math rendering)
    "style",
  ],
  // Block generic data-* attributes unless explicitly allowed
  ALLOW_DATA_ATTR: false,
  // Dangerous tags that should never be allowed
  FORBID_TAGS: [
    "script",
    "object",
    "embed",
    "iframe",
    "form",
    "input",
    "button",
  ],
  // Dangerous event handler attributes
  FORBID_ATTR: [
    "onerror",
    "onload",
    "onclick",
    "onmouseover",
    "onfocus",
    "onblur",
  ],
  // Custom sanitizer to block dangerous protocols
  FORBID_CONTENT: (node: HTMLElement | null) => {
    if (!node) return false;
    const href = node.getAttribute?.("href");
    const src = node.getAttribute?.("src");

    const dangerousProtocols = ["javascript:", "data:", "vbscript:", "file:"];

    if (
      href &&
      dangerousProtocols.some((p) => href.toLowerCase().startsWith(p))
    ) {
      return true;
    }

    if (
      src &&
      dangerousProtocols.some((p) => src.toLowerCase().startsWith(p))
    ) {
      return true;
    }

    return false;
  },
};

/**
 * Sanitize HTML string to prevent XSS attacks.
 *
 * Use this for any HTML that will be inserted into the DOM via v-html.
 *
 * @param html - Raw HTML string to sanitize
 * @returns Sanitized HTML string safe for DOM insertion
 */
export function sanitizeHtml(html: string): string {
  return DOMPurify.sanitize(html || "", purifyConfig);
}
