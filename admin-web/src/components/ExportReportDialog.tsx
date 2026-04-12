import { useMutation } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { Alert, Button, Descriptions, Modal, Progress, Select, Space, Typography, message } from "antd";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  buildExportFileName,
  downloadExportFile,
  downloadExportJobFile,
  downloadExportReport,
  exportReport,
  pollExportJobStatus,
  submitExportJob,
  type DownloadedExportReport,
  type ExportFormat,
  type ExportJobStatus,
  type ExportTarget
} from "../features/exports/api";
import { useI18n } from "../i18n/provider";

type Props = {
  open: boolean;
  title: string;
  description: string;
  target: ExportTarget | null;
  onClose: () => void;
};

function formatLabel(format: ExportFormat, t: (key: string) => string) {
  return format === "PDF" ? "PDF" : t("export.formatText");
}

const POLL_INTERVAL_MS = 2000;

export function ExportReportDialog({ open, title, description, target, onClose }: Props) {
  const { t } = useI18n();
  const [exportFormat, setExportFormat] = useState<ExportFormat>("TEXT");
  const [exportResult, setExportResult] = useState<DownloadedExportReport | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);
  const [jobId, setJobId] = useState<string | null>(null);
  const [jobStatus, setJobStatus] = useState<ExportJobStatus | null>(null);
  const [jobFileName, setJobFileName] = useState<string | null>(null);
  const [jobContentType, setJobContentType] = useState<string | null>(null);
  const pollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const clearPoll = () => {
    if (pollTimerRef.current !== null) {
      clearTimeout(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  };

  useEffect(() => {
    if (!open) {
      clearPoll();
      setExportFormat("TEXT");
      setExportResult(null);
      setExportError(null);
      setJobId(null);
      setJobStatus(null);
      setJobFileName(null);
      setJobContentType(null);
    }
  }, [open]);

  useEffect(() => {
    if (open && target) {
      clearPoll();
      setExportResult(null);
      setExportError(null);
      setJobId(null);
      setJobStatus(null);
    }
  }, [open, target]);

  useEffect(() => {
    if (!jobId || jobStatus === "DONE" || jobStatus === "FAILED") return;

    const poll = async () => {
      try {
        const status = await pollExportJobStatus(jobId);
        setJobStatus(status.status);
        if (status.fileName) setJobFileName(status.fileName);
        if (status.contentType) setJobContentType(status.contentType);
        if (status.status === "DONE") {
          const fileName = status.fileName ?? "export";
          await downloadExportJobFile(jobId, fileName, status.contentType ?? "application/octet-stream");
          void message.success(t("export.downloadComplete", { fileName }));
        } else if (status.status === "FAILED") {
          setExportError(status.error ?? t("export.failedRetry"));
          void message.error(status.error ?? t("export.failed"));
        } else {
          pollTimerRef.current = setTimeout(() => void poll(), POLL_INTERVAL_MS);
        }
      } catch {
        setExportError(t("export.statusFailed"));
        setJobStatus("FAILED");
      }
    };

    pollTimerRef.current = setTimeout(() => void poll(), POLL_INTERVAL_MS);
    return clearPoll;
  }, [jobId, jobStatus, t]);

  const targetText = useMemo(() => {
    if (!target) return t("export.noTarget");
    if (target.reportId && target.resultId) {
      return t("export.targetBoth", { reportId: target.reportId, resultId: target.resultId });
    }
    if (target.reportId) return t("export.targetReport", { reportId: target.reportId });
    if (target.resultId) return t("export.targetResult", { resultId: target.resultId });
    return t("export.noTarget");
  }, [target, t]);

  const previewFileName = useMemo(() => {
    if (!target) return "-";
    return buildExportFileName({
      fileName: "",
      downloadExtension: exportFormat === "PDF" ? "pdf" : "txt",
      reportId: target.reportId ?? target.resultId ?? 0,
      resultId: target.resultId ?? target.reportId ?? 0,
      exportFormat
    });
  }, [exportFormat, target]);

  const exportMutation = useMutation({
    mutationFn: async () => {
      if (!target || (!target.reportId && !target.resultId)) {
        throw new Error(t("export.noTarget"));
      }
      if (exportFormat === "PDF") {
        const job = await submitExportJob({ reportId: target.reportId, resultId: target.resultId, exportFormat });
        setJobId(job.jobId);
        setJobStatus(job.status);
        return null;
      }
      try {
        return await downloadExportReport({ reportId: target.reportId, resultId: target.resultId, exportFormat });
      } catch {
        const fallback = await exportReport({ reportId: target.reportId, resultId: target.resultId, exportFormat });
        return {
          exportId: fallback.exportId,
          fileName: fallback.fileName,
          exportFormat: fallback.exportFormat,
          downloadExtension: fallback.downloadExtension,
          contentType: fallback.contentType,
          generatedAt: fallback.generatedAt,
          reportId: fallback.reportId,
          resultId: fallback.resultId,
          blob: new Blob([fallback.content], { type: fallback.contentType || "application/octet-stream" })
        } satisfies DownloadedExportReport;
      }
    },
    onSuccess: (data) => {
      if (!data) return;
      setExportResult(data);
      setExportError(null);
      downloadExportFile(data);
      void message.success(t("export.successDownload", { fileName: buildExportFileName(data) }));
    },
    onError: (error: unknown) => {
      const fallbackMessage = t("export.failedRetry");
      const messageText = isAxiosError(error)
        ? (error.response?.data?.message as string | undefined) || error.message || fallbackMessage
        : error instanceof Error && error.message
          ? error.message
          : fallbackMessage;
      setExportError(messageText);
      void message.error(messageText);
    }
  });

  const isAsyncPdf = exportFormat === "PDF";
  const isPending = exportMutation.isPending || (isAsyncPdf && jobStatus === "PENDING") || (isAsyncPdf && jobStatus === "PROCESSING");
  const isDone = exportResult !== null || jobStatus === "DONE";

  const progressPercent =
    jobStatus === "PENDING" ? 15
    : jobStatus === "PROCESSING" ? 55
    : jobStatus === "DONE" ? 100
    : 0;

  return (
    <Modal
      title={title}
      open={open}
      onCancel={onClose}
      width={780}
      footer={[
        <Button key="close" onClick={onClose}>
          {t("export.close")}
        </Button>,
        isDone && exportResult ? (
          <Button key="redownload" type="primary" onClick={() => downloadExportFile(exportResult)}>
            {t("export.redownload")}
          </Button>
        ) : isDone && jobStatus === "DONE" && jobFileName ? (
          <Button
            key="redownload"
            type="primary"
            onClick={() => void downloadExportJobFile(jobId!, jobFileName, jobContentType ?? "application/octet-stream")}
          >
            {t("export.redownload")}
          </Button>
        ) : (
          <Button
            key="export"
            type="primary"
            loading={isPending}
            onClick={() => void exportMutation.mutateAsync()}
            disabled={!target || (!target.reportId && !target.resultId)}
          >
            {isAsyncPdf ? t("export.submitJob") : t("export.start")}
          </Button>
        )
      ]}
      destroyOnClose
    >
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <Typography.Text type="secondary">{description}</Typography.Text>
        <Descriptions bordered column={1} size="small">
          <Descriptions.Item label={t("export.target")}>{targetText}</Descriptions.Item>
          <Descriptions.Item label={t("export.format")}>
            <Select
              value={exportFormat}
              style={{ width: 220 }}
              options={[
                { label: t("export.textOption"), value: "TEXT" },
                { label: t("export.pdfOption"), value: "PDF" }
              ]}
              onChange={(value) => setExportFormat(value as ExportFormat)}
              disabled={isPending || isDone}
            />
          </Descriptions.Item>
          <Descriptions.Item label={t("export.fileName")}>{previewFileName}</Descriptions.Item>
        </Descriptions>

        {isAsyncPdf && !jobId ? (
          <Alert
            type="info"
            showIcon
            message={t("export.pdfInfoTitle")}
            description={t("export.pdfInfoDesc")}
          />
        ) : null}

        {isAsyncPdf && jobId && (jobStatus === "PENDING" || jobStatus === "PROCESSING") ? (
          <Space direction="vertical" size={8} style={{ width: "100%" }}>
            <Typography.Text type="secondary">
              {jobStatus === "PENDING" ? t("export.jobPending") : t("export.jobProcessing")}
            </Typography.Text>
            <Progress percent={progressPercent} status="active" />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {t("export.jobId", { jobId })}
            </Typography.Text>
          </Space>
        ) : null}

        {exportError ? <Alert type="error" showIcon message={t("export.failed")} description={exportError} /> : null}

        {isDone && exportResult ? (
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Alert type="success" showIcon message={t("export.completedLocal")} />
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label={t("export.exportId")}>{exportResult.exportId}</Descriptions.Item>
              <Descriptions.Item label={t("export.fileName")}>{buildExportFileName(exportResult)}</Descriptions.Item>
              <Descriptions.Item label={t("export.format")}>
                {formatLabel(exportResult.exportFormat as ExportFormat, t)}
              </Descriptions.Item>
              <Descriptions.Item label={t("export.generatedAt")}>{exportResult.generatedAt}</Descriptions.Item>
            </Descriptions>
          </Space>
        ) : isDone && jobStatus === "DONE" ? (
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Alert type="success" showIcon message={t("export.pdfCompletedLocal")} />
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label={t("export.jobId", { jobId })}>{jobId}</Descriptions.Item>
              <Descriptions.Item label={t("export.fileName")}>{jobFileName ?? "-"}</Descriptions.Item>
            </Descriptions>
          </Space>
        ) : null}
      </Space>
    </Modal>
  );
}
