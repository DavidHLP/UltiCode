/**
 * User roles
 */
export type UserRole = 'USER' | 'MODERATOR' | 'ADMIN' | 'SUPER_ADMIN';
export declare const UserRole: {
    readonly USER: UserRole;
    readonly MODERATOR: UserRole;
    readonly ADMIN: UserRole;
    readonly SUPER_ADMIN: UserRole;
};
export declare function isUserRole(value: string): value is UserRole;
/**
 * Role hierarchy for permission checks
 * Higher index = higher privilege
 */
export declare const RoleHierarchy: readonly UserRole[];
export declare function hasRoleOrHigher(userRole: UserRole, requiredRole: UserRole): boolean;
//# sourceMappingURL=role.enum.d.ts.map