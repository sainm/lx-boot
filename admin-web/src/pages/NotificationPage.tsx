import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Card, Empty, Grid, Space, Tag, Typography, message } from "antd";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useSession } from "../auth/session";
import { fetchMyNotifications, markNotificationRead, type MyNotification } from "../features/notifications/api";
import { useI18n } from "../i18n/provider";

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
  const [filterMode, setFilterMode] = useState<"ALL" | "UNREAD">("ALL");
  const { currentRole } = useSession();
  const notificationsQuery = useQuery({
    queryKey: ["notifications", "my"],
    queryFn: fetchMyNotifications
  });

  const markReadMutation = useMutation({
    mutationFn: markNotificationRead,
    onSuccess: async () => {
      message.success(t("notifications.markReadSuccess"));
      await queryClient.invalidateQueries({ queryKey: ["notifications"] });
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
    </Space>
  );
}
