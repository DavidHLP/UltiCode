/**
 * @ulticode/design-system — single source of truth for cross-app UI tokens
 * and proven shared primitives.
 *
 * Before this module existed, the design-system "seam" was a single
 * `style.css` of Tailwind + CSS variables. The behavior (Vue components,
 * Tailwind class merging, props contracts) lived in two parallel trees:
 * `console/src/components/ui/**` and `management/src/components/ui/**`.
 * Both apps had near-identical shadcn-vue ports of `Separator`, `Kbd`,
 * `Skeleton`, `Spinner`, etc. — diverging only in Prettier quote style
 * and a few app-specific variants (see arch review 2026-07-10, candidate
 * #1).
 *
 * Strategy: migrate primitives one at a time. Each app keeps its
 * `components/ui/<name>/index.ts` as a thin re-export shim pointing at
 * the shared component, so existing `import { Foo } from
 * '@/components/ui/foo'` paths keep working. App-only extensions (e.g.
 * management's `terminal` variants on `Button`, its `TerminalCard` /
 * `TerminalInput` / `TerminalBadge` / `DataBlock` siblings of the shared
 * terminal badge) stay where they are.
 *
 * Each app imports the public stylesheet through
 * `@ulticode/design-system/style.css`. Callers consume semantic tokens;
 * the canonical palette, dual-mode mapping and accessibility adjustments
 * remain implementation details of this module.
 */

// Re-export the badge color type so design-system consumers have one
// place to import presentation types from. Kept as a value-less re-export
// to avoid pulling badge-config's runtime into design-system's main entry.
export type { SemanticColor } from "../../badge-config/src/semantic-colors";
export {
  BADGE_VARIANT_CLASSES,
  BUTTON_BASE_CLASSES,
  BUTTON_SIZE_CLASSES,
  BUTTON_VARIANT_CLASSES,
  getDifficultyBadgeClass,
  MENU_ITEM_VARIANT_CLASSES,
} from "./variants";

/**
 * Canonical CSS variable names that the design-system exposes. Use these
 * instead of inline color values so color/contrast changes flow from a
 * single place. The list mirrors the public variables declared in
 * `style.css`.
 */
export const CSS_TOKENS = {
  // Geometry
  radius: "var(--radius)",
  radiusSm: "var(--radius-sm)",
  radiusMd: "var(--radius-md)",
  radiusLg: "var(--radius-lg)",
  radiusXl: "var(--radius-xl)",
  localePageGutter: "var(--uc-layout-page-gutter)",
  localeSectionGap: "var(--uc-layout-section-gap)",
  localeControlGap: "var(--uc-layout-control-gap)",
  localeControlHeight: "var(--uc-layout-control-height)",
  localeControlPaddingInline: "var(--uc-layout-control-padding-inline)",
  localePanelPaddingBlock: "var(--uc-layout-panel-padding-block)",
  localePanelPaddingInline: "var(--uc-layout-panel-padding-inline)",
  localeControlRadius: "var(--uc-component-control-radius)",
  localeCardRadius: "var(--uc-component-card-radius)",
  // Surfaces
  background: "var(--background)",
  foreground: "var(--foreground)",
  foregroundStrong: "var(--foreground-strong)",
  foregroundMuted: "var(--foreground-muted)",
  surface: "var(--surface)",
  surfaceElevated: "var(--surface-elevated)",
  surfaceHighlight: "var(--surface-highlight)",
  surfaceSunken: "var(--surface-sunken)",
  overlay: "var(--overlay)",
  card: "var(--card)",
  cardForeground: "var(--card-foreground)",
  popover: "var(--popover)",
  popoverForeground: "var(--popover-foreground)",
  muted: "var(--muted)",
  mutedForeground: "var(--muted-foreground)",
  // Brand
  primary: "var(--primary)",
  primaryForeground: "var(--primary-foreground)",
  primaryControl: "var(--primary-control)",
  primaryControlForeground: "var(--primary-control-foreground)",
  linkForeground: "var(--link-foreground)",
  linkDecoration: "var(--link-decoration)",
  accent: "var(--accent)",
  accentPrimary: "var(--accent-primary)",
  accentElectric: "var(--accent-primary)",
  accentForeground: "var(--accent-foreground)",
  // Status palette
  destructive: "var(--destructive)",
  destructiveForeground: "var(--destructive-foreground)",
  statusSuccess: "var(--status-success)",
  statusWarning: "var(--status-warning)",
  statusError: "var(--status-error)",
  statusInfo: "var(--status-info)",
  statusSpecial: "var(--status-special)",
  statusSuccessMark: "var(--status-success-mark)",
  statusWarningMark: "var(--status-warning-mark)",
  statusErrorMark: "var(--status-error-mark)",
  statusInfoMark: "var(--status-info-mark)",
  statusSpecialMark: "var(--status-special-mark)",
  statusSuccessSurface: "var(--status-success-surface)",
  statusWarningSurface: "var(--status-warning-surface)",
  statusErrorSurface: "var(--status-error-surface)",
  statusInfoSurface: "var(--status-info-surface)",
  statusSpecialSurface: "var(--status-special-surface)",
  // Borders & inputs
  border: "var(--border)",
  borderSubtle: "var(--border-subtle)",
  borderControl: "var(--border-control)",
  input: "var(--input)",
  ring: "var(--ring)",
  // Charts
  chartSeries1: "var(--chart-series-1)",
  chartSeries2: "var(--chart-series-2)",
  chartSeries3: "var(--chart-series-3)",
  chartSeries4: "var(--chart-series-4)",
  chartSeries5: "var(--chart-series-5)",
  chartSeries6: "var(--chart-series-6)",
  chartSeries7: "var(--chart-series-7)",
  chartSeries8: "var(--chart-series-8)",
  chartGrid: "var(--chart-grid-color)",
  chartBackground: "var(--chart-background)",
  chartTooltipBackground: "var(--chart-tooltip-background)",
  chartTooltipBorder: "var(--chart-tooltip-border)",
  // Domain presentation
  rankFirst: "var(--rank-first)",
  rankSecond: "var(--rank-second)",
  rankThird: "var(--rank-third)",
} as const;

export type CssTokenKey = keyof typeof CSS_TOKENS;

/**
 * Canonical Solarized runtime palette bridge for non-CSS renderers
 * (ECharts, Monaco, WebGL). CSS consumers keep using CSS_TOKENS / the
 * stylesheet; renderers resolve concrete colors via `readCssColor` with a
 * canonical `SOLARIZED_PALETTE` fallback.
 */
export { readCssColor, SOLARIZED_PALETTE } from "./palette";
export type {
  SolarizedPaletteKey,
  SolarizedPaletteValue,
} from "./palette";
