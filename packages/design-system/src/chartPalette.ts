import { ref, type Ref } from "vue";
import { readCssColor, SOLARIZED_PALETTE } from "./palette.ts";

export interface ChartPalette {
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
    series1: readCssColor("--chart-series-1", SOLARIZED_PALETTE.blue),
    series2: readCssColor("--chart-series-2", SOLARIZED_PALETTE.cyan),
    series3: readCssColor("--chart-series-3", SOLARIZED_PALETTE.green),
    series4: readCssColor("--chart-series-4", SOLARIZED_PALETTE.violet),
    solved: readCssColor("--chart-status-solved", SOLARIZED_PALETTE.green),
    background: readCssColor(
      "--chart-tooltip-background",
      SOLARIZED_PALETTE.base3,
    ),
    surface: readCssColor("--surface-highlight", SOLARIZED_PALETTE.base2),
    foreground: readCssColor(
      "--foreground-strong",
      SOLARIZED_PALETTE.base03,
    ),
    muted: readCssColor("--foreground", SOLARIZED_PALETTE.base01),
    axis: readCssColor("--foreground", SOLARIZED_PALETTE.base01),
    border: readCssColor("--chart-grid-color", SOLARIZED_PALETTE.base1),
    tooltipBorder: readCssColor(
      "--chart-tooltip-border",
      SOLARIZED_PALETTE.base00,
    ),
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
    attributeFilter: ["class"],
  });
}

export function useChartPalette(): Readonly<Ref<ChartPalette>> {
  chartPalette.value = resolveChartPalette();
  ensureThemeObserver();
  return chartPalette;
}
