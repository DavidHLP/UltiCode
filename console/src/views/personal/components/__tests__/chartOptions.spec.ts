import { color } from "echarts";
import { describe, expect, it } from "vitest";
import {
  createAcceptedAreaGradient,
  withSafeChartAnimation,
} from "../chartOptions";

describe("withSafeChartAnimation", () => {
  it("disables ECharts animation to avoid array interpolation crashes", () => {
    expect(withSafeChartAnimation({ series: [] })).toEqual({
      animation: false,
      series: [],
    });
  });

  it("uses gradient colors that ECharts can parse", () => {
    const gradient = createAcceptedAreaGradient();

    expect(gradient.colorStops).toHaveLength(2);
    gradient.colorStops.forEach((stop) => {
      expect(color.parse(stop.color)).toBeDefined();
    });
  });
});
