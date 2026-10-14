/** Fallbacks for non-CSS renderers; runtime colors resolve from semantic tokens. */
export const UI_PALETTE = {
  background: "#ffffff",
  foreground: "#171717",
  card: "#ffffff",
  secondary: "#f5f5f5",
  darkBackground: "#171717",
  darkCard: "#262626",
  darkForeground: "#fafafa",
  muted: "#737373",
  border: "#e5e5e5",
  blue: "#2a78d6",
  orange: "#eb6834",
  cyan: "#1baf7a",
  green: "#15803d",
  violet: "#6250d6",
  yellow: "#92400e",
  red: "#b91c1c",
  magenta: "#e87ba4",
  amber: "#eda100",
  darkMuted: "#a3a3a3",
  darkGreen: "#86efac",
  darkYellow: "#fcd34d",
  darkBlue: "#93c5fd",
  darkViolet: "#d8b4fe",
  darkRed: "#f87171",
} as const;

/** Canvas converts OKLCH to sRGB for renderers such as ECharts and Monaco. */
export function readCssColor(variable: string, fallback: string): string {
  const name = variable.trim().replace(/^var\((--[\w-]+)\)$/, "$1");
  if (!/^--[\w-]+$/.test(name)) return fallback;
  if (typeof document === "undefined" || typeof getComputedStyle !== "function")
    return fallback;
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(name)
    .trim();
  if (!value) return fallback;
  if (/^#(?:[a-f\d]{3,4}|[a-f\d]{6}|[a-f\d]{8})$/i.test(value)) return value;
  if (!globalThis.CSS?.supports("color", value)) return fallback;
  const context = document
    .createElement("canvas")
    .getContext("2d", { willReadFrequently: true });
  if (!context) return fallback;
  context.canvas.width = context.canvas.height = 1;
  context.fillStyle = value;
  context.fillRect(0, 0, 1, 1);
  const [r, g, b, alpha] = context.getImageData(0, 0, 1, 1).data;
  const channels = alpha === 255 ? [r, g, b] : [r, g, b, alpha];
  return (
    "#" +
    channels.map((channel) => channel.toString(16).padStart(2, "0")).join("")
  );
}
