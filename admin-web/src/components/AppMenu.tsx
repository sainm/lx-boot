import { Menu } from "antd";
import type { AppRoute } from "../app/route-config";

type Props = {
  routes: AppRoute[];
  currentPath: string;
  onNavigate: (path: string) => void;
};

export function AppMenu({ routes, currentPath, onNavigate }: Props) {
  return (
    <Menu
      mode="inline"
      selectedKeys={[currentPath]}
      items={routes
        .filter((route) => route.menu)
        .map((route) => ({
          key: route.path,
          icon: route.icon,
          label: route.label
        }))}
      onClick={({ key }) => onNavigate(key)}
    />
  );
}
