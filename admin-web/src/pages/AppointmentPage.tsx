import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Card, DatePicker, Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag, Typography, message } from "antd";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useSession } from "../auth/session";
import { Permission } from "../components/Permission";
import { createAppointment, createSchedule, fetchCounselorSchedules, fetchMyAppointments, type AppointmentSummary } from "../features/appointments/api";
import { createCounselingRecord, type CreateCounselingRecordRequest } from "../features/counseling-records/api";

function appointmentColor(status: string) {
  switch (status) {
    case "COMPLETED":
      return "green";
    case "CANCELLED":
      return "red";
    default:
      return "blue";
  }
}

export function AppointmentPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { currentRole } = useSession();
  const isUserView = currentRole === "USER";

  const [scheduleCounselorId, setScheduleCounselorId] = useState<number | null>(null);
  const [appointmentOpen, setAppointmentOpen] = useState(false);
  const [recordOpen, setRecordOpen] = useState(false);
  const [createScheduleOpen, setCreateScheduleOpen] = useState(false);
  const [selectedAppointment, setSelectedAppointment] = useState<AppointmentSummary | null>(null);
  const [createdAppointment, setCreatedAppointment] = useState<{
    id: number;
    appointmentStatus: string;
    counselorUserId: number;
    scheduleLabel?: string;
    remark?: string;
  } | null>(null);
  const [appointmentForm] = Form.useForm();
  const [recordForm] = Form.useForm<CreateCounselingRecordRequest>();
  const [scheduleForm] = Form.useForm();

  const appointmentsQuery = useQuery({
    queryKey: ["appointments", "my"],
    queryFn: fetchMyAppointments
  });

  const schedulesQuery = useQuery({
    queryKey: ["appointments", "schedules", scheduleCounselorId],
    queryFn: () => fetchCounselorSchedules(scheduleCounselorId ?? 0),
    enabled: Boolean(scheduleCounselorId)
  });

  const createAppointmentMutation = useMutation({
    mutationFn: createAppointment,
    onSuccess: async (data, variables) => {
      const matchedSchedule = (schedulesQuery.data ?? []).find((item) => item.id === variables.scheduleId);
      setCreatedAppointment({
        id: data.id,
        appointmentStatus: data.appointmentStatus,
        counselorUserId: variables.counselorUserId,
        scheduleLabel: matchedSchedule
          ? `${matchedSchedule.scheduleDate} ${matchedSchedule.startTime}-${matchedSchedule.endTime}`
          : undefined,
        remark: variables.remark
      });
      message.success("Appointment created");
      setAppointmentOpen(false);
      appointmentForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["appointments"] });
    }
  });

  const createRecordMutation = useMutation({
    mutationFn: createCounselingRecord,
    onSuccess: async () => {
      message.success("Counseling record saved");
      setRecordOpen(false);
      recordForm.resetFields();
      setSelectedAppointment(null);
      await queryClient.invalidateQueries({ queryKey: ["appointments"] });
    }
  });

  const createScheduleMutation = useMutation({
    mutationFn: createSchedule,
    onSuccess: async () => {
      message.success("Schedule created");
      setCreateScheduleOpen(false);
      scheduleForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["appointments", "schedules"] });
    }
  });

  const appointmentColumns = useMemo(
    () => [
      { title: "Appointment ID", dataIndex: "id", key: "id", width: 120 },
      { title: "Counselor User ID", dataIndex: "counselorUserId", key: "counselorUserId", width: 160 },
      { title: "Schedule ID", dataIndex: "scheduleId", key: "scheduleId", width: 120 },
      {
        title: "Status",
        dataIndex: "appointmentStatus",
        key: "appointmentStatus",
        width: 140,
        render: (value: string) => <Tag color={appointmentColor(value)}>{value}</Tag>
      },
      { title: "Source", dataIndex: "sourceType", key: "sourceType", width: 140 },
      { title: "Remark", dataIndex: "remark", key: "remark" },
      ...(!isUserView
        ? [
            {
              title: "Action",
              key: "action",
              render: (_: unknown, record: AppointmentSummary) => (
                <Permission roles={["COUNSELOR", "ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
                  <Button
                    type="link"
                    onClick={() => {
                      setSelectedAppointment(record);
                      recordForm.setFieldsValue({
                        appointmentId: record.id,
                        needRetestFlag: false,
                        needTransferFlag: false
                      });
                      setRecordOpen(true);
                    }}
                  >
                    Add counseling record
                  </Button>
                </Permission>
              )
            }
          ]
        : [])
    ],
    [isUserView, recordForm]
  );

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={4} style={{ marginBottom: 8 }}>
          {isUserView ? "My Appointments" : "Appointment Management"}
        </Typography.Title>
        <Typography.Text type="secondary">
          {isUserView
            ? "Create a counseling appointment, review your existing bookings, and check counselor schedules."
            : "Manage appointments, review counselor schedules, and record counseling follow-up work."}
        </Typography.Text>
      </div>

      {createdAppointment ? (
        <Alert
          type="success"
          showIcon
          message="Appointment booked successfully"
          description={
            <Space direction="vertical" size={4}>
              <Typography.Text>{`Appointment #${createdAppointment.id} is ${createdAppointment.appointmentStatus.toLowerCase()}.`}</Typography.Text>
              <Typography.Text>{`Counselor user id: ${createdAppointment.counselorUserId}`}</Typography.Text>
              {createdAppointment.scheduleLabel ? <Typography.Text>{`Schedule: ${createdAppointment.scheduleLabel}`}</Typography.Text> : null}
              {createdAppointment.remark ? <Typography.Text>{`Remark: ${createdAppointment.remark}`}</Typography.Text> : null}
            </Space>
          }
          action={
            <Space wrap>
              <Button size="small" onClick={() => navigate("/notifications")}>
                Open notifications
              </Button>
              <Button size="small" onClick={() => navigate("/my/reports")}>
                Open my reports
              </Button>
              <Button size="small" onClick={() => setCreatedAppointment(null)}>
                Dismiss
              </Button>
            </Space>
          }
        />
      ) : null}

      <Card
        title="Counselor Schedules"
        extra={
          <Space>
            <InputNumber
              min={1}
              placeholder="Counselor user id"
              value={scheduleCounselorId ?? undefined}
              onChange={(value) => setScheduleCounselorId(value ?? null)}
              style={{ width: 180 }}
            />
            <Button
              onClick={() => {
                if (scheduleCounselorId) {
                  void schedulesQuery.refetch();
                }
              }}
            >
              Search schedules
            </Button>
            <Permission roles={["COUNSELOR", "ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
              <Button onClick={() => setCreateScheduleOpen(true)}>Create my schedule</Button>
            </Permission>
          </Space>
        }
      >
        {schedulesQuery.isError ? <Alert type="warning" showIcon message="Unable to load counselor schedules right now." /> : null}
        <Table
          rowKey="id"
          loading={schedulesQuery.isLoading}
          dataSource={schedulesQuery.data ?? []}
          pagination={false}
          columns={[
            { title: "Schedule ID", dataIndex: "id" },
            { title: "Counselor User ID", dataIndex: "counselorUserId" },
            { title: "Date", dataIndex: "scheduleDate" },
            { title: "Start", dataIndex: "startTime" },
            { title: "End", dataIndex: "endTime" },
            { title: "Quota", dataIndex: "quotaCount" },
            {
              title: "Status",
              dataIndex: "status",
              render: (value: string) => <Tag color="blue">{value}</Tag>
            }
          ]}
        />
      </Card>

      <Card
        title={isUserView ? "My Bookings" : "My Appointments"}
        extra={
          <Permission roles={["USER", "COUNSELOR", "ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
            <Button type="primary" onClick={() => setAppointmentOpen(true)}>
              Create appointment
            </Button>
          </Permission>
        }
      >
        {appointmentsQuery.isError ? <Alert type="warning" showIcon message="Unable to load appointment data right now." /> : null}
        {isUserView ? (
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="After booking, return here to review your booking status and check notifications for follow-up updates."
          />
        ) : null}
        <Table
          rowKey="id"
          loading={appointmentsQuery.isLoading}
          dataSource={appointmentsQuery.data ?? []}
          pagination={false}
          columns={appointmentColumns}
        />
      </Card>

      <Modal
        title="Create Appointment"
        open={appointmentOpen}
        onCancel={() => setAppointmentOpen(false)}
        onOk={() => void appointmentForm.validateFields().then((values) => createAppointmentMutation.mutateAsync(values))}
        confirmLoading={createAppointmentMutation.isPending}
        destroyOnClose
      >
        <Form
          form={appointmentForm}
          layout="vertical"
          onValuesChange={(changedValues) => {
            if (typeof changedValues.counselorUserId === "number") {
              setScheduleCounselorId(changedValues.counselorUserId);
              appointmentForm.setFieldValue("scheduleId", undefined);
            }
          }}
        >
          <Form.Item
            label="Counselor User ID"
            name="counselorUserId"
            rules={[{ required: true, message: "Please enter a counselor user id" }]}
          >
            <InputNumber min={1} style={{ width: "100%" }} placeholder="Enter counselor user id" />
          </Form.Item>
          <Form.Item label="Schedule ID" name="scheduleId" rules={[{ required: true, message: "Please select a schedule" }]}>
            <Select
              placeholder="Select a schedule"
              loading={schedulesQuery.isLoading}
              notFoundContent={scheduleCounselorId ? "No schedules found for the selected counselor" : "Enter counselor user id first"}
              options={(schedulesQuery.data ?? []).map((item) => ({
                label: `${item.scheduleDate} ${item.startTime}-${item.endTime} (${item.status})`,
                value: item.id
              }))}
            />
          </Form.Item>
          {!isUserView ? (
            <Form.Item label="Related Warning ID" name="warningId">
              <InputNumber min={1} style={{ width: "100%" }} placeholder="Optional warning id" />
            </Form.Item>
          ) : null}
          <Form.Item label="Remark" name="remark">
            <Input.TextArea rows={4} placeholder="Optional appointment remark" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={selectedAppointment ? `Counseling Record - Appointment #${selectedAppointment.id}` : "Counseling Record"}
        open={recordOpen}
        onCancel={() => setRecordOpen(false)}
        onOk={() => void recordForm.validateFields().then((values) => createRecordMutation.mutateAsync(values))}
        confirmLoading={createRecordMutation.isPending}
        destroyOnClose
      >
        <Form form={recordForm} layout="vertical">
          <Form.Item name="appointmentId" hidden>
            <Input />
          </Form.Item>
          <Form.Item label="Summary" name="summaryText">
            <Input.TextArea rows={4} placeholder="Summarize the counseling discussion" />
          </Form.Item>
          <Form.Item label="Suggestions" name="suggestionText">
            <Input.TextArea rows={4} placeholder="Record the follow-up suggestions" />
          </Form.Item>
          <Form.Item label="Need Retest" name="needRetestFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label="Need Transfer" name="needTransferFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="Create My Schedule"
        open={createScheduleOpen}
        onCancel={() => setCreateScheduleOpen(false)}
        onOk={() =>
          void scheduleForm.validateFields().then((values) =>
            createScheduleMutation.mutateAsync({
              // eslint-disable-next-line @typescript-eslint/no-unsafe-member-access
              scheduleDate: values.scheduleDate.format("YYYY-MM-DD"),
              startTime: values.startTime.toISOString(),
              endTime: values.endTime.toISOString(),
              quotaCount: values.quotaCount
            })
          )
        }
        confirmLoading={createScheduleMutation.isPending}
        destroyOnClose
      >
        <Form form={scheduleForm} layout="vertical" initialValues={{ quotaCount: 1 }}>
          <Form.Item label="Date" name="scheduleDate" rules={[{ required: true, message: "Please select a date" }]}>
            <DatePicker style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label="Start time" name="startTime" rules={[{ required: true, message: "Please select start time" }]}>
            <DatePicker showTime style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label="End time" name="endTime" rules={[{ required: true, message: "Please select end time" }]}>
            <DatePicker showTime style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label="Quota (max bookings)" name="quotaCount" rules={[{ required: true }]}>
            <InputNumber min={1} max={50} style={{ width: "100%" }} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
