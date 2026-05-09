import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Form, InputNumber, Modal, Pagination, Popconfirm, Select, Space, Table, Tag, Typography, message } from "antd";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ExportReportDialog } from "../components/ExportReportDialog";
import { Permission } from "../components/Permission";
import { assignWarning, claimWarning, fetchWarningPage, type WarningSummary } from "../features/warnings/api";
import { useI18n } from "../i18n/provider";
import { formatDateTime } from "../utils/date";
import { InterventionDraftModal } from "./InterventionDraftModal";

const PAGE_SIZE = 20;

export function WarningListPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [warningLevelInput, setWarningLevelInput] = useState<string | undefined>(undefined);
  const [statusInput, setStatusInput] = useState<string | undefined>(undefined);
  const [warningLevelFilter, setWarningLevelFilter] = useState<string | undefined>(undefined);
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(1);
  const [assignOpen, setAssignOpen] = useState(false);
  const [interventionOpen, setInterventionOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);
  const [assignForm] = Form.useForm();
  const [currentWarningId, setCurrentWarningId] = useState<number | null>(null);
  const [exportTarget, setExportTarget] = useState<{ resultId?: number; reportId?: number } | null>(null);

  const queryParams = { page, size: PAGE_SIZE, warningLevel: warningLevelFilter, status: statusFilter };

  const warningQuery = useQuery({
    queryKey: ["warnings", queryParams],
    queryFn: () => fetchWarningPage(queryParams)
  });

  const claimMutation = useMutation({
    mutationFn: claimWarning,
    onSuccess: async () => {
      message.success(t("warnings.claimed"));
      await queryClient.invalidateQueries({ queryKey: ["warnings"] });
    }
  });

  const assignMutation = useMutation({
    mutationFn: ({ warningId, assigneeUserId }: { warningId: number; assigneeUserId: number }) =>
      assignWarning(warningId, assigneeUserId),
    onSuccess: async () => {
      message.success(t("warnings.assigned"));
      setAssignOpen(false);
      assignForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["warnings"] });
    }
  });

  const handleAssign = async () => {
    const values = await assignForm.validateFields();
    if (currentWarningId == null) return;
    await assignMutation.mutateAsync({ warningId: currentWarningId, assigneeUserId: values.assigneeUserId });
  };

  const handleSearch = () => {
    setWarningLevelFilter(warningLevelInput);
    setStatusFilter(statusInput);
    setPage(1);
  };

  const handleReset = () => {
    setWarningLevelInput(undefined);
    setStatusInput(undefined);
    setWarningLevelFilter(undefined);
    setStatusFilter(undefined);
    setPage(1);
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={4}>{t("warnings.title")}</Typography.Title>
        <Typography.Text type="secondary">{t("warnings.subtitle")}</Typography.Text>
      </div>

      <Space wrap>
        <Select
          style={{ width: 180 }}
          allowClear
          placeholder={t("warnings.levelPlaceholder")}
          value={warningLevelInput}
          onChange={(value) => setWarningLevelInput(value)}
          options={[
            { label: t("warnings.level.medium"), value: "MEDIUM" },
            { label: t("warnings.level.high"), value: "HIGH" }
          ]}
        />
        <Select
          style={{ width: 180 }}
          allowClear
          placeholder={t("warnings.statusPlaceholder")}
          value={statusInput}
          onChange={(value) => setStatusInput(value)}
          options={[
            { label: t("warnings.status.pending"), value: "PENDING" },
            { label: t("warnings.status.claimed"), value: "CLAIMED" },
            { label: t("warnings.status.assigned"), value: "ASSIGNED" },
            { label: t("warnings.status.processing"), value: "PROCESSING" },
            { label: t("warnings.status.closed"), value: "CLOSED" }
          ]}
        />
        <Button type="primary" onClick={handleSearch}>{t("warnings.search")}</Button>
        <Button onClick={handleReset}>{t("warnings.reset")}</Button>
      </Space>

      {warningQuery.isError ? <Alert type="warning" showIcon message={t("warnings.loadError")} /> : null}

      <Table<WarningSummary>
        rowKey="id"
        loading={warningQuery.isLoading}
        dataSource={warningQuery.data?.list ?? []}
        pagination={false}
        columns={[
          { title: t("warnings.col.id"), dataIndex: "id", width: 100 },
          { title: t("warnings.col.resultId"), dataIndex: "resultId", width: 100 },
          {
            title: t("warnings.col.level"),
            dataIndex: "warningLevel",
            width: 120,
            render: (value: string) => <Tag color={value === "HIGH" ? "red" : "orange"}>{value}</Tag>
          },
          { title: t("warnings.col.priority"), dataIndex: "warningPriority", width: 100, render: (value: string) => <Tag color="purple">{value}</Tag> },
          { title: t("warnings.col.status"), dataIndex: "status", width: 120, render: (value: string) => <Tag color="blue">{value}</Tag> },
          { title: t("warnings.col.reason"), dataIndex: "warningReason" },
          { title: t("warnings.col.createdAt"), dataIndex: "createdAt", width: 180, render: (value: string) => formatDateTime(value) },
          {
            title: t("warnings.col.action"),
            width: 300,
            render: (_, record) => (
              <Space wrap>
                <Permission roles={["COUNSELOR", "ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
                  <Popconfirm
                    title={t("warnings.claimConfirm")}
                    okText={t("warnings.claim")}
                    cancelText={t("warnings.cancel")}
                    onConfirm={() => claimMutation.mutate(record.id)}
                  >
                    <Button type="link" loading={claimMutation.isPending} size="small">
                      {t("warnings.claim")}
                    </Button>
                  </Popconfirm>
                </Permission>
                <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
                  <Button
                    type="link"
                    size="small"
                    onClick={() => {
                      setCurrentWarningId(record.id);
                      setAssignOpen(true);
                    }}
                  >
                    {t("warnings.assign")}
                  </Button>
                </Permission>
                <Button type="link" size="small" onClick={() => navigate(`/reports?resultId=${record.resultId}`)}>
                  {t("warnings.report")}
                </Button>
                <Permission roles={["COUNSELOR", "ASSESSMENT_ADMIN", "ORG_MANAGER", "SYS_ADMIN"]}>
                  <Button
                    type="link"
                    size="small"
                    onClick={() => {
                      setExportTarget({ resultId: record.resultId });
                      setExportOpen(true);
                    }}
                  >
                    {t("warnings.export")}
                  </Button>
                </Permission>
                <Permission roles={["COUNSELOR", "ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
                  <Button
                    type="link"
                    size="small"
                    onClick={() => {
                      setCurrentWarningId(record.id);
                      setInterventionOpen(true);
                    }}
                  >
                    {t("warnings.intervention")}
                  </Button>
                </Permission>
              </Space>
            )
          }
        ]}
      />

      {(warningQuery.data?.total ?? 0) > PAGE_SIZE ? (
        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <Pagination
            current={page}
            pageSize={PAGE_SIZE}
            total={warningQuery.data?.total ?? 0}
            showTotal={(total) => t("warnings.total", { total })}
            onChange={(p) => setPage(p)}
            showSizeChanger={false}
          />
        </div>
      ) : null}

      <Modal
        title={t("warnings.assignTitle")}
        open={assignOpen}
        onCancel={() => setAssignOpen(false)}
        onOk={() => void handleAssign()}
        confirmLoading={assignMutation.isPending}
        destroyOnHidden
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item
            label={t("warnings.assigneeUserId")}
            name="assigneeUserId"
            rules={[{ required: true, message: t("warnings.assigneeRequired") }]}
          >
            <InputNumber min={1} style={{ width: "100%" }} placeholder={t("warnings.assigneePlaceholder")} />
          </Form.Item>
        </Form>
      </Modal>

      <ExportReportDialog
        open={exportOpen}
        title={t("warnings.exportTitle")}
        description={t("warnings.exportDesc")}
        target={exportTarget}
        onClose={() => setExportOpen(false)}
      />

      <InterventionDraftModal
        open={interventionOpen}
        warningId={currentWarningId}
        onClose={() => setInterventionOpen(false)}
        onSuccess={async () => {
          await queryClient.invalidateQueries({ queryKey: ["warnings"] });
        }}
      />
    </Space>
  );
}
