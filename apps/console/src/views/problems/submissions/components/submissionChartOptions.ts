import { color as echartsColor, type EChartsOption } from "echarts";

export type DistributionChartUnit = "ms" | "MB";

export interface DistributionChartPoint {
  i: number;
  bin: number;
  count: number;
}

export interface ChartCssColors {
  foreground: string;
  border: string;
  accent: string;
  mutedBar: string;
}

export interface TooltipCallbackDataParams {
  dataIndex: number;
  value: number;
  [key: string]: unknown;
}

interface Translator {
  (key: string, params?: Record<string, unknown>): string;
}

const FALLBACK_COLORS: Required<Omit<ChartCssColors, "mutedBar">> = {
  foreground: "#839496",
  border: "#586e75",
  accent: "#268bd2",
};

export function readChartCssColors(fallbacks: {
  foreground: string;
  border: string;
  accent: string;
}): ChartCssColors {
  if (typeof window === "undefined") {
    return {
      ...fallbacks,
      mutedBar: fallbacks.foreground,
    };
  }
  const computed = getComputedStyle(document.documentElement);
  const resolve = (token: string, fallback: string) => {
    const raw = computed.getPropertyValue(token).trim();
    return raw || fallback;
  };
  const foreground = resolve("--muted-foreground", fallbacks.foreground);
  const border = resolve("--border", fallbacks.border);
  const accent = resolve("--chart-series-1", fallbacks.accent);
  const mutedBar = echartsColor.modifyAlpha(foreground, 0.32) || foreground;
  return {
    foreground,
    border,
    accent,
    mutedBar,
  };
}

export const DEFAULT_CHART_FALLBACKS: {
  foreground: string;
  border: string;
  accent: string;
} = FALLBACK_COLORS;

export function buildDistributionChartOption(
  paired: DistributionChartPoint[],
  total: number,
  userIndex: number,
  unit: DistributionChartUnit,
  t: Translator = (key) => key,
): EChartsOption {
  const colors = readChartCssColors(FALLBACK_COLORS);
  const safeTotal = Number.isFinite(total) ? total : 0;

  return {
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params: unknown) => {
        if (!Array.isArray(params) || params.length === 0) return "";
        const data = params[0] as TooltipCallbackDataParams | undefined;
        if (!data) return "";
        const point = paired[data.dataIndex];
        if (!point || !Number.isFinite(point.bin)) return "";
        const count = Number.isFinite(point.count) ? point.count : 0;
        const percentage = safeTotal
          ? ((count / safeTotal) * 100).toFixed(2)
          : "0";
        const isUserPosition = data.dataIndex === userIndex;
        return `${point.bin}${unit}<br/>${t("problem.layout.count")}: ${count}<br/>${t("problem.layout.percentage")}: ${percentage}%${isUserPosition ? `<br/><span style="color: var(--chart-series-1);">${t("problem.layout.userPosition")}</span>` : ""}`;
      },
    },
    grid: {
      left: "3%",
      right: "4%",
      bottom: "15%",
      top: "15%",
      containLabel: true,
    },
    xAxis: {
      type: "category",
      data: paired.map((point) => `${point.bin}${unit}`),
      axisLabel: {
        interval: paired.length <= 8 ? 0 : Math.ceil(paired.length / 8),
        rotate: 0,
        fontSize: 10,
        color: colors.foreground,
        fontFamily:
          '"LXGW WenKai", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", monospace',
      },
      axisLine: { lineStyle: { color: colors.border } },
    },
    yAxis: {
      type: "value",
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: colors.border } },
      axisLabel: {
        fontSize: 10,
        color: colors.foreground,
        fontFamily:
          '"LXGW WenKai", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", monospace',
      },
    },
    series: [
      {
        type: "bar",
        data: paired.map((d, i) => ({
          value: Number.isFinite(d.count) ? d.count : 0,
          itemStyle: {
            color: i === userIndex ? colors.accent : colors.mutedBar,
            borderRadius: 0,
          },
        })),
        barMaxWidth: 40,
      },
    ],
  };
}

export function formatRuntime(value: number | undefined): string {
  if (!Number.isFinite(value)) return "-- ms";
  return `${Math.round(value as number)} ms`;
}

export function formatMemory(value: number | undefined): string {
  if (!Number.isFinite(value)) return "-- MB";
  const memoryMb = value as number;
  return `${memoryMb >= 100 ? Math.round(memoryMb) : memoryMb.toFixed(1)} MB`;
}

export function formatPercentile(value: number | undefined): string {
  return Number.isFinite(value) ? (value as number).toFixed(1) : "0.0";
}

export async function renderAvatarMarkPointDataUrl(
  avatarUrl: string,
  accentColor: string,
): Promise<string | null> {
  if (typeof document === "undefined") return null;
  const avatarImg = new Image();
  avatarImg.crossOrigin = "anonymous";
  await new Promise<void>((resolve, reject) => {
    avatarImg.onload = () => resolve();
    avatarImg.onerror = () => reject(new Error("avatar load failed"));
    avatarImg.src = avatarUrl;
  });
  const canvas = document.createElement("canvas");
  const ctx = canvas.getContext("2d");
  if (!ctx) return null;
  const size = 28;
  canvas.width = size;
  canvas.height = size;
  ctx.beginPath();
  ctx.arc(size / 2, size / 2, size / 2, 0, Math.PI * 2);
  ctx.closePath();
  ctx.clip();
  ctx.drawImage(avatarImg, 0, 0, size, size);
  ctx.restore();
  ctx.beginPath();
  ctx.arc(size / 2, size / 2, size / 2 - 1, 0, Math.PI * 2);
  ctx.strokeStyle = accentColor || "var(--chart-series-1)";
  ctx.lineWidth = 2;
  ctx.stroke();
  return canvas.toDataURL();
}
