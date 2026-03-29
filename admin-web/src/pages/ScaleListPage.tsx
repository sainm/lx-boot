import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Alert, Button, Form, Input, Modal, Space, Switch, Table, Tag, Typography, message } from "antd";
import { Permission } from "../components/Permission";
import { createScale, fetchScalePage } from "../features/scales/api";

export function ScaleListPage() {
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();
  const queryClient = useQueryClient();

  const scaleQuery = useQuery({
    queryKey: ["scales", { page: 1, size: 20 }],
    queryFn: () => fetchScalePage({ page: 1, size: 20 })
  });

  const createScaleMutation = useMutation({
    mutationFn: createScale,
    onSuccess: async () => {
      message.success("量表创建成功");
      setOpen(false);
      form.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["scales"] });
    }
  });

  const handleCreate = async () => {
    const values = await form.validateFields();
    await createScaleMutation.mutateAsync(values);
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 16 }}>
        <div>
          <Typography.Title level={4}>量表管理</Typography.Title>
          <Typography.Text type="secondary">这里管理量表基础信息，创建后可以继续维护维度、题目和结果规则。</Typography.Text>
        </div>
        <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
          <Button type="primary" onClick={() => setOpen(true)}>
            新建量表
          </Button>
        </Permission>
      </div>

      <Space>
        <Input placeholder="按量表名称搜索" style={{ width: 260 }} />
        <Button>查询</Button>
      </Space>

      {scaleQuery.isError ? (
        <Alert type="warning" showIcon message="当前暂时无法获取量表数据，后端接口可用后会自动恢复。" />
      ) : null}

      <Table
        rowKey="id"
        loading={scaleQuery.isLoading}
        dataSource={scaleQuery.data?.list ?? []}
        pagination={false}
        columns={[
          { title: "量表编码", dataIndex: "scaleCode" },
          { title: "量表名称", dataIndex: "scaleName" },
          { title: "版本", dataIndex: "versionNo" },
          {
            title: "状态",
            dataIndex: "status",
            render: (value: string) => <Tag color="gold">{value}</Tag>
          },
          {
            title: "操作",
            render: () => (
              <Permission roles={["ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
                <Button type="link">查看详情</Button>
              </Permission>
            )
          }
        ]}
      />

      <Modal
        title="新建量表"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={() => void handleCreate()}
        confirmLoading={createScaleMutation.isPending}
        destroyOnClose
      >
        <Form form={form} layout="vertical" initialValues={{ versionNo: "v1", anonymousSupported: false }}>
          <Form.Item label="量表编码" name="scaleCode" rules={[{ required: true, message: "请输入量表编码" }]}>
            <Input placeholder="例如：SCL-STRESS-01" />
          </Form.Item>
          <Form.Item label="量表名称" name="scaleName" rules={[{ required: true, message: "请输入量表名称" }]}>
            <Input placeholder="请输入量表名称" />
          </Form.Item>
          <Form.Item label="适用对象" name="applicableTarget">
            <Input placeholder="例如：student / employee" />
          </Form.Item>
          <Form.Item label="版本号" name="versionNo">
            <Input placeholder="v1" />
          </Form.Item>
          <Form.Item label="量表描述" name="description">
            <Input.TextArea rows={3} placeholder="请输入量表简介" />
          </Form.Item>
          <Form.Item label="报告模板" name="reportTemplate">
            <Input.TextArea rows={4} placeholder="可选：系统报告模板说明" />
          </Form.Item>
          <Form.Item label="支持匿名" name="anonymousSupported" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
