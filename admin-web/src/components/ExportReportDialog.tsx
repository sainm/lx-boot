import { useMutation } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { Alert, Button, Descriptions, Modal, Progress, Select, Space, Typography, message } from "antd";
import { useEffect, useRef, useMemo, useState } from "react";
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
  type ExportReportResponse,
  type ExportTarget
} from "../features/exports/api";

type Props = {
  open: boolean;
  title: string;
  description: string;
  target: ExportTarget | null;
  onClose: () => void;
};

function formatLabel(format: ExportFormat) {
  return format === "PDF" ? "PDF" : "文本";
}

const POLL_INTERVAL_MS = 2000;

export function ExportReportDialog({ open, title, description, target, onClose }: Props) {
  const [exportFormat, setExportFormat] = useState<ExportFormat>("TEXT");
  const [exportResult, setExportResult] = useState<DownloadedExportReport | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);

  // Async job state
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

  // Poll async job until DONE or FAILED
  useEffect(() => {
    if (!jobId || jobStatus === "DONE" || jobStatus === "FAILED") return;

    const poll = async () => {
      try {
        const status = await pollExportJobStatus(jobId);
        setJobStatus(status.status);
        if (status.fileName) setJobFileName(status.fileName);
        if (status.contentType) setJobContentType(status.contentType);
        if (status.status === "DONE") {
          await downloadExportJobFile(jobId, status.fileName ?? "export", status.contentType ?? "application/octet-stream");
          void message.success(`导出完成，已开始下载 ${status.fileName ?? "export"}`);
        } else if (status.status === "FAILED") {
          setExportError(status.error ?? "导出失败，请稍后重试");
          void message.error(status.error ?? "导出失败");
        } else {
          pollTimerRef.current = setTimeout(() => void poll(), POLL_INTERVAL_MS);
        }
      } catch {
        setExportError("导出状态查询失败，请稍后重试");
        setJobStatus("FAILED");
      }
    };

    pollTimerRef.current = setTimeout(() => void poll(), POLL_INTERVAL_MS);
    return clearPoll;
  }, [jobId, jobStatus]);

  const targetText = useMemo(() => {
    if (!target) return "当前没有可导出的报告目标";
    if (target.reportId && target.resultId) return `报告编号 ${target.reportId}，结果编号 ${target.resultId}`;
    if (target.reportId) return `报告编号 ${target.reportId}`;
    if (target.resultId) return `结果编号 ${target.resultId}`;
    return "当前没有可导出的报告目标";
  }, [target]);

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
        throw new Error("当前没有可导出的报告目标");
      }
      // Use async job mode for PDF; sync download mode for TEXT
      if (exportFormat === "PDF") {
        const job = await submitExportJob({ reportId: target.reportId, resultId: target.resultId, exportFormat });
        setJobId(job.jobId);
        setJobStatus(job.status);
        return null;
      }
      try {
        return await downloadExportReport({ reportId: target.reportId, resultId: target.resultId, exportFormat });
      } catch (error) {
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
      if (!data) return; // async PDF job submitted, polling handles the rest
      setExportResult(data);
      setExportError(null);
      downloadExportFile(data);
      void message.success(`导出成功，已开始下载 ${buildExportFileName(data)}`);
    },
    onError: (error: unknown) => {
      const fallbackMessage = "导出失败，请稍后重试";
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
          关闭
        </Button>,
        isDone && exportResult ? (
          <Button key="redownload" type="primary" onClick={() => downloadExportFile(exportResult)}>
            重新下载
          </Button>
        ) : isDone && jobStatus === "DONE" && jobFileName ? (
          <Button
            key="redownload"
            type="primary"
            onClick={() => void downloadExportJobFile(jobId!, jobFileName, jobContentType ?? "application/octet-stream")}
          >
            重新下载
          </Button>
        ) : (
          <Button
            key="export"
            type="primary"
            loading={isPending}
            onClick={() => void exportMutation.mutateAsync()}
            disabled={!target || (!target.reportId && !target.resultId)}
          >
            {isAsyncPdf ? "提交导出任务" : "开始导出"}
          </Button>
        )
      ]}
      destroyOnClose
    >
      <Space direction="vertical" size={16} style={{ width: "100%" }}>
        <Typography.Text type="secondary">{description}</Typography.Text>
        <Descriptions bordered column={1} size="small">
          <Descriptions.Item label="导出目标">{targetText}</Descriptions.Item>
          <Descriptions.Item label="导出格式">
            <Select
              value={exportFormat}
              style={{ width: 220 }}
              options={[
                { label: "文本导出（即时）", value: "TEXT" },
                { label: "PDF 导出（后台任务）", value: "PDF" }
              ]}
              onChange={(value) => setExportFormat(value as ExportFormat)}
              disabled={isPending || isDone}
            />
          </Descriptions.Item>
          <Descriptions.Item label="建议文件名">{previewFileName}</Descriptions.Item>
        </Descriptions>

        {isAsyncPdf && !jobId ? (
          <Alert
            type="info"
            showIcon
            message="PDF 导出使用后台任务模式"
            description="点击「提交导出任务」后，系统将在后台生成 PDF，完成后自动下载。"
          />
        ) : null}

        {isAsyncPdf && jobId && (jobStatus === "PENDING" || jobStatus === "PROCESSING") ? (
          <Space direction="vertical" size={8} style={{ width: "100%" }}>
            <Typography.Text type="secondary">
              {jobStatus === "PENDING" ? "任务已提交，等待处理..." : "正在生成 PDF，请稍候..."}
            </Typography.Text>
            <Progress percent={progressPercent} status="active" />
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              任务编号：{jobId}
            </Typography.Text>
          </Space>
        ) : null}

        {exportError ? <Alert type="error" showIcon message="导出失败" description={exportError} /> : null}

        {isDone && exportResult ? (
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Alert type="success" showIcon message="导出完成，文件已下载到本地" />
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="导出编号">{exportResult.exportId}</Descriptions.Item>
              <Descriptions.Item label="文件名">{buildExportFileName(exportResult)}</Descriptions.Item>
              <Descriptions.Item label="导出格式">{formatLabel(exportResult.exportFormat as ExportFormat)}</Descriptions.Item>
              <Descriptions.Item label="Generated At">{exportResult.generatedAt}</Descriptions.Item>
            </Descriptions>
          </Space>
        ) : isDone && jobStatus === "DONE" ? (
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Alert type="success" showIcon message="PDF 导出完成，文件已下载到本地" />
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="任务编号">{jobId}</Descriptions.Item>
              <Descriptions.Item label="文件名">{jobFileName ?? "-"}</Descriptions.Item>
            </Descriptions>
          </Space>
        ) : null}
      </Space>
    </Modal>
  );
}
