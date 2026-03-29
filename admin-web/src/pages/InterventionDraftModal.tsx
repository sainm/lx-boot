import { useMutation } from "@tanstack/react-query";
import { Button, Form, Input, message, Modal, Space, Switch, Tabs } from "antd";
import { useEffect, useState } from "react";
import { closeIntervention, createIntervention } from "../features/interventions/api";
import type { InterventionDraft } from "../features/interventions/types";

type Props = {
  open: boolean;
  warningId: number | null;
  onClose: () => void;
  onSuccess?: () => void;
};

export function InterventionDraftModal({ open, warningId, onClose, onSuccess }: Props) {
  const [form] = Form.useForm<InterventionDraft>();
  const [interventionId, setInterventionId] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<"plan" | "close">("plan");

  const createMutation = useMutation({
    mutationFn: createIntervention
  });
  const closeMutation = useMutation({
    mutationFn: ({
      interventionId: currentInterventionId,
      closeSummary
    }: {
      interventionId: number;
      closeSummary: string;
    }) => closeIntervention(currentInterventionId, { closeSummary })
  });

  useEffect(() => {
    if (!open) {
      form.resetFields();
      setInterventionId(null);
      setActiveTab("plan");
    } else if (warningId != null) {
      form.setFieldsValue({ warningId, needRetestFlag: false, needTransferFlag: false });
    }
  }, [form, open, warningId]);

  const handleSubmitIntervention = async () => {
    const values = await form.validateFields(["warningId", "planText"]);
    const result = await createMutation.mutateAsync({
      warningId: values.warningId,
      planText: values.planText,
      counselorUserId: undefined
    });
    setInterventionId(result.interventionId);
    message.success(`预警 ${result.warningId} 已提交干预，状态：${result.status}`);
    onSuccess?.();
  };

  const handleCloseIntervention = async () => {
    const values = await form.validateFields(["warningId", "planText", "closeSummary"]);
    const currentInterventionId =
      interventionId ??
      (await createMutation.mutateAsync({
        warningId: values.warningId,
        planText: values.planText,
        counselorUserId: undefined
      })).interventionId;

    const result = await closeMutation.mutateAsync({
      interventionId: currentInterventionId,
      closeSummary: values.closeSummary ?? ""
    });
    message.success(`预警 ${result.warningId} 已结案`);
    onSuccess?.();
    onClose();
  };

  return (
    <Modal
      title="干预记录 / 结案"
      open={open}
      onCancel={onClose}
      width={760}
      footer={[
        <Button key="cancel" onClick={onClose}>
          取消
        </Button>,
        <Button
          key="submit"
          type="primary"
          loading={createMutation.isPending}
          onClick={() => void handleSubmitIntervention()}
        >
          提交干预
        </Button>,
        <Button
          key="close"
          type="primary"
          danger
          loading={closeMutation.isPending}
          onClick={() => void handleCloseIntervention()}
        >
          结案
        </Button>
      ]}
      destroyOnClose
    >
      <Form form={form} layout="vertical">
        <Form.Item name="warningId" hidden>
          <Input />
        </Form.Item>
        <Tabs
          activeKey={activeTab}
          onChange={(key) => setActiveTab(key as "plan" | "close")}
          items={[
            {
              key: "plan",
              label: "干预记录",
              children: (
                <Space direction="vertical" style={{ width: "100%" }} size={16}>
                  <Form.Item
                    label="干预计划"
                    name="planText"
                    rules={[{ required: true, message: "请输入干预计划" }]}
                  >
                    <Input.TextArea rows={5} placeholder="例如：先进行面对面访谈，再安排一周后复测" />
                  </Form.Item>
                  <Form.Item label="咨询摘要" name="summaryText">
                    <Input.TextArea rows={4} placeholder="记录干预过程中的关键信息" />
                  </Form.Item>
                  <Form.Item label="建议复测" name="needRetestFlag" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item label="建议转介" name="needTransferFlag" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Space>
              )
            },
            {
              key: "close",
              label: "结案信息",
              children: (
                <Form.Item
                  label="结案说明"
                  name="closeSummary"
                  rules={[{ required: true, message: "请输入结案说明" }]}
                >
                  <Input.TextArea rows={8} placeholder="例如：已完成首次访谈，建议继续观察，无需追加处理" />
                </Form.Item>
              )
            }
          ]}
        />
      </Form>
    </Modal>
  );
}
