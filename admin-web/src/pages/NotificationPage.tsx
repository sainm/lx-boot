import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Alert,
  Button,
  Card,
  Empty,
  Form,
  Grid,
  Input,
  InputNumber,
  List,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message
} from "antd";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useSession } from "../auth/session";
import {
  deactivateMyDevice,
  fetchMyDevices,
  fetchMyNotifications,
  fetchAdminNotificationOpsFeed,
  fetchNotificationDeliveries,
  fetchNotificationDeliveryOpsSummary,
  fetchNotificationPolicies,
  markNotificationRead,
  registerMyDevice,
  retryNotificationDeliveriesBatch,
  retryNotificationDeliveries,
  upsertNotificationPolicy,
  type AdminNotificationOpsItem,
  type MyNotification
} from "../features/notifications/api";
import { useI18n } from "../i18n/provider";

type DeliveryModalNotification = {
  id: number;
  notificationType: string;
  title: string;
};

function notificationColor(notificationType: string) {
  if (notificationType.startsWith("APPOINTMENT_")) {
    return "blue";
  }
  if (notificationType.startsWith("WARNING_")) {
    return "red";
  }
  if (notificationType.startsWith("INTERVENTION_")) {
    return "purple";
  }
  return "default";
}

function resolveNotificationAction(item: MyNotification, currentRole: string, t: (key: string) => string) {
  if (item.targetPath) {
    if (currentRole === "USER" && (item.targetPath === "/warnings" || item.targetPath === "/auth-audit")) {
      return null;
    }
    const label = item.targetPath.startsWith("/reports/")
      ? t("notifications.openReport")
      : item.targetPath.startsWith("/my/tasks/")
        ? t("notifications.openTask")
        : item.targetPath.startsWith("/appointments")
          ? t("notifications.openAppointments")
          : item.targetPath.startsWith("/warnings")
            ? t("notifications.openWarnings")
            : t("notifications.openRelated");
    return {
      label,
      path: item.targetPath
    };
  }

  if (item.bizType === "APPOINTMENT" || item.notificationType.startsWith("APPOINTMENT_")) {
    return {
      label: t("notifications.openAppointments"),
      path: "/appointments"
    };
  }

  if ((item.bizType === "WARNING" || item.notificationType.startsWith("WARNING_")) && currentRole !== "USER") {
    return {
      label: t("notifications.openWarnings"),
      path: "/warnings"
    };
  }

  if ((item.bizType === "INTERVENTION" || item.notificationType.startsWith("INTERVENTION_")) && currentRole !== "USER") {
    return {
      label: t("notifications.openWarnings"),
      path: "/warnings"
    };
  }

  return null;
}

export function NotificationPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [deviceForm] = Form.useForm<{ deviceType: string; deviceId: string; pushToken?: string; appVersion?: string }>();
  const [policyForm] = Form.useForm<{ notificationType: string; inAppEnabled: boolean; pushEnabled: boolean; cooldownMinutes: number }>();
  const [opsFilterForm] = Form.useForm<{ notificationType?: string; bizType?: string; deliveryStatus?: string }>();
  const [filterMode, setFilterMode] = useState<"ALL" | "UNREAD">("ALL");
  const [selectedNotification, setSelectedNotification] = useState<DeliveryModalNotification | null>(null);
  const [opsFilter, setOpsFilter] = useState<{ notificationType?: string; bizType?: string; deliveryStatus?: string }>({});
  const [selectedOpsIds, setSelectedOpsIds] = useState<Array<string | number>>([]);
  const { currentRole } = useSession();
  const adminNotificationOps = currentRole !== "USER";
  const notificationsQuery = useQuery({
    queryKey: ["notifications", "my"],
    queryFn: fetchMyNotifications
  });
  const devicesQuery = useQuery({
    queryKey: ["notifications", "devices"],
    queryFn: fetchMyDevices
  });
  const deliverySummaryQuery = useQuery({
    queryKey: ["notifications", "delivery-summary"],
    queryFn: fetchNotificationDeliveryOpsSummary,
    enabled: adminNotificationOps
  });
  const adminOpsFeedQuery = useQuery({
    queryKey: ["notifications", "ops-feed", opsFilter],
    queryFn: () => fetchAdminNotificationOpsFeed({ ...opsFilter, limit: 20 }),
    enabled: adminNotificationOps
  });
  const policiesQuery = useQuery({
    queryKey: ["notifications", "policies"],
    queryFn: fetchNotificationPolicies,
    enabled: adminNotificationOps
  });
  const deliveriesQuery = useQuery({
    queryKey: ["notifications", "deliveries", selectedNotification?.id],
    queryFn: () => fetchNotificationDeliveries(selectedNotification!.id),
    enabled: adminNotificationOps && selectedNotification != null
  });

  const markReadMutation = useMutation({
    mutationFn: markNotificationRead,
    onSuccess: async () => {
      message.success(t("notifications.markReadSuccess"));
      await queryClient.invalidateQueries({ queryKey: ["notifications"] });
    }
  });
  const registerDeviceMutation = useMutation({
    mutationFn: registerMyDevice,
    onSuccess: async () => {
      message.success(t("notifications.deviceRegistered"));
      deviceForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["notifications", "devices"] });
    }
  });
  const deactivateDeviceMutation = useMutation({
    mutationFn: deactivateMyDevice,
    onSuccess: async () => {
      message.success(t("notifications.deviceDeactivated"));
      await queryClient.invalidateQueries({ queryKey: ["notifications", "devices"] });
    }
  });
  const upsertPolicyMutation = useMutation({
    mutationFn: upsertNotificationPolicy,
    onSuccess: async () => {
      message.success(t("notifications.policySaved"));
      policyForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["notifications", "policies"] });
    }
  });
  const retryDeliveriesMutation = useMutation({
    mutationFn: ({ notificationId, deliveryChannel }: { notificationId: number; deliveryChannel?: string }) =>
      retryNotificationDeliveries(notificationId, deliveryChannel),
    onSuccess: async (_, variables) => {
      message.success(t("notifications.deliveryRetried", { channel: variables.deliveryChannel ?? "ALL" }));
      await queryClient.invalidateQueries({ queryKey: ["notifications", "deliveries", variables.notificationId] });
      await queryClient.invalidateQueries({ queryKey: ["notifications", "delivery-summary"] });
    }
  });
  const retryDeliveriesBatchMutation = useMutation({
    mutationFn: retryNotificationDeliveriesBatch,
    onSuccess: async (result) => {
      message.success(t("notifications.batchRetried", { count: result.retriedCount }));
      setSelectedOpsIds([]);
      await queryClient.invalidateQueries({ queryKey: ["notifications", "ops-feed"] });
      await queryClient.invalidateQueries({ queryKey: ["notifications", "delivery-summary"] });
    }
  });

  const unreadCount = useMemo(() => (notificationsQuery.data ?? []).filter((item) => !item.readFlag).length, [notificationsQuery.data]);
  const visibleNotifications = useMemo(
    () =>
      filterMode === "UNREAD"
        ? (notificationsQuery.data ?? []).filter((item) => !item.readFlag)
        : (notificationsQuery.data ?? []),
    [filterMode, notificationsQuery.data]
  );
  const adminOpsItems = adminOpsFeedQuery.data ?? [];
  const failedErrorGroups = useMemo(() => {
    const counts = new Map<string, number>();
    adminOpsItems
      .filter((item) => item.failedDeliveries > 0)
      .forEach((item) => {
        const key = item.latestErrorMessage?.trim() || t("notifications.opsUnknownError");
        counts.set(key, (counts.get(key) ?? 0) + item.failedDeliveries);
      });
    return Array.from(counts.entries())
      .map(([error, count]) => ({ error, count }))
      .sort((left, right) => right.count - left.count)
      .slice(0, 5);
  }, [adminOpsItems, t]);

  const openNotificationTarget = async (item: MyNotification) => {
    const action = resolveNotificationAction(item, currentRole, t);
    if (!action) {
      return;
    }
    if (!item.readFlag) {
      await markReadMutation.mutateAsync(item.id);
    }
    navigate(action.path);
  };

  const handleRegisterDevice = async () => {
    const values = await deviceForm.validateFields();
    await registerDeviceMutation.mutateAsync({
      deviceType: values.deviceType.trim(),
      deviceId: values.deviceId.trim(),
      pushToken: values.pushToken?.trim() || undefined,
      appVersion: values.appVersion?.trim() || undefined
    });
  };

  const handleSavePolicy = async () => {
    const values = await policyForm.validateFields();
    await upsertPolicyMutation.mutateAsync({
      notificationType: values.notificationType.trim().toUpperCase(),
      inAppEnabled: values.inAppEnabled,
      pushEnabled: values.pushEnabled,
      cooldownMinutes: values.cooldownMinutes
    });
  };

  const handleSearchOps = async () => {
    const values = await opsFilterForm.validateFields();
    setOpsFilter({
      notificationType: values.notificationType?.trim() || undefined,
      bizType: values.bizType?.trim() || undefined,
      deliveryStatus: values.deliveryStatus || undefined
    });
  };

  const handleResetOps = () => {
    opsFilterForm.resetFields();
    setOpsFilter({});
    setSelectedOpsIds([]);
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div
        style={{
          padding: isMobile ? 18 : 20,
          borderRadius: 20,
          background: "linear-gradient(160deg, rgba(245,249,252,0.96) 0%, rgba(255,255,255,0.96) 100%)",
          border: "1px solid #dfe7f0"
        }}
      >
        <Typography.Title level={4} style={{ margin: 0 }}>
          {t("notifications.title")}
        </Typography.Title>
        <div style={{ height: 8 }} />
        <Typography.Text type="secondary">{t("notifications.subtitle")}</Typography.Text>
      </div>

      {notificationsQuery.isError ? <Alert type="warning" showIcon message={t("notifications.error")} /> : null}

      <Alert
        type="info"
        showIcon
        style={{ borderRadius: 18 }}
        message={t("notifications.unreadSummary", { count: unreadCount })}
        description={t("notifications.summaryDesc")}
      />

      <Space
        wrap
        style={
          isMobile
            ? {
                position: "sticky",
                top: 64,
                zIndex: 4,
                background: "rgba(246,248,251,0.96)",
                paddingBottom: 4
              }
            : undefined
        }
      >
        <Button block={isMobile} type={filterMode === "ALL" ? "primary" : "default"} onClick={() => setFilterMode("ALL")}>
          {t("notifications.filter.all")}
        </Button>
        <Button block={isMobile} type={filterMode === "UNREAD" ? "primary" : "default"} onClick={() => setFilterMode("UNREAD")}>
          {t("notifications.filter.unread")}
        </Button>
      </Space>

      {visibleNotifications.length ? (
        visibleNotifications.map((item) => {
          const action = resolveNotificationAction(item, currentRole, t);
          const actionButtons = (
            <Space direction={isMobile ? "vertical" : "horizontal"} wrap style={{ width: isMobile ? "100%" : undefined }}>
              {action ? (
                <Button
                  type={isMobile ? "primary" : "link"}
                  block={isMobile}
                  size={isMobile ? "large" : "middle"}
                  onClick={() => void openNotificationTarget(item)}
                  loading={markReadMutation.isPending}
                >
                  {action.label}
                </Button>
              ) : null}
              {adminNotificationOps ? (
                <Button
                  type={isMobile ? "default" : "link"}
                  block={isMobile}
                  size={isMobile ? "large" : "middle"}
                  onClick={() => setSelectedNotification(item)}
                >
                  {t("notifications.viewDeliveries")}
                </Button>
              ) : null}
              {!item.readFlag ? (
                <Button
                  type={isMobile ? "default" : "link"}
                  block={isMobile}
                  size={isMobile ? "large" : "middle"}
                  loading={markReadMutation.isPending}
                  onClick={() => markReadMutation.mutate(item.id)}
                >
                  {t("notifications.markRead")}
                </Button>
              ) : null}
            </Space>
          );
          return (
            <Card
              key={item.id}
              size="small"
              style={{
                borderRadius: isMobile ? 18 : 12,
                borderColor: item.readFlag ? undefined : "#1677ff",
                background: item.readFlag ? undefined : "#f7fbff",
                boxShadow: isMobile ? "0 12px 28px rgba(19, 51, 78, 0.08)" : undefined
              }}
              title={
                <Space direction={isMobile ? "vertical" : "horizontal"} size={8} style={{ width: "100%" }}>
                  <Typography.Text strong style={{ fontSize: isMobile ? 16 : undefined }}>
                    {item.title}
                  </Typography.Text>
                  <Tag color={item.readFlag ? "default" : "blue"}>{item.readFlag ? t("notifications.read") : t("notifications.unread")}</Tag>
                  <Tag color={notificationColor(item.notificationType)}>{item.notificationType}</Tag>
                  {item.bizType ? <Tag>{item.bizType}</Tag> : null}
                  {item.bizId ? <Tag>{`#${item.bizId}`}</Tag> : null}
                </Space>
              }
              extra={isMobile ? undefined : actionButtons}
            >
              <Typography.Paragraph style={{ marginBottom: 8, whiteSpace: "pre-wrap" }}>{item.content}</Typography.Paragraph>
              <Typography.Text type="secondary">
                {item.createdAt}
                {item.readTime ? ` | ${t("notifications.readAt")} ${item.readTime}` : ""}
              </Typography.Text>
              {isMobile ? <div style={{ height: 12 }} /> : null}
              {isMobile ? actionButtons : null}
            </Card>
          );
        })
      ) : (
        <Empty description={filterMode === "UNREAD" ? t("notifications.emptyUnread") : t("notifications.empty")} />
      )}

      <Card title={t("notifications.devicesTitle")} size="small">
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Typography.Text type="secondary">{t("notifications.devicesDesc")}</Typography.Text>
          <Form form={deviceForm} layout="vertical">
            <Space direction={isMobile ? "vertical" : "horizontal"} style={{ width: "100%" }} size={12} align="start">
              <Form.Item
                label={t("notifications.deviceType")}
                name="deviceType"
                rules={[{ required: true, message: t("notifications.deviceTypeRequired") }]}
                style={{ flex: 1, width: isMobile ? "100%" : 160 }}
              >
                <Input placeholder="ANDROID" />
              </Form.Item>
              <Form.Item
                label={t("notifications.deviceId")}
                name="deviceId"
                rules={[{ required: true, message: t("notifications.deviceIdRequired") }]}
                style={{ flex: 2, width: "100%" }}
              >
                <Input placeholder={t("notifications.deviceIdPlaceholder")} />
              </Form.Item>
            </Space>
            <Space direction={isMobile ? "vertical" : "horizontal"} style={{ width: "100%" }} size={12} align="start">
              <Form.Item label={t("notifications.pushToken")} name="pushToken" style={{ flex: 2, width: "100%" }}>
                <Input placeholder={t("notifications.pushTokenPlaceholder")} />
              </Form.Item>
              <Form.Item label={t("notifications.appVersion")} name="appVersion" style={{ flex: 1, width: isMobile ? "100%" : 180 }}>
                <Input placeholder="1.0.0" />
              </Form.Item>
            </Space>
            <Button type="primary" onClick={() => void handleRegisterDevice()} loading={registerDeviceMutation.isPending}>
              {t("notifications.registerDevice")}
            </Button>
          </Form>

          <List
            dataSource={devicesQuery.data ?? []}
            locale={{ emptyText: t("notifications.devicesEmpty") }}
            renderItem={(device) => (
              <List.Item
                actions={[
                  <Popconfirm
                    key="deactivate"
                    title={t("notifications.deactivateConfirm")}
                    onConfirm={() => deactivateDeviceMutation.mutate(device.deviceId)}
                    okText={t("notifications.deactivate")}
                    cancelText={t("common.cancel")}
                  >
                    <Button type="link" size="small" loading={deactivateDeviceMutation.isPending}>
                      {t("notifications.deactivate")}
                    </Button>
                  </Popconfirm>
                ]}
              >
                <List.Item.Meta
                  title={
                    <Space wrap>
                      <Typography.Text strong>{device.deviceId}</Typography.Text>
                      <Tag color={device.activeFlag ? "green" : "default"}>
                        {device.activeFlag ? t("notifications.deviceActive") : t("notifications.deviceInactive")}
                      </Tag>
                      <Tag>{device.deviceType}</Tag>
                    </Space>
                  }
                  description={`${t("notifications.deviceTokenMasked")}: ${device.pushTokenMasked ?? "-"} | ${t("notifications.appVersion")}: ${device.appVersion ?? "-"}`}
                />
              </List.Item>
            )}
          />
        </Space>
      </Card>

      {adminNotificationOps ? (
        <Card title={t("notifications.opsTitle")} size="small">
          <Space direction="vertical" size={16} style={{ width: "100%" }}>
            <Typography.Text type="secondary">{t("notifications.opsDesc")}</Typography.Text>
            {deliverySummaryQuery.isError ? <Alert type="warning" showIcon message={t("notifications.opsLoadError")} /> : null}
            <Space wrap>
              <Tag color="blue">{t("notifications.totalPending", { count: deliverySummaryQuery.data?.totalPending ?? 0 })}</Tag>
              <Tag color="processing">{t("notifications.totalProcessing", { count: deliverySummaryQuery.data?.totalProcessing ?? 0 })}</Tag>
              <Tag color="red">{t("notifications.totalFailed", { count: deliverySummaryQuery.data?.totalFailed ?? 0 })}</Tag>
            </Space>
            <Table
              size="small"
              pagination={false}
              rowKey={(record) => `${record.deliveryChannel}-${record.deliveryStatus}`}
              dataSource={deliverySummaryQuery.data?.buckets ?? []}
              columns={[
                { title: t("notifications.deliveryChannel"), dataIndex: "deliveryChannel", width: 140 },
                { title: t("notifications.deliveryStatus"), dataIndex: "deliveryStatus", width: 160 },
                { title: t("notifications.deliveryCount"), dataIndex: "count", width: 120 }
              ]}
            />
          </Space>
        </Card>
      ) : null}

      {adminNotificationOps ? (
        <Card
          title={t("notifications.opsWorkbenchTitle")}
          size="small"
          extra={
            <Space wrap>
              <Button onClick={handleResetOps}>{t("notifications.reset")}</Button>
              <Button type="primary" onClick={() => void handleSearchOps()}>
                {t("notifications.search")}
              </Button>
              <Button
                danger
                disabled={selectedOpsIds.length === 0}
                loading={retryDeliveriesBatchMutation.isPending}
                onClick={() =>
                  retryDeliveriesBatchMutation.mutate({
                    notificationIds: selectedOpsIds.map((value) => Number(value)),
                    deliveryChannel: "PUSH"
                  })
                }
              >
                {t("notifications.retrySelected")}
              </Button>
            </Space>
          }
        >
          <Space direction="vertical" size={16} style={{ width: "100%" }}>
            <Typography.Text type="secondary">{t("notifications.opsWorkbenchDesc")}</Typography.Text>
            <Form form={opsFilterForm} layout={isMobile ? "vertical" : "inline"}>
              <Form.Item name="notificationType" label={t("notifications.policyType")}>
                <Input placeholder="WARNING_REMINDER" />
              </Form.Item>
              <Form.Item name="bizType" label={t("notifications.deliveryBizType")}>
                <Input placeholder="WARNING" />
              </Form.Item>
              <Form.Item name="deliveryStatus" label={t("notifications.deliveryStatus")}>
                <Select
                  allowClear
                  style={{ width: isMobile ? "100%" : 180 }}
                  options={[
                    { label: "FAILED", value: "FAILED" },
                    { label: "PENDING", value: "PENDING" },
                    { label: "PROCESSING", value: "PROCESSING" },
                    { label: "SENT", value: "SENT" }
                  ]}
                />
              </Form.Item>
            </Form>
            {adminOpsFeedQuery.isError ? <Alert type="warning" showIcon message={t("notifications.opsWorkbenchLoadError")} /> : null}
            <Table<AdminNotificationOpsItem>
              size="small"
              rowKey="id"
              loading={adminOpsFeedQuery.isLoading}
              dataSource={adminOpsItems}
              pagination={false}
              rowSelection={{
                selectedRowKeys: selectedOpsIds,
                onChange: (selectedRowKeys) =>
                  setSelectedOpsIds(selectedRowKeys.filter((value): value is string | number => typeof value === "string" || typeof value === "number")),
                getCheckboxProps: (record) => ({ disabled: record.failedDeliveries === 0 })
              }}
              locale={{ emptyText: t("notifications.opsWorkbenchEmpty") }}
              scroll={{ x: 1080 }}
              columns={[
                {
                  title: t("notifications.deliveryId"),
                  dataIndex: "id",
                  width: 90
                },
                {
                  title: t("notifications.policyType"),
                  dataIndex: "notificationType",
                  width: 180
                },
                {
                  title: t("notifications.opsItemTitle"),
                  dataIndex: "title",
                  render: (value: string, record) => (
                    <Space direction="vertical" size={0}>
                      <Typography.Text strong>{value}</Typography.Text>
                      <Typography.Text type="secondary">
                        {record.bizType ?? "-"}
                        {record.bizId != null ? ` / #${record.bizId}` : ""}
                      </Typography.Text>
                    </Space>
                  )
                },
                {
                  title: t("notifications.deliveryPending"),
                  dataIndex: "pendingDeliveries",
                  width: 100
                },
                {
                  title: t("notifications.deliveryProcessing"),
                  dataIndex: "processingDeliveries",
                  width: 110
                },
                {
                  title: t("notifications.deliveryFailed"),
                  dataIndex: "failedDeliveries",
                  width: 90,
                  render: (value: number) => <Tag color={value > 0 ? "red" : "default"}>{value}</Tag>
                },
                {
                  title: t("notifications.deliverySent"),
                  dataIndex: "sentDeliveries",
                  width: 90
                },
                {
                  title: t("notifications.deliveryError"),
                  dataIndex: "latestErrorMessage",
                  render: (value?: string | null) => value ?? "-"
                },
                {
                  title: t("notifications.deliveryAction"),
                  width: 140,
                  render: (_, record) => (
                    <Space wrap>
                      <Button
                        size="small"
                        type="link"
                        onClick={() =>
                          setSelectedNotification({
                            id: record.id,
                            notificationType: record.notificationType,
                            title: record.title
                          })
                        }
                      >
                        {t("notifications.viewDeliveries")}
                      </Button>
                      <Button
                        size="small"
                        type="link"
                        danger
                        disabled={record.failedDeliveries === 0}
                        onClick={() => retryDeliveriesMutation.mutate({ notificationId: record.id, deliveryChannel: "PUSH" })}
                      >
                        {t("notifications.retryChannel")}
                      </Button>
                    </Space>
                  )
                }
              ]}
            />
            <Card size="small" title={t("notifications.opsErrorClustersTitle")}>
              {failedErrorGroups.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t("notifications.opsErrorClustersEmpty")} />
              ) : (
                <Space direction="vertical" size={10} style={{ width: "100%" }}>
                  {failedErrorGroups.map((group) => (
                    <Space
                      key={group.error}
                      align="start"
                      style={{
                        width: "100%",
                        justifyContent: "space-between",
                        padding: "10px 12px",
                        borderRadius: 12,
                        border: "1px solid #ffe7ba",
                        background: "#fffdf6"
                      }}
                    >
                      <Typography.Text>{group.error}</Typography.Text>
                      <Tag color="gold">{t("notifications.opsErrorClustersCount", { count: group.count })}</Tag>
                    </Space>
                  ))}
                </Space>
              )}
            </Card>
          </Space>
        </Card>
      ) : null}

      {adminNotificationOps ? (
        <Card title={t("notifications.policyTitle")} size="small">
          <Space direction="vertical" size={16} style={{ width: "100%" }}>
            <Typography.Text type="secondary">{t("notifications.policyDesc")}</Typography.Text>
            <Form
              form={policyForm}
              layout="vertical"
              initialValues={{ inAppEnabled: true, pushEnabled: true, cooldownMinutes: 0 }}
            >
              <Space direction={isMobile ? "vertical" : "horizontal"} style={{ width: "100%" }} size={12} align="start">
                <Form.Item
                  label={t("notifications.policyType")}
                  name="notificationType"
                  rules={[{ required: true, message: t("notifications.policyTypeRequired") }]}
                  style={{ flex: 2, width: "100%" }}
                >
                  <Input placeholder="TASK_ASSIGNED" />
                </Form.Item>
                <Form.Item label={t("notifications.cooldownMinutes")} name="cooldownMinutes" style={{ width: isMobile ? "100%" : 180 }}>
                  <InputNumber min={0} style={{ width: "100%" }} />
                </Form.Item>
              </Space>
              <Space wrap>
                <Form.Item label={t("notifications.inAppEnabled")} name="inAppEnabled" valuePropName="checked">
                  <Switch />
                </Form.Item>
                <Form.Item label={t("notifications.pushEnabled")} name="pushEnabled" valuePropName="checked">
                  <Switch />
                </Form.Item>
              </Space>
              <Button type="primary" onClick={() => void handleSavePolicy()} loading={upsertPolicyMutation.isPending}>
                {t("notifications.savePolicy")}
              </Button>
            </Form>

            <Table
              size="small"
              pagination={false}
              rowKey="id"
              dataSource={policiesQuery.data ?? []}
              columns={[
                { title: t("notifications.policyType"), dataIndex: "notificationType" },
                {
                  title: t("notifications.inAppEnabled"),
                  dataIndex: "inAppEnabled",
                  width: 120,
                  render: (value: boolean) => <Tag color={value ? "green" : "default"}>{value ? "ON" : "OFF"}</Tag>
                },
                {
                  title: t("notifications.pushEnabled"),
                  dataIndex: "pushEnabled",
                  width: 120,
                  render: (value: boolean) => <Tag color={value ? "green" : "default"}>{value ? "ON" : "OFF"}</Tag>
                },
                { title: t("notifications.cooldownMinutes"), dataIndex: "cooldownMinutes", width: 140 }
              ]}
            />
          </Space>
        </Card>
      ) : null}

      <Modal
        title={
          selectedNotification
            ? t("notifications.deliveryModalTitle", { id: selectedNotification.id })
            : t("notifications.deliveryModalTitleFallback")
        }
        open={selectedNotification != null}
        onCancel={() => setSelectedNotification(null)}
        footer={[
          <Button key="close" onClick={() => setSelectedNotification(null)}>
            {t("common.close")}
          </Button>,
          <Button
            key="retry-all"
            type="primary"
            disabled={!selectedNotification}
            loading={retryDeliveriesMutation.isPending}
            onClick={() =>
              selectedNotification
                ? retryDeliveriesMutation.mutate({ notificationId: selectedNotification.id })
                : undefined
            }
          >
            {t("notifications.retryAllFailed")}
          </Button>
        ]}
        width={isMobile ? "100%" : 920}
        destroyOnClose
      >
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          {selectedNotification ? (
            <Typography.Text type="secondary">
              {selectedNotification.notificationType} | {selectedNotification.title}
            </Typography.Text>
          ) : null}
          {deliveriesQuery.isError ? <Alert type="warning" showIcon message={t("notifications.deliveryLoadError")} /> : null}
          <Table
            size="small"
            loading={deliveriesQuery.isLoading}
            pagination={false}
            rowKey="id"
            dataSource={deliveriesQuery.data ?? []}
            locale={{ emptyText: t("notifications.deliveryEmpty") }}
            columns={[
              { title: t("notifications.deliveryId"), dataIndex: "id", width: 90 },
              { title: t("notifications.deliveryChannel"), dataIndex: "deliveryChannel", width: 120 },
              {
                title: t("notifications.deliveryStatus"),
                dataIndex: "deliveryStatus",
                width: 140,
                render: (value: string) => (
                  <Tag color={value === "FAILED" ? "red" : value === "SENT" ? "green" : value === "PROCESSING" ? "processing" : "default"}>
                    {value}
                  </Tag>
                )
              },
              { title: t("notifications.receiverUserId"), dataIndex: "receiverUserId", width: 120 },
              { title: t("notifications.deliveryDeviceId"), dataIndex: "deviceId", width: 110, render: (value?: number | null) => value ?? "-" },
              { title: t("notifications.deliveryError"), dataIndex: "errorMessage", render: (value?: string | null) => value ?? "-" },
              {
                title: t("notifications.deliveryAction"),
                width: 120,
                render: (_, record: { deliveryStatus: string; deliveryChannel: string }) =>
                  selectedNotification && ["FAILED", "SKIPPED"].includes(record.deliveryStatus) ? (
                    <Button
                      type="link"
                      size="small"
                      loading={retryDeliveriesMutation.isPending}
                      onClick={() =>
                        retryDeliveriesMutation.mutate({
                          notificationId: selectedNotification.id,
                          deliveryChannel: record.deliveryChannel
                        })
                      }
                    >
                      {t("notifications.retryChannel")}
                    </Button>
                  ) : null
              }
            ]}
          />
        </Space>
      </Modal>
    </Space>
  );
}
