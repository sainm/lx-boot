import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Alert, Button, DatePicker, Form, Input, Modal, Pagination, Select, Space, Switch, Table, Tag, Typography, message } from "antd";
import { Permission } from "../components/Permission";
import { assignTaskGroups, assignTaskUsers, createTask, fetchTaskPage } from "../features/tasks/api";
import { fetchScalePage } from "../features/scales/api";

const PAGE_SIZE = 20;

export function TaskListPage() {
  const [createOpen, setCreateOpen] = useState(false);
  const [assignOpen, setAssignOpen] = useState(false);
  const [assignTaskId, setAssignTaskId] = useState<number | null>(null);
  const [form] = Form.useForm();
  const [assignForm] = Form.useForm();
  const queryClient = useQueryClient();

  // filters
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
      message.success("任务创建成功");
      setCreateOpen(false);
      form.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["tasks"] });
    }
  });

  const assignGroupMutation = useMutation({
    mutationFn: ({ taskId, groupIds }: { taskId: number; groupIds: number[] }) =>
      assignTaskGroups(taskId, groupIds),
    onSuccess: async () => {
      message.success("按组分配成功");
      setAssignOpen(false);
      assignForm.resetFields();
    }
  });

  const assignUserMutation = useMutation({
    mutationFn: ({ taskId, userIds }: { taskId: number; userIds: number[] }) =>
      assignTaskUsers(taskId, userIds),
    onSuccess: async () => {
      message.success("按个人分配成功");
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

  const parseIds = (raw: string): number[] =>
    raw
      .split(/[,\n，;]/)
      .map((item) => item.trim())
      .filter(Boolean)
      .map((item) => Number(item))
      .filter((item) => Number.isInteger(item) && item > 0);

  const handleAssign = async () => {
    if (assignTaskId == null) return;
    const values = await assignForm.validateFields();
    const ids = parseIds(values.targetIds);
    if (ids.length === 0) {
      message.warning("请至少输入一个有效 ID");
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
          <Typography.Title level={4}>测评任务</Typography.Title>
          <Typography.Text type="secondary">这里管理测评任务的创建、分配和状态查看。</Typography.Text>
        </div>
        <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            创建任务
          </Button>
        </Permission>
      </div>

      <Space>
        <Input
          placeholder="按任务名称搜索"
          style={{ width: 260 }}
          value={nameInput}
          onChange={(e) => setNameInput(e.target.value)}
          onPressEnter={handleSearch}
          allowClear
          onClear={handleReset}
        />
        <Button type="primary" onClick={handleSearch}>查询</Button>
        <Button onClick={handleReset}>重置</Button>
      </Space>

      {taskQuery.isError ? (
        <Alert type="warning" showIcon message="当前暂时无法获取任务数据，后端接口可用后会自动恢复。" />
      ) : null}

      <Table
        rowKey="id"
        loading={taskQuery.isLoading}
        dataSource={taskQuery.data?.list ?? []}
        pagination={false}
        columns={[
          { title: "任务名称", dataIndex: "taskName" },
          { title: "量表", dataIndex: "scaleName" },
          { title: "模式", dataIndex: "taskMode", width: 100 },
          { title: "开始时间", dataIndex: "startTime", width: 180 },
          { title: "截止时间", dataIndex: "endTime", width: 180 },
          {
            title: "状态",
            dataIndex: "status",
            width: 100,
            render: (value: string) => <Tag color="blue">{value}</Tag>
          },
          {
            title: "操作",
            width: 160,
            render: (_, record: { id: number }) => (
              <Space>
                <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
                  <Button
                    type="link"
                    size="small"
                    onClick={() => {
                      setAssignTaskId(record.id);
                      setAssignOpen(true);
                      assignForm.setFieldsValue({ targetType: "GROUP" });
                    }}
                  >
                    分配任务
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
            showTotal={(total) => `共 ${total} 条`}
            onChange={(p) => setPage(p)}
            showSizeChanger={false}
          />
        </div>
      ) : null}

      <Modal
        title="创建任务"
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={() => void handleCreate()}
        confirmLoading={createTaskMutation.isPending}
        destroyOnClose
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
          <Form.Item label="任务名称" name="taskName" rules={[{ required: true, message: "请输入任务名称" }]}>
            <Input placeholder="例如：2026 春季新生心理普查" />
          </Form.Item>
          <Form.Item label="量表" name="scaleId" rules={[{ required: true, message: "请选择量表" }]}>
            <Select
              placeholder="请选择量表"
              loading={scaleOptionsQuery.isLoading}
              options={(scaleOptionsQuery.data?.list ?? []).map((item) => ({
                label: `${item.scaleName} (${item.scaleCode})`,
                value: item.id
              }))}
            />
          </Form.Item>
          <Form.Item label="任务模式" name="taskMode" rules={[{ required: true, message: "请选择任务模式" }]}>
            <Select
              options={[
                { label: "普查", value: "SCREENING" },
                { label: "复测", value: "RETEST" },
                { label: "随访", value: "FOLLOW_UP" }
              ]}
            />
          </Form.Item>
          <Form.Item label="开始时间" name="startTime" rules={[{ required: true, message: "请选择开始时间" }]}>
            <DatePicker showTime style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label="截止时间" name="endTime" rules={[{ required: true, message: "请选择截止时间" }]}>
            <DatePicker showTime style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label="匿名任务" name="anonymousFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="允许暂存" name="allowSaveFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="允许超时提交" name="allowTimeoutSubmitFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="允许重做" name="allowRetakeFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="任务分配"
        open={assignOpen}
        onCancel={() => setAssignOpen(false)}
        onOk={() => void handleAssign()}
        confirmLoading={assignGroupMutation.isPending || assignUserMutation.isPending}
        destroyOnClose
      >
        <Form form={assignForm} layout="vertical" initialValues={{ targetType: "GROUP" }}>
          <Form.Item label="分配方式" name="targetType" rules={[{ required: true, message: "请选择分配方式" }]}>
            <Select
              options={[
                { label: "按组分配", value: "GROUP" },
                { label: "按个人分配", value: "USER" }
              ]}
            />
          </Form.Item>
          <Form.Item
            label="目标 ID 列表"
            name="targetIds"
            rules={[{ required: true, message: "请输入目标 ID 列表" }]}
            extra="支持英文逗号、中文逗号或换行分隔，例如：101,102,103"
          >
            <Input.TextArea rows={5} placeholder="请输入组 ID 或用户 ID 列表" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
