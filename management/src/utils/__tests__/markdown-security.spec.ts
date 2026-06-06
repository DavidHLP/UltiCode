import { describe, expect, it } from 'vitest'
import { renderSafeMarkdown } from '@/utils/sanitize-markdown'

describe('markdown security', () => {
  it('renders KaTeX while removing executable markup', () => {
    const html = renderSafeMarkdown(
      '$x^2$ <img src=x onerror=alert(1)> [x](javascript:alert(1))',
    )

    expect(html).toContain('katex')
    expect(html).not.toMatch(/<img|href=["']javascript:|<script/i)
    expect(html).toContain('&lt;img')
  })
})
