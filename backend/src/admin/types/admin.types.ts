import { PermissionAction, PermissionResource } from '@prisma/client';

export interface RequiredPermission {
  action: PermissionAction;
  resource: PermissionResource;
}

export interface AuditLogData {
  performerId: string;
  action: string;
  entityType: string;
  entityId: string;
  oldValues?: unknown;
  newValues?: unknown;
  userId?: string;
  ipAddress?: string;
  userAgent?: string;
}
