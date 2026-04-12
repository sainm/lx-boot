import { createBrowserRouter, Navigate } from "react-router-dom";
import { appRoutes, loginRoute, routesForShell } from "../app/route-config";
import { useSession } from "../auth/session";
import { AccessGuard } from "../components/AccessGuard";
import { SessionGate } from "../components/SessionGate";
import { AdminLayout } from "../layouts/AdminLayout";
import { UserLayout } from "../layouts/UserLayout";

const userRoutes = routesForShell("user");
const adminRoutes = routesForShell("admin");

function HomeRedirect() {
  const { currentRole } = useSession();
  return <Navigate to={currentRole === "USER" ? "/my/tasks" : "/dashboard"} replace />;
}

function RoleLayout() {
  const { currentRole } = useSession();
  return currentRole === "USER" ? <UserLayout routes={userRoutes} /> : <AdminLayout routes={adminRoutes} />;
}

export const router = createBrowserRouter([
  {
    path: loginRoute.path,
    element: loginRoute.element
  },
  {
    path: "/",
    element: (
      <SessionGate>
        <RoleLayout />
      </SessionGate>
    ),
    children: [
      {
        index: true,
        element: <HomeRedirect />
      },
      ...appRoutes.map((route) => ({
        path: route.path,
        element: <AccessGuard roles={route.roles}>{route.element}</AccessGuard>
      })),
      {
        path: "*",
        element: <HomeRedirect />
      }
    ]
  }
]);
