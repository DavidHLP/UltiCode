import { describe, expect, it } from "vitest";
import {
  buildDistributionDisplayPoints,
  normalizeDistributionBins,
} from "../useSubmissionDetail";

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

describe("buildDistributionDisplayPoints", () => {
  it("keeps real counted distribution data", () => {
    const points = normalizeDistributionBins([
      { bin: 0, count: 2 },
      { bin: 1, count: 3 },
    ]);

    expect(buildDistributionDisplayPoints(points, 1)).toEqual(points);
  });

  it("adds an honest current-submission bar when aggregate counts are missing", () => {
    expect(buildDistributionDisplayPoints([], 6.6)).toEqual([
      { i: 0, bin: 6.6, count: 1 },
    ]);
  });

  it("promotes zero-count ticks to a visible current value bar", () => {
    const points = normalizeDistributionBins([0, 1, 2]);

    expect(buildDistributionDisplayPoints(points, 1)).toEqual([
      { i: 0, bin: 0, count: 0 },
      { i: 1, bin: 1, count: 1 },
      { i: 2, bin: 2, count: 0 },
    ]);
  });
});
