import { ref, type Ref } from "vue";
import { readCssColor, UI_PALETTE } from "./palette.ts";

export interface ChartPalette {
  fontFamily: string;
  series1: string;
  series2: string;
  series3: string;
  series4: string;
  solved: string;
  background: string;
  surface: string;
  foreground: string;
  muted: string;
  axis: string;
  border: string;
  tooltipBorder: string;
}

export function resolveChartPalette(): ChartPalette {
  return {
    fontFamily:
      typeof document === "undefined" || typeof getComputedStyle !== "function"
        ? "system-ui, sans-serif"
        : getComputedStyle(document.documentElement)
            .getPropertyValue("--uc-font-ui")
            .trim() || "system-ui, sans-serif",
    series1: readCssColor("--chart-1", UI_PALETTE.blue),
    series2: readCssColor("--chart-2", UI_PALETTE.orange),
    series3: readCssColor("--chart-3", UI_PALETTE.cyan),
    series4: readCssColor("--chart-4", UI_PALETTE.amber),
    solved: readCssColor("--status-success-mark", UI_PALETTE.green),
    background: readCssColor("--popover", UI_PALETTE.background),
    surface: readCssColor("--muted", UI_PALETTE.secondary),
    foreground: readCssColor("--foreground", UI_PALETTE.darkBackground),
    muted: readCssColor("--muted-foreground", UI_PALETTE.muted),
    axis: readCssColor("--muted-foreground", UI_PALETTE.muted),
    border: readCssColor("--border", UI_PALETTE.border),
    tooltipBorder: readCssColor("--border", UI_PALETTE.muted),
  };
}

const chartPalette = ref<ChartPalette>(resolveChartPalette());
let themeObserver: MutationObserver | null = null;

function ensureThemeObserver() {
  if (
    themeObserver ||
    typeof document === "undefined" ||
    typeof MutationObserver === "undefined"
  ) {
    return;
  }

  themeObserver = new MutationObserver(() => {
    chartPalette.value = resolveChartPalette();
  });
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: [
      "class",
      "style",
      "data-appearance-base",
      "data-appearance-accent",
      "data-appearance-chart",
      "data-appearance-font",
    ],
  });
}

export function useChartPalette(): Readonly<Ref<ChartPalette>> {
  chartPalette.value = resolveChartPalette();
  ensureThemeObserver();
  return chartPalette;
}
