import type { PropsWithChildren, ReactNode } from "react";
import { hasAnyRole, type AppRole } from "../auth/roles";
import { useSession } from "../auth/session";

type Props = PropsWithChildren<{
  roles: AppRole[];
  fallback?: ReactNode;
}>;

export function Permission({ roles, fallback = null, children }: Props) {
  const { roles: heldRoles } = useSession();
  if (!hasAnyRole(roles, heldRoles)) {
    return <>{fallback}</>;
  }
  return <>{children}</>;
}
