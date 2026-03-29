import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from "antd";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { ExportReportDialog } from "../components/ExportReportDialog";
import { Permission } from "../components/Permission";
import { assignWarning, claimWarning, fetchWarningPage } from "../features/warnings/api";
import { InterventionDraftModal } from "./InterventionDraftModal";

export function WarningListPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [assignOpen, setAssignOpen] = useState(false);
  const [interventionOpen, setInterventionOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);
  const [assignForm] = Form.useForm();
  const [currentWarningId, setCurrentWarningId] = useState<number | null>(null);
  const [exportTarget, setExportTarget] = useState<{ resultId?: number; reportId?: number } | null>(null);

  const warningQuery = useQuery({
    queryKey: ["warnings", { page: 1, size: 20 }],
    queryFn: () => fetchWarningPage({ page: 1, size: 20 })
  });

  const claimMutation = useMutation({
    mutationFn: claimWarning,
    onSuccess: async () => {
      message.success("预警已接单");
      await queryClient.invalidateQueries({ queryKey: ["warnings"] });
    }
  });

  const assignMutation = useMutation({
    mutationFn: ({ warningId, assigneeUserId }: { warningId: number; assigneeUserId: number }) =>
      assignWarning(warningId, assigneeUserId),
    onSuccess: async () => {
      message.success("预警已指派");
      setAssignOpen(false);
      assignForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["warnings"] });
    }
  });

  const handleAssign = async () => {
    const values = await assignForm.validateFields();
    if (currentWarningId == null) {
      return;
    }
    await assignMutation.mutateAsync({
      warningId: currentWarningId,
      assigneeUserId: values.assigneeUserId
    });
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={4}>预警列表</Typography.Title>
        <Typography.Text type="secondary">这里承接预警接单、指派、导出和干预记录入口。</Typography.Text>
      </div>

      <Space wrap>
        <Select
          style={{ width: 180 }}
          placeholder="按预警等级筛选"
          options={[
            { label: "关注", value: "MEDIUM" },
            { label: "高风险", value: "HIGH" }
          ]}
        />
        <Input placeholder="按状态筛选" style={{ width: 180 }} />
      </Space>

      {warningQuery.isError ? <Alert type="warning" showIcon message="当前暂时无法获取预警数据。" /> : null}

      <Table
        rowKey="id"
        loading={warningQuery.isLoading}
        dataSource={warningQuery.data?.list ?? []}
        pagination={false}
        columns={[
          { title: "预警编号", dataIndex: "id" },
          { title: "结果编号", dataIndex: "resultId" },
          {
            title: "预警等级",
            dataIndex: "warningLevel",
            render: (value: string) => <Tag color={value === "HIGH" ? "red" : "orange"}>{value}</Tag>
          },
          { title: "优先级", dataIndex: "warningPriority", render: (value: string) => <Tag color="purple">{value}</Tag> },
          { title: "状态", dataIndex: "status", render: (value: string) => <Tag color="blue">{value}</Tag> },
          { title: "触发原因", dataIndex: "warningReason" },
          { title: "创建时间", dataIndex: "createdAt" },
          {
            title: "操作",
            render: (_, record) => (
              <Space wrap>
                <Permission roles={["COUNSELOR", "ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
                  <Popconfirm
                    title="确认接单该预警吗？"
                    okText="接单"
                    cancelText="取消"
                    onConfirm={() => claimMutation.mutate(record.id)}
                  >
                    <Button type="link" loading={claimMutation.isPending}>
                      接单
                    </Button>
                  </Popconfirm>
                </Permission>
                <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
                  <Button
                    type="link"
                    onClick={() => {
                      setCurrentWarningId(record.id);
                      setAssignOpen(true);
                    }}
                  >
                    指派
                  </Button>
                </Permission>
                <Button type="link" onClick={() => navigate(`/reports?resultId=${record.resultId}`)}>
                  报告页
                </Button>
                <Permission roles={["COUNSELOR", "ASSESSMENT_ADMIN", "ORG_MANAGER", "SYS_ADMIN"]}>
                  <Button
                    type="link"
                    onClick={() => {
                      setExportTarget({ resultId: record.resultId });
                      setExportOpen(true);
                    }}
                  >
                    导出
                  </Button>
                </Permission>
                <Permission roles={["COUNSELOR", "ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
                  <Button
                    type="link"
                    onClick={() => {
                      setCurrentWarningId(record.id);
                      setInterventionOpen(true);
                    }}
                  >
                    干预记录
                  </Button>
                </Permission>
              </Space>
            )
          }
        ]}
      />

      <Modal
        title="指派预警"
        open={assignOpen}
        onCancel={() => setAssignOpen(false)}
        onOk={() => void handleAssign()}
        confirmLoading={assignMutation.isPending}
        destroyOnClose
      >
        <Form form={assignForm} layout="vertical">
          <Form.Item
            label="责任人用户ID"
            name="assigneeUserId"
            rules={[{ required: true, message: "请输入责任人用户 ID" }]}
          >
            <InputNumber min={1} style={{ width: "100%" }} placeholder="请输入用户 ID" />
          </Form.Item>
        </Form>
      </Modal>

      <ExportReportDialog
        open={exportOpen}
        title="导出预警关联报告"
        description="可以将当前预警关联的报告以文本或 PDF 形式导出。"
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
