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
 * The CSS file is still imported by each app via
 * `@import "../../shared/design-system/style.css"` from its own
 * `src/style.css`. Migrating the CSS to a workspace export is out of
 * scope for this seam — that becomes a build-pipeline question.
 */

// Re-export the badge color type so design-system consumers have one
// place to import presentation types from. Kept as a value-less re-export
// to avoid pulling badge-config's runtime into design-system's main entry.
export type { SemanticColor } from '../../badge-config/src/semantic-colors'

/**
 * Canonical CSS variable names that the design-system exposes. Use these
 * instead of inline oklch() values so color/contrast changes flow from a
 * single place. The list mirrors the variable names declared in
 * `shared/design-system/style.css`.
 */
export const CSS_TOKENS = {
  // Surfaces
  background: 'var(--background)',
  foreground: 'var(--foreground)',
  card: 'var(--card)',
  cardForeground: 'var(--card-foreground)',
  popover: 'var(--popover)',
  popoverForeground: 'var(--popover-foreground)',
  muted: 'var(--muted)',
  mutedForeground: 'var(--muted-foreground)',
  // Brand
  primary: 'var(--primary)',
  primaryForeground: 'var(--primary-foreground)',
  accent: 'var(--accent)',
  accentElectric: 'var(--accent-electric)',
  accentForeground: 'var(--accent-foreground)',
  // Status palette
  destructive: 'var(--destructive)',
  destructiveForeground: 'var(--destructive-foreground)',
  // Borders & inputs
  border: 'var(--border)',
  input: 'var(--input)',
  ring: 'var(--ring)',
  // Solarized accents
  solarizedYellow: 'var(--solarized-yellow)',
  solarizedOrange: 'var(--solarized-orange)',
  solarizedRed: 'var(--solarized-red)',
  solarizedMagenta: 'var(--solarized-magenta)',
  solarizedViolet: 'var(--solarized-violet)',
  solarizedBlue: 'var(--solarized-blue)',
  solarizedCyan: 'var(--solarized-cyan)',
  solarizedGreen: 'var(--solarized-green)',
  // Terminal palette
  terminalGreen: 'var(--terminal-green)',
  terminalAmber: 'var(--terminal-amber)',
  terminalRed: 'var(--terminal-red)',
  terminalCyan: 'var(--terminal-cyan)',
  terminalPurple: 'var(--terminal-purple)',
  // Silver (neutral)
  silver100: 'var(--silver-100)',
  silver300: 'var(--silver-300)',
  silver400: 'var(--silver-400)',
  silver500: 'var(--silver-500)',
  silver600: 'var(--silver-600)',
  silver800: 'var(--silver-800)',
} as const

export type CssTokenKey = keyof typeof CSS_TOKENS
