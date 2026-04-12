import type { AppRoute } from "../app/route-config";
import { AppShellLayout } from "./AppShellLayout";

type Props = {
  routes: AppRoute[];
};

export function UserLayout({ routes }: Props) {
  return (
    <AppShellLayout
      routes={routes}
      shell="user"
      titleKey="app.userTitle"
      brandKey="app.userBrand"
      accent="linear-gradient(145deg, #183a56 0%, #1f5f86 58%, #63a7b7 100%)"
    />
  );
}
