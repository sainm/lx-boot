import { Badge, Menu, Typography } from "antd";
import type { AppRoute } from "../app/route-config";
import { useI18n } from "../i18n/provider";

type Props = {
  routes: AppRoute[];
  selectedKey: string;
  onNavigate: (path: string) => void;
  unreadNotificationCount?: number;
};

export function AppMenu({ routes, selectedKey, onNavigate, unreadNotificationCount = 0 }: Props) {
  const { t } = useI18n();

  const menuRoutes = routes.filter((route) => route.menu);
  const groups = new Map<string, typeof menuRoutes>();
  for (const route of menuRoutes) {
    const key = route.groupKey ?? "nav.group.other";
    groups.set(key, [...(groups.get(key) ?? []), route]);
  }

  const toItem = (route: (typeof menuRoutes)[number]) => ({
    key: route.path,
    icon: route.icon,
    label:
      route.key === "notifications" && unreadNotificationCount > 0 ? (
        <Badge count={unreadNotificationCount} size="small" offset={[8, 0]}>
          {t(route.labelKey)}
        </Badge>
      ) : (
        t(route.labelKey)
      )
  });

  const heading = (
    <Typography.Text type="secondary" style={{ display: "block", padding: "8px 16px 4px", fontSize: 12 }}>
      {t("nav.menuTitle")}
    </Typography.Text>
  );

  const items = Array.from(groups.entries()).map(([groupKey, groupRoutes]) => ({
    type: "group" as const,
    key: groupKey,
    label: t(groupKey),
    children: groupRoutes.map(toItem)
  }));

  return (
    <>
      {heading}
      <Menu mode="inline" selectedKeys={[selectedKey]} items={items} onClick={({ key }) => onNavigate(key)} />
    </>
  );
}
