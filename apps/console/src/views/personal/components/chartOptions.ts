import { graphic, type EChartsOption } from "echarts";
import { SOLARIZED_PALETTE } from "@ulticode/design-system";

export function createAcceptedAreaGradient(
  color: string = SOLARIZED_PALETTE.green,
): graphic.LinearGradient {
  return new graphic.LinearGradient(0, 0, 0, 1, [
    { offset: 0, color },
    { offset: 1, color },
  ]);
}

export function withSafeChartAnimation(option: EChartsOption): EChartsOption {
  return {
    ...option,
    animation: false,
  };
}
