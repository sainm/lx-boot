import type { PropsWithChildren, ReactNode } from "react";
import { canAccess, type AppRole } from "../auth/roles";
import { useSession } from "../auth/session";

type Props = PropsWithChildren<{
  roles: AppRole[];
  fallback?: ReactNode;
}>;

export function Permission({ roles, fallback = null, children }: Props) {
  const { currentRole } = useSession();
  if (!canAccess(roles, currentRole)) {
    return <>{fallback}</>;
  }
  return <>{children}</>;
}
