import { describe, expect, it } from "vitest";
import { normalizeDistributionBins } from "../useSubmissionDetail";

describe("normalizeDistributionBins", () => {
  it("keeps object bins with counts", () => {
    expect(
      normalizeDistributionBins([
        { min: 20, max: 40, count: 3 },
        { min: 40, max: 80, count: 7 },
      ]),
    ).toEqual([
      { i: 0, bin: 20, count: 3 },
      { i: 1, bin: 40, count: 7 },
    ]);
  });

  it("converts numeric bins to zero-count display ticks", () => {
    expect(normalizeDistributionBins([20, 40, 80])).toEqual([
      { i: 0, bin: 20, count: 0 },
      { i: 1, bin: 40, count: 0 },
      { i: 2, bin: 80, count: 0 },
    ]);
  });

  it("supports tuple bins and filters invalid labels", () => {
    expect(
      normalizeDistributionBins([[16, 2], [32, 5], { count: 9 }, Number.NaN]),
    ).toEqual([
      { i: 0, bin: 16, count: 2 },
      { i: 1, bin: 32, count: 5 },
    ]);
  });
});
