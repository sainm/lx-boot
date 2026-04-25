export type ChartPoint = {
  key: string;
  label: string;
  value?: number | null;
  series?: string | null;
  group?: string | null;
};

export type ChartDataSet = {
  name: string;
  points: ChartPoint[];
};

export type ReportVisualization = {
  configId?: number | null;
  chartType: string;
  chartTitle: string;
  viewScope: string;
  dataSource: string;
  configJson: string;
  dataSets: ChartDataSet[];
};

export type ScaleVisualizationConfig = {
  id: number;
  scaleId: number;
  chartType: string;
  chartTitle: string;
  viewScope: string;
  dataSource: string;
  configJson: string;
  enabled: boolean;
  sortNo: number;
};

export type ScaleVisualizationConfigDraft = {
  chartType: string;
  chartTitle: string;
  viewScope: string;
  dataSource: string;
  configJson?: string;
  enabled?: boolean;
  sortNo?: number;
};
