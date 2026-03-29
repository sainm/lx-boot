import { createBrowserRouter, Navigate } from "react-router-dom";
import { appRoutes, loginRoute } from "../app/route-config";
import { useSession } from "../auth/session";
import { AccessGuard } from "../components/AccessGuard";
import { SessionGate } from "../components/SessionGate";
import { AdminLayout } from "../layouts/AdminLayout";

const visibleRoutes = appRoutes.filter((route) => route.menu);

function HomeRedirect() {
  const { currentRole } = useSession();
  return <Navigate to={currentRole === "USER" ? "/my/tasks" : "/dashboard"} replace />;
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
        <AdminLayout routes={visibleRoutes} />
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
