import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Card, DatePicker, Form, Grid, Input, InputNumber, Modal, Pagination, Popconfirm, Select, Space, Switch, Table, Tag, Tooltip, Typography, message } from "antd";
import dayjs, { type Dayjs } from "dayjs";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useSession } from "../auth/session";
import { Permission } from "../components/Permission";
import {
  cancelAppointment,
  createAppointment,
  createSchedule,
  fetchAppointmentPage,
  fetchCounselorSchedules,
  fetchCounselors,
  fetchMyAppointments,
  type AppointmentSummary,
  type CounselorOption
} from "../features/appointments/api";
import { createCounselingRecord, type CreateCounselingRecordRequest } from "../features/counseling-records/api";
import { fetchDirectoryUsers, type DirectoryUser } from "../features/directory/api";
import { useI18n } from "../i18n/provider";
import { appointmentStatusLabel, scheduleStatusLabel } from "../i18n/enumLabel";
import { formatDateTime } from "../utils/date";

const PAGE_SIZE = 20;

function appointmentColor(status: string) {
  switch (status) {
    case "COMPLETED":
      return "green";
    case "CANCELLED":
      return "red";
    case "NO_SHOW":
      return "orange";
    default:
      return "blue";
  }
}

function appointmentSourceLabel(sourceType: string, t: (key: string) => string) {
  switch (sourceType) {
    case "USER":
      return t("appointments.source.user");
    case "ADMIN":
      return t("appointments.source.admin");
    default:
      return sourceType;
  }
}

function appointmentScheduleLabel(record: AppointmentSummary) {
  if (!record.scheduleDate || !record.startTime || !record.endTime) {
    return "-";
  }
  const start = formatClockTime(record.startTime);
  const end = formatClockTime(record.endTime);
  return `${record.scheduleDate} ${start}-${end}`;
}

function formatClockTime(value?: string | null) {
  if (!value) {
    return "-";
  }
  if (/^\d{2}:\d{2}/.test(value)) {
    return value.slice(0, 5);
  }
  const formatted = formatDateTime(value);
  return formatted === "-" ? "-" : formatted.slice(11, 16);
}

/**
 * A counseling record belongs to a completed appointment.  Appointments that
 * are still "CONFIRMED" but whose slot already passed are treated as completed
 * so operators can record the session (frontend review finding B3).
 */
function canAddRecord(record: AppointmentSummary) {
  if (record.appointmentStatus === "COMPLETED") {
    return true;
  }
  if (record.appointmentStatus !== "CONFIRMED") {
    return false;
  }
  const end = record.endTime ? dayjs(record.endTime) : null;
  return Boolean(end && end.isValid() && end.isBefore(dayjs()));
}

function isFinalStatus(status: string) {
  return status === "COMPLETED" || status === "CANCELLED" || status === "NO_SHOW";
}

function respondentLabel(record: AppointmentSummary, fallback: string) {
  const name = record.userDisplayName || record.userUsername;
  return name ? `${name} / #${record.userId}` : `${fallback} #${record.userId}`;
}

type ServiceFilterValues = {
  status?: string;
  userId?: number;
  counselorUserId?: number;
  dateRange?: [Dayjs | null, Dayjs | null] | null;
};

export function AppointmentPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { currentRole } = useSession();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const isUserView = currentRole === "USER";

  const [scheduleCounselorId, setScheduleCounselorId] = useState<number | null>(null);
  const [appointmentStatusFilter, setAppointmentStatusFilter] = useState<"ALL" | "CREATED" | "COMPLETED" | "CANCELLED">("ALL");
  const [appointmentOpen, setAppointmentOpen] = useState(false);
  const [recordOpen, setRecordOpen] = useState(false);
  const [createScheduleOpen, setCreateScheduleOpen] = useState(false);
  const [selectedAppointment, setSelectedAppointment] = useState<AppointmentSummary | null>(null);
  const [registerPage, setRegisterPage] = useState(1);
  const [registerFilter, setRegisterFilter] = useState<{
    status?: string;
    userId?: number;
    counselorUserId?: number;
    dateFrom?: string;
    dateTo?: string;
  }>({});
  const [userKeyword, setUserKeyword] = useState("");
  const [createdAppointment, setCreatedAppointment] = useState<{
    id: number;
    appointmentStatus: string;
    counselorUserId: number;
    counselorName?: string;
    scheduleLabel?: string;
    remark?: string;
  } | null>(null);
  const [appointmentForm] = Form.useForm();
  const [serviceFilterForm] = Form.useForm<ServiceFilterValues>();
  const [recordForm] = Form.useForm<CreateCounselingRecordRequest>();
  const [scheduleForm] = Form.useForm();

  const counselorsQuery = useQuery({
    queryKey: ["appointments", "counselors"],
    queryFn: fetchCounselors
  });

  const appointmentsQuery = useQuery({
    queryKey: ["appointments", "my"],
    queryFn: fetchMyAppointments,
    enabled: isUserView
  });

  const registerQuery = useQuery({
    queryKey: ["appointments", "register", registerFilter, registerPage],
    queryFn: () => fetchAppointmentPage({ ...registerFilter, page: registerPage, size: PAGE_SIZE }),
    enabled: !isUserView
  });

  const directoryUsersQuery = useQuery({
    queryKey: ["directory", "users", userKeyword],
    queryFn: () => fetchDirectoryUsers({ keyword: userKeyword.trim() || undefined, size: 50 }),
    enabled: !isUserView,
    staleTime: 30_000
  });

  /**
   * Booking candidates exclude disabled/deleted users: the create API rejects
   * them (APPOINTMENT_TARGET_NOT_FOUND) while the filter picker keeps them so
   * historical appointments stay searchable.
   */
  const bookingCandidateQuery = useQuery({
    queryKey: ["directory", "users", "booking-candidates", userKeyword],
    queryFn: () => fetchDirectoryUsers({ keyword: userKeyword.trim() || undefined, activeOnly: true, size: 50 }),
    enabled: !isUserView,
    staleTime: 30_000
  });

  const schedulesQuery = useQuery({
    queryKey: ["appointments", "schedules", scheduleCounselorId],
    queryFn: () => fetchCounselorSchedules(scheduleCounselorId!),
    enabled: scheduleCounselorId != null
  });

  const createAppointmentMutation = useMutation({
    mutationFn: createAppointment,
    onSuccess: async (data) => {
      const formValues = appointmentForm.getFieldsValue();
      const counselor = (counselorsQuery.data ?? []).find(
        (item: CounselorOption) => item.userId === formValues.counselorUserId
      );
      const schedule = (schedulesQuery.data ?? []).find((item) => item.id === formValues.scheduleId);
      setCreatedAppointment({
        id: data.appointmentId,
        appointmentStatus: data.status,
        counselorUserId: formValues.counselorUserId,
        counselorName: counselor?.displayName,
        scheduleLabel: schedule
          ? `${schedule.scheduleDate} ${formatClockTime(schedule.startTime)}-${formatClockTime(schedule.endTime)}`
          : undefined,
        remark: formValues.remark
      });
      message.success(t("appointments.created"));
      setAppointmentOpen(false);
      appointmentForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["appointments"] });
    }
  });

  const createRecordMutation = useMutation({
    mutationFn: createCounselingRecord,
    onSuccess: async () => {
      message.success(t("appointments.recordSaved"));
      setRecordOpen(false);
      recordForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["appointments"] });
    }
  });

  const createScheduleMutation = useMutation({
    mutationFn: createSchedule,
    onSuccess: async () => {
      message.success(t("appointments.scheduleCreated"));
      setCreateScheduleOpen(false);
      scheduleForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["appointments", "schedules"] });
    }
  });

  const cancelAppointmentMutation = useMutation({
    mutationFn: cancelAppointment,
    onSuccess: async () => {
      message.success(t("appointments.cancelled"));
      await queryClient.invalidateQueries({ queryKey: ["appointments"] });
    }
  });

  const counselorOptions = useMemo(
    () =>
      (counselorsQuery.data ?? []).map((item: CounselorOption) => ({
        label: `${item.displayName} (${item.username})`,
        value: item.userId
      })),
    [counselorsQuery.data]
  );

  const respondentOptions = useMemo(
    () =>
      (directoryUsersQuery.data?.list ?? []).map((user: DirectoryUser) => ({
        label: `${user.displayName} / ${user.username} / #${user.userId}`,
        value: user.userId
      })),
    [directoryUsersQuery.data]
  );

  const bookingCandidateOptions = useMemo(
    () =>
      (bookingCandidateQuery.data?.list ?? []).map((user: DirectoryUser) => ({
        label: `${user.displayName} / ${user.username} / #${user.userId}`,
        value: user.userId
      })),
    [bookingCandidateQuery.data]
  );

  const openRecordModal = (record: AppointmentSummary) => {
    setSelectedAppointment(record);
    recordForm.setFieldsValue({
      appointmentId: record.id,
      needRetestFlag: false,
      needTransferFlag: false
    });
    setRecordOpen(true);
  };

  const staffColumns = useMemo(
    () => [
      { title: t("appointments.appointmentId"), dataIndex: "id", key: "id", width: 110 },
      {
        title: t("appointments.filter.user"),
        key: "user",
        width: 200,
        render: (_: unknown, record: AppointmentSummary) =>
          respondentLabel(record, t("appointments.counselorShort"))
      },
      {
        title: t("appointments.counselorId"),
        key: "counselor",
        width: 180,
        render: (_: unknown, record: AppointmentSummary) =>
          record.counselorDisplayName || `${t("appointments.counselorShort")} #${record.counselorUserId}`
      },
      {
        title: t("appointments.scheduleAt"),
        key: "schedule",
        width: 200,
        render: (_: unknown, record: AppointmentSummary) => appointmentScheduleLabel(record)
      },
      {
        title: t("appointments.status"),
        dataIndex: "appointmentStatus",
        key: "appointmentStatus",
        width: 120,
        render: (value: string) => <Tag color={appointmentColor(value)}>{appointmentStatusLabel(t, value)}</Tag>
      },
      {
        title: t("appointments.source"),
        dataIndex: "sourceType",
        key: "sourceType",
        width: 120,
        render: (value: string) => appointmentSourceLabel(value, t)
      },
      { title: t("appointments.remark"), dataIndex: "remark", key: "remark" },
      {
        title: t("appointments.action"),
        key: "action",
        width: 220,
        render: (_: unknown, record: AppointmentSummary) => {
          const recordAllowed = canAddRecord(record);
          return (
            <Space wrap size={4}>
              <Tooltip title={recordAllowed ? undefined : t("appointments.recordBlocked")}>
                <Button type="link" size="small" disabled={!recordAllowed} onClick={() => openRecordModal(record)}>
                  {t("appointments.addRecord")}
                </Button>
              </Tooltip>
              {isFinalStatus(record.appointmentStatus) ? null : (
                <Popconfirm
                  title={t("appointments.cancelConfirm")}
                  okText={t("appointments.cancel")}
                  cancelText={t("common.cancel")}
                  onConfirm={() => cancelAppointmentMutation.mutateAsync(record.id)}
                >
                  <Button type="link" size="small" danger loading={cancelAppointmentMutation.isPending}>
                    {t("appointments.cancel")}
                  </Button>
                </Popconfirm>
              )}
            </Space>
          );
        }
      }
    ],
    [cancelAppointmentMutation.isPending, t]
  );

  const userColumns = useMemo(
    () => [
      { title: t("appointments.appointmentId"), dataIndex: "id", key: "id", width: 120 },
      {
        title: t("appointments.counselorId"),
        key: "counselor",
        render: (_: unknown, record: AppointmentSummary) =>
          record.counselorDisplayName || `${t("appointments.counselorShort")} #${record.counselorUserId}`
      },
      {
        title: t("appointments.scheduleAt"),
        key: "schedule",
        render: (_: unknown, record: AppointmentSummary) => appointmentScheduleLabel(record)
      },
      {
        title: t("appointments.status"),
        dataIndex: "appointmentStatus",
        key: "appointmentStatus",
        render: (value: string) => <Tag color={appointmentColor(value)}>{appointmentStatusLabel(t, value)}</Tag>
      },
      {
        title: t("appointments.source"),
        dataIndex: "sourceType",
        key: "sourceType",
        render: (value: string) => appointmentSourceLabel(value, t)
      },
      { title: t("appointments.remark"), dataIndex: "remark", key: "remark" },
      {
        title: t("appointments.action"),
        key: "action",
        width: 140,
        render: (_: unknown, record: AppointmentSummary) =>
          isFinalStatus(record.appointmentStatus) ? null : (
            <Popconfirm
              title={t("appointments.cancelConfirm")}
              okText={t("appointments.cancel")}
              cancelText={t("common.cancel")}
              onConfirm={() => cancelAppointmentMutation.mutateAsync(record.id)}
            >
              <Button type="link" danger loading={cancelAppointmentMutation.isPending}>
                {t("appointments.cancel")}
              </Button>
            </Popconfirm>
          )
      }
    ],
    [cancelAppointmentMutation.isPending, t]
  );

  const myAppointments = appointmentsQuery.data ?? [];
  const filteredAppointments = useMemo(() => {
    if (appointmentStatusFilter === "ALL") {
      return myAppointments;
    }
    if (appointmentStatusFilter === "CREATED") {
      return myAppointments.filter((item) => item.appointmentStatus === "CREATED" || item.appointmentStatus === "CONFIRMED");
    }
    return myAppointments.filter((item) => item.appointmentStatus === appointmentStatusFilter);
  }, [appointmentStatusFilter, myAppointments]);

  const registerList = registerQuery.data?.list ?? [];

  const handleServiceSearch = async () => {
    const values = await serviceFilterForm.validateFields();
    const [from, to] = values.dateRange ?? [];
    setRegisterPage(1);
    setRegisterFilter({
      status: values.status,
      userId: values.userId,
      counselorUserId: values.counselorUserId,
      dateFrom: from?.format("YYYY-MM-DD"),
      dateTo: to?.format("YYYY-MM-DD")
    });
  };

  const handleServiceReset = () => {
    serviceFilterForm.resetFields();
    setRegisterPage(1);
    setRegisterFilter({});
  };

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={4} style={{ marginBottom: 8 }}>
          {isUserView ? t("appointments.userTitle") : t("appointments.staffTitle")}
        </Typography.Title>
        <Typography.Text type="secondary">
          {isUserView ? t("appointments.userSubtitle") : t("appointments.staffSubtitle")}
        </Typography.Text>
      </div>

      {createdAppointment ? (
        <Alert
          type="success"
          showIcon
          closable
          onClose={() => setCreatedAppointment(null)}
          message={t("appointments.bookedSuccess")}
          description={
            <Space direction="vertical" size={2}>
              <Typography.Text>{t("appointments.bookedLine", { id: createdAppointment.id, status: createdAppointment.appointmentStatus.toLowerCase() })}</Typography.Text>
              <Typography.Text>{createdAppointment.counselorName ?? t("appointments.counselorLine", { id: createdAppointment.counselorUserId })}</Typography.Text>
              {createdAppointment.scheduleLabel ? <Typography.Text>{t("appointments.scheduleLine", { schedule: createdAppointment.scheduleLabel })}</Typography.Text> : null}
              {createdAppointment.remark ? <Typography.Text>{t("appointments.remarkLine", { remark: createdAppointment.remark })}</Typography.Text> : null}
              <Space wrap>
                <Button size="small" onClick={() => navigate("/notifications")}>
                  {t("appointments.openNotifications")}
                </Button>
                <Button size="small" onClick={() => navigate("/my/reports")}>
                  {t("appointments.openReports")}
                </Button>
                <Button size="small" type="link" onClick={() => setCreatedAppointment(null)}>
                  {t("appointments.dismiss")}
                </Button>
              </Space>
            </Space>
          }
        />
      ) : null}

      <Card
        size={isMobile ? "small" : "default"}
        title={t("appointments.schedules")}
        extra={
          <Space direction={isMobile ? "vertical" : "horizontal"} style={{ width: isMobile ? "100%" : undefined }}>
            <Select
              showSearch
              placeholder={t("appointments.counselorId")}
              value={scheduleCounselorId ?? undefined}
              onChange={(value) => setScheduleCounselorId(value ?? null)}
              loading={counselorsQuery.isLoading}
              options={counselorOptions}
              optionFilterProp="label"
              allowClear
              style={{ width: isMobile ? "100%" : 180 }}
            />
            <Button
              block={isMobile}
              onClick={() => {
                if (scheduleCounselorId) {
                  void schedulesQuery.refetch();
                }
              }}
            >
              {t("appointments.searchSchedules")}
            </Button>
            <Permission roles={["COUNSELOR", "ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
              <Button block={isMobile} onClick={() => setCreateScheduleOpen(true)}>{t("appointments.createSchedule")}</Button>
            </Permission>
          </Space>
        }
      >
        {counselorsQuery.isError ? <Alert type="warning" showIcon message={t("appointments.appointmentLoadError")} style={{ marginBottom: 16 }} /> : null}
        {schedulesQuery.isError ? <Alert type="warning" showIcon message={t("appointments.scheduleLoadError")} /> : null}
        {isUserView && isMobile ? (
          <Space direction="vertical" size={12} style={{ width: "100%" }}>
            {(schedulesQuery.data ?? []).map((item) => (
              <Card
                key={item.id}
                size="small"
                styles={{ body: { padding: 16 } }}
                style={{
                  borderRadius: 16,
                  boxShadow: "0 12px 28px rgba(19, 51, 78, 0.08)",
                  borderColor: "#e3edf7"
                }}
              >
                <Space direction="vertical" size={8} style={{ width: "100%" }}>
                  <Typography.Text strong>{item.scheduleDate}</Typography.Text>
                  <Typography.Text>{formatClockTime(item.startTime)} - {formatClockTime(item.endTime)}</Typography.Text>
                  <Space wrap>
                    <Tag>{t("appointments.scheduleId")} #{item.id}</Tag>
                    <Tag>{t("appointments.quota")} {item.quotaCount}</Tag>
                    <Tag color="blue">{scheduleStatusLabel(t, item.status)}</Tag>
                  </Space>
                </Space>
              </Card>
            ))}
          </Space>
        ) : (
          <Table
            rowKey="id"
            loading={schedulesQuery.isLoading}
            dataSource={schedulesQuery.data ?? []}
            pagination={false}
            scroll={isMobile ? { x: 720 } : undefined}
            columns={[
              { title: t("appointments.scheduleId"), dataIndex: "id" },
              { title: t("appointments.counselorId"), dataIndex: "counselorUserId" },
              { title: t("appointments.date"), dataIndex: "scheduleDate" },
              { title: t("appointments.start"), dataIndex: "startTime", render: (value: string) => formatClockTime(value) },
              { title: t("appointments.end"), dataIndex: "endTime", render: (value: string) => formatClockTime(value) },
              { title: t("appointments.quota"), dataIndex: "quotaCount" },
              {
                title: t("appointments.status"),
                dataIndex: "status",
                render: (value: string) => <Tag color="blue">{scheduleStatusLabel(t, value)}</Tag>
              }
            ]}
          />
        )}
      </Card>

      {isUserView ? (
        <Card
          size={isMobile ? "small" : "default"}
          title={t("appointments.userBookings")}
          extra={
            <Permission roles={["USER", "COUNSELOR", "ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
              <Button block={isMobile} type="primary" onClick={() => setAppointmentOpen(true)}>
                {t("appointments.createAppointment")}
              </Button>
            </Permission>
          }
        >
          {appointmentsQuery.isError ? <Alert type="warning" showIcon message={t("appointments.appointmentLoadError")} /> : null}
          <Alert type="info" showIcon style={{ marginBottom: 16 }} message={t("appointments.userInfo")} />
          {isMobile ? (
            <div
              style={{
                marginBottom: 16,
                position: "sticky",
                top: 64,
                zIndex: 4,
                background: "rgba(255,255,255,0.96)",
                paddingBottom: 8,
                display: "grid",
                gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
                gap: 8
              }}
            >
              <Button block type={appointmentStatusFilter === "ALL" ? "primary" : "default"} onClick={() => setAppointmentStatusFilter("ALL")}>
                {t("appointments.filter.all")}
              </Button>
              <Button block type={appointmentStatusFilter === "CREATED" ? "primary" : "default"} onClick={() => setAppointmentStatusFilter("CREATED")}>
                {t("appointments.filter.created")}
              </Button>
              <Button block type={appointmentStatusFilter === "COMPLETED" ? "primary" : "default"} onClick={() => setAppointmentStatusFilter("COMPLETED")}>
                {t("appointments.filter.completed")}
              </Button>
              <Button block type={appointmentStatusFilter === "CANCELLED" ? "primary" : "default"} onClick={() => setAppointmentStatusFilter("CANCELLED")}>
                {t("appointments.filter.cancelled")}
              </Button>
            </div>
          ) : (
            <Space wrap style={{ marginBottom: 16 }}>
              <Button type={appointmentStatusFilter === "ALL" ? "primary" : "default"} onClick={() => setAppointmentStatusFilter("ALL")}>
                {t("appointments.filter.all")}
              </Button>
              <Button type={appointmentStatusFilter === "CREATED" ? "primary" : "default"} onClick={() => setAppointmentStatusFilter("CREATED")}>
                {t("appointments.filter.created")}
              </Button>
              <Button type={appointmentStatusFilter === "COMPLETED" ? "primary" : "default"} onClick={() => setAppointmentStatusFilter("COMPLETED")}>
                {t("appointments.filter.completed")}
              </Button>
              <Button type={appointmentStatusFilter === "CANCELLED" ? "primary" : "default"} onClick={() => setAppointmentStatusFilter("CANCELLED")}>
                {t("appointments.filter.cancelled")}
              </Button>
            </Space>
          )}
          {isMobile ? (
            filteredAppointments.length ? (
              <Space direction="vertical" size={12} style={{ width: "100%" }}>
                {filteredAppointments.map((record) => (
                  <Card
                    key={record.id}
                    size="small"
                    styles={{ body: { padding: 16 } }}
                    style={{
                      borderRadius: 16,
                      boxShadow: "0 12px 28px rgba(19, 51, 78, 0.08)",
                      borderColor: "#e3edf7"
                    }}
                  >
                    <Space direction="vertical" size={8} style={{ width: "100%" }}>
                      <Space wrap>
                        <Tag color={appointmentColor(record.appointmentStatus)}>{appointmentStatusLabel(t, record.appointmentStatus)}</Tag>
                        <Tag>{appointmentSourceLabel(record.sourceType, t)}</Tag>
                      </Space>
                      <Typography.Text strong>
                        {record.counselorDisplayName || `${t("appointments.counselorShort")} #${record.counselorUserId}`}
                      </Typography.Text>
                      <Typography.Text>{t("appointments.appointmentId")} #{record.id}</Typography.Text>
                      <Typography.Text>{t("appointments.scheduleAt")}: {appointmentScheduleLabel(record)}</Typography.Text>
                      {record.remark ? <Typography.Text type="secondary">{record.remark}</Typography.Text> : null}
                      {isFinalStatus(record.appointmentStatus) ? null : (
                        <Popconfirm
                          title={t("appointments.cancelConfirm")}
                          okText={t("appointments.cancel")}
                          cancelText={t("common.cancel")}
                          onConfirm={() => cancelAppointmentMutation.mutateAsync(record.id)}
                        >
                          <Button danger loading={cancelAppointmentMutation.isPending}>
                            {t("appointments.cancel")}
                          </Button>
                        </Popconfirm>
                      )}
                    </Space>
                  </Card>
                ))}
              </Space>
            ) : (
              <Alert type="info" showIcon message={t("myTasks.emptyFiltered")} />
            )
          ) : (
            <Table
              rowKey="id"
              loading={appointmentsQuery.isLoading}
              dataSource={filteredAppointments}
              pagination={false}
              scroll={{ x: 860 }}
              columns={userColumns}
            />
          )}
        </Card>
      ) : (
        <Card
          size={isMobile ? "small" : "default"}
          title={t("appointments.staffRegister")}
          extra={
            <Permission roles={["USER", "COUNSELOR", "ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
              <Button block={isMobile} type="primary" onClick={() => setAppointmentOpen(true)}>
                {t("appointments.bookFor")}
              </Button>
            </Permission>
          }
        >
          <Typography.Text type="secondary">{t("appointments.staffRegisterDesc")}</Typography.Text>
          <Form form={serviceFilterForm} layout="inline" style={{ marginTop: 12, marginBottom: 16, rowGap: 8 }} onFinish={() => void handleServiceSearch()}>
            <Form.Item label={t("appointments.filter.user")} name="userId">
              <Select
                allowClear
                showSearch
                filterOption={false}
                onSearch={setUserKeyword}
                loading={directoryUsersQuery.isLoading}
                options={respondentOptions}
                style={{ width: 240 }}
                placeholder={t("appointments.filter.userPlaceholder")}
              />
            </Form.Item>
            <Form.Item label={t("appointments.counselorId")} name="counselorUserId">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                options={counselorOptions}
                loading={counselorsQuery.isLoading}
                style={{ width: 200 }}
                placeholder={t("appointments.counselorPlaceholder")}
              />
            </Form.Item>
            <Form.Item label={t("appointments.filter.dateRange")} name="dateRange">
              <DatePicker.RangePicker style={{ width: 260 }} />
            </Form.Item>
            <Form.Item label={t("appointments.status")} name="status">
              <Select
                allowClear
                style={{ width: 150 }}
                placeholder={t("appointments.status")}
                options={[
                  { label: t("appointments.filter.created"), value: "CONFIRMED" },
                  { label: t("appointments.filter.completed"), value: "COMPLETED" },
                  { label: t("appointments.filter.cancelled"), value: "CANCELLED" },
                  { label: appointmentStatusLabel(t, "NO_SHOW"), value: "NO_SHOW" }
                ]}
              />
            </Form.Item>
            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit">
                  {t("appointments.filter.query")}
                </Button>
                <Button onClick={handleServiceReset}>{t("appointments.filter.reset")}</Button>
              </Space>
            </Form.Item>
          </Form>
          {registerQuery.isError ? <Alert type="warning" showIcon message={t("appointments.appointmentLoadError")} /> : null}
          {registerList.length === 0 && !registerQuery.isLoading ? (
            <Alert type="info" showIcon message={t("appointments.staffEmpty")} style={{ marginBottom: 12 }} />
          ) : null}
          <Table
            rowKey="id"
            loading={registerQuery.isLoading}
            dataSource={registerList}
            pagination={false}
            scroll={{ x: 1180 }}
            columns={staffColumns}
          />
          {(registerQuery.data?.total ?? 0) > PAGE_SIZE ? (
            <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 12 }}>
              <Pagination
                current={registerQuery.data?.page ?? registerPage}
                pageSize={registerQuery.data?.size ?? PAGE_SIZE}
                total={registerQuery.data?.total ?? 0}
                showTotal={(total) => t("appointments.total", { total })}
                onChange={(page) => setRegisterPage(page)}
                showSizeChanger={false}
              />
            </div>
          ) : null}
        </Card>
      )}

      <Modal
        title={isUserView ? t("appointments.createAppointment") : t("appointments.bookFor")}
        open={appointmentOpen}
        onCancel={() => setAppointmentOpen(false)}
        onOk={() => void appointmentForm.validateFields().then((values) => createAppointmentMutation.mutateAsync(values))}
        confirmLoading={createAppointmentMutation.isPending}
        destroyOnHidden
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
          {!isUserView ? (
            <Form.Item
              label={t("appointments.bookForUser")}
              name="userId"
              rules={[{ required: true, message: t("appointments.bookForUserPlaceholder") }]}
            >
              <Select
                showSearch
                filterOption={false}
                onSearch={setUserKeyword}
                loading={bookingCandidateQuery.isLoading}
                options={bookingCandidateOptions}
                style={{ width: "100%" }}
                placeholder={t("appointments.bookForUserPlaceholder")}
              />
            </Form.Item>
          ) : null}
          <Form.Item
            label={t("appointments.counselorId")}
            name="counselorUserId"
            rules={[{ required: true, message: t("appointments.counselorRequired") }]}
          >
            <Select
              showSearch
              style={{ width: "100%" }}
              placeholder={t("appointments.counselorPlaceholder")}
              loading={counselorsQuery.isLoading}
              options={counselorOptions}
              optionFilterProp="label"
            />
          </Form.Item>
          <Form.Item label={t("appointments.scheduleId")} name="scheduleId" rules={[{ required: true, message: t("appointments.scheduleRequired") }]}>
            <Select
              placeholder={t("appointments.schedulePlaceholder")}
              loading={schedulesQuery.isLoading}
              notFoundContent={scheduleCounselorId ? t("appointments.noSchedules") : t("appointments.enterCounselorFirst")}
              options={(schedulesQuery.data ?? []).map((item) => ({
                label: `${item.scheduleDate} ${formatClockTime(item.startTime)}-${formatClockTime(item.endTime)} (${scheduleStatusLabel(t, item.status)})`,
                value: item.id
              }))}
            />
          </Form.Item>
          {!isUserView ? (
            <Form.Item label={t("appointments.warningId")} name="warningId">
              <InputNumber min={1} style={{ width: "100%" }} placeholder={t("appointments.warningPlaceholder")} />
            </Form.Item>
          ) : null}
          <Form.Item label={t("appointments.remark")} name="remark">
            <Input.TextArea rows={4} placeholder={t("appointments.remarkPlaceholder")} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={selectedAppointment ? t("appointments.recordTitle", { id: selectedAppointment.id }) : t("appointments.recordFallbackTitle")}
        open={recordOpen}
        onCancel={() => setRecordOpen(false)}
        onOk={() => void recordForm.validateFields().then((values) => createRecordMutation.mutateAsync(values))}
        confirmLoading={createRecordMutation.isPending}
        destroyOnHidden
      >
        <Form form={recordForm} layout="vertical">
          <Form.Item name="appointmentId" hidden>
            <Input />
          </Form.Item>
          <Form.Item label={t("appointments.summary")} name="summaryText">
            <Input.TextArea rows={4} placeholder={t("appointments.summaryPlaceholder")} />
          </Form.Item>
          <Form.Item label={t("appointments.suggestions")} name="suggestionText">
            <Input.TextArea rows={4} placeholder={t("appointments.suggestionsPlaceholder")} />
          </Form.Item>
          <Form.Item label={t("appointments.needRetest")} name="needRetestFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item label={t("appointments.needTransfer")} name="needTransferFlag" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t("appointments.createMySchedule")}
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
        destroyOnHidden
      >
        <Form form={scheduleForm} layout="vertical" initialValues={{ quotaCount: 1 }}>
          <Form.Item label={t("appointments.date")} name="scheduleDate" rules={[{ required: true, message: t("appointments.dateRequired") }]}>
            <DatePicker style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label={t("appointments.start")} name="startTime" rules={[{ required: true, message: t("appointments.startRequired") }]}>
            <DatePicker showTime style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label={t("appointments.end")} name="endTime" rules={[{ required: true, message: t("appointments.endRequired") }]}>
            <DatePicker showTime style={{ width: "100%" }} />
          </Form.Item>
          <Form.Item label={t("appointments.quotaLabel")} name="quotaCount" rules={[{ required: true }]}>
            <InputNumber min={1} max={50} style={{ width: "100%" }} />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
