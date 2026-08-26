// ---------------------------------------------------------------------------
// @ulticode/theme — typography token metadata
//
// CSS variables in `typography.css` are the runtime source of truth for font
// families, sizes, line heights, weights, and tracking. This module exposes
// a small TypeScript surface for non-CSS consumers:
//
//   - ECharts and Monaco text styles
//   - unit tests that assert token names exist
//   - the applyTypographyDensity helper that writes data-uc-density on <html>
//
// Important: do NOT generate CSS at runtime from these constants. They exist
// only for consumers that cannot read CSS variables directly. Components
// should keep using CSS classes (uc-type-*, terminal-label, font-mono, ...)
// so styling stays governed by the shared design system.
// ---------------------------------------------------------------------------

/**
 * Density profiles. Console is comfortable (longer reading, markdown, forum
 * content); management is compact (tables, moderation queues, dashboards).
 */
export const TYPOGRAPHY_DENSITIES = ['comfortable', 'compact'] as const

export type TypographyDensity = (typeof TYPOGRAPHY_DENSITIES)[number]

/** Convenience aliases — mirrors of the `as const` array. */
export const TYPOGRAPHY_DENSITY: { readonly comfortable: 'comfortable'; readonly compact: 'compact' } =
  {
    comfortable: 'comfortable',
    compact: 'compact',
  }

/**
 * Foundation size tokens, in rem units. Values match the CSS declarations in
 * `typography.css`; both surfaces must be updated together.
 */
export const typographySizes = {
  text2xs: '0.625rem', // 10px
  textXxs: '0.6875rem', // 11px
  textXs: '0.75rem', // 12px
  textSm: '0.875rem', // 14px
  textMd: '1rem', // 16px
  textLg: '1.125rem', // 18px
  textXl: '1.25rem', // 20px
  text2xl: '1.5rem', // 24px
  text3xl: '1.875rem', // 30px
} as const

export type TypographySizeToken = keyof typeof typographySizes

/**
 * Names of the CSS variables that govern the most-frequently-tweaked
 * semantic roles. Listed in one place so tests can verify they are still
 * wired up after a refactor.
 */
export const typographyCssVariables = {
  bodyFamily: '--uc-type-body-family',
  bodySize: '--uc-type-body-size',
  bodyLineHeight: '--uc-type-body-line-height',
  bodyWeight: '--uc-type-body-weight',
  pageTitleSize: '--uc-type-page-title-size',
  sectionTitleSize: '--uc-type-section-title-size',
  cardTitleSize: '--uc-type-card-title-size',
  controlSize: '--uc-type-control-size',
  controlLineHeight: '--uc-type-control-line-height',
  controlWeight: '--uc-type-control-weight',
  labelSize: '--uc-type-label-size',
  tableHeaderSize: '--uc-type-table-header-size',
  tableCellSize: '--uc-type-table-cell-size',
  dataSize: '--uc-type-data-size',
  codeSize: '--uc-type-code-size',
  markdownSize: '--uc-type-markdown-size',
  localeHeadingTracking: '--uc-locale-heading-tracking',
  localeControlTracking: '--uc-locale-control-tracking',
} as const

export type TypographyCssVariable = keyof typeof typographyCssVariables

/**
 * Foundation CSS variable prefixes. Useful for guardrail scripts that need
 * to enumerate the canonical source-of-truth surface.
 */
export const typographyFoundationPrefixes = [
  '--uc-font-',
  '--uc-text-',
  '--uc-leading-',
  '--uc-tracking-',
  '--uc-font-weight-',
  '--uc-type-',
] as const

/**
 * Utility class names shipped by `typography.css`. Components should prefer
 * these over arbitrary `text-[…px]` Tailwind values. Listed in one place so
 * the guardrail script and the test suite can verify the surface is stable.
 */
export const typographyUtilityClasses = [
  'uc-type-body',
  'uc-type-page-title',
  'uc-type-section-title',
  'uc-type-card-title',
  'uc-type-control',
  'uc-type-label',
  'uc-type-data',
  'uc-type-code',
  'markdown-block',
  // Legacy utility classes preserved for the migration. New code should
  // prefer the uc-type-* classes above, but existing call sites are kept
  // working through the rewrite of shared/design-system/style.css.
  'font-data',
  'font-mono',
  'font-sans',
  'terminal-label',
  'terminal-comment',
  'terminal-badge',
  'terminal-kv-key',
  'terminal-kv-value',
  'terminal-input',
  'ascii-progress',
  'terminal-row-num',
  'header-btn',
] as const

export type TypographyUtilityClass = (typeof typographyUtilityClasses)[number]

/**
 * Set the typography density profile on <html>. Apps should call this once
 * during bootstrap (after the FOUC script has had a chance to run); the same
 * module is the only place that writes the `data-uc-density` attribute.
 *
 * Safe to call on the server (no-op).
 */
export function applyTypographyDensity(density: TypographyDensity): void {
  if (typeof document === 'undefined') return
  document.documentElement.dataset.ucDensity = density
}

/** Read the currently-active density profile. Returns null on the server. */
export function getTypographyDensity(): TypographyDensity | null {
  if (typeof document === 'undefined') return null
  const value = document.documentElement.dataset.ucDensity
  return value === 'comfortable' || value === 'compact' ? value : null
}
