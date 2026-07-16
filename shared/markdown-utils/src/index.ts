/**
 * Markdown rendering deep module — single source of truth for the UltiCode
 * frontends (console + management).
 *
 * <p>Owns:
 * <ul>
 *   <li>MarkdownIt instance configuration (html disabled, linkify, breaks, katex)</li>
 *   <li>Highlight.js integration via the `highlight` option</li>
 *   <li>Custom code-fence grouping (`{group="..."}` syntax for tabbed code blocks)</li>
 *   <li>Custom standalone-fence renderer (toolbar + copy button)</li>
 *   <li>Custom heading renderer (slugified IDs for scroll-spy / TOC)</li>
 *   <li>DOMPurify sanitization of the rendered HTML (always-on — sanitization
 *       cannot be forgotten because it is part of the render pipeline)</li>
 * </ul>
 *
 * <p>This module exists because the same ~210 LoC of Markdown plumbing was
 * previously duplicated between `console/src/utils/markdown.ts` (236 LoC) and
 * `management/src/utils/markdown.ts` (160 LoC), and management's copy was
 * missing the `sanitizeHtml()` step — a latent XSS surface against any
 * `v-html` consumer in the admin UI (see
 * `/tmp/architecture-review-1783341079.html`, Card 1).
 *
 * <p>The single public API is {@link renderMarkdown}. Both apps import it
 * through `@ulticode/markdown-utils` and never reach for the underlying
 * MarkdownIt instance or DOMPurify directly.
 */
import MarkdownIt from 'markdown-it'
import { katex } from '@mdit/plugin-katex'
import hljs from 'highlight.js'
import DOMPurify, { type Config as DOMPurifyConfig } from 'dompurify'

type Token = NonNullable<ReturnType<MarkdownIt['parse']>>[number]

// ---------------------------------------------------------------------------
// Sanitization — always-on, cannot be bypassed.
// ---------------------------------------------------------------------------

/**
 * DOMPurify configuration for sanitizing markdown-rendered HTML.
 *
 * Preserves all tags/attributes needed by the custom renderers below (data-*
 * for copy buttons, class for hljs, href for links, id for heading anchors).
 * Blocks javascript:, data:, vbscript: schemes and every event-handler attr.
 */
const PURIFY_CONFIG: DOMPurifyConfig = {
  ALLOWED_TAGS: [
    'p',
    'br',
    'strong',
    'em',
    'u',
    's',
    'code',
    'pre',
    'h1',
    'h2',
    'h3',
    'h4',
    'h5',
    'h6',
    'ul',
    'ol',
    'li',
    'blockquote',
    'hr',
    'div',
    'span',
    'a',
    'img',
    'table',
    'thead',
    'tbody',
    'tfoot',
    'tr',
    'th',
    'td',
    'button',
    'svg',
    'path',
    'rect',
    'polygon',
    'line',
    'g',
  ],
  ALLOWED_ATTR: [
    'href',
    'target',
    'rel',
    'src',
    'alt',
    'title',
    'class',
    'id',
    'data-code',
    'data-index',
    'aria-label',
    'viewBox',
    'width',
    'height',
    'fill',
    'stroke',
    'stroke-width',
    'stroke-linecap',
    'stroke-linejoin',
    'xmlns',
    'd',
    'x',
    'y',
    'rx',
    'ry',
    'points',
    'x1',
    'y1',
    'x2',
    'y2',
  ],
  ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i,
  FORBID_TAGS: ['script', 'style', 'iframe', 'object', 'embed', 'form', 'input'],
  FORBID_ATTR: ['onerror', 'onload', 'onclick', 'onmouseover', 'onfocus', 'onblur', 'onmouseout'],
}

/** Sanitize HTML — public re-export for downstream callers that need it. */
export function sanitizeHtml(html: string): string {
  return String(DOMPurify.sanitize(html || '', PURIFY_CONFIG))
}

// ---------------------------------------------------------------------------
// MarkdownIt instance + plugins.
// ---------------------------------------------------------------------------

const md: MarkdownIt = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  highlight(str, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(str, { language: lang }).value
      } catch {
        // fall through to default escaping
      }
    }
    return '' // markdown-it uses its own escaping
  },
})

md.use(katex)

// ---------------------------------------------------------------------------
// Custom plugin: group consecutive fences with shared `{group="id"}` so they
// render as a single tabbed code-block widget instead of N independent blocks.
// ---------------------------------------------------------------------------

const groupFencesPlugin = (instance: MarkdownIt): void => {
  instance.core.ruler.push('group_fences', (state) => {
    const tokens = state.tokens
    const next: Token[] = []
    let i = 0

    const getGroupId = (info: number | string | null | undefined): string | null => {
      if (typeof info !== 'string') return null
      const match = info.match(/\{group="([^"]+)"\}/)
      return match && match[1] ? match[1] : null
    }

    while (i < tokens.length) {
      const token = tokens[i]
      if (!token) {
        i++
        continue
      }

      if (token.type === 'fence') {
        const groupId = getGroupId(token.info)
        if (groupId) {
          const group: Token[] = [token]
          let j = i + 1
          while (j < tokens.length) {
            const nextToken = tokens[j]
            if (nextToken && nextToken.type === 'fence') {
              if (getGroupId(nextToken.info) === groupId) {
                group.push(nextToken)
                j++
              } else {
                break
              }
            } else {
              break
            }
          }

          const groupToken = new state.Token('code_group', 'div', 0)
          groupToken.block = true
          ;(groupToken.meta as Record<string, unknown>) = { group }
          next.push(groupToken)

          i = j
        } else {
          next.push(token)
          i++
        }
      } else {
        next.push(token)
        i++
      }
    }
    state.tokens = next
  })

  instance.renderer.rules.code_group = (tokens, idx) => {
    const token = tokens[idx]
    if (!token) return ''
    const group = (token.meta as { group?: Token[] } | undefined)?.group
    if (!group || !group.length) return ''

    const getLangName = (info: string): string =>
      info
        .replace(/\{group="[^"]+"\}/, '')
        .trim()
        .split(/\s+/)[0] || 'Text'

    let tabsHtml = '<div class="lc-tabs-header">'
    group.forEach((fence, index) => {
      const langName = getLangName(fence.info || '')
      const activeClass = index === 0 ? 'active' : ''
      tabsHtml += `\n        <button class="lc-tab-btn ${activeClass}" data-index="${index}">\n          ${langName}\n        </button>\n      `
    })
    tabsHtml += '</div>'

    let bodyHtml = '<div class="lc-tabs-body">'
    group.forEach((fence, index) => {
      const langName = getLangName(fence.info || '')
      const options = md.options
      let highlighted = ''
      if (options.highlight) {
        highlighted = options.highlight(fence.content, langName, '') || md.utils.escapeHtml(fence.content)
      } else {
        highlighted = md.utils.escapeHtml(fence.content)
      }
      const activeClass = index === 0 ? 'active' : ''
      const encoded = encodeURIComponent(fence.content)
      bodyHtml += `\n        <div class="lc-code-panel ${activeClass}" data-index="${index}">\n          <div class="lc-code-block group/code">\n             <div class="lc-copy-wrapper">\n                <button class="lc-copy-btn" data-code="${encoded}" aria-label="Copy code">\n                  <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lc-copy-icon"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>\n                </button>\n             </div>\n             <pre><code class="hljs language-${langName}">${highlighted}</code></pre>\n          </div>\n        </div>\n      `
    })
    bodyHtml += '</div>'

    return `<div class="lc-code-tabs">${tabsHtml}${bodyHtml}</div>`
  }
}

md.use(groupFencesPlugin)

// ---------------------------------------------------------------------------
// Heading extraction — canonical slug + duplicate-aware ID derivation.
// ---------------------------------------------------------------------------

const HEADING_ID_PATTERN = /[^a-z0-9\u4e00-\u9fa5]+/g

/**
 * Slugify heading text into a stable id used both by `renderMarkdown`
 * (for `id` attributes on rendered headings) and by `extractHeadings`
 * (for TOC entries). The same function backs both so TOC IDs always
 * match rendered IDs.
 */
export function slugifyHeading(text: string): string {
  return String(text ?? '')
    .trim()
    .toLowerCase()
    .replace(HEADING_ID_PATTERN, '-')
    .replace(/^-+|-+$/g, '')
}

/**
 * Make a base slug unique against a set of already-used ids by appending
 * the conventional `-2`, `-3`, ... suffix. Used by both the renderer and
 * `extractHeadings` so duplicate headings stay in sync.
 */
export function dedupeHeadingId(
  baseId: string,
  used: { has: (id: string) => boolean; add: (id: string) => void },
): string {
  if (!baseId) return baseId
  if (!used.has(baseId)) {
    used.add(baseId)
    return baseId
  }
  let suffix = 2
  let candidate = `${baseId}-${suffix}`
  while (used.has(candidate)) {
    suffix += 1
    candidate = `${baseId}-${suffix}`
  }
  used.add(candidate)
  return candidate
}

export interface MarkdownHeading {
  id: string
  text: string
  level: number
}

export interface ExtractHeadingsOptions {
  /** Heading levels to extract (1-6). Defaults to `[2, 3]`. */
  levels?: number[]
}

const FENCE_LINE_PATTERN = /^\s*(```|~~~)/

/**
 * Extract `{id, text, level}` entries from a raw markdown string.
 *
 * - Skips headings inside fenced code blocks (``` and ~~~).
 * - Deduplicates IDs across the document using the same
 *   `slugifyHeading` + `dedupeHeadingId` rules as `renderMarkdown`,
 *   so the IDs in the returned array always match the IDs rendered
 *   into the HTML, even when the same heading text appears multiple
 *   times.
 */
export function extractHeadings(
  markdown: string,
  options: ExtractHeadingsOptions = {},
): MarkdownHeading[] {
  const source = String(markdown ?? '')
  if (!source) return []

  const levels = (options.levels && options.levels.length > 0
    ? options.levels
    : [2, 3]
  ).slice().sort((a, b) => a - b)
  if (levels.some((level) => !Number.isInteger(level) || level < 1 || level > 6)) {
    throw new RangeError('Heading levels must be integers from 1 to 6')
  }
  const levelSet = new Set(levels)

  const lines = source.split('\n')
  const headings: MarkdownHeading[] = []
  const used = new Set<string>()
  let inFence = false
  let fenceMarker: string | null = null

  for (const line of lines) {
    const fenceMatch = line.match(FENCE_LINE_PATTERN)
    if (fenceMatch) {
      const marker = fenceMatch[1] ?? '```'
      if (!inFence) {
        inFence = true
        fenceMarker = marker
      } else if (fenceMarker && line.trimStart().startsWith(fenceMarker)) {
        inFence = false
        fenceMarker = null
      }
      continue
    }
    if (inFence) continue

    const match = line.match(/^(#{1,6})\s+(.+?)\s*#*\s*$/)
    if (!match) continue
    const level = match[1]?.length ?? 0

    const rawText = (match[2] ?? '').trim()
    if (!rawText) continue

    const baseId = slugifyHeading(rawText)
    const id = dedupeHeadingId(baseId, used)
    if (levelSet.has(level)) {
      headings.push({ id, text: rawText, level })
    }
  }

  return headings
}

// ---------------------------------------------------------------------------
// Heading renderer — slugify text into a stable id for scroll-spy / TOC.
// Renderer dedupes IDs per render call using an env-scoped Set so the
// emitted `id` attributes always match what `extractHeadings` returns.
// ---------------------------------------------------------------------------

interface RenderEnv {
  __headingIds?: Set<string>
}

const defaultHeadingOpen =
  md.renderer.rules.heading_open ||
  ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

md.renderer.rules.heading_open = (tokens, idx, options, env, self) => {
  const token = tokens[idx]
  const inlineToken = tokens[idx + 1]
  let text = ''
  if (inlineToken && inlineToken.type === 'inline') {
    text = inlineToken.content
  }
  const baseId = slugifyHeading(text)
  let id = baseId
  if (baseId) {
    const renderEnv = env as RenderEnv | undefined
    let seen = renderEnv?.__headingIds
    if (!seen && renderEnv) {
      seen = new Set<string>()
      renderEnv.__headingIds = seen
    }
    if (seen) {
      id = dedupeHeadingId(baseId, seen)
    }
    const idAttrIdx = token.attrIndex('id')
    if (idAttrIdx >= 0 && token.attrs) {
      token.attrs[idAttrIdx][1] = id
    } else {
      token.attrPush(['id', id])
    }
  }
  return defaultHeadingOpen(tokens, idx, options, env, self)
}

// ---------------------------------------------------------------------------
// Standalone fence renderer — toolbar (lang label + copy button) for blocks
// that aren't grouped.
// ---------------------------------------------------------------------------

md.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]
  const langName = token.info ? token.info.trim().split(/\s+/)[0] : 'text'
  const content = token.content
  const encoded = encodeURIComponent(content)
  const options = md.options

  let highlighted = ''
  if (options.highlight) {
    highlighted = options.highlight(content, langName, '') || md.utils.escapeHtml(content)
  } else {
    highlighted = md.utils.escapeHtml(content)
  }

  const langDisplay = langName
    ? langName.charAt(0).toUpperCase() + langName.slice(1).toLowerCase()
    : 'Text'

  return `
    <div class="lc-code-block-standalone border border-border bg-[var(--surface-sunken)] rounded-none my-4 overflow-hidden">
      <div class="lc-code-block-header flex items-center justify-between px-4 py-2 border-b border-border bg-[var(--surface-sunken)]">
        <span class="text-xs font-mono font-medium text-muted-foreground select-none">${langDisplay}</span>
        <button class="lc-copy-btn p-1 rounded-none hover:bg-accent text-muted-foreground hover:text-foreground transition-colors" data-code="${encoded}" aria-label="Copy code">
          <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="h-3.5 w-3.5"><rect width="14" height="14" x="8" y="8" rx="2" ry="2"/><path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2"/></svg>
        </button>
      </div>
      <div class="lc-code-body p-0 m-0">
        <pre class="m-0! p-4! overflow-x-auto bg-transparent!"><code class="hljs language-${langName} text-xs font-mono">${highlighted}</code></pre>
      </div>
    </div>
  `
}

// ---------------------------------------------------------------------------
// Public API.
// ---------------------------------------------------------------------------

/**
 * Render a markdown string to a sanitized HTML string ready for `v-html`.
 *
 * <p>Sanitization is applied unconditionally — callers cannot accidentally
 * feed unsanitized markdown to the DOM. This is the single seam the
 * security invariant "`v-html` must consume sanitized markdown" lives at.
 */
export function renderMarkdown(text: string): string {
  const env: RenderEnv = { __headingIds: new Set<string>() }
  return sanitizeHtml(md.render(text || '', env))
}