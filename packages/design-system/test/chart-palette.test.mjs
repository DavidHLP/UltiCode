import assert from "node:assert/strict";
import test from "node:test";

test("resolves every chart role from the canonical palette without a DOM", async () => {
  const { resolveChartPalette } = await import("../src/chartPalette.ts");
  const { UI_PALETTE } = await import("../src/palette.ts");

  assert.deepEqual(resolveChartPalette(), {
    fontFamily: "system-ui, sans-serif",
    series1: UI_PALETTE.blue,
    series2: UI_PALETTE.orange,
    series3: UI_PALETTE.cyan,
    series4: UI_PALETTE.amber,
    solved: UI_PALETTE.green,
    background: UI_PALETTE.background,
    surface: UI_PALETTE.secondary,
    foreground: UI_PALETTE.foreground,
    muted: UI_PALETTE.muted,
    axis: UI_PALETTE.muted,
    border: UI_PALETTE.border,
    tooltipBorder: UI_PALETTE.muted,
  });
});


test("resolves chart text from the current UI font", async () => {
  const { resolveChartPalette } = await import("../src/chartPalette.ts");
  const previousDocument = globalThis.document;
  const previousStyle = globalThis.getComputedStyle;
  try {
    globalThis.document = { documentElement: {} };
    globalThis.getComputedStyle = () => ({ getPropertyValue: name => name === "--uc-font-ui" ? "Georgia, serif" : "" });
    assert.equal(resolveChartPalette().fontFamily, "Georgia, serif");
  } finally {
    globalThis.document = previousDocument;
    globalThis.getComputedStyle = previousStyle;
  }
});
