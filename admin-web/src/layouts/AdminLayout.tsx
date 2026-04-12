import type { AppRoute } from "../app/route-config";
import { AppShellLayout } from "./AppShellLayout";

type Props = {
  routes: AppRoute[];
};

export function AdminLayout({ routes }: Props) {
  return (
    <AppShellLayout
      routes={routes}
      shell="admin"
      titleKey="app.title"
      brandKey="app.brand"
      accent="linear-gradient(135deg, #17324d 0%, #275d7f 100%)"
      responsive={false}
    />
  );
}
