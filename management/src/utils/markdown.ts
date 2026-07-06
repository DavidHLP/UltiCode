/**
 * Re-export seam — the actual MarkdownIt plumbing, plugins, renderers and
 * sanitization live in `shared/markdown-utils/` so both frontends share a
 * single deep module and the security invariant (sanitization cannot be
 * forgotten) is enforced by the package's `renderMarkdown()`.
 *
 * Previously this file skipped `sanitizeHtml()` entirely, opening an XSS gap
 * for any `v-html` consumer in the admin UI. Both apps now go through the
 * shared module's sanitized render path.
 *
 * See `/tmp/architecture-review-1783341079.html` Card 1.
 */
export { renderMarkdown, sanitizeHtml } from '@/shared/markdown-utils/src'