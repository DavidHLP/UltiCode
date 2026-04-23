---
status: resolved
trigger: "Management 前端访问 /admin/account/profile 时后端返回 404，API getProfile、getAllSettings、updateProfile 全部失败"
created: 2026-04-23T22:15:00Z
updated: 2026-04-23T22:30:00Z
---

## Symptoms

**Expected behavior:**
Management 前端访问 /admin/account/profile 时，后端正常返回用户资料和设置数据

**Actual behavior:**
所有 API 调用返回 404 Not Found

**Error messages:**
- `Failed to load resource: the server responded with a status of 404 ()`
- `ApiError: Not found` at `AccountView.vue:59` (getProfile)
- `ApiError: Not found` at `AccountView.vue:76` (updateProfile)
- `ApiError: Not found` at `SettingsView.vue:85` (getAllSettings)

**Timeline:**
2026-04-23 首次报告

**Reproduction:**
访问 Management 前端 (port 9003) -> 点击账户/资料页面 -> 触发 getProfile 和 getAllSettings 调用 -> 返回 404

## Resolution

root_cause: "AdminAccountController and AdminSettingsController are missing from backend - frontend calls /admin/account/profile and /admin/settings/* but these endpoints have never been implemented"
fix: "Created AdminAccountController with /admin/account/profile endpoints (getProfile, updateProfile) and AdminSettingsController with /admin/settings/* endpoints (getAllSettings, getSettings, updateSettings, etc.)"
verification: "Maven compile succeeded. Runtime verification requires PM2 restart: pm2 restart ulticode-9001"
files_changed:
  - "backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminAccountController.java (NEW)"
  - "backend-spring/src/main/java/com/ulticode/modules/admin/controller/AdminSettingsController.java (NEW)"

## Fix Summary

**Root Cause:** The Management frontend expects these API endpoints that never existed in the backend:
- `GET /admin/account/profile` - get admin profile
- `PATCH /admin/account/profile` - update admin profile
- `GET /admin/settings/all` - get all settings
- `GET /admin/settings` - get general settings
- `PATCH /admin/settings` - update all settings
- `GET/PATCH /admin/settings/email` - email settings
- `GET/PATCH /admin/settings/rate-limits` - rate limit settings
- `GET/PATCH /admin/settings/uploads` - upload settings
- `GET/PATCH /admin/settings/features` - feature toggles
- `POST /admin/settings/maintenance` - toggle maintenance mode
- `POST /admin/settings/cache/clear` - clear cache
- `POST /admin/settings/reset` - reset to defaults

**Solution:** Created two new controllers:
1. `AdminAccountController.java` - handles /admin/account/* endpoints for profile management
2. `AdminSettingsController.java` - handles /admin/settings/* endpoints for system settings

**Next Step:** Restart the backend to load the new controllers:
```bash
pm2 restart ulticode-9001 --update-env
```
