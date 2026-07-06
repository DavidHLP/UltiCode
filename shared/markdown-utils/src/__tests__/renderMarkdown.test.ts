import { describe, expect, it } from 'vitest'
import { renderMarkdown, sanitizeHtml } from '../index'

describe('renderMarkdown', () => {
  it('renders plain text to a paragraph', () => {
    const out = renderMarkdown('hello world')
    expect(out).toContain('<p>')
    expect(out).toContain('hello world')
  })

  it('produces heading tags with slugified ids', () => {
    const out = renderMarkdown('# Hello World')
    expect(out).toMatch(/<h1[^>]*id="hello-world"/)
  })

  it('keeps inline formatting (strong, em)', () => {
    const out = renderMarkdown('**bold** and *italic*')
    expect(out).toContain('<strong>bold</strong>')
    expect(out).toContain('<em>italic</em>')
  })

  it('renders fenced code with the standalone toolbar wrapper', () => {
    const out = renderMarkdown('```python\nprint("hi")\n```')
    expect(out).toContain('lc-code-block-standalone')
    expect(out).toContain('language-python')
  })

  it('groups consecutive fences with shared group id into tabbed widget', () => {
    const md = '```js {group="foo"}\nlet a = 1\n```\n```py {group="foo"}\na = 1\n```'
    const out = renderMarkdown(md)
    expect(out).toContain('lc-code-tabs')
    expect(out).toContain('lc-tab-btn')
    expect(out).toMatch(/data-index="0"/)
    expect(out).toMatch(/data-index="1"/)
  })

  it('escapes raw HTML through markdown-it (html: false) so it never executes', () => {
    const out = renderMarkdown('<script>alert(1)</script>')
    // markdown-it html:false escapes the tag, so the browser renders literal text
    // (escaped entities) rather than an executable <script> element.
    expect(out).not.toMatch(/<script[\s>]/i)
    expect(out).toContain('&lt;script&gt;')
  })

  it('strips javascript: hrefs in linkified markdown', () => {
    const out = renderMarkdown('[click](javascript:alert(1))')
    expect(out).not.toMatch(/href="javascript:/i)
  })

  it('escapes raw HTML event handler attributes (no executable tag survives)', () => {
    const out = renderMarkdown('<img src="x" onerror="alert(1)" />')
    // markdown-it html:false escapes the entire tag — no executable <img> with
    // a real onerror attribute can reach the DOM.
    expect(out).not.toMatch(/<img[\s>]/i)
    expect(out).toContain('&lt;img')
  })

  it('returns an empty string for empty input', () => {
    expect(renderMarkdown('')).toBe('')
  })

  it('returns an empty string for null/undefined input', () => {
    expect(renderMarkdown(null as unknown as string)).toBe('')
    expect(renderMarkdown(undefined as unknown as string)).toBe('')
  })
})

describe('sanitizeHtml', () => {
  it('removes script tags', () => {
    const out = sanitizeHtml('<div><script>x</script></div>')
    expect(out).not.toContain('<script>')
  })

  it('preserves basic formatting', () => {
    const out = sanitizeHtml('<p><strong>ok</strong></p>')
    expect(out).toContain('<strong>ok</strong>')
  })

  it('removes on* attributes', () => {
    const out = sanitizeHtml('<a href="#" onclick="x">x</a>')
    expect(out).not.toMatch(/onclick/i)
  })
})