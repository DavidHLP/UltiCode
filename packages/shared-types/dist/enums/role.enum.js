"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.RoleHierarchy = exports.UserRole = void 0;
exports.isUserRole = isUserRole;
exports.hasRoleOrHigher = hasRoleOrHigher;
exports.UserRole = {
    USER: 'USER',
    MODERATOR: 'MODERATOR',
    ADMIN: 'ADMIN',
    SUPER_ADMIN: 'SUPER_ADMIN',
};
function isUserRole(value) {
    return Object.values(exports.UserRole).includes(value);
}
/**
 * Role hierarchy for permission checks
 * Higher index = higher privilege
 */
exports.RoleHierarchy = [
    'USER',
    'MODERATOR',
    'ADMIN',
    'SUPER_ADMIN',
];
function hasRoleOrHigher(userRole, requiredRole) {
    const userLevel = exports.RoleHierarchy.indexOf(userRole);
    const requiredLevel = exports.RoleHierarchy.indexOf(requiredRole);
    return userLevel >= requiredLevel;
}
