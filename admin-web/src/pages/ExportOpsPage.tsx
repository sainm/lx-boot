import { useMutation, useQuery } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Form,
  Grid,
  Input,
  Row,
  Segmented,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  message
} from "antd";
import { DownloadOutlined, ReloadOutlined, SearchOutlined, SyncOutlined } from "@ant-design/icons";
import { useMemo, useState } from "react";
import {
  downloadExportJobFile,
  fetchExportArtifactStorageInfo,
  fetchRecentExportJobs,
  pollExportJobStatus,
  retryExportJob,
  type ExportArtifactStorageInfoResponse,
  type ExportJobStatusFilter,
  type ExportJobStatusResponse
} from "../features/exports/api";
import { useI18n } from "../i18n/provider";

function statusTagColor(status?: string | null) {
  switch (status) {
    case "DONE":
      return "success";
    case "FAILED":
      return "error";
    case "PROCESSING":
      return "processing";
    default:
      return "default";
  }
}

function formatStorageSummary(storageInfo?: ExportArtifactStorageInfoResponse | null) {
  if (!storageInfo) {
    return "-";
  }
  if (!storageInfo.fileStorageEnabled) {
    return "DB";
  }
  return storageInfo.mode;
}

export function ExportOpsPage() {
  const { t } = useI18n();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [lookupForm] = Form.useForm<{ jobId: string }>();
  const [activeJobId, setActiveJobId] = useState("");
  const [statusFilter, setStatusFilter] = useState<ExportJobStatusFilter>("ALL");

  const storageInfoQuery = useQuery({
    queryKey: ["exports", "storage-info"],
    queryFn: fetchExportArtifactStorageInfo
  });

  const recentJobsQuery = useQuery({
    queryKey: ["exports", "recent-jobs", statusFilter],
    queryFn: () => fetchRecentExportJobs({ limit: 12, status: statusFilter }),
    refetchInterval: 6000
  });

  const jobStatusQuery = useQuery({
    queryKey: ["exports", "job-status", activeJobId],
    queryFn: () => pollExportJobStatus(activeJobId),
    enabled: activeJobId.trim().length > 0,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "PENDING" || status === "PROCESSING" ? 4000 : false;
    }
  });

  const retryMutation = useMutation({
    mutationFn: retryExportJob,
    onSuccess: async (result) => {
      message.success(t("exportOps.retrySuccess", { jobId: result.jobId }));
      setActiveJobId(result.jobId);
      lookupForm.setFieldValue("jobId", result.jobId);
      await jobStatusQuery.refetch();
    }
  });

  const selectedJob = jobStatusQuery.data;
  const recentJobs = recentJobsQuery.data ?? [];

  const highlightStats = useMemo(() => {
    const storageInfo = storageInfoQuery.data;
    return [
      {
        key: "mode",
        title: t("exportOps.highlight.mode"),
        value: formatStorageSummary(storageInfo),
        hint: t("exportOps.highlight.modeHint")
      },
      {
        key: "batch",
        title: t("exportOps.highlight.batch"),
        value: storageInfo?.pendingBatchSize ?? 0,
        hint: t("exportOps.highlight.batchHint")
      },
      {
        key: "scan",
        title: t("exportOps.highlight.scan"),
        value: `${Math.round((storageInfo?.pendingScanDelayMs ?? 0) / 1000)}s`,
        hint: t("exportOps.highlight.scanHint")
      }
    ];
  }, [storageInfoQuery.data, t]);

  const handleLookup = async () => {
    const values = await lookupForm.validateFields();
    setActiveJobId(values.jobId.trim());
  };

  const handleDownload = async () => {
    if (!selectedJob?.jobId || !selectedJob.fileName || !selectedJob.contentType) {
      return;
    }
    await downloadExportJobFile(selectedJob.jobId, selectedJob.fileName, selectedJob.contentType);
    message.success(t("exportOps.downloadStarted", { fileName: selectedJob.fileName }));
  };

  const recentJobColumns = [
    {
      title: t("exportOps.table.job"),
      key: "job",
      render: (_: unknown, record: ExportJobStatusResponse) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{record.fileName || record.jobId}</Typography.Text>
          <Typography.Text type="secondary">{record.jobId}</Typography.Text>
        </Space>
      )
    },
    {
      title: t("exportOps.table.status"),
      dataIndex: "status",
      render: (value: string) => <Tag color={statusTagColor(value)}>{value}</Tag>
    },
    {
      title: t("exportOps.table.target"),
      key: "target",
      render: (_: unknown, record: ExportJobStatusResponse) =>
        record.reportId != null || record.resultId != null
          ? `${record.reportId ?? "-"} / ${record.resultId ?? "-"}`
          : "-"
    },
    {
      title: t("exportOps.table.storage"),
      dataIndex: "storageLocation",
      render: (value?: string | null) => value ?? "-"
    },
    {
      title: t("exportOps.table.createdAt"),
      dataIndex: "createdAt"
    },
    {
      title: t("exportOps.table.action"),
      key: "action",
      render: (_: unknown, record: ExportJobStatusResponse) => (
        <Button
          size="small"
          onClick={() => {
            setActiveJobId(record.jobId);
            lookupForm.setFieldValue("jobId", record.jobId);
          }}
        >
          {t("exportOps.inspect")}
        </Button>
      )
    }
  ];

  return (
    <Space direction="vertical" size={20} style={{ width: "100%" }}>
      <div
        style={{
          padding: isMobile ? 20 : 28,
          borderRadius: 24,
          background:
            "linear-gradient(145deg, rgba(14,28,39,0.96) 0%, rgba(24,48,61,0.92) 58%, rgba(229,164,72,0.18) 100%)",
          color: "#f5f7fa",
          overflow: "hidden",
          position: "relative"
        }}
      >
        <div
          style={{
            position: "absolute",
            inset: 0,
            background:
              "radial-gradient(circle at top right, rgba(255,210,122,0.34), transparent 28%), radial-gradient(circle at bottom left, rgba(64,146,201,0.22), transparent 34%)",
            pointerEvents: "none"
          }}
        />
        <Space direction="vertical" size={18} style={{ width: "100%", position: "relative" }}>
          <div>
            <Typography.Title level={3} style={{ margin: 0, color: "#f5f7fa" }}>
              {t("exportOps.title")}
            </Typography.Title>
            <Typography.Paragraph style={{ marginTop: 10, marginBottom: 0, maxWidth: 760, color: "rgba(245,247,250,0.8)" }}>
              {t("exportOps.subtitle")}
            </Typography.Paragraph>
          </div>
          <Row gutter={[16, 16]}>
            {highlightStats.map((item) => (
              <Col key={item.key} xs={24} sm={8}>
                <div
                  style={{
                    borderRadius: 20,
                    padding: "16px 18px",
                    background: "rgba(255,255,255,0.08)",
                    backdropFilter: "blur(16px)",
                    border: "1px solid rgba(255,255,255,0.12)"
                  }}
                >
                  <Typography.Text style={{ color: "rgba(245,247,250,0.72)" }}>{item.title}</Typography.Text>
                  <div style={{ height: 6 }} />
                  <Typography.Title level={4} style={{ margin: 0, color: "#ffffff" }}>
                    {item.value}
                  </Typography.Title>
                  <div style={{ height: 8 }} />
                  <Typography.Text style={{ color: "rgba(245,247,250,0.66)" }}>{item.hint}</Typography.Text>
                </div>
              </Col>
            ))}
          </Row>
        </Space>
      </div>

      {storageInfoQuery.isError ? <Alert type="warning" showIcon message={t("exportOps.storageLoadError")} /> : null}

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={14}>
          <Card
            title={t("exportOps.storageTitle")}
            extra={
              <Button icon={<ReloadOutlined />} onClick={() => void storageInfoQuery.refetch()} loading={storageInfoQuery.isFetching}>
                {t("exportOps.refresh")}
              </Button>
            }
          >
            <Space direction="vertical" size={16} style={{ width: "100%" }}>
              <Typography.Text type="secondary">{t("exportOps.storageDesc")}</Typography.Text>
              <Descriptions
                size="small"
                column={1}
                bordered={false}
                items={[
                  {
                    key: "mode",
                    label: t("exportOps.mode"),
                    children: <Tag color="blue">{storageInfoQuery.data?.mode ?? "-"}</Tag>
                  },
                  {
                    key: "fileStorageEnabled",
                    label: t("exportOps.fileStorageEnabled"),
                    children: storageInfoQuery.data?.fileStorageEnabled ? t("common.yes") : t("common.no")
                  },
                  {
                    key: "bucket",
                    label: t("exportOps.bucket"),
                    children: storageInfoQuery.data?.bucket ?? "-"
                  },
                  {
                    key: "keyPrefix",
                    label: t("exportOps.keyPrefix"),
                    children: storageInfoQuery.data?.keyPrefix ?? "-"
                  },
                  {
                    key: "endpointUrl",
                    label: t("exportOps.endpointUrl"),
                    children: storageInfoQuery.data?.endpointUrl ?? "-"
                  },
                  {
                    key: "baseDir",
                    label: t("exportOps.baseDir"),
                    children: storageInfoQuery.data?.baseDir ?? "-"
                  }
                ]}
              />
            </Space>
          </Card>
        </Col>

        <Col xs={24} xl={10}>
          <Card title={t("exportOps.workerTitle")}>
            <Space direction="vertical" size={16} style={{ width: "100%" }}>
              <Typography.Text type="secondary">{t("exportOps.workerDesc")}</Typography.Text>
              <Row gutter={[12, 12]}>
                <Col span={12}>
                  <Statistic
                    title={t("exportOps.pendingScanDelay")}
                    value={Math.round((storageInfoQuery.data?.pendingScanDelayMs ?? 0) / 1000)}
                    suffix="s"
                  />
                </Col>
                <Col span={12}>
                  <Statistic title={t("exportOps.pendingBatchSize")} value={storageInfoQuery.data?.pendingBatchSize ?? 0} />
                </Col>
              </Row>
              <Alert type="info" showIcon message={t("exportOps.workerHint")} />
            </Space>
          </Card>
        </Col>
      </Row>

      <Card
        title={t("exportOps.recentTitle")}
        extra={
          <Space wrap>
            <Segmented<ExportJobStatusFilter>
              size={isMobile ? "middle" : "small"}
              value={statusFilter}
              onChange={(value) => setStatusFilter(value)}
              options={[
                { label: t("exportOps.filter.all"), value: "ALL" },
                { label: t("exportOps.filter.failed"), value: "FAILED" },
                { label: t("exportOps.filter.processing"), value: "PROCESSING" },
                { label: t("exportOps.filter.pending"), value: "PENDING" },
                { label: t("exportOps.filter.done"), value: "DONE" }
              ]}
            />
            <Button icon={<ReloadOutlined />} onClick={() => void recentJobsQuery.refetch()} loading={recentJobsQuery.isFetching}>
              {t("exportOps.refresh")}
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Typography.Text type="secondary">{t("exportOps.recentDesc")}</Typography.Text>
          {recentJobsQuery.isError ? <Alert type="warning" showIcon message={t("exportOps.recentLoadError")} /> : null}
          <Table<ExportJobStatusResponse>
            rowKey="jobId"
            size="middle"
            loading={recentJobsQuery.isLoading}
            dataSource={recentJobs}
            pagination={false}
            locale={{ emptyText: t("exportOps.recentEmpty") }}
            scroll={{ x: 960 }}
            columns={recentJobColumns}
          />
        </Space>
      </Card>

      <Card title={t("exportOps.lookupTitle")}>
        <Space direction="vertical" size={18} style={{ width: "100%" }}>
          <Typography.Text type="secondary">{t("exportOps.lookupDesc")}</Typography.Text>
          <Form form={lookupForm} layout={isMobile ? "vertical" : "inline"}>
            <Form.Item
              name="jobId"
              label={t("exportOps.jobId")}
              rules={[{ required: true, message: t("exportOps.jobIdRequired") }]}
              style={{ flex: 1, width: isMobile ? "100%" : 420 }}
            >
              <Input placeholder={t("exportOps.jobIdPlaceholder")} allowClear />
            </Form.Item>
            <Form.Item>
              <Button type="primary" icon={<SearchOutlined />} onClick={() => void handleLookup()} loading={jobStatusQuery.isFetching}>
                {t("exportOps.lookupAction")}
              </Button>
            </Form.Item>
          </Form>

          {activeJobId && jobStatusQuery.isError ? <Alert type="error" showIcon message={t("exportOps.lookupError")} /> : null}

          {selectedJob ? (
            <div
              style={{
                borderRadius: 20,
                padding: isMobile ? 18 : 22,
                background: "linear-gradient(180deg, #fbfdff 0%, #f1f6fa 100%)",
                border: "1px solid #dce6ef"
              }}
            >
              <Space direction="vertical" size={18} style={{ width: "100%" }}>
                <Space wrap align="center">
                  <Typography.Title level={4} style={{ margin: 0 }}>
                    {selectedJob.fileName || selectedJob.jobId}
                  </Typography.Title>
                  <Tag color={statusTagColor(selectedJob.status)}>{selectedJob.status}</Tag>
                  {selectedJob.exportFormat ? <Tag>{selectedJob.exportFormat}</Tag> : null}
                </Space>

                <Descriptions
                  size="small"
                  column={isMobile ? 1 : 2}
                  items={[
                    { key: "jobId", label: t("exportOps.jobId"), children: selectedJob.jobId },
                    { key: "createdAt", label: t("exportOps.createdAt"), children: selectedJob.createdAt },
                    { key: "completedAt", label: t("exportOps.completedAt"), children: selectedJob.completedAt ?? "-" },
                    { key: "storageLocation", label: t("exportOps.storageLocation"), children: selectedJob.storageLocation ?? "-" },
                    { key: "fileSize", label: t("exportOps.fileSize"), children: selectedJob.fileSize ?? "-" },
                    { key: "localeTag", label: t("exportOps.localeTag"), children: selectedJob.localeTag ?? "-" },
                    { key: "reportId", label: t("exportOps.reportId"), children: selectedJob.reportId ?? "-" },
                    { key: "resultId", label: t("exportOps.resultId"), children: selectedJob.resultId ?? "-" }
                  ]}
                />

                {selectedJob.error ? <Alert type="error" showIcon message={selectedJob.error} /> : null}

                <Space wrap>
                  <Button icon={<SyncOutlined />} onClick={() => void jobStatusQuery.refetch()} loading={jobStatusQuery.isFetching}>
                    {t("exportOps.refreshJob")}
                  </Button>
                  <Button
                    type="primary"
                    icon={<DownloadOutlined />}
                    onClick={() => void handleDownload()}
                    disabled={selectedJob.status !== "DONE" || !selectedJob.fileName || !selectedJob.contentType}
                  >
                    {t("exportOps.download")}
                  </Button>
                  <Button
                    danger
                    icon={<ReloadOutlined />}
                    onClick={() => retryMutation.mutate(selectedJob.jobId)}
                    loading={retryMutation.isPending}
                    disabled={selectedJob.status !== "FAILED"}
                  >
                    {t("exportOps.retry")}
                  </Button>
                </Space>
              </Space>
            </div>
          ) : (
            <Empty description={t("exportOps.lookupEmpty")} />
          )}
        </Space>
      </Card>
    </Space>
  );
}
