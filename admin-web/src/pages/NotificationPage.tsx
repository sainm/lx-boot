import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Card, Empty, Space, Tag, Typography, message } from "antd";
import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { useSession } from "../auth/session";
import { fetchMyNotifications, markNotificationRead, type MyNotification } from "../features/notifications/api";

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

function resolveNotificationAction(item: MyNotification, currentRole: string) {
  if (item.targetPath) {
    if (currentRole === "USER" && (item.targetPath === "/warnings" || item.targetPath === "/auth-audit")) {
      return null;
    }
    const label = item.targetPath.startsWith("/reports/")
      ? "Open report"
      : item.targetPath.startsWith("/my/tasks/")
        ? "Open task"
        : item.targetPath.startsWith("/appointments")
          ? "Open appointments"
          : item.targetPath.startsWith("/warnings")
            ? "Open warnings"
            : "Open related page";
    return {
      label,
      path: item.targetPath
    };
  }

  if (item.bizType === "APPOINTMENT" || item.notificationType.startsWith("APPOINTMENT_")) {
    return {
      label: "Open appointments",
      path: "/appointments"
    };
  }

  if ((item.bizType === "WARNING" || item.notificationType.startsWith("WARNING_")) && currentRole !== "USER") {
    return {
      label: "Open warnings",
      path: "/warnings"
    };
  }

  if ((item.bizType === "INTERVENTION" || item.notificationType.startsWith("INTERVENTION_")) && currentRole !== "USER") {
    return {
      label: "Open warnings",
      path: "/warnings"
    };
  }

  return null;
}

export function NotificationPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { currentRole } = useSession();
  const notificationsQuery = useQuery({
    queryKey: ["notifications", "my"],
    queryFn: fetchMyNotifications
  });

  const markReadMutation = useMutation({
    mutationFn: markNotificationRead,
    onSuccess: async () => {
      message.success("Notification marked as read.");
      await queryClient.invalidateQueries({ queryKey: ["notifications"] });
    }
  });

  const unreadCount = useMemo(() => (notificationsQuery.data ?? []).filter((item) => !item.readFlag).length, [notificationsQuery.data]);

  const openNotificationTarget = async (item: MyNotification) => {
    const action = resolveNotificationAction(item, currentRole);
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
      <div>
        <Typography.Title level={4}>Notifications</Typography.Title>
        <Typography.Text type="secondary">
          Review recent updates, mark messages as read, and jump back into the related workflow.
        </Typography.Text>
      </div>

      {notificationsQuery.isError ? <Alert type="warning" showIcon message="Unable to load notifications right now." /> : null}

      <Alert
        type="info"
        showIcon
        message={`Unread notifications: ${unreadCount}`}
        description="Appointment updates can take you back to your booking flow. Staff-facing warning and intervention alerts stay linked to the warning workspace."
      />

      {notificationsQuery.data?.length ? (
        notificationsQuery.data.map((item) => {
          const action = resolveNotificationAction(item, currentRole);
          return (
            <Card
              key={item.id}
              size="small"
              style={{ borderColor: item.readFlag ? undefined : "#1677ff" }}
              title={
                <Space wrap>
                  <span>{item.title}</span>
                  <Tag color={item.readFlag ? "default" : "blue"}>{item.readFlag ? "Read" : "Unread"}</Tag>
                  <Tag color={notificationColor(item.notificationType)}>{item.notificationType}</Tag>
                  {item.bizType ? <Tag>{item.bizType}</Tag> : null}
                  {item.bizId ? <Tag>{`#${item.bizId}`}</Tag> : null}
                </Space>
              }
              extra={
                <Space wrap>
                  {action ? (
                    <Button type="link" onClick={() => void openNotificationTarget(item)} loading={markReadMutation.isPending}>
                      {action.label}
                    </Button>
                  ) : null}
                  {!item.readFlag ? (
                    <Button type="link" loading={markReadMutation.isPending} onClick={() => markReadMutation.mutate(item.id)}>
                      Mark as read
                    </Button>
                  ) : null}
                </Space>
              }
            >
              <Typography.Paragraph style={{ marginBottom: 8, whiteSpace: "pre-wrap" }}>{item.content}</Typography.Paragraph>
              <Typography.Text type="secondary">
                {item.createdAt}
                {item.readTime ? ` | Read at ${item.readTime}` : ""}
              </Typography.Text>
            </Card>
          );
        })
      ) : (
        <Empty description="No notifications yet" />
      )}
    </Space>
  );
}
