import { graphic, type EChartsOption } from "echarts";

export function createAcceptedAreaGradient(): graphic.LinearGradient {
  return new graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color: "rgba(133, 153, 0, 0.3)" },
    { offset: 1, color: "rgba(133, 153, 0, 0.05)" },
  ]);
}

export function withSafeChartAnimation(option: EChartsOption): EChartsOption {
  return {
    ...option,
    animation: false,
  };
}
