export type InterventionDraft = {
  warningId: number;
  planText: string;
  summaryText?: string;
  needRetestFlag?: boolean;
  needTransferFlag?: boolean;
  closeSummary?: string;
};
