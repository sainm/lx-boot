import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Card, Col, Empty, Row, Space, Statistic, Table, Tag, Typography } from "antd";
import { useNavigate } from "react-router-dom";
import { fetchMyTasks, type MyAssessmentTask } from "../features/my-tasks/api";
import { fetchMyNotifications } from "../features/notifications/api";

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

export function MyTaskListPage() {
  const navigate = useNavigate();
  const tasksQuery = useQuery({
    queryKey: ["my-tasks"],
    queryFn: fetchMyTasks
  });
  const notificationsQuery = useQuery({
    queryKey: ["notifications", "my", "summary"],
    queryFn: fetchMyNotifications
  });

  const tasks = tasksQuery.data ?? [];
  const pendingCount = tasks.filter((item) => item.status !== "COMPLETED").length;
  const completedCount = tasks.filter((item) => item.status === "COMPLETED").length;
  const unreadNotifications = (notificationsQuery.data ?? []).filter((item) => !item.readFlag).length;

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={4}>My Assessment Tasks</Typography.Title>
        <Typography.Text type="secondary">
          Review your assigned questionnaires, continue unfinished work, and jump to related actions quickly.
        </Typography.Text>
      </div>

      {tasksQuery.isError ? <Alert type="warning" showIcon message="Unable to load your tasks right now." /> : null}
      {notificationsQuery.isError ? <Alert type="warning" showIcon message="Unable to load your notification summary." /> : null}

      <Row gutter={[16, 16]}>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="Pending Tasks" value={pendingCount} />
            <Button type="link" onClick={() => navigate("/my/tasks")} style={{ paddingLeft: 0 }}>
              Review tasks
            </Button>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="Completed Tasks" value={completedCount} />
            <Button type="link" onClick={() => navigate("/my/reports")} style={{ paddingLeft: 0 }}>
              Open reports
            </Button>
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="Unread Notifications" value={unreadNotifications} />
            <Space>
              <Button type="link" onClick={() => navigate("/notifications")} style={{ paddingLeft: 0 }}>
                Open notifications
              </Button>
              <Button type="link" onClick={() => navigate("/appointments")} style={{ paddingLeft: 0 }}>
                Appointments
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>

      <Card>
        {tasks.length ? (
          <Table<MyAssessmentTask>
            rowKey="taskId"
            loading={tasksQuery.isLoading}
            pagination={false}
            dataSource={tasks}
            columns={[
              { title: "Task", dataIndex: "taskName", key: "taskName" },
              { title: "Scale", dataIndex: "scaleName", key: "scaleName" },
              { title: "Due Time", dataIndex: "endTime", key: "endTime", width: 220 },
              {
                title: "Status",
                dataIndex: "status",
                key: "status",
                width: 140,
                render: (value: string) => <Tag color={taskTagColor(value)}>{value}</Tag>
              },
              {
                title: "Action",
                key: "action",
                width: 220,
                render: (_: unknown, record: MyAssessmentTask) => (
                  <Space>
                    <Button type="primary" onClick={() => navigate(`/my/tasks/${record.taskId}`)}>
                      {record.status === "COMPLETED" ? "Review" : "Start"}
                    </Button>
                    {record.status === "COMPLETED" ? (
                      <Button onClick={() => navigate(`/my/reports?taskId=${record.taskId}`)}>Open report</Button>
                    ) : null}
                  </Space>
                )
              }
            ]}
          />
        ) : (
          <Empty description="No tasks assigned yet" />
        )}
      </Card>
    </Space>
  );
}
