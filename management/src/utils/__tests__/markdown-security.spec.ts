import { describe, expect, it } from 'vitest'
import { renderMarkdown } from '@/utils/markdown'

describe('markdown security', () => {
  it('renders KaTeX while removing executable markup', () => {
    const html = renderMarkdown(
      '$x^2$ <img src=x onerror=alert(1)> [x](javascript:alert(1))',
    )

    expect(html).toContain('katex')
    expect(html).not.toMatch(/<img|href=["']javascript:|<script/i)
    expect(html).toContain('&lt;img')
  })
})
