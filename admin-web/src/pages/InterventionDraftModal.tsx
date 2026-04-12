import { useMutation } from "@tanstack/react-query";
import { Button, Form, Input, message, Modal, Space, Switch, Tabs } from "antd";
import { useEffect, useState } from "react";
import { closeIntervention, createIntervention } from "../features/interventions/api";
import type { InterventionDraft } from "../features/interventions/types";
import { useI18n } from "../i18n/provider";

type Props = {
  open: boolean;
  warningId: number | null;
  onClose: () => void;
  onSuccess?: () => void;
};

export function InterventionDraftModal({ open, warningId, onClose, onSuccess }: Props) {
  const { t } = useI18n();
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
    void message.success(t("intervention.submitted", { warningId: result.warningId, status: result.status }));
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
    void message.success(t("intervention.closed", { warningId: result.warningId }));
    onSuccess?.();
    onClose();
  };

  return (
    <Modal
      title={t("intervention.title")}
      open={open}
      onCancel={onClose}
      width={760}
      footer={[
        <Button key="cancel" onClick={onClose}>
          {t("intervention.cancel")}
        </Button>,
        <Button
          key="submit"
          type="primary"
          loading={createMutation.isPending}
          onClick={() => void handleSubmitIntervention()}
        >
          {t("intervention.submit")}
        </Button>,
        <Button
          key="close"
          type="primary"
          danger
          loading={closeMutation.isPending}
          onClick={() => void handleCloseIntervention()}
        >
          {t("intervention.close")}
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
              label: t("intervention.planTab"),
              children: (
                <Space direction="vertical" style={{ width: "100%" }} size={16}>
                  <Form.Item
                    label={t("intervention.plan")}
                    name="planText"
                    rules={[{ required: true, message: t("intervention.planRequired") }]}
                  >
                    <Input.TextArea rows={5} placeholder={t("intervention.planPlaceholder")} />
                  </Form.Item>
                  <Form.Item label={t("intervention.summary")} name="summaryText">
                    <Input.TextArea rows={4} placeholder={t("intervention.summaryPlaceholder")} />
                  </Form.Item>
                  <Form.Item label={t("intervention.needRetest")} name="needRetestFlag" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                  <Form.Item label={t("intervention.needTransfer")} name="needTransferFlag" valuePropName="checked">
                    <Switch />
                  </Form.Item>
                </Space>
              )
            },
            {
              key: "close",
              label: t("intervention.closeTab"),
              children: (
                <Form.Item
                  label={t("intervention.closeSummary")}
                  name="closeSummary"
                  rules={[{ required: true, message: t("intervention.closeRequired") }]}
                >
                  <Input.TextArea rows={8} placeholder={t("intervention.closePlaceholder")} />
                </Form.Item>
              )
            }
          ]}
        />
      </Form>
    </Modal>
  );
}
