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
 * Canonical Solarized runtime palette bridge for non-CSS renderers
 * (ECharts, Monaco, WebGL). CSS consumers use the stylesheet; renderers
 * resolve concrete colors via `readCssColor` with a canonical
 * `SOLARIZED_PALETTE` fallback.
 */
export { readCssColor, SOLARIZED_PALETTE } from "./palette";
export type {
  SolarizedPaletteKey,
  SolarizedPaletteValue,
} from "./palette";
export {
  resolveChartPalette,
  useChartPalette,
} from "./chartPalette";
export type { ChartPalette } from "./chartPalette";
