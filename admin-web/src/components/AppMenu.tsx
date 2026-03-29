import { Badge, Menu } from "antd";
import type { AppRoute } from "../app/route-config";

type Props = {
  routes: AppRoute[];
  currentPath: string;
  onNavigate: (path: string) => void;
  unreadNotificationCount?: number;
};

export function AppMenu({ routes, currentPath, onNavigate, unreadNotificationCount = 0 }: Props) {
  return (
    <Menu
      mode="inline"
      selectedKeys={[currentPath]}
      items={routes
        .filter((route) => route.menu)
        .map((route) => ({
          key: route.path,
          icon: route.icon,
          label:
            route.key === "notifications" && unreadNotificationCount > 0 ? (
              <Badge count={unreadNotificationCount} size="small" offset={[8, 0]}>
                {route.label}
              </Badge>
            ) : (
              route.label
            )
        }))}
      onClick={({ key }) => onNavigate(key)}
    />
  );
}

