import ReactECharts from "echarts-for-react";
import { Card, Empty, Space } from "antd";
import type { EChartsOption } from "echarts";
import type { ReportVisualization } from "../features/visualizations/types";

type ChartRendererProps = {
  visualizations?: ReportVisualization[];
  emptyText: string;
  chartHeight?: number;
};

export type BarChartItem = {
  key: string;
  label: string;
  value: number;
  color?: string;
  suffix?: string;
  meta?: string;
};

export type SegmentItem = {
  key: string;
  label: string;
  value: number;
  color?: string;
};

const riskColors: Record<string, string> = {
  CRITICAL: "#991b1b",
  HIGH: "#dc2626",
  HIGH_RISK_ITEM: "#991b1b",
  ATTENTION: "#d97706",
  MEDIUM: "#d97706",
  MODERATE: "#d97706",
  LOW: "#16a34a",
  NORMAL: "#16a34a"
};

export function ChartRenderer({ visualizations = [], emptyText, chartHeight }: ChartRendererProps) {
  const enabled = visualizations.filter((item) => item.dataSets.some((set) => set.points.length > 0));
  if (enabled.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />;
  }
  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      {enabled.map((visualization, index) => (
        <Card key={visualization.configId ?? `${visualization.chartType}-${index}`} title={visualization.chartTitle} size="small">
          <ReactECharts option={toChartOption(visualization)} style={{ height: chartHeight ?? defaultChartHeight(visualization.chartType) }} notMerge lazyUpdate />
        </Card>
      ))}
    </Space>
  );
}

export function scoreRiskColor(riskLevel: string) {
  return riskColors[riskLevel] ?? "#64748b";
}

export function HorizontalBarChart({ items, emptyText, maxValue }: { items: BarChartItem[]; emptyText: string; maxValue?: number }) {
  const points = items.filter((item) => Number.isFinite(item.value));
  if (points.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />;
  }
  const max = Math.max(maxValue ?? 0, ...points.map((item) => item.value), 1);
  return (
    <ReactECharts
      style={{ height: 260 }}
      option={{
        tooltip: { trigger: "axis" },
        grid: { left: 96, right: 24, top: 16, bottom: 24 },
        xAxis: { type: "value", max },
        yAxis: { type: "category", data: points.map((item) => item.label), axisLabel: { width: 86, overflow: "truncate" } },
        series: [{ type: "bar", data: points.map((item) => ({ value: item.value, itemStyle: { color: item.color ?? "#2563eb" } })), barMaxWidth: 20 }]
      }}
      notMerge
      lazyUpdate
    />
  );
}

export function SegmentedRiskBar({ items, emptyText }: { items: SegmentItem[]; emptyText: string }) {
  const points = items.filter((item) => item.value > 0);
  if (points.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />;
  }
  return (
    <ReactECharts
      style={{ height: 260 }}
      option={{
        tooltip: { trigger: "item" },
        legend: { bottom: 0 },
        series: [
          {
            type: "pie",
            radius: ["42%", "68%"],
            data: points.map((item) => ({
              name: item.label,
              value: item.value,
              itemStyle: { color: item.color ?? scoreRiskColor(item.key) }
            }))
          }
        ]
      }}
      notMerge
      lazyUpdate
    />
  );
}

function toChartOption(visualization: ReportVisualization): EChartsOption {
  switch (visualization.chartType) {
    case "RADAR":
      return radarOption(visualization);
    case "DIMENSION_BAR":
    case "GROUP_COMPLETION_BAR":
    case "GROUP_SCORE_RANKING":
      return barOption(visualization);
    case "ANSWER_SCORE_DISTRIBUTION":
      return lineBarOption(visualization);
    case "RISK_CUE":
    case "GROUP_RISK_STACK":
      return pieOption(visualization);
    case "NORM_COMPARE":
      return groupedBarOption(visualization);
    case "GROUP_DIMENSION_HEATMAP":
      return heatmapLikeBarOption(visualization);
    default:
      return barOption(visualization);
  }
}

function radarOption(visualization: ReportVisualization): EChartsOption {
  const points = firstPoints(visualization);
  const max = Math.max(1, ...points.map((point) => point.value ?? 0));
  return {
    tooltip: {},
    radar: {
      radius: "62%",
      indicator: points.map((point) => ({ name: point.label, max }))
    },
    series: [
      {
        type: "radar",
        areaStyle: { opacity: 0.18 },
        data: [{ value: points.map((point) => point.value ?? 0), name: visualization.chartTitle }]
      }
    ]
  };
}

function barOption(visualization: ReportVisualization): EChartsOption {
  const points = sortedPoints(firstPoints(visualization), visualization.configJson);
  return {
    tooltip: { trigger: "axis" },
    grid: { left: 96, right: 24, top: 16, bottom: 24 },
    xAxis: { type: "value" },
    yAxis: { type: "category", data: points.map((point) => point.label), axisLabel: { width: 86, overflow: "truncate" } },
    series: [
      {
        type: "bar",
        data: points.map((point) => ({
          value: point.value ?? 0,
          itemStyle: { color: scoreRiskColor(point.series ?? point.key) }
        })),
        barMaxWidth: 20
      }
    ]
  };
}

function lineBarOption(visualization: ReportVisualization): EChartsOption {
  const points = firstPoints(visualization);
  return {
    tooltip: { trigger: "axis" },
    grid: { left: 40, right: 20, top: 20, bottom: 32 },
    xAxis: { type: "category", data: points.map((point) => point.label) },
    yAxis: { type: "value" },
    series: [{ type: "bar", data: points.map((point) => point.value ?? 0), barMaxWidth: 28, itemStyle: { color: "#2563eb" } }]
  };
}

function pieOption(visualization: ReportVisualization): EChartsOption {
  const points = firstPoints(visualization);
  return {
    tooltip: { trigger: "item" },
    legend: { bottom: 0 },
    series: [
      {
        type: "pie",
        radius: ["42%", "68%"],
        data: points.map((point) => ({
          name: point.label,
          value: point.value ?? 0,
          itemStyle: { color: scoreRiskColor(point.series ?? point.key) }
        }))
      }
    ]
  };
}

function groupedBarOption(visualization: ReportVisualization): EChartsOption {
  const points = firstPoints(visualization);
  const labels = Array.from(new Set(points.map((point) => point.label)));
  const seriesNames = Array.from(new Set(points.map((point) => point.series ?? "VALUE")));
  return {
    tooltip: { trigger: "axis" },
    legend: { top: 0 },
    grid: { left: 44, right: 20, top: 42, bottom: 48 },
    xAxis: { type: "category", data: labels, axisLabel: { rotate: labels.length > 6 ? 28 : 0 } },
    yAxis: { type: "value" },
    series: seriesNames.map((name) => ({
      name,
      type: "bar",
      data: labels.map((label) => points.find((point) => point.label === label && (point.series ?? "VALUE") === name)?.value ?? 0)
    }))
  };
}

function heatmapLikeBarOption(visualization: ReportVisualization): EChartsOption {
  const points = firstPoints(visualization);
  return {
    tooltip: { trigger: "axis" },
    visualMap: { min: 0, max: Math.max(1, ...points.map((point) => point.value ?? 0)), show: false, inRange: { color: ["#dbeafe", "#2563eb"] } },
    grid: { left: 96, right: 24, top: 16, bottom: 24 },
    xAxis: { type: "value" },
    yAxis: { type: "category", data: points.map((point) => point.label), axisLabel: { width: 86, overflow: "truncate" } },
    series: [{ type: "bar", data: points.map((point) => point.value ?? 0), barMaxWidth: 18 }]
  };
}

function firstPoints(visualization: ReportVisualization) {
  return visualization.dataSets[0]?.points ?? [];
}

function sortedPoints(points: ReturnType<typeof firstPoints>, configJson: string) {
  const config = parseConfig(configJson);
  if (config.sort === "desc") {
    return [...points].sort((left, right) => (right.value ?? 0) - (left.value ?? 0));
  }
  if (config.sort === "asc") {
    return [...points].sort((left, right) => (left.value ?? 0) - (right.value ?? 0));
  }
  return points;
}

function parseConfig(configJson: string): Record<string, string> {
  try {
    return JSON.parse(configJson || "{}") as Record<string, string>;
  } catch {
    return {};
  }
}

function defaultChartHeight(chartType: string) {
  return chartType === "RADAR" || chartType === "NORM_COMPARE" ? 320 : 260;
}
