import { describe, expect, it } from "vitest";
import {
  buildDistributionChartOption,
  formatRuntime,
  formatMemory,
  formatPercentile,
  readChartCssColors,
  type DistributionChartUnit,
} from "../submissionChartOptions";

describe("formatRuntime", () => {
  it("formats finite runtime values rounded to integer ms", () => {
    expect(formatRuntime(12.4)).toBe("12 ms");
    expect(formatRuntime(12.6)).toBe("13 ms");
  });

  it("falls back to placeholder for non-finite values", () => {
    expect(formatRuntime(Number.NaN)).toBe("-- ms");
    expect(formatRuntime(Number.POSITIVE_INFINITY)).toBe("-- ms");
    expect(formatRuntime(undefined)).toBe("-- ms");
  });
});

describe("formatMemory", () => {
  it("formats small memory values with one decimal", () => {
    expect(formatMemory(12.34)).toBe("12.3 MB");
  });

  it("rounds memory values at or above 100 MB to integer", () => {
    expect(formatMemory(99.6)).toBe("99.6 MB");
    expect(formatMemory(100.4)).toBe("100 MB");
    expect(formatMemory(150.7)).toBe("151 MB");
  });

  it("falls back to placeholder for non-finite values", () => {
    expect(formatMemory(Number.NaN)).toBe("-- MB");
    expect(formatMemory(undefined)).toBe("-- MB");
  });
});

describe("formatPercentile", () => {
  it("formats finite percentiles to one decimal", () => {
    expect(formatPercentile(72.34)).toBe("72.3");
    expect(formatPercentile(0)).toBe("0.0");
    expect(formatPercentile(100)).toBe("100.0");
  });

  it("falls back to 0.0 for non-finite values", () => {
    expect(formatPercentile(Number.NaN)).toBe("0.0");
    expect(formatPercentile(undefined)).toBe("0.0");
  });
});

describe("readChartCssColors", () => {
  it("returns provided fallbacks when CSS variables resolve to empty strings", () => {
    const colors = readChartCssColors({
      foreground: "#foreground",
      border: "#border",
      accent: "#accent",
    });

    expect(colors.foreground).toBe("#foreground");
    expect(colors.border).toBe("#border");
    expect(colors.accent).toBe("#accent");
    expect(colors.mutedBar).toBeTruthy();
  });
});

describe("buildDistributionChartOption", () => {
  const unit: DistributionChartUnit = "ms";

  it("emits category labels suffixed with the unit and y-axis minimum interval of 1", () => {
    const option = buildDistributionChartOption(
      [
        { i: 0, bin: 10, count: 2 },
        { i: 1, bin: 20, count: 5 },
      ],
      7,
      -1,
      unit,
    );

    expect(option.xAxis.data).toEqual(["10ms", "20ms"]);
    expect(option.yAxis.minInterval).toBe(1);
    expect(Array.isArray(option.series)).toBe(true);
  });

  it("uses accent color on the user bar and muted color on the rest", () => {
    const option = buildDistributionChartOption(
      [
        { i: 0, bin: 10, count: 2 },
        { i: 1, bin: 20, count: 5 },
        { i: 2, bin: 30, count: 1 },
      ],
      8,
      1,
      unit,
    );

    const series = option.series as { data: { itemStyle: { color: string } }[] }[];
    expect(series[0].data[0].itemStyle.color).not.toBe(series[0].data[1].itemStyle.color);
    expect(series[0].data[2].itemStyle.color).toBe(series[0].data[0].itemStyle.color);
  });

  it("falls back to accent for every bar when user index is out of range", () => {
    const option = buildDistributionChartOption(
      [
        { i: 0, bin: 10, count: 1 },
        { i: 1, bin: 20, count: 3 },
      ],
      4,
      99,
      unit,
    );

    const series = option.series as { data: { itemStyle: { color: string } }[] }[];
    expect(series[0].data[0].itemStyle.color).toBe(series[0].data[1].itemStyle.color);
  });

  it("renders zero bars without throwing when distribution is empty", () => {
    const option = buildDistributionChartOption([], 0, -1, unit);

    expect(option.xAxis.data).toEqual([]);
    const series = option.series as { data: unknown[] }[];
    expect(series[0].data).toEqual([]);
  });

  it("formats tooltip text including unit, count, percentage and user marker", () => {
    const option = buildDistributionChartOption(
      [
        { i: 0, bin: 50, count: 4 },
        { i: 1, bin: 100, count: 2 },
      ],
      6,
      0,
      unit,
    );

    const tooltip = option.tooltip as {
      formatter: (params: unknown) => string;
    };
    const html = tooltip.formatter([
      { dataIndex: 0, value: 4 },
    ] as never);

    expect(html).toContain("50ms");
    expect(html).toContain("count");
    expect(html).toContain("66.67");
    expect(html).toContain("userPosition");
  });

  it("returns empty tooltip string when params are missing", () => {
    const option = buildDistributionChartOption(
      [{ i: 0, bin: 50, count: 4 }],
      4,
      -1,
      unit,
    );

    const tooltip = option.tooltip as {
      formatter: (params: unknown) => string;
    };
    expect(tooltip.formatter([] as never)).toBe("");
    expect(tooltip.formatter(undefined as never)).toBe("");
  });

  it("returns empty tooltip string when bin is non-finite", () => {
    const option = buildDistributionChartOption(
      [{ i: 0, bin: Number.NaN, count: 4 }],
      4,
      -1,
      unit,
    );

    const tooltip = option.tooltip as {
      formatter: (params: unknown) => string;
    };
    expect(tooltip.formatter([{ dataIndex: 0, value: 4 }] as never)).toBe("");
  });

  it("supports memory unit strings in category labels and tooltip", () => {
    const option = buildDistributionChartOption(
      [{ i: 0, bin: 64, count: 3 }],
      3,
      0,
      "MB",
    );

    expect(option.xAxis.data).toEqual(["64MB"]);
    const tooltip = option.tooltip as {
      formatter: (params: unknown) => string;
    };
    const html = tooltip.formatter([
      { dataIndex: 0, value: 3 },
    ] as never);
    expect(html).toContain("64MB");
  });

  it("uses an interval-based label density for >8 bins to keep axis legible", () => {
    const option = buildDistributionChartOption(
      Array.from({ length: 12 }, (_, i) => ({
        i,
        bin: i * 10,
        count: 1,
      })),
      12,
      -1,
      unit,
    );

    const xAxis = option.xAxis as {
      axisLabel: { interval: number };
    };
    expect(xAxis.axisLabel.interval).toBeGreaterThan(0);
  });
});