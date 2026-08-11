export type InterventionDraft = {
  warningId: number;
  planText: string;
  summaryText?: string;
  needRetestFlag?: boolean;
  needTransferFlag?: boolean;
  closeSummary?: string;
  contactChannel?: string;
  contactOutcome?: string;
  safetyAssessmentSummary?: string;
  imminentDangerFlag?: boolean;
  responsibleHandoffSummary?: string;
  followUpDueTime?: { format: (pattern: string) => string };
};
