import { describe, it, expect } from 'vitest'
import { renderInlineContent } from '../comment-renderer'
import { isVNode } from 'vue'

describe('comment-renderer', () => {
  it('should return empty array for empty string', () => {
    expect(renderInlineContent('')).toEqual([])
  })

  it('should return plain text as-is', () => {
    const result = renderInlineContent('Hello world')
    expect(result).toHaveLength(1)
    expect(result[0]).toBe('Hello world')
  })

  it('should parse bold username mentions', () => {
    const result = renderInlineContent('**@admin_two** is here')
    expect(result).toHaveLength(2)
    expect(isVNode(result[0])).toBe(true)
    expect(result[0].props.class).toContain('text-[var(--primary)]')
    expect(result[0].children).toBe('@admin_two')
    expect(result[1]).toBe(' is here')
  })

  it('should parse plain username mentions', () => {
    const result = renderInlineContent('@admin_two is here')
    expect(result).toHaveLength(2)
    expect(isVNode(result[0])).toBe(true)
    expect(result[0].props.class).toContain('text-[var(--primary)]')
    expect(result[0].children).toBe('@admin_two')
    expect(result[1]).toBe(' is here')
  })

  it('should parse bold text', () => {
    const result = renderInlineContent('This is **bold** text')
    expect(result).toHaveLength(3)
    expect(result[0]).toBe('This is ')
    expect(isVNode(result[1])).toBe(true)
    expect(result[1].type).toBe('strong')
    expect(result[1].children).toBe('bold')
    expect(result[2]).toBe(' text')
  })

  it('should parse inline code', () => {
    const result = renderInlineContent('Use `map` here')
    expect(result).toHaveLength(3)
    expect(result[0]).toBe('Use ')
    expect(isVNode(result[1])).toBe(true)
    expect(result[1].type).toBe('code')
    expect(result[1].children).toBe('map')
    expect(result[1].props.class).toContain('bg-[color-mix(in_oklch')
    expect(result[1].props.class).toContain('dark:border-[var(--border-subtle)]')
    expect(result[1].props.class).not.toContain('dark:border-[var(--foreground-strong)]')
    expect(result[2]).toBe(' here')
  })

  it('should parse complex combinations', () => {
    const result = renderInlineContent('Hey **@admin_two**, check `code` here!')
    expect(result).toHaveLength(5)
    expect(result[0]).toBe('Hey ')
    expect(isVNode(result[1])).toBe(true) // **@admin_two**
    expect(result[2]).toBe(', check ')
    expect(isVNode(result[3])).toBe(true) // `code`
    expect(result[4]).toBe(' here!')
  })
})
