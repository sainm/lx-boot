import { Empty, Space, Tag, Typography } from "antd";

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

type HorizontalBarChartProps = {
  items: BarChartItem[];
  emptyText: string;
  maxValue?: number;
};

type SegmentedRiskBarProps = {
  items: SegmentItem[];
  emptyText: string;
};

type DimensionRadarChartProps = {
  items: BarChartItem[];
  emptyText: string;
  maxValue?: number;
};

const palette = ["#2563eb", "#0891b2", "#16a34a", "#ca8a04", "#dc2626", "#7c3aed", "#db2777", "#475569"];

export function HorizontalBarChart({ items, emptyText, maxValue }: HorizontalBarChartProps) {
  const visibleItems = items.filter((item) => Number.isFinite(item.value));
  const max = Math.max(1, maxValue ?? 0, ...visibleItems.map((item) => item.value));

  if (visibleItems.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />;
  }

  return (
    <Space direction="vertical" size={10} style={{ width: "100%" }}>
      {visibleItems.map((item, index) => {
        const percent = Math.max(3, Math.min(100, (item.value / max) * 100));
        return (
          <div className="report-bar-row" key={item.key}>
            <div className="report-bar-meta">
              <Typography.Text ellipsis title={item.label}>{item.label}</Typography.Text>
              <Typography.Text type="secondary">
                {formatChartNumber(item.value)}
                {item.suffix ?? ""}
              </Typography.Text>
            </div>
            <div className="report-bar-track">
              <div
                className="report-bar-fill"
                style={{
                  width: `${percent}%`,
                  background: item.color ?? palette[index % palette.length]
                }}
              />
            </div>
            {item.meta ? <Typography.Text type="secondary" className="report-chart-meta-text">{item.meta}</Typography.Text> : null}
          </div>
        );
      })}
    </Space>
  );
}

export function SegmentedRiskBar({ items, emptyText }: SegmentedRiskBarProps) {
  const visibleItems = items.filter((item) => item.value > 0);
  const total = visibleItems.reduce((sum, item) => sum + item.value, 0);

  if (total === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />;
  }

  return (
    <Space direction="vertical" size={12} style={{ width: "100%" }}>
      <div className="report-risk-track">
        {visibleItems.map((item, index) => (
          <div
            key={item.key}
            className="report-risk-segment"
            style={{
              width: `${(item.value / total) * 100}%`,
              background: item.color ?? palette[index % palette.length]
            }}
            title={`${item.label}: ${item.value}`}
          />
        ))}
      </div>
      <Space wrap size={[8, 8]}>
        {visibleItems.map((item, index) => (
          <Tag key={item.key} color={item.color ?? palette[index % palette.length]}>
            {item.label}: {item.value}
          </Tag>
        ))}
      </Space>
    </Space>
  );
}

export function DimensionRadarChart({ items, emptyText, maxValue }: DimensionRadarChartProps) {
  const visibleItems = items.filter((item) => Number.isFinite(item.value)).slice(0, 10);
  const max = Math.max(1, maxValue ?? 0, ...visibleItems.map((item) => item.value));
  const size = 220;
  const center = size / 2;
  const radius = 78;
  const rings = [0.25, 0.5, 0.75, 1];

  if (visibleItems.length < 3) {
    return <HorizontalBarChart items={visibleItems} emptyText={emptyText} maxValue={max} />;
  }

  const axisPoints = visibleItems.map((_, index) => pointOnCircle(center, radius, index, visibleItems.length));
  const valuePoints = visibleItems.map((item, index) =>
    pointOnCircle(center, radius * Math.min(1, item.value / max), index, visibleItems.length)
  );
  const polygon = valuePoints.map((point) => `${point.x},${point.y}`).join(" ");

  return (
    <div className="report-radar-wrap">
      <svg className="report-radar" viewBox={`0 0 ${size} ${size}`} role="img">
        {rings.map((ring) => {
          const points = visibleItems
            .map((_, index) => pointOnCircle(center, radius * ring, index, visibleItems.length))
            .map((point) => `${point.x},${point.y}`)
            .join(" ");
          return <polygon key={ring} points={points} className="report-radar-ring" />;
        })}
        {axisPoints.map((point, index) => (
          <line key={visibleItems[index].key} x1={center} y1={center} x2={point.x} y2={point.y} className="report-radar-axis" />
        ))}
        <polygon points={polygon} className="report-radar-area" />
        {valuePoints.map((point, index) => (
          <circle key={visibleItems[index].key} cx={point.x} cy={point.y} r={3.5} className="report-radar-dot" />
        ))}
      </svg>
      <Space direction="vertical" size={6} className="report-radar-legend">
        {visibleItems.map((item) => (
          <div key={item.key} className="report-radar-legend-row">
            <Typography.Text ellipsis title={item.label}>{item.label}</Typography.Text>
            <Typography.Text strong>{formatChartNumber(item.value)}</Typography.Text>
          </div>
        ))}
      </Space>
    </div>
  );
}

export function scoreRiskColor(riskLevel: string) {
  switch (riskLevel) {
    case "CRITICAL":
      return "#991b1b";
    case "HIGH":
      return "#dc2626";
    case "ATTENTION":
    case "MEDIUM":
    case "MODERATE":
      return "#d97706";
    case "LOW":
    case "NORMAL":
      return "#16a34a";
    default:
      return "#64748b";
  }
}

function pointOnCircle(center: number, radius: number, index: number, total: number) {
  const angle = (Math.PI * 2 * index) / total - Math.PI / 2;
  return {
    x: center + Math.cos(angle) * radius,
    y: center + Math.sin(angle) * radius
  };
}

function formatChartNumber(value: number) {
  return Number.isInteger(value) ? String(value) : value.toFixed(2);
}
