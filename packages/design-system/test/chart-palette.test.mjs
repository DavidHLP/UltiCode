import assert from "node:assert/strict";
import test from "node:test";

test("resolves every chart role from the canonical palette without a DOM", async () => {
  const { resolveChartPalette } = await import("../src/chartPalette.ts");
  const { SOLARIZED_PALETTE } = await import("../src/palette.ts");

  assert.deepEqual(resolveChartPalette(), {
    series1: SOLARIZED_PALETTE.blue,
    series2: SOLARIZED_PALETTE.cyan,
    series3: SOLARIZED_PALETTE.green,
    series4: SOLARIZED_PALETTE.violet,
    solved: SOLARIZED_PALETTE.green,
    background: SOLARIZED_PALETTE.base3,
    surface: SOLARIZED_PALETTE.base2,
    foreground: SOLARIZED_PALETTE.base03,
    muted: SOLARIZED_PALETTE.base01,
    axis: SOLARIZED_PALETTE.base01,
    border: SOLARIZED_PALETTE.base1,
    tooltipBorder: SOLARIZED_PALETTE.base00,
  });
});
