import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Card, Col, Empty, Grid, Row, Space, Statistic, Table, Tag, Typography } from "antd";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchMyTasks, type MyAssessmentTask } from "../features/my-tasks/api";
import { fetchMyNotifications } from "../features/notifications/api";
import { useI18n } from "../i18n/provider";

const LOCAL_COMPLETED_PREFIX = "psy-respondent-task-completed";

function taskTagColor(status: string) {
  switch (status) {
    case "COMPLETED":
      return "green";
    case "IN_PROGRESS":
      return "processing";
    case "OVERDUE":
      return "red";
    default:
      return "blue";
  }
}

function resolveTaskBucket(endTime: string) {
  const due = new Date(endTime);
  const now = new Date();
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const tomorrowStart = new Date(todayStart);
  tomorrowStart.setDate(todayStart.getDate() + 1);
  const threeDaysLater = new Date(todayStart);
  threeDaysLater.setDate(todayStart.getDate() + 4);
  if (due.getTime() < now.getTime()) {
    return "overdue";
  }
  if (due >= todayStart && due < tomorrowStart) {
    return "today";
  }
  if (due >= tomorrowStart && due < threeDaysLater) {
    return "soon";
  }
  return "later";
}

export function MyTaskListPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [statusFilter, setStatusFilter] = useState<"ALL" | "PENDING" | "COMPLETED" | "OVERDUE">("ALL");
  const tasksQuery = useQuery({
    queryKey: ["my-tasks"],
    queryFn: fetchMyTasks
  });
  const notificationsQuery = useQuery({
    queryKey: ["notifications", "my", "summary"],
    queryFn: fetchMyNotifications
  });

  const tasks = useMemo(() => {
    const source = tasksQuery.data ?? [];
    if (typeof window === "undefined") {
      return source;
    }
    return source.map((item) =>
      window.localStorage.getItem(`${LOCAL_COMPLETED_PREFIX}:${item.taskId}`) === "1"
        ? { ...item, status: "COMPLETED" }
        : item
    );
  }, [tasksQuery.data]);
  const pendingCount = tasks.filter((item) => item.status !== "COMPLETED").length;
  const completedCount = tasks.filter((item) => item.status === "COMPLETED").length;
  const unreadNotifications = (notificationsQuery.data ?? []).filter((item) => !item.readFlag).length;
  const filteredTasks = useMemo(() => {
    if (statusFilter === "ALL") {
      return tasks;
    }
    if (statusFilter === "PENDING") {
      return tasks.filter((item) => item.status === "IN_PROGRESS");
    }
    if (statusFilter === "COMPLETED") {
      return tasks.filter((item) => item.status === "COMPLETED");
    }
    return tasks.filter((item) => item.status === "OVERDUE");
  }, [statusFilter, tasks]);
  const groupedTasks = useMemo(() => {
    return {
      today: filteredTasks.filter((item) => resolveTaskBucket(item.endTime) === "today"),
      soon: filteredTasks.filter((item) => resolveTaskBucket(item.endTime) === "soon"),
      overdue: filteredTasks.filter((item) => resolveTaskBucket(item.endTime) === "overdue"),
      later: filteredTasks.filter((item) => resolveTaskBucket(item.endTime) === "later")
    };
  }, [filteredTasks]);

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div
        style={{
          padding: isMobile ? 18 : 20,
          borderRadius: 20,
          background: "linear-gradient(160deg, rgba(24,58,86,0.96) 0%, rgba(31,95,134,0.9) 58%, rgba(99,167,183,0.72) 100%)",
          color: "#fff"
        }}
      >
        <Typography.Title level={4} style={{ color: "#fff", margin: 0 }}>
          {t("myTasks.title")}
        </Typography.Title>
        <div style={{ height: 8 }} />
        <Typography.Text style={{ color: "rgba(255,255,255,0.82)" }}>{t("myTasks.subtitle")}</Typography.Text>
      </div>

      {tasksQuery.isError ? <Alert type="warning" showIcon message={t("myTasks.error.tasks")} /> : null}
      {notificationsQuery.isError ? <Alert type="warning" showIcon message={t("myTasks.error.notifications")} /> : null}

      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card size={isMobile ? "small" : "default"} style={{ background: "linear-gradient(180deg, #ffffff 0%, #f5f9fc 100%)" }}>
            <Statistic title={t("myTasks.pending")} value={pendingCount} valueStyle={{ fontSize: isMobile ? 28 : undefined }} />
            <Button type="link" onClick={() => navigate("/my/tasks")} style={{ paddingLeft: 0 }}>
              {t("myTasks.reviewTasks")}
            </Button>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card size={isMobile ? "small" : "default"} style={{ background: "linear-gradient(180deg, #ffffff 0%, #f7fbf8 100%)" }}>
            <Statistic title={t("myTasks.completed")} value={completedCount} valueStyle={{ fontSize: isMobile ? 28 : undefined }} />
            <Button type="link" onClick={() => navigate("/my/reports")} style={{ paddingLeft: 0 }}>
              {t("myTasks.openReports")}
            </Button>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card size={isMobile ? "small" : "default"} style={{ background: "linear-gradient(180deg, #ffffff 0%, #f7faff 100%)" }}>
            <Statistic title={t("myTasks.unread")} value={unreadNotifications} valueStyle={{ fontSize: isMobile ? 28 : undefined }} />
            <Space wrap>
              <Button type="link" onClick={() => navigate("/notifications")} style={{ paddingLeft: 0 }}>
                {t("myTasks.openNotifications")}
              </Button>
              <Button type="link" onClick={() => navigate("/appointments")} style={{ paddingLeft: 0 }}>
                {t("myTasks.appointments")}
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>

      {isMobile ? (
        <Card>
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            <Typography.Text strong>{t("myTasks.quickActions")}</Typography.Text>
            <Row gutter={[12, 12]}>
              <Col span={12}>
                <Button block size="large" onClick={() => navigate("/my/reports")}>
                  {t("myTasks.openReports")}
                </Button>
              </Col>
              <Col span={12}>
                <Button block size="large" onClick={() => navigate("/notifications")}>
                  {t("myTasks.openNotifications")}
                </Button>
              </Col>
              <Col span={24}>
                <Button block type="primary" size="large" onClick={() => navigate("/appointments")}>
                  {t("myTasks.appointments")}
                </Button>
              </Col>
            </Row>
          </Space>
        </Card>
      ) : null}

      <Card size={isMobile ? "small" : "default"}>
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Space
            wrap
            style={
              isMobile
                ? {
                    position: "sticky",
                    top: 64,
                    zIndex: 4,
                    background: "rgba(255,255,255,0.96)",
                    paddingBottom: 4
                  }
                : undefined
            }
          >
            <Button block={isMobile} type={statusFilter === "ALL" ? "primary" : "default"} onClick={() => setStatusFilter("ALL")}>
              {t("myTasks.filter.all")}
            </Button>
            <Button block={isMobile} type={statusFilter === "PENDING" ? "primary" : "default"} onClick={() => setStatusFilter("PENDING")}>
              {t("myTasks.filter.pending")}
            </Button>
            <Button block={isMobile} type={statusFilter === "COMPLETED" ? "primary" : "default"} onClick={() => setStatusFilter("COMPLETED")}>
              {t("myTasks.filter.completed")}
            </Button>
            <Button block={isMobile} type={statusFilter === "OVERDUE" ? "primary" : "default"} onClick={() => setStatusFilter("OVERDUE")}>
              {t("myTasks.filter.overdue")}
            </Button>
          </Space>

        {filteredTasks.length ? (
          isMobile ? (
            <Space direction="vertical" size={12} style={{ width: "100%" }}>
              {([
                ["today", groupedTasks.today, "myTasks.group.today"],
                ["soon", groupedTasks.soon, "myTasks.group.soon"],
                ["overdue", groupedTasks.overdue, "myTasks.group.overdue"],
                ["later", groupedTasks.later, "myTasks.group.later"]
              ] as const)
                .filter(([, items]) => items.length > 0)
                .map(([groupKey, items, labelKey]) => (
                  <Space key={groupKey} direction="vertical" size={8} style={{ width: "100%" }}>
                    <Typography.Text strong>{t(labelKey)}</Typography.Text>
                    {items.map((record) => (
                      <Card key={record.taskId} size="small" styles={{ body: { padding: 14 } }}>
                        <Space direction="vertical" size={12} style={{ width: "100%" }}>
                          <div>
                            <Typography.Title level={5} style={{ margin: 0 }}>
                              {record.taskName}
                            </Typography.Title>
                            <Typography.Text type="secondary">{record.scaleName}</Typography.Text>
                          </div>
                          <Space wrap>
                            <Tag color={taskTagColor(record.status)}>{t(`status.${record.status}`) || record.status}</Tag>
                            <Typography.Text type="secondary">{t("myTasks.col.dueTime")}: {record.endTime}</Typography.Text>
                          </Space>
                          <Space direction="vertical" size={8} style={{ width: "100%" }}>
                            <Button
                              block
                              type="primary"
                              size="large"
                              onClick={() =>
                                navigate(
                                  record.status === "COMPLETED"
                                    ? `/my/reports?taskId=${record.taskId}`
                                    : `/my/tasks/${record.taskId}`
                                )
                              }
                            >
                              {record.status === "COMPLETED" ? t("myTasks.review") : t("myTasks.start")}
                            </Button>
                            {record.status === "COMPLETED" ? (
                              <Button block size="large" onClick={() => navigate(`/my/reports?taskId=${record.taskId}`)}>
                                {t("myTasks.openReport")}
                              </Button>
                            ) : null}
                          </Space>
                        </Space>
                      </Card>
                    ))}
                  </Space>
                ))}
            </Space>
          ) : (
            <Table<MyAssessmentTask>
              rowKey="taskId"
              loading={tasksQuery.isLoading}
              pagination={false}
              dataSource={filteredTasks}
              columns={[
                { title: t("myTasks.col.task"), dataIndex: "taskName", key: "taskName" },
                { title: t("myTasks.col.scale"), dataIndex: "scaleName", key: "scaleName" },
                { title: t("myTasks.col.dueTime"), dataIndex: "endTime", key: "endTime", width: 220 },
                {
                  title: t("myTasks.col.status"),
                  dataIndex: "status",
                  key: "status",
                  width: 140,
                  render: (value: string) => <Tag color={taskTagColor(value)}>{t(`status.${value}`) || value}</Tag>
                },
                {
                  title: t("myTasks.col.action"),
                  key: "action",
                  width: 220,
                  render: (_: unknown, record: MyAssessmentTask) => (
                    <Space>
                      <Button
                        type="primary"
                        onClick={() =>
                          navigate(
                            record.status === "COMPLETED"
                              ? `/my/reports?taskId=${record.taskId}`
                              : `/my/tasks/${record.taskId}`
                          )
                        }
                      >
                        {record.status === "COMPLETED" ? t("myTasks.review") : t("myTasks.start")}
                      </Button>
                      {record.status === "COMPLETED" ? (
                        <Button onClick={() => navigate(`/my/reports?taskId=${record.taskId}`)}>{t("myTasks.openReport")}</Button>
                      ) : null}
                    </Space>
                  )
                }
              ]}
            />
          )
        ) : (
          <Empty description={t("myTasks.emptyFiltered")} />
        )}
        </Space>
      </Card>
    </Space>
  );
}
