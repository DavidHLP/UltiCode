import { describe, expect, it } from "vitest";
import { defaultColors } from ".";

describe("defaultColors", () => {
  it("uses the stable public Solarized series and wraps after eight", () => {
    expect(defaultColors(10)).toEqual([
      "var(--chart-series-1)",
      "var(--chart-series-2)",
      "var(--chart-series-3)",
      "var(--chart-series-4)",
      "var(--chart-series-5)",
      "var(--chart-series-6)",
      "var(--chart-series-7)",
      "var(--chart-series-8)",
      "var(--chart-series-1)",
      "var(--chart-series-2)",
    ]);
  });
});
