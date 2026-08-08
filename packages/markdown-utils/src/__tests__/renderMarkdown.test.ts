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
