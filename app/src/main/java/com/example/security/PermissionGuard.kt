package com.example.security

import com.example.data.local.UserRole

enum class AdminPermission {
    VIEW_DASHBOARD,
    MANAGE_STUDENTS,
    MANAGE_TEACHERS,
    MANAGE_HALAQAT,
    MANAGE_AI_KNOWLEDGE,
    MANAGE_QURAN_CONTENT,
    MANAGE_ADHAN_PRAYER,
    MANAGE_FEATURE_TOGGLES,
    SEND_ANNOUNCEMENTS,
    MANAGE_PROMO_CARDS,
    BACKUP_RESTORE,
    REVIEW_RECITATIONS,
    SUBMIT_RECITATION
}

object PermissionGuard {
    fun permissionsFor(role: UserRole): Set<AdminPermission> = when (role) {
        UserRole.SUPER_ADMIN -> AdminPermission.entries.toSet()
        UserRole.ADMIN -> setOf(
            AdminPermission.VIEW_DASHBOARD,
            AdminPermission.MANAGE_STUDENTS,
            AdminPermission.MANAGE_TEACHERS,
            AdminPermission.MANAGE_HALAQAT,
            AdminPermission.MANAGE_AI_KNOWLEDGE,
            AdminPermission.MANAGE_QURAN_CONTENT,
            AdminPermission.SEND_ANNOUNCEMENTS,
            AdminPermission.MANAGE_PROMO_CARDS,
            AdminPermission.REVIEW_RECITATIONS
        )
        UserRole.TEACHER -> setOf(
            AdminPermission.REVIEW_RECITATIONS
        )
        UserRole.STUDENT -> setOf(
            AdminPermission.SUBMIT_RECITATION
        )
    }

    fun hasPermission(role: UserRole, permission: AdminPermission): Boolean =
        permissionsFor(role).contains(permission)

    fun requirePermission(role: UserRole, permission: AdminPermission) {
        if (!hasPermission(role, permission)) {
            throw SecurityException("Role ${role.name} lacks permission ${permission.name}")
        }
    }
}
