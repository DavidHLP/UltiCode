// ---------------------------------------------------------------------------
// useTypographyDensity — console re-export of the shared density helper.
//
// The implementation lives in shared/theme/src/typography.ts. This thin
// wrapper exists for symmetry with `@/composables/useTheme` so that all
// design-system helpers are imported through `@/composables/...` rather
// than reaching into `src/shared` directly. It also makes it trivial to
// grep for "every place density is set" — there should be exactly one
// call site in src/main.ts, and zero in component code.
// ---------------------------------------------------------------------------

export {
  TYPOGRAPHY_DENSITIES,
  TYPOGRAPHY_DENSITY,
  applyTypographyDensity,
  getTypographyDensity,
  typographyCssVariables,
  typographyFoundationPrefixes,
  typographySizes,
  typographyUtilityClasses,
  type TypographyCssVariable,
  type TypographyDensity,
  type TypographySizeToken,
  type TypographyUtilityClass,
} from '@/shared/theme/src'
