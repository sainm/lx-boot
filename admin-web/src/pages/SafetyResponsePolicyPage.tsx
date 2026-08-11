import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Card, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography, message } from "antd";
import { useState } from "react";
import {
  approveSafetyResponsePolicy,
  createSafetyResponsePolicy,
  fetchSafetyResponsePolicies,
  type CreateSafetyResponsePolicyRequest,
  type SafetyResponsePolicy
} from "../features/safety-policies/api";
import { useI18n } from "../i18n/provider";
import { formatDateTime } from "../utils/date";

export function SafetyResponsePolicyPage() {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const [createOpen, setCreateOpen] = useState(false);
  const [approvePolicy, setApprovePolicy] = useState<SafetyResponsePolicy | null>(null);
  const [createForm] = Form.useForm<CreateSafetyResponsePolicyRequest>();
  const [approveForm] = Form.useForm<{ professionalReviewerId: number }>();
  const query = useQuery({ queryKey: ["safety-response-policies"], queryFn: fetchSafetyResponsePolicies });
  const createMutation = useMutation({
    mutationFn: createSafetyResponsePolicy,
    onSuccess: async () => {
      setCreateOpen(false);
      createForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["safety-response-policies"] });
      void message.success(t("safetyPolicy.created"));
    }
  });
  const approveMutation = useMutation({
    mutationFn: ({ id, reviewerId }: { id: number; reviewerId: number }) => approveSafetyResponsePolicy(id, reviewerId),
    onSuccess: async () => {
      setApprovePolicy(null);
      approveForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["safety-response-policies"] });
      void message.success(t("safetyPolicy.approved"));
    }
  });

  const submitCreate = async () => createMutation.mutate(await createForm.validateFields());
  const submitApprove = async () => {
    if (!approvePolicy) return;
    const values = await approveForm.validateFields();
    approveMutation.mutate({ id: approvePolicy.id, reviewerId: values.professionalReviewerId });
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 16 }}>
        <div>
          <Typography.Title level={4}>{t("safetyPolicy.title")}</Typography.Title>
          <Typography.Text type="secondary">{t("safetyPolicy.subtitle")}</Typography.Text>
        </div>
        <Button type="primary" onClick={() => setCreateOpen(true)}>{t("safetyPolicy.create")}</Button>
      </div>
      <Alert type="warning" showIcon message={t("safetyPolicy.governanceNotice")} />
      <Card>
        <Table<SafetyResponsePolicy>
          rowKey="id"
          loading={query.isLoading}
          dataSource={query.data ?? []}
          pagination={false}
          scroll={{ x: 1250 }}
          columns={[
            { title: t("safetyPolicy.code"), dataIndex: "policyCode" },
            { title: t("safetyPolicy.version"), dataIndex: "versionNo", width: 90 },
            { title: t("safetyPolicy.risk"), dataIndex: "riskCategory", width: 90 },
            { title: t("safetyPolicy.firstResponse"), dataIndex: "firstResponseMinutes", width: 130 },
            { title: t("safetyPolicy.escalation"), dataIndex: "escalationMinutes", width: 130 },
            { title: t("safetyPolicy.followUp"), dataIndex: "followUpMinutes", width: 120, render: (value?: number | null) => value ?? "-" },
            { title: t("safetyPolicy.responsibleRole"), dataIndex: "responsibleRole" },
            { title: t("safetyPolicy.backupRole"), dataIndex: "backupRole" },
            {
              title: t("safetyPolicy.status"),
              key: "status",
              render: (_, record) => <Tag color={record.activeFlag ? "green" : record.status === "DRAFT" ? "gold" : "default"}>{record.status}</Tag>
            },
            { title: t("safetyPolicy.approvedAt"), dataIndex: "approvedAt", render: (value?: string | null) => formatDateTime(value) },
            {
              title: t("safetyPolicy.action"),
              fixed: "right",
              width: 110,
              render: (_, record) => record.status === "DRAFT" ? (
                <Button type="link" onClick={() => setApprovePolicy(record)}>{t("safetyPolicy.approve")}</Button>
              ) : "-"
            }
          ]}
        />
      </Card>

      <Modal
        title={t("safetyPolicy.create")}
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => void submitCreate()}
        confirmLoading={createMutation.isPending}
        width={720}
        destroyOnHidden
      >
        <Form form={createForm} layout="vertical" initialValues={{ versionNo: 1, riskCategory: "P1" }}>
          <Space wrap style={{ width: "100%" }}>
            <Form.Item name="policyCode" label={t("safetyPolicy.code")} rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="versionNo" label={t("safetyPolicy.version")} rules={[{ required: true }]}><InputNumber min={1} /></Form.Item>
            <Form.Item name="riskCategory" label={t("safetyPolicy.risk")} rules={[{ required: true }]}>
              <Select style={{ width: 120 }} options={["P0", "P1", "P2", "P3"].map((value) => ({ value, label: value }))} />
            </Form.Item>
          </Space>
          <Space wrap>
            <Form.Item name="firstResponseMinutes" label={t("safetyPolicy.firstResponse")} rules={[{ required: true }]}><InputNumber min={1} /></Form.Item>
            <Form.Item name="escalationMinutes" label={t("safetyPolicy.escalation")} rules={[{ required: true }]}><InputNumber min={1} /></Form.Item>
            <Form.Item name="followUpMinutes" label={t("safetyPolicy.followUp")}><InputNumber min={1} /></Form.Item>
          </Space>
          <Form.Item name="responsibleRole" label={t("safetyPolicy.responsibleRole")} rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="backupRole" label={t("safetyPolicy.backupRole")} rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="emergencyContactText" label={t("safetyPolicy.emergencyContact")} rules={[{ required: true }]}>
            <Input.TextArea rows={4} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t("safetyPolicy.approve")}
        open={Boolean(approvePolicy)}
        onCancel={() => setApprovePolicy(null)}
        onOk={() => void submitApprove()}
        confirmLoading={approveMutation.isPending}
        destroyOnHidden
      >
        <Alert type="info" showIcon message={t("safetyPolicy.dualReviewNotice")} style={{ marginBottom: 16 }} />
        <Form form={approveForm} layout="vertical">
          <Form.Item name="professionalReviewerId" label={t("safetyPolicy.reviewerId")} rules={[{ required: true }]}>
            <InputNumber min={1} style={{ width: "100%" }} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
