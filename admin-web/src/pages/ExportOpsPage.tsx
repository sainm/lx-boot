import { useMutation, useQuery } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
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
import { formatDateTime } from "../utils/date";

function statusTagColor(status?: string | null) {
  switch (status) {
    case "DONE":
      return "success";
    case "FAILED":
      return "error";
    case "DEAD_LETTER":
      return "volcano";
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

function normalizeErrorSummary(error?: string | null) {
  const value = error?.trim();
  if (!value) {
    return "Unknown export error";
  }
  const firstLine = value.split(/\r?\n/)[0]?.trim() || value;
  return firstLine.length > 72 ? `${firstLine.slice(0, 69)}...` : firstLine;
}

export function ExportOpsPage() {
  const { t } = useI18n();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [lookupForm] = Form.useForm<{ jobId: string }>();
  const [activeJobId, setActiveJobId] = useState("");
  const [statusFilter, setStatusFilter] = useState<ExportJobStatusFilter>("ALL");
  const [drawerJob, setDrawerJob] = useState<ExportJobStatusResponse | null>(null);

  const storageInfoQuery = useQuery({
    queryKey: ["exports", "storage-info"],
    queryFn: fetchExportArtifactStorageInfo
  });

  const exportOpsOverviewQuery = useQuery({
    queryKey: ["exports", "recent-jobs-overview"],
    queryFn: () => fetchRecentExportJobs({ limit: 24, status: "ALL" }),
    refetchInterval: 6000
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
      await exportOpsOverviewQuery.refetch();
      await recentJobsQuery.refetch();
      await jobStatusQuery.refetch();
    }
  });

  const selectedJob = jobStatusQuery.data;
  const recentJobs = recentJobsQuery.data ?? [];
  const overviewJobs = exportOpsOverviewQuery.data ?? [];

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
      dataIndex: "createdAt",
      render: (value: string) => formatDateTime(value)
    },
    {
      title: t("exportOps.table.action"),
      key: "action",
      render: (_: unknown, record: ExportJobStatusResponse) => (
        <Space wrap size={8}>
          <Button
            size="small"
            onClick={() => {
              setActiveJobId(record.jobId);
              lookupForm.setFieldValue("jobId", record.jobId);
            }}
          >
            {t("exportOps.inspect")}
          </Button>
          <Button size="small" type="default" onClick={() => setDrawerJob(record)}>
            {t("exportOps.quickView")}
          </Button>
        </Space>
      )
    }
  ];

  const queueHealth = useMemo(() => {
    const failed = overviewJobs.filter((job) => job.status === "FAILED");
    const deadLetter = overviewJobs.filter((job) => job.status === "DEAD_LETTER");
    const pending = overviewJobs.filter((job) => job.status === "PENDING");
    const processing = overviewJobs.filter((job) => job.status === "PROCESSING");
    const done = overviewJobs.filter((job) => job.status === "DONE");
    const latestCompleted = done[0];
    return {
      failedCount: failed.length + deadLetter.length,
      pendingCount: pending.length,
      processingCount: processing.length,
      doneCount: done.length,
      retryableJobs: [...deadLetter, ...failed].slice(0, 4),
      latestCompletedName: latestCompleted?.fileName || latestCompleted?.jobId || "-"
    };
  }, [overviewJobs]);

  const errorGroups = useMemo(() => {
    const counts = new Map<string, number>();
    overviewJobs
      .filter((job) => job.status === "FAILED" || job.status === "DEAD_LETTER")
      .forEach((job) => {
        const key = normalizeErrorSummary(job.error);
        counts.set(key, (counts.get(key) ?? 0) + 1);
      });
    return Array.from(counts.entries())
      .map(([error, count]) => ({ error, count }))
      .sort((left, right) => right.count - left.count)
      .slice(0, 5);
  }, [overviewJobs]);

  const drawerDetails = drawerJob?.jobId === selectedJob?.jobId ? selectedJob : drawerJob;

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

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={11}>
          <Card
            title={t("exportOps.healthTitle")}
            extra={
              <Button icon={<ReloadOutlined />} onClick={() => void exportOpsOverviewQuery.refetch()} loading={exportOpsOverviewQuery.isFetching}>
                {t("exportOps.refresh")}
              </Button>
            }
          >
            <Space direction="vertical" size={16} style={{ width: "100%" }}>
              <Typography.Text type="secondary">{t("exportOps.healthDesc")}</Typography.Text>
              {exportOpsOverviewQuery.isError ? <Alert type="warning" showIcon message={t("exportOps.healthLoadError")} /> : null}
              <Row gutter={[12, 12]}>
                <Col xs={12} md={6}>
                  <div style={{ borderRadius: 18, padding: 16, background: "#fff4f4", border: "1px solid #ffd6d6" }}>
                    <Typography.Text type="secondary">{t("exportOps.health.failed")}</Typography.Text>
                    <Typography.Title level={3} style={{ margin: "8px 0 0", color: "#cf1322" }}>
                      {queueHealth.failedCount}
                    </Typography.Title>
                  </div>
                </Col>
                <Col xs={12} md={6}>
                  <div style={{ borderRadius: 18, padding: 16, background: "#fffbe6", border: "1px solid #ffe58f" }}>
                    <Typography.Text type="secondary">{t("exportOps.health.pending")}</Typography.Text>
                    <Typography.Title level={3} style={{ margin: "8px 0 0", color: "#ad6800" }}>
                      {queueHealth.pendingCount}
                    </Typography.Title>
                  </div>
                </Col>
                <Col xs={12} md={6}>
                  <div style={{ borderRadius: 18, padding: 16, background: "#e6f4ff", border: "1px solid #91caff" }}>
                    <Typography.Text type="secondary">{t("exportOps.health.processing")}</Typography.Text>
                    <Typography.Title level={3} style={{ margin: "8px 0 0", color: "#0958d9" }}>
                      {queueHealth.processingCount}
                    </Typography.Title>
                  </div>
                </Col>
                <Col xs={12} md={6}>
                  <div style={{ borderRadius: 18, padding: 16, background: "#f6ffed", border: "1px solid #b7eb8f" }}>
                    <Typography.Text type="secondary">{t("exportOps.health.done")}</Typography.Text>
                    <Typography.Title level={3} style={{ margin: "8px 0 0", color: "#389e0d" }}>
                      {queueHealth.doneCount}
                    </Typography.Title>
                  </div>
                </Col>
              </Row>
              <Alert
                type={queueHealth.failedCount > 0 ? "warning" : "success"}
                showIcon
                message={
                  queueHealth.failedCount > 0
                    ? t("exportOps.healthAttention", { count: queueHealth.failedCount })
                    : t("exportOps.healthHealthy")
                }
                description={t("exportOps.healthLatestCompleted", { fileName: queueHealth.latestCompletedName })}
              />
            </Space>
          </Card>
        </Col>

        <Col xs={24} xl={7}>
          <Card title={t("exportOps.errorClustersTitle")}>
            <Space direction="vertical" size={14} style={{ width: "100%" }}>
              <Typography.Text type="secondary">{t("exportOps.errorClustersDesc")}</Typography.Text>
              {errorGroups.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t("exportOps.errorClustersEmpty")} />
              ) : (
                errorGroups.map((group) => (
                  <div
                    key={group.error}
                    style={{
                      borderRadius: 18,
                      padding: 14,
                      background: "#fffdf6",
                      border: "1px solid #ffe7ba"
                    }}
                  >
                    <Space direction="vertical" size={8} style={{ width: "100%" }}>
                      <Space align="center" style={{ justifyContent: "space-between", width: "100%" }}>
                        <Typography.Text strong>{group.error}</Typography.Text>
                        <Tag color="gold">{t("exportOps.errorClustersCount", { count: group.count })}</Tag>
                      </Space>
                    </Space>
                  </div>
                ))
              )}
            </Space>
          </Card>
        </Col>

        <Col xs={24} xl={6}>
          <Card title={t("exportOps.retryRadarTitle")}>
            <Space direction="vertical" size={14} style={{ width: "100%" }}>
              <Typography.Text type="secondary">{t("exportOps.retryRadarDesc")}</Typography.Text>
              {queueHealth.retryableJobs.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t("exportOps.retryRadarEmpty")} />
              ) : (
                queueHealth.retryableJobs.map((job) => (
                  <div
                    key={job.jobId}
                    style={{
                      borderRadius: 18,
                      padding: 14,
                      background: "#fff8f6",
                      border: "1px solid #ffd8bf"
                    }}
                  >
                    <Space direction="vertical" size={10} style={{ width: "100%" }}>
                      <Space wrap align="center">
                        <Typography.Text strong>{job.fileName || job.jobId}</Typography.Text>
                        <Tag color="error">{job.status}</Tag>
                      </Space>
                      <Typography.Text type="secondary">{job.error || t("exportOps.retryRadarNoError")}</Typography.Text>
                      <Space wrap>
                        <Button
                          size="small"
                          onClick={() => {
                            setActiveJobId(job.jobId);
                            lookupForm.setFieldValue("jobId", job.jobId);
                          }}
                        >
                          {t("exportOps.inspect")}
                        </Button>
                        <Button
                          size="small"
                          danger
                          icon={<ReloadOutlined />}
                          onClick={() => retryMutation.mutate(job.jobId)}
                          loading={retryMutation.isPending && activeJobId === job.jobId}
                        >
                          {t("exportOps.retry")}
                        </Button>
                      </Space>
                    </Space>
                  </div>
                ))
              )}
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
                { label: t("exportOps.filter.deadLetter"), value: "DEAD_LETTER" },
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
                    { key: "createdAt", label: t("exportOps.createdAt"), children: formatDateTime(selectedJob.createdAt) },
                    { key: "completedAt", label: t("exportOps.completedAt"), children: formatDateTime(selectedJob.completedAt) },
                    { key: "retryCount", label: t("exportOps.retryCount"), children: selectedJob.retryCount },
                    { key: "nextRetryAt", label: t("exportOps.nextRetryAt"), children: formatDateTime(selectedJob.nextRetryAt) },
                    { key: "processingStartedAt", label: t("exportOps.processingStartedAt"), children: formatDateTime(selectedJob.processingStartedAt) },
                    { key: "deadLetterAt", label: t("exportOps.deadLetterAt"), children: formatDateTime(selectedJob.deadLetterAt) },
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
                    disabled={selectedJob.status !== "FAILED" && selectedJob.status !== "DEAD_LETTER"}
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

      <Drawer
        title={drawerDetails?.fileName || drawerDetails?.jobId || t("exportOps.quickViewTitle")}
        placement="right"
        width={isMobile ? "100%" : 460}
        open={Boolean(drawerJob)}
        onClose={() => setDrawerJob(null)}
      >
        {drawerDetails ? (
          <Space direction="vertical" size={16} style={{ width: "100%" }}>
            <Space wrap align="center">
              <Tag color={statusTagColor(drawerDetails.status)}>{drawerDetails.status}</Tag>
              {drawerDetails.exportFormat ? <Tag>{drawerDetails.exportFormat}</Tag> : null}
            </Space>
            <Descriptions
              size="small"
              column={1}
              items={[
                { key: "jobId", label: t("exportOps.jobId"), children: drawerDetails.jobId },
                { key: "createdAt", label: t("exportOps.createdAt"), children: formatDateTime(drawerDetails.createdAt) },
                { key: "completedAt", label: t("exportOps.completedAt"), children: formatDateTime(drawerDetails.completedAt) },
                { key: "retryCount", label: t("exportOps.retryCount"), children: drawerDetails.retryCount },
                { key: "nextRetryAt", label: t("exportOps.nextRetryAt"), children: formatDateTime(drawerDetails.nextRetryAt) },
                { key: "processingStartedAt", label: t("exportOps.processingStartedAt"), children: formatDateTime(drawerDetails.processingStartedAt) },
                { key: "deadLetterAt", label: t("exportOps.deadLetterAt"), children: formatDateTime(drawerDetails.deadLetterAt) },
                { key: "storageLocation", label: t("exportOps.storageLocation"), children: drawerDetails.storageLocation ?? "-" },
                { key: "fileSize", label: t("exportOps.fileSize"), children: drawerDetails.fileSize ?? "-" },
                { key: "localeTag", label: t("exportOps.localeTag"), children: drawerDetails.localeTag ?? "-" },
                { key: "reportId", label: t("exportOps.reportId"), children: drawerDetails.reportId ?? "-" },
                { key: "resultId", label: t("exportOps.resultId"), children: drawerDetails.resultId ?? "-" }
              ]}
            />
            {drawerDetails.error ? (
              <Alert
                type="error"
                showIcon
                message={t("exportOps.drawerErrorTitle")}
                description={drawerDetails.error}
              />
            ) : null}
            <Space wrap>
              <Button
                onClick={() => {
                  setActiveJobId(drawerDetails.jobId);
                  lookupForm.setFieldValue("jobId", drawerDetails.jobId);
                }}
              >
                {t("exportOps.inspect")}
              </Button>
              <Button
                type="primary"
                icon={<DownloadOutlined />}
                onClick={() => {
                  if (drawerDetails.fileName && drawerDetails.contentType) {
                    void downloadExportJobFile(drawerDetails.jobId, drawerDetails.fileName, drawerDetails.contentType);
                  }
                }}
                disabled={drawerDetails.status !== "DONE" || !drawerDetails.fileName || !drawerDetails.contentType}
              >
                {t("exportOps.download")}
              </Button>
              <Button
                danger
                icon={<ReloadOutlined />}
                onClick={() => retryMutation.mutate(drawerDetails.jobId)}
                disabled={drawerDetails.status !== "FAILED" && drawerDetails.status !== "DEAD_LETTER"}
              >
                {t("exportOps.retry")}
              </Button>
            </Space>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
