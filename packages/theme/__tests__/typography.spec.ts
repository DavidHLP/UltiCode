// ---------------------------------------------------------------------------
// @ulticode/theme — typography unit tests
//
// Verifies the public contract: token names, density profile names,
// duplicate-free token lists, and the applyTypographyDensity DOM helper.
// Keeping the typography metadata free of duplicates and with stable names
// is what lets the guardrail script in packages/theme/scripts/verify-typography-tokens.mjs
// rely on the lists exported from the index barrel.
// ---------------------------------------------------------------------------

import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

import {
  TYPOGRAPHY_DENSITIES,
  TYPOGRAPHY_DENSITY,
  applyTypographyDensity,
  getTypographyDensity,
  typographyCssVariables,
  typographyFoundationPrefixes,
  typographySizes,
  typographyUtilityClasses,
} from '../src'

const typographyCss = readFileSync(
  fileURLToPath(new URL('../src/typography.css', import.meta.url)),
  'utf8',
)

beforeEach(() => {
  delete document.documentElement.dataset.ucDensity
})

afterEach(() => {
  delete document.documentElement.dataset.ucDensity
})

describe('typographySizes', () => {
  it('contains the nine expected size tokens in order', () => {
    expect(Object.keys(typographySizes)).toEqual([
      'text2xs',
      'textXxs',
      'textXs',
      'textSm',
      'textMd',
      'textLg',
      'textXl',
      'text2xl',
      'text3xl',
    ])
  })

  it('uses rem units that match the design doc', () => {
    expect(typographySizes.text2xs).toBe('0.625rem')
    expect(typographySizes.textXxs).toBe('0.6875rem')
    expect(typographySizes.textXs).toBe('0.75rem')
    expect(typographySizes.textSm).toBe('0.875rem')
    expect(typographySizes.textMd).toBe('1rem')
    expect(typographySizes.textLg).toBe('1.125rem')
    expect(typographySizes.textXl).toBe('1.25rem')
    expect(typographySizes.text2xl).toBe('1.5rem')
    expect(typographySizes.text3xl).toBe('1.875rem')
  })

  it('has no duplicate keys (catches copy-paste bugs early)', () => {
    const keys = Object.keys(typographySizes)
    expect(new Set(keys).size).toBe(keys.length)
  })
})

describe('typographyCssVariables', () => {
  it('every value is a --uc-* custom property name', () => {
    for (const name of Object.values(typographyCssVariables)) {
      expect(name.startsWith('--uc-')).toBe(true)
    }
  })

  it('has no duplicate values (catches accidental aliasing)', () => {
    const values = Object.values(typographyCssVariables)
    expect(new Set(values).size).toBe(values.length)
  })
})

describe('typographyFoundationPrefixes', () => {
  it('covers font, text, leading, weight, tracking, and type surfaces', () => {
    expect(typographyFoundationPrefixes).toContain('--uc-font-')
    expect(typographyFoundationPrefixes).toContain('--uc-text-')
    expect(typographyFoundationPrefixes).toContain('--uc-leading-')
    expect(typographyFoundationPrefixes).toContain('--uc-tracking-')
    expect(typographyFoundationPrefixes).toContain('--uc-font-weight-')
    expect(typographyFoundationPrefixes).toContain('--uc-type-')
  })

  it('has no duplicate prefixes', () => {
    expect(new Set(typographyFoundationPrefixes).size).toBe(
      typographyFoundationPrefixes.length,
    )
  })
})

describe('typographyUtilityClasses', () => {
  it('includes the canonical uc-type-* roles', () => {
    expect(typographyUtilityClasses).toContain('uc-type-body')
    expect(typographyUtilityClasses).toContain('uc-type-page-title')
    expect(typographyUtilityClasses).toContain('uc-type-section-title')
    expect(typographyUtilityClasses).toContain('uc-type-card-title')
    expect(typographyUtilityClasses).toContain('uc-type-control')
    expect(typographyUtilityClasses).toContain('uc-type-label')
    expect(typographyUtilityClasses).toContain('uc-type-data')
    expect(typographyUtilityClasses).toContain('uc-type-code')
    expect(typographyUtilityClasses).toContain('markdown-block')
  })

  it('keeps the legacy terminal-* utilities so existing call sites still work', () => {
    expect(typographyUtilityClasses).toContain('terminal-label')
    expect(typographyUtilityClasses).toContain('terminal-badge')
    expect(typographyUtilityClasses).toContain('terminal-input')
    expect(typographyUtilityClasses).toContain('ascii-progress')
  })

  it('has no duplicate class names', () => {
    expect(new Set(typographyUtilityClasses).size).toBe(
      typographyUtilityClasses.length,
    )
  })
})

describe('density profiles', () => {
  it('exposes the two documented densities', () => {
    expect([...TYPOGRAPHY_DENSITIES]).toEqual(['comfortable', 'compact'])
  })

  it('exposes named alias constants', () => {
    expect(TYPOGRAPHY_DENSITY.comfortable).toBe('comfortable')
    expect(TYPOGRAPHY_DENSITY.compact).toBe('compact')
  })
})

describe('locale design profiles', () => {
  it('publishes explicit zh-CN and en-US typography selectors', () => {
    expect(typographyCss).toContain(':root[lang="zh-CN"]')
    expect(typographyCss).toContain(':root[lang="en-US"]')
    expect(typographyCss).toContain('--uc-leading-normal: 1.65')
    expect(typographyCss).toContain('--uc-leading-normal: 1.5')
    expect(typographyCss).toContain('--uc-locale-heading-tracking')
    expect(typographyCss).toContain('--uc-locale-control-tracking')
  })
})

describe('applyTypographyDensity', () => {
  it('writes data-uc-density on <html>', () => {
    applyTypographyDensity('comfortable')
    expect(document.documentElement.dataset.ucDensity).toBe('comfortable')

    applyTypographyDensity('compact')
    expect(document.documentElement.dataset.ucDensity).toBe('compact')
  })

  it('getTypographyDensity mirrors the value back', () => {
    applyTypographyDensity('compact')
    expect(getTypographyDensity()).toBe('compact')

    applyTypographyDensity('comfortable')
    expect(getTypographyDensity()).toBe('comfortable')
  })

  it('getTypographyDensity returns null when unset', () => {
    expect(getTypographyDensity()).toBeNull()
  })
})
