/**
 * Canonical Solarized runtime palette bridge.
 *
 * The 16 official Solarized sRGB values, mirrored from `style.css` (see
 * "Canonical Solarized sRGB palette" in the stylesheet, which remains the
 * source of truth for CSS consumers). Non-CSS renderers (ECharts, Monaco,
 * WebGL) need concrete colors and must resolve them through this module:
 * `SOLARIZED_PALETTE` carries the values, `readCssColor` resolves the current
 * theme at runtime with a canonical fallback. Consumers must not invent a
 * second palette — this is the only runtime copy.
 */

export const SOLARIZED_PALETTE = {
  base03: "#002b36",
  base02: "#073642",
  base01: "#586e75",
  base00: "#657b83",
  base0: "#839496",
  base1: "#93a1a1",
  base2: "#eee8d5",
  base3: "#fdf6e3",
  yellow: "#b58900",
  orange: "#cb4b16",
  red: "#dc322f",
  magenta: "#d33682",
  violet: "#6c71c4",
  blue: "#268bd2",
  cyan: "#2aa198",
  green: "#859900",
} as const;

export type SolarizedPaletteKey = keyof typeof SOLARIZED_PALETTE;
export type SolarizedPaletteValue =
  (typeof SOLARIZED_PALETTE)[SolarizedPaletteKey];

const CANONICAL_VALUES = Object.fromEntries(
  Object.values(SOLARIZED_PALETTE).map((value) => [value, true]),
) as Record<SolarizedPaletteValue, true>;

function normalizeVariable(variable: string): string {
  const trimmed = variable.trim();
  return trimmed.startsWith("var(") ? trimmed.slice(4, -1) : trimmed;
}

/**
 * Resolve a CSS custom property to a concrete color for a non-CSS renderer.
 *
 * Accepts both `"--token"` and `"var(--token)"` names. Prefers the
 * browser-computed value of the property; when there is no DOM or the property
 * is unset it returns `fallback`. The fallback MUST be one of the
 * SOLARIZED_PALETTE values (validated at runtime) so renderers can never paint
 * a non-Solarized color.
 */
export function readCssColor(
  variable: string,
  fallback: SolarizedPaletteValue,
): string {
  if (!Object.prototype.hasOwnProperty.call(CANONICAL_VALUES, fallback)) {
    throw new TypeError(
      `readCssColor fallback must be a canonical Solarized value, got "${fallback}"`,
    );
  }
  const runtime = globalThis as typeof globalThis & {
    document?: { documentElement: unknown };
    getComputedStyle?: (
      element: unknown,
    ) => { getPropertyValue(name: string): string };
  };
  if (
    !runtime.document ||
    typeof runtime.getComputedStyle !== "function"
  ) {
    return fallback;
  }
  const resolved = runtime
    .getComputedStyle(runtime.document.documentElement)
    .getPropertyValue(normalizeVariable(variable))
    .trim();
  return resolved.length > 0 ? resolved : fallback;
}
