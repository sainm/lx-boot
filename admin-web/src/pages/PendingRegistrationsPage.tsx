import { Button, Card, List, Popconfirm, Space, Tag, Typography } from "antd";
import { useCallback, useEffect, useState } from "react";
import { http } from "../services/http";
import { useI18n } from "../i18n/provider";

type PendingRow = { id: number; username: string; display_name: string; email: string; register_source: string; created_at: string };

/**
 * Admin page listing external-registration users in PENDING_APPROVAL state,
 * with approve / reject actions.
 */
export function PendingRegistrationsPage() {
  const { t } = useI18n();
  const [rows, setRows] = useState<PendingRow[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchList = useCallback(() => {
    setLoading(true);
    http.get<PendingRow[]>("/api/v1/admin/external-registrations/pending")
      .then((r) => setRows(r.data))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { fetchList(); }, [fetchList]);

  const approve = async (userId: number) => {
    await http.post(`/api/v1/admin/external-registrations/${userId}/approve`);
    fetchList();
  };

  const reject = async (userId: number) => {
    await http.post(`/api/v1/admin/external-registrations/${userId}/reject`);
    fetchList();
  };

  return (
    <Card title={t("pendingRegistrations.title")}>
      <List
        loading={loading}
        dataSource={rows}
        locale={{ emptyText: t("pendingRegistrations.empty") }}
        renderItem={(item) => (
          <List.Item
            actions={[
              <Popconfirm
                key="approve"
                title={t("pendingRegistrations.approveConfirm")}
                onConfirm={() => void approve(item.id)}
                okText={t("pendingRegistrations.approve")}
                cancelText={t("common.cancel")}
              >
                <Button type="primary" size="small">
                  {t("pendingRegistrations.approve")}
                </Button>
              </Popconfirm>,
              <Popconfirm
                key="reject"
                title={t("pendingRegistrations.rejectConfirm")}
                onConfirm={() => void reject(item.id)}
                okText={t("pendingRegistrations.reject")}
                cancelText={t("common.cancel")}
              >
                <Button danger size="small">
                  {t("pendingRegistrations.reject")}
                </Button>
              </Popconfirm>,
            ]}
          >
            <List.Item.Meta
              title={
                <Space>
                  <Typography.Text strong>{item.username}</Typography.Text>
                  {item.display_name ? <Typography.Text type="secondary">({item.display_name})</Typography.Text> : null}
                </Space>
              }
              description={
                <Space>
                  <Tag>{item.email}</Tag>
                  <Typography.Text type="secondary">{item.created_at?.substring(0, 10)}</Typography.Text>
                </Space>
              }
            />
          </List.Item>
        )}
      />
    </Card>
  );
}
