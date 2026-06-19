// ---------------------------------------------------------------------------
// useAuthLayout — shared layout types
// ---------------------------------------------------------------------------

/**
 * One line in the right-side `system_status.sh` terminal block.
 *
 * - `prompt`: the `$ <command>` line (rendered as a shell prompt)
 * - `output`: subsequent lines of output (rendered verbatim)
 *
 * Pass an array to `AuthPatternBackground`'s `spec` prop to fully
 * customize the panel per surface. Console and management use different
 * port numbers and test counts; nothing else differs.
 */
export interface AuthPatternLine {
  prompt?: string
  output: string | { text: string; tone?: 'normal' | 'success' | 'accent' | 'muted' }
}

export interface AuthLayoutProps {
  /**
   * Text shown next to the UltiCode logo (e.g. "CODE" for console,
   * "ADMIN" for management). Defaults to "CODE".
   */
  badge?: string
  /** Version string shown in the footer (defaults to "v2.0.0") */
  version?: string
  /** Status text shown next to the pulsing dot in the footer */
  statusText?: string
  /**
   * Hide the right-side pattern panel. Useful for narrow/mobile layouts
   * where the panel would feel cramped. The form-side still renders.
   */
  hidePattern?: boolean
  /**
   * Override the home link target (defaults to `/`). Useful when the
   * auth surface sits behind a reverse proxy with a different base path.
   */
  homeHref?: string
}