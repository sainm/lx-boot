import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, DatePicker, Form, Input, Modal, Pagination, Popconfirm, Select, Space, Switch, Table, Tag, Typography, message } from "antd";
import dayjs from "dayjs";
import { useState } from "react";
import { Permission } from "../components/Permission";
import { fetchScalePage } from "../features/scales/api";
import {
  assignTaskGroups,
  assignTaskUsers,
  createTask,
  deleteTask,
  fetchTaskDetail,
  fetchTaskPage,
  updateTask,
  type CreateTaskRequest,
  type TaskSummary
} from "../features/tasks/api";
import { useI18n } from "../i18n/provider";
import { formatDateTime } from "../utils/date";

const PAGE_SIZE = 20;

export function TaskListPage() {
  const { t } = useI18n();
  const [createOpen, setCreateOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editingTaskId, setEditingTaskId] = useState<number | null>(null);
  const [editLoading, setEditLoading] = useState(false);
  const [assignOpen, setAssignOpen] = useState(false);
  const [assignTaskId, setAssignTaskId] = useState<number | null>(null);
  const [form] = Form.useForm();
  const [editForm] = Form.useForm();
  const [assignForm] = Form.useForm();
  const queryClient = useQueryClient();
  const [nameInput, setNameInput] = useState("");
  const [nameFilter, setNameFilter] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(1);

  const queryParams = { taskName: nameFilter, page, size: PAGE_SIZE };

  const taskQuery = useQuery({
    queryKey: ["tasks", queryParams],
    queryFn: () => fetchTaskPage(queryParams)
  });

  const scaleOptionsQuery = useQuery({
    queryKey: ["scales", "task-select"],
    queryFn: () => fetchScalePage({ page: 1, size: 200 })
  });

  const createTaskMutation = useMutation({
    mutationFn: createTask,
    onSuccess: async () => {
      message.success(t("tasks.created"));
      setCreateOpen(false);
      form.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["tasks"] });
    }
  });

  const updateTaskMutation = useMutation({
    mutationFn: ({ taskId, payload }: { taskId: number; payload: CreateTaskRequest }) => updateTask(taskId, payload),
    onSuccess: async () => {
      message.success(t("tasks.updated"));
      setEditOpen(false);
      setEditingTaskId(null);
      editForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["tasks"] });
    }
  });

  const deleteTaskMutation = useMutation({
    mutationFn: deleteTask,
    onSuccess: async () => {
      message.success(t("tasks.deleted"));
      await queryClient.invalidateQueries({ queryKey: ["tasks"] });
    }
  });

  const assignGroupMutation = useMutation({
    mutationFn: ({ taskId, groupIds }: { taskId: number; groupIds: number[] }) => assignTaskGroups(taskId, groupIds),
    onSuccess: async () => {
      message.success(t("tasks.assignedGroups"));
      setAssignOpen(false);
      assignForm.resetFields();
    }
  });

  const assignUserMutation = useMutation({
    mutationFn: ({ taskId, userIds }: { taskId: number; userIds: number[] }) => assignTaskUsers(taskId, userIds),
    onSuccess: async () => {
      message.success(t("tasks.assignedUsers"));
      setAssignOpen(false);
      assignForm.resetFields();
    }
  });

  const handleCreate = async () => {
    const values = await form.validateFields();
    await createTaskMutation.mutateAsync({
      ...values,
      startTime: values.startTime.toISOString(),
      endTime: values.endTime.toISOString()
    });
  };

  const handleUpdate = async () => {
    if (editingTaskId == null) return;
    const values = await editForm.validateFields();
    await updateTaskMutation.mutateAsync({
      taskId: editingTaskId,
      payload: {
        ...values,
        startTime: values.startTime.toISOString(),
        endTime: values.endTime.toISOString()
      }
    });
  };

  const openEdit = async (taskId: number) => {
    setEditLoading(true);
    try {
      const detail = await fetchTaskDetail(taskId);
      if (detail.status !== "DRAFT") {
        message.warning(t("tasks.editDraftOnly"));
        return;
      }
      setEditingTaskId(taskId);
      editForm.setFieldsValue({
        taskName: detail.taskName,
        scaleId: detail.scaleId,
        taskMode: detail.taskMode,
        anonymousFlag: detail.anonymousFlag,
        allowSaveFlag: detail.allowSaveFlag,
        allowTimeoutSubmitFlag: detail.allowTimeoutSubmitFlag,
        allowRetakeFlag: detail.allowRetakeFlag,
        startTime: dayjs(detail.startTime),
        endTime: dayjs(detail.endTime)
      });
      setEditOpen(true);
    } finally {
      setEditLoading(false);
    }
  };

  const parseIds = (raw: string): number[] =>
    raw
      .split(/[,\n，]/)
      .map((item) => item.trim())
      .filter(Boolean)
      .map((item) => Number(item))
      .filter((item) => Number.isInteger(item) && item > 0);

  const handleAssign = async () => {
    if (assignTaskId == null) return;
    const values = await assignForm.validateFields();
    const ids = parseIds(values.targetIds);
    if (ids.length === 0) {
      message.warning(t("tasks.invalidTargetIds"));
      return;
    }
    if (values.targetType === "GROUP") {
      await assignGroupMutation.mutateAsync({ taskId: assignTaskId, groupIds: ids });
      return;
    }
    await assignUserMutation.mutateAsync({ taskId: assignTaskId, userIds: ids });
  };

  const handleSearch = () => {
    setNameFilter(nameInput.trim() || undefined);
    setPage(1);
  };

  const handleReset = () => {
    setNameInput("");
    setNameFilter(undefined);
    setPage(1);
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 16 }}>
        <div>
          <Typography.Title level={4}>{t("tasks.title")}</Typography.Title>
          <Typography.Text type="secondary">{t("tasks.subtitle")}</Typography.Text>
        </div>
        <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            {t("tasks.create")}
          </Button>
        </Permission>
      </div>

      <Space>
        <Input
          placeholder={t("tasks.searchPlaceholder")}
          style={{ width: 260 }}
          value={nameInput}
          onChange={(e) => setNameInput(e.target.value)}
          onPressEnter={handleSearch}
          allowClear
          onClear={handleReset}
        />
        <Button type="primary" onClick={handleSearch}>{t("tasks.search")}</Button>
        <Button onClick={handleReset}>{t("tasks.reset")}</Button>
      </Space>

      {taskQuery.isError ? <Alert type="warning" showIcon message={t("tasks.loadError")} /> : null}

      <Table<TaskSummary>
        rowKey="id"
        loading={taskQuery.isLoading}
        dataSource={taskQuery.data?.list ?? []}
        pagination={false}
        columns={[
          { title: t("tasks.col.name"), dataIndex: "taskName" },
          { title: t("tasks.col.scale"), dataIndex: "scaleName" },
          { title: t("tasks.col.mode"), dataIndex: "taskMode", width: 100 },
          { title: t("tasks.col.start"), dataIndex: "startTime", width: 180, render: (value: string) => formatDateTime(value) },
          { title: t("tasks.col.end"), dataIndex: "endTime", width: 180, render: (value: string) => formatDateTime(value) },
          {
            title: t("tasks.col.status"),
            dataIndex: "status",
            width: 100,
            render: (value: string) => <Tag color="blue">{value}</Tag>
          },
          {
            title: t("tasks.col.action"),
            width: 260,
            render: (_, record) => (
              <Space wrap>
                <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
                  {record.status === "DRAFT" ? (
                    <>
                      <Button type="link" size="small" loading={editLoading && editingTaskId === record.id} onClick={() => void openEdit(record.id)}>
                        {t("tasks.edit")}
                      </Button>
                      <Popconfirm
                        title={t("tasks.deleteConfirm")}
                        okText={t("tasks.delete")}
                        cancelText={t("warnings.cancel")}
                        onConfirm={() => deleteTaskMutation.mutate(record.id)}
                      >
                        <Button type="link" danger size="small" loading={deleteTaskMutation.isPending}>
                          {t("tasks.delete")}
                        </Button>
                      </Popconfirm>
                    </>
                  ) : null}
                  <Button
                    type="link"
                    size="small"
                    onClick={() => {
                      setAssignTaskId(record.id);
                      setAssignOpen(true);
                      assignForm.setFieldsValue({ targetType: "GROUP" });
                    }}
                  >
                    {t("tasks.assign")}
                  </Button>
                </Permission>
              </Space>
            )
          }
        ]}
      />

      {(taskQuery.data?.total ?? 0) > PAGE_SIZE ? (
        <div style={{ display: "flex", justifyContent: "flex-end" }}>
          <Pagination
            current={page}
            pageSize={PAGE_SIZE}
            total={taskQuery.data?.total ?? 0}
            showTotal={(total) => t("tasks.total", { total })}
            onChange={(p) => setPage(p)}
            showSizeChanger={false}
          />
        </div>
      ) : null}

      <Modal
        title={t("tasks.create")}
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => void handleCreate()}
        confirmLoading={createTaskMutation.isPending}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
          initialValues={{
            taskMode: "SCREENING",
            anonymousFlag: false,
            allowSaveFlag: true,
            allowTimeoutSubmitFlag: false,
            allowRetakeFlag: false
          }}
        >
          <Form.Item label={t("tasks.name")} name="taskName" rules={[{ required: true, message: t("tasks.nameRequired") }]}>
            <Input placeholder={t("tasks.namePlaceholder")} />
          </Form.Item>
          <Form.Item label={t("tasks.scale")} name="scaleId" rules={[{ required: true, message: t("tasks.scaleRequired") }]}>
            <Select
              placeholder={t("tasks.scalePlaceholder")}
              loading={scaleOptionsQuery.isLoading}
              options={(scaleOptionsQuery.data?.list ?? []).map((item) => ({
                label: `${item.scaleName} (${item.scaleCode})`,
                value: item.id
              }))}
            />
          </Form.Item>
          <Form.Item label={t("tasks.mode")} name="taskMode" rules={[{ required: true, message: t("tasks.modeRequired") }]}>
            <Select
              options={[
                { label: t("tasks.mode.screening"), value: "SCREENING" },
                { label: t("tasks.mode.retest"), value: "RETEST" },
                { label: t("tasks.mode.followUp"), value: "FOLLOW_UP" }
              ]}
            />
          </Form.Item>
          <Form.Item label={t("tasks.col.start")} name="startTime" rules={[{ required: true, message: t("tasks.startRequired") }]}>
            <DatePicker showTime style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label={t("tasks.col.end")} name="endTime" rules={[{ required: true, message: t("tasks.endRequired") }]}>
            <DatePicker showTime style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label={t("tasks.anonymous")} name="anonymousFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label={t("tasks.allowSave")} name="allowSaveFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label={t("tasks.allowTimeoutSubmit")} name="allowTimeoutSubmitFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label={t("tasks.allowRetake")} name="allowRetakeFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t("tasks.edit")}
        open={editOpen}
        onCancel={() => {
          setEditOpen(false);
          setEditingTaskId(null);
          editForm.resetFields();
        }}
        onOk={() => void handleUpdate()}
        confirmLoading={updateTaskMutation.isPending}
        destroyOnHidden
      >
        <Form form={editForm} layout="vertical">
          <Form.Item label={t("tasks.name")} name="taskName" rules={[{ required: true, message: t("tasks.nameRequired") }]}>
            <Input placeholder={t("tasks.namePlaceholder")} />
          </Form.Item>
          <Form.Item label={t("tasks.scale")} name="scaleId" rules={[{ required: true, message: t("tasks.scaleRequired") }]}>
            <Select
              placeholder={t("tasks.scalePlaceholder")}
              loading={scaleOptionsQuery.isLoading}
              options={(scaleOptionsQuery.data?.list ?? []).map((item) => ({
                label: `${item.scaleName} (${item.scaleCode})`,
                value: item.id
              }))}
            />
          </Form.Item>
          <Form.Item label={t("tasks.mode")} name="taskMode" rules={[{ required: true, message: t("tasks.modeRequired") }]}>
            <Select
              options={[
                { label: t("tasks.mode.screening"), value: "SCREENING" },
                { label: t("tasks.mode.retest"), value: "RETEST" },
                { label: t("tasks.mode.followUp"), value: "FOLLOW_UP" }
              ]}
            />
          </Form.Item>
          <Form.Item label={t("tasks.col.start")} name="startTime" rules={[{ required: true, message: t("tasks.startRequired") }]}>
            <DatePicker showTime style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label={t("tasks.col.end")} name="endTime" rules={[{ required: true, message: t("tasks.endRequired") }]}>
            <DatePicker showTime style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label={t("tasks.anonymous")} name="anonymousFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label={t("tasks.allowSave")} name="allowSaveFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label={t("tasks.allowTimeoutSubmit")} name="allowTimeoutSubmitFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label={t("tasks.allowRetake")} name="allowRetakeFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t("tasks.assignTitle")}
        open={assignOpen}
        onCancel={() => setAssignOpen(false)}
        onOk={() => void handleAssign()}
        confirmLoading={assignGroupMutation.isPending || assignUserMutation.isPending}
        destroyOnHidden
      >
        <Form form={assignForm} layout="vertical" initialValues={{ targetType: "GROUP" }}>
          <Form.Item label={t("tasks.assignType")} name="targetType" rules={[{ required: true, message: t("tasks.assignTypeRequired") }]}>
            <Select
              options={[
                { label: t("tasks.assignByGroup"), value: "GROUP" },
                { label: t("tasks.assignByUser"), value: "USER" }
              ]}
            />
          </Form.Item>
          <Form.Item
            label={t("tasks.targetIds")}
            name="targetIds"
            rules={[{ required: true, message: t("tasks.targetIdsRequired") }]}
            extra={t("tasks.targetIdsExtra")}
          >
            <Input.TextArea rows={5} placeholder={t("tasks.targetIdsPlaceholder")} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
