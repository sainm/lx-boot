import { useQuery } from "@tanstack/react-query";
import { BellOutlined, CalendarOutlined, FormOutlined, ReadOutlined } from "@ant-design/icons";
import { Alert, Button, Card, Col, Empty, Grid, Row, Space, Statistic, Typography } from "antd";
import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { fetchMyNotifications } from "../features/notifications/api";
import { fetchMyReports } from "../features/reports/api";
import { fetchMyTasks } from "../features/my-tasks/api";
import { useI18n } from "../i18n/provider";
import { formatDateTime } from "../utils/date";

function actionGradient(index: number) {
  const gradients = [
    "linear-gradient(145deg, #183a56 0%, #1f5f86 60%, #63a7b7 100%)",
    "linear-gradient(145deg, #14532d 0%, #1f7a4c 60%, #7dc498 100%)",
    "linear-gradient(145deg, #6b3f16 0%, #9a5b1f 60%, #e7b36b 100%)",
    "linear-gradient(145deg, #4b2e83 0%, #6e46b5 60%, #b39af5 100%)"
  ];
  return gradients[index % gradients.length];
}

export function UserHomePage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;

  const tasksQuery = useQuery({
    queryKey: ["my-tasks"],
    queryFn: fetchMyTasks
  });
  const reportsQuery = useQuery({
    queryKey: ["reports", "my"],
    queryFn: fetchMyReports
  });
  const notificationsQuery = useQuery({
    queryKey: ["notifications", "my", "summary"],
    queryFn: fetchMyNotifications
  });

  const tasks = tasksQuery.data ?? [];
  const reports = reportsQuery.data ?? [];
  const notifications = notificationsQuery.data ?? [];

  const pendingTasks = tasks.filter((item) => item.status !== "COMPLETED");
  const completedTasks = tasks.filter((item) => item.status === "COMPLETED");
  const unreadNotifications = notifications.filter((item) => !item.readFlag);

  const recentTasks = useMemo(() => pendingTasks.slice(0, 3), [pendingTasks]);
  const recentReports = useMemo(() => reports.slice(0, 3), [reports]);

  const quickActions = [
    {
      key: "tasks",
      title: t("userHome.action.tasks.title"),
      description: t("userHome.action.tasks.desc"),
      icon: <FormOutlined />,
      onClick: () => navigate("/my/tasks")
    },
    {
      key: "reports",
      title: t("userHome.action.reports.title"),
      description: t("userHome.action.reports.desc"),
      icon: <ReadOutlined />,
      onClick: () => navigate("/my/reports")
    },
    {
      key: "notifications",
      title: t("userHome.action.notifications.title"),
      description: t("userHome.action.notifications.desc"),
      icon: <BellOutlined />,
      onClick: () => navigate("/notifications")
    },
    {
      key: "appointments",
      title: t("userHome.action.appointments.title"),
      description: t("userHome.action.appointments.desc"),
      icon: <CalendarOutlined />,
      onClick: () => navigate("/appointments")
    }
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div
        style={{
          padding: isMobile ? "18px 18px 20px" : 28,
          borderRadius: isMobile ? 22 : 24,
          background: "radial-gradient(circle at 88% 0%, rgba(255,255,255,0.24), transparent 30%), linear-gradient(145deg, rgba(24,58,86,0.98) 0%, rgba(31,95,134,0.92) 58%, rgba(99,167,183,0.78) 100%)",
          color: "#fff",
          overflow: "hidden",
          boxShadow: isMobile ? "0 16px 34px rgba(24, 58, 86, 0.18)" : undefined
        }}
      >
        <Typography.Title level={isMobile ? 3 : 2} style={{ color: "#fff", margin: 0 }}>
          {t("userHome.welcome")}
        </Typography.Title>
        <div style={{ height: 10 }} />
        <Typography.Text style={{ color: "rgba(255,255,255,0.84)", fontSize: isMobile ? 14 : 15 }}>
          {t("userHome.subtitle")}
        </Typography.Text>
      </div>

      {tasksQuery.isError ? <Alert type="warning" showIcon message={t("myTasks.error.tasks")} /> : null}
      {reportsQuery.isError ? <Alert type="warning" showIcon message={t("myReports.error")} /> : null}
      {notificationsQuery.isError ? <Alert type="warning" showIcon message={t("myTasks.error.notifications")} /> : null}

      <Row gutter={isMobile ? [8, 8] : [16, 16]}>
        <Col xs={8} md={8}>
          <Card size={isMobile ? "small" : "default"}>
            <Statistic title={t("userHome.stats.pendingTasks")} value={pendingTasks.length} />
          </Card>
        </Col>
        <Col xs={8} md={8}>
          <Card size={isMobile ? "small" : "default"}>
            <Statistic title={t("userHome.stats.reports")} value={reports.length} />
          </Card>
        </Col>
        <Col xs={8} md={8}>
          <Card size={isMobile ? "small" : "default"}>
            <Statistic title={t("userHome.stats.unreadNotifications")} value={unreadNotifications.length} />
          </Card>
        </Col>
      </Row>

      <Row gutter={isMobile ? [10, 10] : [16, 16]}>
        {quickActions.map((action, index) => (
          <Col key={action.key} xs={12} md={12}>
            <Card
              hoverable
              onClick={action.onClick}
              styles={{ body: { padding: isMobile ? 14 : 22 } }}
              style={{
                borderRadius: isMobile ? 18 : 22,
                background: actionGradient(index),
                color: "#fff",
                cursor: "pointer",
                minHeight: isMobile ? 126 : 168
              }}
            >
              <Space direction="vertical" size={isMobile ? 8 : 12} style={{ width: "100%" }}>
                <Typography.Text style={{ color: "rgba(255,255,255,0.92)", fontSize: 18 }}>
                  {action.icon}
                </Typography.Text>
                <Typography.Title level={isMobile ? 5 : 4} style={{ color: "#fff", margin: 0 }}>
                  {action.title}
                </Typography.Title>
                <Typography.Text style={{ color: "rgba(255,255,255,0.82)", display: isMobile ? "none" : undefined }}>
                  {action.description}
                </Typography.Text>
              </Space>
            </Card>
          </Col>
        ))}
      </Row>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card
            title={t("userHome.recentTasks")}
            extra={
              <Button type="link" onClick={() => navigate("/my/tasks")}>
                {t("common.viewAll")}
              </Button>
            }
          >
            {recentTasks.length ? (
              <Space direction="vertical" size={12} style={{ width: "100%" }}>
                {recentTasks.map((task) => (
                  <Card key={task.taskId} size="small" styles={{ body: { padding: 14 } }}>
                    <Space direction="vertical" size={8} style={{ width: "100%" }}>
                      <div>
                        <Typography.Text strong>{task.taskName}</Typography.Text>
                        <br />
                        <Typography.Text type="secondary">{task.scaleName}</Typography.Text>
                      </div>
                      <Typography.Text type="secondary">{t("userHome.deadline", { time: formatDateTime(task.endTime) })}</Typography.Text>
                      <Button type="primary" onClick={() => navigate(`/my/tasks/${task.taskId}`)}>
                        {t("userHome.continueTask")}
                      </Button>
                    </Space>
                  </Card>
                ))}
              </Space>
            ) : (
              <Empty description={t("userHome.emptyTasks")} />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card
            title={t("userHome.recentReports")}
            extra={
              <Button type="link" onClick={() => navigate("/my/reports")}>
                {t("common.viewAll")}
              </Button>
            }
          >
            {recentReports.length ? (
              <Space direction="vertical" size={12} style={{ width: "100%" }}>
                {recentReports.map((report) => (
                  <Card key={report.reportId} size="small" styles={{ body: { padding: 14 } }}>
                    <Space direction="vertical" size={8} style={{ width: "100%" }}>
                      <div>
                        <Typography.Text strong>{report.scaleName}</Typography.Text>
                        <br />
                        <Typography.Text type="secondary">{report.taskName}</Typography.Text>
                      </div>
                      <Typography.Text type="secondary">{t("userHome.createdAt", { time: formatDateTime(report.createdAt) })}</Typography.Text>
                      <Button type="primary" onClick={() => navigate(`/reports/${report.reportId}?resultId=${report.resultId}`)}>
                        {t("userHome.viewReport")}
                      </Button>
                    </Space>
                  </Card>
                ))}
              </Space>
            ) : (
              <Empty description={t("userHome.emptyReports")} />
            )}
          </Card>
        </Col>
      </Row>

      {completedTasks.length === 0 && reports.length === 0 && pendingTasks.length === 0 ? (
        <Alert
          type="info"
          showIcon
          message={t("userHome.emptyState.title")}
          description={t("userHome.emptyState.desc")}
        />
      ) : null}
    </Space>
  );
}
