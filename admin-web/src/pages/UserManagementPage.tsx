import { ApartmentOutlined, PlusOutlined } from "@ant-design/icons";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, TreeSelect, Typography, message } from "antd";
import { useMemo, useState } from "react";
import {
  assignUserAdminRoles,
  createUserAdminUser,
  fetchUserAdminGroups,
  fetchUserAdminRoles,
  fetchUserAdminTenants,
  fetchUserAdminUserPage,
  resetUserAdminPassword,
  updateUserAdminStatus,
  type UserAdminGroup,
  type CreateUserAdminUserRequest,
  type UserAdminRole,
  type UserAdminTenant,
  type UserAdminUser
} from "../features/user-admin/api";
import { useI18n } from "../i18n/provider";
import type { PageResponse } from "../types/api";

const PAGE_SIZE = 20;

type GroupTreeNode = {
  key: number;
  value: number;
  title: string;
  children?: GroupTreeNode[];
};

function renderStatusTag(status: string) {
  switch (status) {
    case "ENABLED":
      return <Tag color="green">{status}</Tag>;
    case "DISABLED":
      return <Tag color="default">{status}</Tag>;
    case "LOCKED":
      return <Tag color="volcano">{status}</Tag>;
    default:
      return <Tag>{status}</Tag>;
  }
}

function buildGroupTree(groups: UserAdminGroup[]): GroupTreeNode[] {
  const nodes = new Map<number, GroupTreeNode>();
  groups.forEach((group) => {
    nodes.set(group.groupId, {
      key: group.groupId,
      value: group.groupId,
      title: `${group.groupName} (${group.groupCode})`
    });
  });

  const roots: GroupTreeNode[] = [];
  groups.forEach((group) => {
    const node = nodes.get(group.groupId);
    if (!node) return;
    const parent = group.parentId == null ? null : nodes.get(group.parentId);
    if (parent) {
      parent.children = [...(parent.children ?? []), node];
    } else {
      roots.push(node);
    }
  });
  return roots;
}

function findGroupPath(groupId: number | null | undefined, groupsById: Map<number, UserAdminGroup>) {
  if (groupId == null) return [];
  const path: UserAdminGroup[] = [];
  const visited = new Set<number>();
  let current = groupsById.get(groupId);
  while (current && !visited.has(current.groupId)) {
    path.unshift(current);
    visited.add(current.groupId);
    current = current.parentId == null ? undefined : groupsById.get(current.parentId);
  }
  return path;
}

export function UserManagementPage() {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(1);
  const [usernameInput, setUsernameInput] = useState("");
  const [usernameFilter, setUsernameFilter] = useState<string | undefined>(undefined);
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
  const [tenantFilter, setTenantFilter] = useState<number | undefined>(undefined);
  const [groupFilter, setGroupFilter] = useState<number | undefined>(undefined);
  const [createOpen, setCreateOpen] = useState(false);
  const [rolesOpen, setRolesOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserAdminUser | null>(null);
  const [statusMutatingUserId, setStatusMutatingUserId] = useState<number | null>(null);

  const [createForm] = Form.useForm<CreateUserAdminUserRequest>();
  const [rolesForm] = Form.useForm<{ roleCodes: string[] }>();
  const [passwordForm] = Form.useForm<{ newPassword: string }>();

  const queryParams = {
    username: usernameFilter,
    status: statusFilter,
    tenantId: tenantFilter,
    groupId: groupFilter,
    page,
    size: PAGE_SIZE
  };

  const usersQuery = useQuery({
    queryKey: ["user-admin", "users", queryParams],
    queryFn: () => fetchUserAdminUserPage(queryParams)
  });
  const tenantsQuery = useQuery({
    queryKey: ["user-admin", "tenants"],
    queryFn: fetchUserAdminTenants
  });
  const rolesQuery = useQuery({
    queryKey: ["user-admin", "roles", selectedUser?.tenantId ?? tenantFilter],
    queryFn: () => fetchUserAdminRoles(selectedUser?.tenantId ?? tenantFilter)
  });
  const groupsQuery = useQuery({
    queryKey: ["user-admin", "groups", tenantFilter],
    queryFn: () => fetchUserAdminGroups(tenantFilter)
  });
  const createTenantId = Form.useWatch("tenantId", createForm);
  const createRolesQuery = useQuery({
    queryKey: ["user-admin", "roles", "create", createTenantId],
    queryFn: () => fetchUserAdminRoles(createTenantId),
    enabled: createOpen
  });
  const createGroupsQuery = useQuery({
    queryKey: ["user-admin", "groups", "create", createTenantId],
    queryFn: () => fetchUserAdminGroups(createTenantId),
    enabled: createOpen
  });

  const createUserMutation = useMutation({
    mutationFn: createUserAdminUser,
    onSuccess: async () => {
      message.success(t("userAdmin.created"));
      setCreateOpen(false);
      createForm.resetFields();
      await queryClient.invalidateQueries({ queryKey: ["user-admin", "users"] });
    }
  });
  const assignRolesMutation = useMutation({
    mutationFn: ({ userId, roleCodes }: { userId: number; roleCodes: string[] }) => assignUserAdminRoles(userId, roleCodes),
    onSuccess: async () => {
      message.success(t("userAdmin.rolesSaved"));
      setRolesOpen(false);
      rolesForm.resetFields();
      setSelectedUser(null);
      await queryClient.invalidateQueries({ queryKey: ["user-admin", "users"] });
    }
  });
  const updateStatusMutation = useMutation({
    mutationFn: ({ userId, enabled }: { userId: number; enabled: boolean }) => updateUserAdminStatus(userId, enabled),
    onMutate: async ({ userId, enabled }) => {
      setStatusMutatingUserId(userId);
      await queryClient.cancelQueries({ queryKey: ["user-admin", "users"] });
      const queryKey = ["user-admin", "users", queryParams] as const;
      const previousPage = queryClient.getQueryData<PageResponse<UserAdminUser>>(queryKey);
      queryClient.setQueryData<PageResponse<UserAdminUser>>(queryKey, (current) => {
        if (!current) {
          return current;
        }
        return {
          ...current,
          list: current.list.map((item) =>
            item.userId === userId
              ? {
                  ...item,
                  status: enabled ? "ENABLED" : "DISABLED"
                }
              : item
          )
        };
      });
      return { previousPage, queryKey };
    },
    onError: (_error, _variables, context) => {
      if (context?.previousPage) {
        queryClient.setQueryData(context.queryKey, context.previousPage);
      }
    },
    onSuccess: async (_, variables) => {
      message.success(variables.enabled ? t("userAdmin.enabled") : t("userAdmin.disabled"));
      await queryClient.invalidateQueries({ queryKey: ["user-admin", "users"] });
    },
    onSettled: () => {
      setStatusMutatingUserId(null);
    }
  });
  const resetPasswordMutation = useMutation({
    mutationFn: ({ userId, newPassword }: { userId: number; newPassword: string }) => resetUserAdminPassword(userId, newPassword),
    onSuccess: async () => {
      message.success(t("userAdmin.passwordReset"));
      setPasswordOpen(false);
      passwordForm.resetFields();
      setSelectedUser(null);
      await queryClient.invalidateQueries({ queryKey: ["user-admin", "users"] });
    }
  });

  const tenantOptions = useMemo(
    () =>
      (tenantsQuery.data ?? []).map((tenant: UserAdminTenant) => ({
        label: `${tenant.tenantName} (#${tenant.tenantId})`,
        value: tenant.tenantId
      })),
    [tenantsQuery.data]
  );
  const roleOptions = useMemo(
    () =>
      (rolesQuery.data ?? []).map((role: UserAdminRole) => ({
        label: `${role.roleName} (${role.roleCode})`,
        value: role.roleCode
      })),
    [rolesQuery.data]
  );
  const createRoleOptions = useMemo(
    () =>
      (createRolesQuery.data ?? []).map((role: UserAdminRole) => ({
        label: `${role.roleName} (${role.roleCode})`,
        value: role.roleCode
      })),
    [createRolesQuery.data]
  );
  const groupTreeData = useMemo(() => buildGroupTree(groupsQuery.data ?? []), [groupsQuery.data]);
  const createGroupTreeData = useMemo(() => buildGroupTree(createGroupsQuery.data ?? []), [createGroupsQuery.data]);
  const groupsById = useMemo(
    () => new Map((groupsQuery.data ?? []).map((group) => [group.groupId, group])),
    [groupsQuery.data]
  );

  function closeCreateModal() {
    setCreateOpen(false);
    createForm.resetFields();
    createUserMutation.reset();
  }

  function closeRolesModal() {
    setRolesOpen(false);
    setSelectedUser(null);
    rolesForm.resetFields();
    assignRolesMutation.reset();
  }

  function closePasswordModal() {
    setPasswordOpen(false);
    setSelectedUser(null);
    passwordForm.resetFields();
    resetPasswordMutation.reset();
  }

  const columns = [
    { title: t("userAdmin.col.userId"), dataIndex: "userId", key: "userId", width: 90 },
    { title: t("userAdmin.col.username"), dataIndex: "username", key: "username", width: 160 },
    {
      title: t("userAdmin.col.displayName"),
      dataIndex: "displayName",
      key: "displayName",
      width: 160,
      render: (value?: string | null) => value || "-"
    },
    {
      title: t("userAdmin.col.tenant"),
      key: "tenant",
      width: 180,
      render: (_: unknown, record: UserAdminUser) => record.tenantName || (record.tenantId != null ? `#${record.tenantId}` : "-")
    },
    {
      title: t("userAdmin.col.group"),
      key: "group",
      width: 260,
      render: (_: unknown, record: UserAdminUser) => {
        const path = findGroupPath(record.groupId, groupsById);
        if (path.length === 0) {
          return record.groupName || (record.groupId != null ? `#${record.groupId}` : "-");
        }
        const leaf = path[path.length - 1];
        return (
          <Space direction="vertical" size={2}>
            <Space size={4} wrap>
              <ApartmentOutlined />
              <Typography.Text strong>{leaf.groupName}</Typography.Text>
              <Tag>{leaf.groupCode}</Tag>
            </Space>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>
              {path.map((group) => group.groupName).join(" / ")}
            </Typography.Text>
          </Space>
        );
      }
    },
    {
      title: t("userAdmin.col.status"),
      dataIndex: "status",
      key: "status",
      width: 120,
      render: (value: string) => renderStatusTag(value)
    },
    {
      title: t("userAdmin.col.roles"),
      key: "roles",
      render: (_: unknown, record: UserAdminUser) =>
        record.roles.length > 0 ? (
          <Space size={[4, 4]} wrap>
            {record.roles.map((role) => (
              <Tag key={role}>{role}</Tag>
            ))}
          </Space>
        ) : (
          "-"
        )
    },
    {
      title: t("userAdmin.col.contact"),
      key: "contact",
      render: (_: unknown, record: UserAdminUser) => [record.email, record.mobile].filter(Boolean).join(" / ") || "-"
    },
    {
      title: t("userAdmin.col.actions"),
      key: "actions",
      width: 260,
      render: (_: unknown, record: UserAdminUser) => (
        <Space wrap>
          <Button
            size="small"
            onClick={() => {
              setSelectedUser(record);
              rolesForm.setFieldsValue({ roleCodes: record.roles });
              setRolesOpen(true);
            }}
          >
            {t("userAdmin.assignRoles")}
          </Button>
          <Button
            size="small"
            onClick={() => {
              setSelectedUser(record);
              passwordForm.resetFields();
              setPasswordOpen(true);
            }}
          >
            {t("userAdmin.resetPassword")}
          </Button>
          {record.status === "ENABLED" ? (
            <Popconfirm title={t("userAdmin.disableConfirm")} onConfirm={() => updateStatusMutation.mutate({ userId: record.userId, enabled: false })}>
              <Button size="small" danger loading={statusMutatingUserId === record.userId}>
                {t("userAdmin.disable")}
              </Button>
            </Popconfirm>
          ) : (
            <Popconfirm title={t("userAdmin.enableConfirm")} onConfirm={() => updateStatusMutation.mutate({ userId: record.userId, enabled: true })}>
              <Button size="small" loading={statusMutatingUserId === record.userId}>
                {t("userAdmin.enable")}
              </Button>
            </Popconfirm>
          )}
        </Space>
      )
    }
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: "100%" }}>
      <div>
        <Typography.Title level={3} style={{ marginBottom: 8 }}>
          {t("userAdmin.title")}
        </Typography.Title>
        <Typography.Text type="secondary">{t("userAdmin.subtitle")}</Typography.Text>
      </div>

      <Space wrap>
        <Input
          style={{ width: 220 }}
          placeholder={t("userAdmin.usernamePlaceholder")}
          value={usernameInput}
          onChange={(event) => setUsernameInput(event.target.value)}
          onPressEnter={() => {
            setPage(1);
            setUsernameFilter(usernameInput.trim() || undefined);
          }}
        />
        <Select
          allowClear
          style={{ width: 160 }}
          placeholder={t("userAdmin.status")}
          value={statusFilter}
          onChange={(value) => {
            setPage(1);
            setStatusFilter(value);
          }}
          options={[
            { label: "ENABLED", value: "ENABLED" },
            { label: "DISABLED", value: "DISABLED" },
            { label: "LOCKED", value: "LOCKED" }
          ]}
        />
        <Select
          allowClear
          style={{ width: 220 }}
          placeholder={t("userAdmin.tenant")}
          value={tenantFilter}
          onChange={(value) => {
            setPage(1);
            setTenantFilter(value);
            setGroupFilter(undefined);
          }}
          options={tenantOptions}
        />
        <TreeSelect
          allowClear
          showSearch
          treeDefaultExpandAll
          style={{ width: 280 }}
          placeholder={t("userAdmin.group")}
          value={groupFilter}
          onChange={(value) => {
            setPage(1);
            setGroupFilter(value);
          }}
          treeData={groupTreeData}
          treeNodeFilterProp="title"
        />
        <Button
          onClick={() => {
            setPage(1);
            setUsernameFilter(usernameInput.trim() || undefined);
          }}
        >
          {t("common.search")}
        </Button>
        <Button
          onClick={() => {
            setPage(1);
            setUsernameInput("");
            setUsernameFilter(undefined);
            setStatusFilter(undefined);
            setTenantFilter(undefined);
            setGroupFilter(undefined);
          }}
        >
          {t("common.reset")}
        </Button>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            createForm.resetFields();
            setCreateOpen(true);
          }}
        >
          {t("userAdmin.create")}
        </Button>
      </Space>

      <Table<UserAdminUser>
        rowKey="userId"
        loading={usersQuery.isLoading}
        columns={columns}
        dataSource={usersQuery.data?.list ?? []}
        scroll={{ x: 1280 }}
        pagination={{
          current: page,
          pageSize: PAGE_SIZE,
          total: usersQuery.data?.total ?? 0,
          onChange: (nextPage) => setPage(nextPage)
        }}
      />

      <Modal
        title={t("userAdmin.create")}
        open={createOpen}
        onCancel={closeCreateModal}
        onOk={async () => {
          try {
            const values = await createForm.validateFields();
            await createUserMutation.mutateAsync(values);
          } catch {
            // Keep the modal interactive after validation or request failures.
          }
        }}
        confirmLoading={createUserMutation.isPending}
        destroyOnHidden
      >
        <Form form={createForm} layout="vertical" initialValues={{ roleCodes: ["USER"] }}>
          <Form.Item name="username" label={t("userAdmin.username")} rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="displayName" label={t("userAdmin.displayName")}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label={t("userAdmin.password")} rules={[{ required: true, min: 8 }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="email" label={t("userAdmin.email")}>
            <Input />
          </Form.Item>
          <Form.Item name="mobile" label={t("userAdmin.mobile")}>
            <Input />
          </Form.Item>
          <Form.Item name="tenantId" label={t("userAdmin.tenant")}>
            <Select
              allowClear
              options={tenantOptions}
              onChange={() => createForm.setFieldValue("groupId", undefined)}
            />
          </Form.Item>
          <Form.Item name="groupId" label={t("userAdmin.group")}>
            <TreeSelect
              allowClear
              showSearch
              treeDefaultExpandAll
              treeData={createGroupTreeData}
              treeNodeFilterProp="title"
              placeholder={t("userAdmin.groupPlaceholder")}
            />
          </Form.Item>
          <Form.Item name="roleCodes" label={t("userAdmin.roles")} rules={[{ required: true }]}>
            <Select mode="multiple" options={createRoleOptions} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t("userAdmin.assignRoles")}
        open={rolesOpen}
        onCancel={closeRolesModal}
        onOk={async () => {
          try {
            const values = await rolesForm.validateFields();
            if (!selectedUser) {
              return;
            }
            await assignRolesMutation.mutateAsync({ userId: selectedUser.userId, roleCodes: values.roleCodes });
          } catch {
            // Keep the modal interactive after validation or request failures.
          }
        }}
        confirmLoading={assignRolesMutation.isPending}
        destroyOnHidden
      >
        <Form form={rolesForm} layout="vertical">
          <Form.Item name="roleCodes" label={t("userAdmin.roles")} rules={[{ required: true }]}>
            <Select mode="multiple" options={roleOptions} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t("userAdmin.resetPassword")}
        open={passwordOpen}
        onCancel={closePasswordModal}
        onOk={async () => {
          try {
            const values = await passwordForm.validateFields();
            if (!selectedUser) {
              return;
            }
            await resetPasswordMutation.mutateAsync({ userId: selectedUser.userId, newPassword: values.newPassword });
          } catch {
            // Keep the modal interactive after validation or request failures.
          }
        }}
        confirmLoading={resetPasswordMutation.isPending}
        destroyOnHidden
      >
        <Form form={passwordForm} layout="vertical">
          <Form.Item name="newPassword" label={t("userAdmin.password")} rules={[{ required: true, min: 8 }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
