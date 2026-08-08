import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Card, DatePicker, Form, Grid, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag, Typography, message } from "antd";
import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useSession } from "../auth/session";
import { Permission } from "../components/Permission";
import {
  cancelAppointment,
  createAppointment,
  createSchedule,
  fetchAppointmentHistory,
  fetchCounselorSchedules,
  fetchCounselors,
  fetchMyAppointments,
  markAppointmentNoShow,
  rescheduleAppointment,
  type AppointmentStatusLog,
  type AppointmentSummary,
  type CounselorOption
} from "../features/appointments/api";
import { createCounselingRecord, type CreateCounselingRecordRequest } from "../features/counseling-records/api";
import { useI18n } from "../i18n/provider";
import { formatDateTime } from "../utils/date";

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

function appointmentStatusLabel(status: string, t: (key: string) => string) {
  switch (status) {
    case "CREATED":
    case "CONFIRMED":
      return t("appointments.filter.created");
    case "COMPLETED":
      return t("appointments.filter.completed");
    case "CANCELLED":
      return t("appointments.filter.cancelled");
    case "NO_SHOW":
      return t("appointments.filter.noShow");
    default:
      return status;
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

function canMarkNoShow(record: AppointmentSummary) {
  if (record.appointmentStatus !== "CONFIRMED" || !record.endTime) {
    return false;
  }
  const endTime = new Date(record.endTime).getTime();
  return Number.isFinite(endTime) && endTime <= Date.now();
}

export function AppointmentPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { currentRole } = useSession();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const isUserView = currentRole === "USER";

  const [scheduleCounselorId, setScheduleCounselorId] = useState<number | null>(null);
  const [appointmentStatusFilter, setAppointmentStatusFilter] = useState<"ALL" | "CREATED" | "COMPLETED" | "CANCELLED" | "NO_SHOW">("ALL");
  const [appointmentOpen, setAppointmentOpen] = useState(false);
  const [recordOpen, setRecordOpen] = useState(false);
  const [createScheduleOpen, setCreateScheduleOpen] = useState(false);
  const [selectedAppointment, setSelectedAppointment] = useState<AppointmentSummary | null>(null);
  const [rescheduleTarget, setRescheduleTarget] = useState<AppointmentSummary | null>(null);
  const [historyTarget, setHistoryTarget] = useState<AppointmentSummary | null>(null);
  const [createdAppointment, setCreatedAppointment] = useState<{
    id: number;
    appointmentStatus: string;
    counselorUserId: number;
    counselorName?: string;
    scheduleLabel?: string;
    remark?: string;
  } | null>(null);
  const [appointmentForm] = Form.useForm();
  const [recordForm] = Form.useForm<CreateCounselingRecordRequest>();
  const [scheduleForm] = Form.useForm();

  const counselorsQuery = useQuery({
    queryKey: ["appointments", "counselors"],
    queryFn: fetchCounselors
  });

  const appointmentsQuery = useQuery({
    queryKey: ["appointments", "my"],
    queryFn: fetchMyAppointments
  });

  const schedulesQuery = useQuery({
    queryKey: ["appointments", "schedules", scheduleCounselorId],
    queryFn: () => fetchCounselorSchedules(scheduleCounselorId ?? 0),
    enabled: Boolean(scheduleCounselorId)
  });

  const historyQuery = useQuery({
    queryKey: ["appointments", "history", historyTarget?.id],
    queryFn: () => fetchAppointmentHistory(historyTarget?.id ?? 0),
    enabled: Boolean(historyTarget)
  });

  const createAppointmentMutation = useMutation({
    mutationFn: createAppointment,
    onSuccess: async (data, variables) => {
      const matchedSchedule = (schedulesQuery.data ?? []).find((item) => item.id === variables.scheduleId);
      const matchedCounselor = (counselorsQuery.data ?? []).find((item) => item.userId === variables.counselorUserId);
      setCreatedAppointment({
        id: data.appointmentId,
        appointmentStatus: data.status,
        counselorUserId: variables.counselorUserId,
        counselorName: matchedCounselor?.displayName,
        scheduleLabel: matchedSchedule
          ? `${matchedSchedule.scheduleDate} ${formatClockTime(matchedSchedule.startTime)}-${formatClockTime(matchedSchedule.endTime)}`
          : undefined,
        remark: variables.remark
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
      setSelectedAppointment(null);
      await queryClient.invalidateQueries({ queryKey: ["appointments"] });
    }
  });

  const rescheduleMutation = useMutation({
    mutationFn: ({ appointmentId, payload }: { appointmentId: number; payload: { counselorUserId: number; scheduleId: number; remark?: string } }) =>
      rescheduleAppointment(appointmentId, payload),
    onSuccess: async () => {
      message.success(t("appointments.rescheduled"));
      setAppointmentOpen(false);
      setRescheduleTarget(null);
      appointmentForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["appointments"] });
    }
  });

  const openReschedule = (record: AppointmentSummary) => {
    setRescheduleTarget(record);
    setScheduleCounselorId(record.counselorUserId);
    appointmentForm.setFieldsValue({
      counselorUserId: record.counselorUserId,
      scheduleId: undefined,
      remark: record.remark
    });
    setAppointmentOpen(true);
  };

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

  const noShowMutation = useMutation({
    mutationFn: markAppointmentNoShow,
    onSuccess: async () => {
      message.success(t("appointments.noShowSaved"));
      await queryClient.invalidateQueries({ queryKey: ["appointments"] });
    }
  });

  const appointmentColumns = useMemo(
    () => [
      { title: t("appointments.appointmentId"), dataIndex: "id", key: "id", width: 120 },
      {
        title: t("appointments.counselorId"),
        key: "counselor",
        width: 180,
        render: (_: unknown, record: AppointmentSummary) => record.counselorDisplayName || `${t("appointments.counselorShort")} #${record.counselorUserId}`
      },
      {
        title: t("appointments.scheduleAt"),
        key: "scheduleAt",
        width: 220,
        render: (_: unknown, record: AppointmentSummary) => appointmentScheduleLabel(record)
      },
      {
        title: t("appointments.status"),
        dataIndex: "appointmentStatus",
        key: "appointmentStatus",
        width: 140,
        render: (value: string) => <Tag color={appointmentColor(value)}>{appointmentStatusLabel(value, t)}</Tag>
      },
      {
        title: t("appointments.source"),
        dataIndex: "sourceType",
        key: "sourceType",
        width: 140,
        render: (value: string) => appointmentSourceLabel(value, t)
      },
      { title: t("appointments.remark"), dataIndex: "remark", key: "remark" },
      ...(isUserView
        ? [
            {
              title: t("appointments.action"),
              key: "userAction",
              width: 120,
              render: (_: unknown, record: AppointmentSummary) =>
                <Space>
                  <Button type="link" onClick={() => setHistoryTarget(record)}>{t("appointments.history")}</Button>
                  {record.appointmentStatus === "CREATED" || record.appointmentStatus === "CONFIRMED" ? (
                    <>
                      <Button type="link" onClick={() => openReschedule(record)}>{t("appointments.reschedule")}</Button>
                      <Button
                        type="link"
                        danger
                        loading={cancelAppointmentMutation.isPending}
                        onClick={() => void cancelAppointmentMutation.mutateAsync(record.id)}
                      >
                        {t("appointments.cancel")}
                      </Button>
                    </>
                  ) : null}
                </Space>
            }
          ]
        : []),
      ...(!isUserView
        ? [
            {
              title: t("appointments.action"),
              key: "action",
              render: (_: unknown, record: AppointmentSummary) => (
                <Permission roles={["COUNSELOR", "ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
                  <Space>
                    <Button type="link" onClick={() => setHistoryTarget(record)}>{t("appointments.history")}</Button>
                    {record.appointmentStatus === "CREATED" || record.appointmentStatus === "CONFIRMED" ? (
                      <Button type="link" onClick={() => openReschedule(record)}>{t("appointments.reschedule")}</Button>
                    ) : null}
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
                      {t("appointments.addRecord")}
                    </Button>
                    {canMarkNoShow(record) ? (
                      <Button type="link" danger loading={noShowMutation.isPending} onClick={() => noShowMutation.mutate(record.id)}>
                        {t("appointments.noShow")}
                      </Button>
                    ) : null}
                  </Space>
                </Permission>
              )
            }
          ]
        : [])
    ],
    [cancelAppointmentMutation.isPending, isUserView, noShowMutation, recordForm, t]
  );
  const filteredAppointments = useMemo(() => {
    const items = appointmentsQuery.data ?? [];
    if (appointmentStatusFilter === "ALL") {
      return items;
    }
    if (appointmentStatusFilter === "CREATED") {
      return items.filter((item) => item.appointmentStatus === "CREATED" || item.appointmentStatus === "CONFIRMED");
    }
    return items.filter((item) => item.appointmentStatus === appointmentStatusFilter);
  }, [appointmentStatusFilter, appointmentsQuery.data]);

  const counselorOptions = useMemo(
    () =>
      (counselorsQuery.data ?? []).map((item: CounselorOption) => ({
        label: `${item.displayName} (${item.username})`,
        value: item.userId
      })),
    [counselorsQuery.data]
  );

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
          message={t("appointments.bookedSuccess")}
          description={
            <Space direction="vertical" size={4}>
              <Typography.Text>{t("appointments.bookedLine", { id: createdAppointment.id, status: createdAppointment.appointmentStatus.toLowerCase() })}</Typography.Text>
              <Typography.Text>{createdAppointment.counselorName ?? t("appointments.counselorLine", { id: createdAppointment.counselorUserId })}</Typography.Text>
              {createdAppointment.scheduleLabel ? <Typography.Text>{t("appointments.scheduleLine", { schedule: createdAppointment.scheduleLabel })}</Typography.Text> : null}
              {createdAppointment.remark ? <Typography.Text>{t("appointments.remarkLine", { remark: createdAppointment.remark })}</Typography.Text> : null}
            </Space>
          }
          action={
            <Space direction={isMobile ? "vertical" : "horizontal"} wrap style={{ width: isMobile ? "100%" : undefined }}>
              <Button size={isMobile ? "middle" : "small"} block={isMobile} onClick={() => navigate("/notifications")}>
                {t("appointments.openNotifications")}
              </Button>
              <Button size={isMobile ? "middle" : "small"} block={isMobile} onClick={() => navigate("/my/reports")}>
                {t("appointments.openReports")}
              </Button>
              <Button size={isMobile ? "middle" : "small"} block={isMobile} onClick={() => setCreatedAppointment(null)}>
                {t("appointments.dismiss")}
              </Button>
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
                    <Tag color="blue">{item.status}</Tag>
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
                render: (value: string) => <Tag color="blue">{value}</Tag>
              }
            ]}
          />
        )}
      </Card>

      <Card
        size={isMobile ? "small" : "default"}
        title={isUserView ? t("appointments.userBookings") : t("appointments.myAppointments")}
        extra={
          <Permission roles={["USER", "COUNSELOR", "ASSESSMENT_ADMIN", "SYS_ADMIN"]}>
            <Button block={isMobile} type="primary" onClick={() => { setRescheduleTarget(null); appointmentForm.resetFields(); setAppointmentOpen(true); }}>
              {t("appointments.createAppointment")}
            </Button>
          </Permission>
        }
      >
        {appointmentsQuery.isError ? <Alert type="warning" showIcon message={t("appointments.appointmentLoadError")} /> : null}
        {isUserView ? <Alert type="info" showIcon style={{ marginBottom: 16 }} message={t("appointments.userInfo")} /> : null}
        {isUserView ? (
          isMobile ? (
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
              <Button block type={appointmentStatusFilter === "NO_SHOW" ? "primary" : "default"} onClick={() => setAppointmentStatusFilter("NO_SHOW")}>
                {t("appointments.filter.noShow")}
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
              <Button type={appointmentStatusFilter === "NO_SHOW" ? "primary" : "default"} onClick={() => setAppointmentStatusFilter("NO_SHOW")}>
                {t("appointments.filter.noShow")}
              </Button>
            </Space>
          )
        ) : null}
        {isUserView && isMobile ? (
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
                    <Tag color={appointmentColor(record.appointmentStatus)}>{appointmentStatusLabel(record.appointmentStatus, t)}</Tag>
                    <Tag>{appointmentSourceLabel(record.sourceType, t)}</Tag>
                  </Space>
                  <Typography.Text strong>
                    {record.counselorDisplayName || `${t("appointments.counselorShort")} #${record.counselorUserId}`}
                  </Typography.Text>
                  <Typography.Text>{t("appointments.appointmentId")} #{record.id}</Typography.Text>
                  <Typography.Text>{t("appointments.scheduleAt")}: {appointmentScheduleLabel(record)}</Typography.Text>
                  {record.remark ? <Typography.Text type="secondary">{record.remark}</Typography.Text> : null}
                  <Button onClick={() => setHistoryTarget(record)}>{t("appointments.history")}</Button>
                  {record.appointmentStatus === "CREATED" || record.appointmentStatus === "CONFIRMED" ? (
                    <Space direction="vertical" style={{ width: "100%" }}>
                      <Button block onClick={() => openReschedule(record)}>{t("appointments.reschedule")}</Button>
                      <Button
                        block
                        danger
                        loading={cancelAppointmentMutation.isPending}
                        onClick={() => void cancelAppointmentMutation.mutateAsync(record.id)}
                      >
                        {t("appointments.cancel")}
                      </Button>
                    </Space>
                  ) : null}
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
            scroll={isMobile ? { x: 860 } : undefined}
            columns={appointmentColumns}
          />
        )}
      </Card>

      <Modal
        title={rescheduleTarget ? t("appointments.rescheduleTitle", { id: rescheduleTarget.id }) : t("appointments.createAppointment")}
        open={appointmentOpen}
        onCancel={() => { setAppointmentOpen(false); setRescheduleTarget(null); appointmentForm.resetFields(); }}
        onOk={() => void appointmentForm.validateFields().then((values) => {
          if (rescheduleTarget) {
            return rescheduleMutation.mutateAsync({
              appointmentId: rescheduleTarget.id,
              payload: { counselorUserId: values.counselorUserId, scheduleId: values.scheduleId, remark: values.remark }
            });
          }
          return createAppointmentMutation.mutateAsync(values);
        })}
        confirmLoading={createAppointmentMutation.isPending || rescheduleMutation.isPending}
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
          {!isUserView && !rescheduleTarget ? (
            <Form.Item
              label={t("appointments.targetUserId")}
              name="userId"
              rules={[{ required: true, message: t("appointments.targetUserRequired") }]}
            >
              <InputNumber min={1} style={{ width: "100%" }} />
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
                label: `${item.scheduleDate} ${formatClockTime(item.startTime)}-${formatClockTime(item.endTime)} (${item.status})`,
                value: item.id
              }))}
            />
          </Form.Item>
          {!isUserView && !rescheduleTarget ? (
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
        title={historyTarget ? t("appointments.historyTitle", { id: historyTarget.id }) : t("appointments.history")}
        open={Boolean(historyTarget)}
        onCancel={() => setHistoryTarget(null)}
        footer={null}
        destroyOnHidden
      >
        {historyQuery.isError ? <Alert type="warning" showIcon message={t("appointments.historyLoadError")} /> : (
          <Table
            rowKey="id"
            loading={historyQuery.isLoading}
            dataSource={historyQuery.data ?? []}
            pagination={false}
            size="small"
            columns={[
              { title: t("appointments.historyAction"), dataIndex: "actionType" },
              { title: t("appointments.historyStatus"), key: "transition", render: (_: unknown, item: AppointmentStatusLog) => `${item.fromStatus ?? "-"} → ${item.toStatus}` },
              { title: t("appointments.historyTime"), dataIndex: "createdAt", render: (value: string) => formatDateTime(value) }
            ]}
          />
        )}
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
