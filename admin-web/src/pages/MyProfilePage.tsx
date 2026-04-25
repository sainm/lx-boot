import { SaveOutlined, UserOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Avatar, Button, Card, Col, Descriptions, Form, Input, Row, Space, Typography, message } from "antd";
import { useEffect } from "react";
import { useSession } from "../auth/session";
import { fetchMyEditableProfile, updateMyEditableProfile, type UpdateMyProfileRequest } from "../features/my-profile/api";
import { useI18n } from "../i18n/provider";

type ProfileFormValues = {
  nickname?: string;
  displayName?: string;
  email?: string;
  mobile?: string;
  avatarUrl?: string;
};

function normalize(values: ProfileFormValues): UpdateMyProfileRequest {
  return {
    nickname: values.nickname?.trim() || null,
    displayName: values.displayName?.trim() || null,
    email: values.email?.trim() || null,
    mobile: values.mobile?.trim() || null,
    avatarUrl: values.avatarUrl?.trim() || null
  };
}

export function MyProfilePage() {
  const [form] = Form.useForm<ProfileFormValues>();
  const queryClient = useQueryClient();
  const { locale, t } = useI18n();
  const { refreshSession } = useSession();

  const profileQuery = useQuery({
    queryKey: ["my-profile"],
    queryFn: fetchMyEditableProfile
  });
  const profile = profileQuery.data;

  useEffect(() => {
    if (!profile) return;
    form.setFieldsValue({
      nickname: profile.nickname ?? undefined,
      displayName: profile.displayName ?? undefined,
      email: profile.email ?? undefined,
      mobile: profile.mobile ?? undefined,
      avatarUrl: profile.avatarUrl ?? undefined
    });
  }, [form, profile]);

  const updateMutation = useMutation({
    mutationFn: updateMyEditableProfile,
    onSuccess: async () => {
      message.success(t("myProfile.updated"));
      await queryClient.invalidateQueries({ queryKey: ["my-profile"] });
      await refreshSession();
    }
  });

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={3} style={{ marginBottom: 8 }}>
          {t("myProfile.title")}
        </Typography.Title>
        <Typography.Text type="secondary">{t("myProfile.subtitle")}</Typography.Text>
      </div>

      <Row gutter={[16, 16]}>
        <Col xs={24} lg={8}>
          <Card loading={profileQuery.isLoading}>
            <Space direction="vertical" size={16} align="center" style={{ width: "100%" }}>
              <Avatar size={96} src={profile?.avatarUrl || undefined} icon={<UserOutlined />} />
              <div style={{ textAlign: "center" }}>
                <Typography.Title level={4} style={{ marginBottom: 4 }}>
                  {profile?.displayName || profile?.nickname || profile?.username || "-"}
                </Typography.Title>
                <Typography.Text type="secondary">@{profile?.username ?? "-"}</Typography.Text>
              </div>
            </Space>
          </Card>

          <Card title={t("myProfile.accountInfo")} loading={profileQuery.isLoading} style={{ marginTop: 16 }}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label={t("myProfile.userId")}>{profile?.userId ?? "-"}</Descriptions.Item>
              <Descriptions.Item label={t("myProfile.username")}>{profile?.username ?? "-"}</Descriptions.Item>
              <Descriptions.Item label={t("myProfile.organization")}>{profile?.tenantName ?? "-"}</Descriptions.Item>
              <Descriptions.Item label={t("myProfile.group")}>{profile?.groupName ?? "-"}</Descriptions.Item>
              <Descriptions.Item label={t("myProfile.roles")}>{profile?.roles.join(", ") || "-"}</Descriptions.Item>
              <Descriptions.Item label={t("myProfile.updatedAt")}>
                {profile?.updatedAt ? new Date(profile.updatedAt).toLocaleString(locale) : "-"}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} lg={16}>
          <Card title={t("myProfile.editInfo")} loading={profileQuery.isLoading}>
            <Form form={form} layout="vertical" onFinish={(values) => updateMutation.mutate(normalize(values))}>
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item
                    name="displayName"
                    label={t("myProfile.displayName")}
                    rules={[{ max: 128, message: t("myProfile.displayNameTooLong") }]}
                  >
                    <Input placeholder={t("myProfile.displayNamePlaceholder")} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item
                    name="nickname"
                    label={t("myProfile.nickname")}
                    rules={[{ max: 128, message: t("myProfile.nicknameTooLong") }]}
                  >
                    <Input placeholder={t("myProfile.nicknamePlaceholder")} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item
                    name="email"
                    label={t("myProfile.email")}
                    rules={[
                      { type: "email", message: t("myProfile.emailInvalid") },
                      { max: 128, message: t("myProfile.emailTooLong") }
                    ]}
                  >
                    <Input placeholder="name@example.com" />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item
                    name="mobile"
                    label={t("myProfile.mobile")}
                    rules={[{ max: 32, message: t("myProfile.mobileTooLong") }]}
                  >
                    <Input placeholder={t("myProfile.optional")} />
                  </Form.Item>
                </Col>
                <Col xs={24}>
                  <Form.Item
                    name="avatarUrl"
                    label={t("myProfile.avatarUrl")}
                    rules={[{ max: 512, message: t("myProfile.avatarUrlTooLong") }]}
                  >
                    <Input placeholder="https://..." />
                  </Form.Item>
                </Col>
              </Row>

              <Space>
                <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={updateMutation.isPending}>
                  {t("myProfile.save")}
                </Button>
                <Button onClick={() => profile && form.resetFields()} disabled={!profile || updateMutation.isPending}>
                  {t("common.reset")}
                </Button>
              </Space>
            </Form>
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
