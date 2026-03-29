import { useMutation } from "@tanstack/react-query";
import { isAxiosError } from "axios";
import { Alert, Button, Descriptions, Modal, Select, Space, Typography, message } from "antd";
import { useEffect, useMemo, useState } from "react";
import {
  buildExportFileName,
  downloadExportFile,
  downloadExportReport,
  exportReport,
  type DownloadedExportReport,
  type ExportFormat,
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

export function ExportReportDialog({ open, title, description, target, onClose }: Props) {
  const [exportFormat, setExportFormat] = useState<ExportFormat>("TEXT");
  const [exportResult, setExportResult] = useState<DownloadedExportReport | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      setExportFormat("TEXT");
      setExportResult(null);
      setExportError(null);
    }
  }, [open]);

  useEffect(() => {
    if (open && target) {
      setExportResult(null);
      setExportError(null);
    }
  }, [open, target]);

  const targetText = useMemo(() => {
    if (!target) {
      return "当前没有可导出的报告目标";
    }
    if (target.reportId && target.resultId) {
      return `报告编号 ${target.reportId}，结果编号 ${target.resultId}`;
    }
    if (target.reportId) {
      return `报告编号 ${target.reportId}`;
    }
    if (target.resultId) {
      return `结果编号 ${target.resultId}`;
    }
    return "当前没有可导出的报告目标";
  }, [target]);

  const previewFileName = useMemo(() => {
    if (!target) {
      return "-";
    }
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
      try {
        return await downloadExportReport({
          reportId: target.reportId,
          resultId: target.resultId,
          exportFormat
        });
      } catch (error) {
        const fallback = await exportReport({
          reportId: target.reportId,
          resultId: target.resultId,
          exportFormat
        });
        return {
          exportId: fallback.exportId,
          fileName: fallback.fileName,
          exportFormat: fallback.exportFormat,
          downloadExtension: fallback.downloadExtension,
          contentType: fallback.contentType,
          generatedAt: fallback.generatedAt,
          reportId: fallback.reportId,
          resultId: fallback.resultId,
          blob: new Blob([
            fallback.content
          ], { type: fallback.contentType || "application/octet-stream" })
        } satisfies DownloadedExportReport;
      }
    },
    onSuccess: (data) => {
      setExportResult(data);
      setExportError(null);
      downloadExportFile(data);
      message.success(`导出成功，已开始下载 ${buildExportFileName(data)}`);
    },
    onError: (error: unknown) => {
      const fallbackMessage = "导出失败，请稍后重试";
      const messageText = isAxiosError(error)
        ? (error.response?.data?.message as string | undefined) || error.message || fallbackMessage
        : error instanceof Error && error.message
          ? error.message
          : fallbackMessage;
      setExportError(messageText);
      message.error(messageText);
    }
  });

  const handleExport = async () => {
    await exportMutation.mutateAsync();
  };

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
        exportResult ? (
          <Button key="redownload" type="primary" onClick={() => downloadExportFile(exportResult)}>
            重新下载
          </Button>
        ) : (
          <Button
            key="export"
            type="primary"
            loading={exportMutation.isPending}
            onClick={() => void handleExport()}
            disabled={!target || (!target.reportId && !target.resultId)}
          >
            开始导出
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
                { label: "文本导出", value: "TEXT" },
                { label: "PDF 导出", value: "PDF" }
              ]}
              onChange={(value) => setExportFormat(value as ExportFormat)}
              disabled={exportMutation.isPending || Boolean(exportResult)}
            />
          </Descriptions.Item>
          <Descriptions.Item label="建议文件名">{previewFileName}</Descriptions.Item>
        </Descriptions>
        {exportFormat === "PDF" ? (
          <Alert
            type="success"
            showIcon
            message="PDF 导出已优先走文件下载流"
            description="前端会优先调用正式下载接口，旧的 JSON 导出仅作为兼容回退。"
          />
        ) : null}
        {exportError ? <Alert type="error" showIcon message="导出失败" description={exportError} /> : null}
        {exportResult ? (
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Alert type="success" showIcon message="导出完成，文件已下载到本地" />
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="导出编号">{exportResult.exportId}</Descriptions.Item>
              <Descriptions.Item label="文件名">{buildExportFileName(exportResult)}</Descriptions.Item>
              <Descriptions.Item label="导出格式">{formatLabel(exportResult.exportFormat as ExportFormat)}</Descriptions.Item>
              <Descriptions.Item label="Content-Type">{exportResult.contentType}</Descriptions.Item>
              <Descriptions.Item label="Generated At">{exportResult.generatedAt}</Descriptions.Item>
              <Descriptions.Item label="报告编号">{exportResult.reportId}</Descriptions.Item>
              <Descriptions.Item label="结果编号">{exportResult.resultId}</Descriptions.Item>
            </Descriptions>
          </Space>
        ) : null}
      </Space>
    </Modal>
  );
}
