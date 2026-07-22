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
  const { locale } = useI18n();
  const isEnglish = locale === "en-US";
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
    <Card title={isEnglish ? "Pending Registrations" : "待审核注册"}>
      <List
        loading={loading}
        dataSource={rows}
        locale={{ emptyText: isEnglish ? "No pending registrations" : "无待审核注册" }}
        renderItem={(item) => (
          <List.Item
            actions={[
              <Popconfirm
                key="approve"
                title={isEnglish ? "Approve this registration?" : "确认审核通过？"}
                onConfirm={() => void approve(item.id)}
                okText={isEnglish ? "Approve" : "通过"}
                cancelText={isEnglish ? "Cancel" : "取消"}
              >
                <Button type="primary" size="small">
                  {isEnglish ? "Approve" : "通过"}
                </Button>
              </Popconfirm>,
              <Popconfirm
                key="reject"
                title={isEnglish ? "Reject this registration?" : "确认拒绝？"}
                onConfirm={() => void reject(item.id)}
                okText={isEnglish ? "Reject" : "拒绝"}
                cancelText={isEnglish ? "Cancel" : "取消"}
              >
                <Button danger size="small">
                  {isEnglish ? "Reject" : "拒绝"}
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
