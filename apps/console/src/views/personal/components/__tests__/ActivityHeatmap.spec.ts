import source from "../ActivityHeatmap.vue?raw";
import { expect, it } from "vitest";

it("keeps zero-activity cells and legend visible on light card surfaces", () => {
  expect(
    source.match(
      /bg-\[color-mix\(in_oklch,var\(--card\)_85%,var\(--foreground\)\)\]/g,
    ),
  ).toHaveLength(2);
  expect(source).not.toContain("var(--card)_60%,var(--muted)");
});
