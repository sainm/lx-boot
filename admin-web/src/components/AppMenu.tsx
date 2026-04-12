import { Badge, Menu } from "antd";
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

  return (
    <Menu
      mode="inline"
      selectedKeys={[selectedKey]}
      items={routes
        .filter((route) => route.menu)
        .map((route) => ({
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
        }))}
      onClick={({ key }) => onNavigate(key)}
    />
  );
}
