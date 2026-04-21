---
phase: 27-profile-backend
reviewed: 2026-04-21T10:50:00Z
depth: standard
files_reviewed: 4
files_reviewed_list:
  - backend-spring/src/main/java/com/ulticode/modules/user/dto/ProfileVO.java
  - backend-spring/src/main/java/com/ulticode/modules/user/service/UserService.java
  - backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java
  - backend-spring/src/main/java/com/ulticode/modules/user/controller/UserController.java
findings:
  critical: 1
  warning: 4
  info: 3
  total: 8
status: issues_found
---
# Phase 27: Code Review Report

**Reviewed:** 2026-04-21T10:50:00Z
**Depth:** standard
**Files Reviewed:** 4
**Status:** issues_found

## Summary

Reviewed the Phase 27 user profile and avatar upload implementation across 4 files. Found 1 critical security vulnerability (path traversal in avatar upload), 4 warnings related to correctness and consistency, and 3 informational items. The avatar upload feature is the primary concern requiring immediate attention.

## Critical Issues

### CR-01: Path Traversal Vulnerability in Avatar Upload

**File:** `backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java:408-412`
**Issue:** The file extension is extracted from user-controlled input (`originalFilename`) without sanitization. While a UUID is prepended to the filename, the `ext` variable could contain path traversal characters if the filename is malicious (e.g., `malicious.png/../../../etc/passwd`).

**Fix:**
```java
// Sanitize the extension to prevent path traversal
String ext = "";
if (originalFilename != null && originalFilename.contains(".")) {
    String rawExt = originalFilename.substring(originalFilename.lastIndexOf("."));
    // Only allow safe image extensions and strip any path components
    ext = rawExt.toLowerCase().replaceAll("[^a-z0-9]", "");
    if (!ext.isEmpty() && !ext.equals("jpg") && !ext.equals("jpeg") &&
        !ext.equals("png") && !ext.equals("gif") && !ext.equals("webp")) {
        throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid file extension");
    }
    ext = "." + ext; // Re-add the dot
}
```

## Warnings

### WR-01: Hardcoded Achievement Count

**File:** `backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java:388`
**Issue:** `achievementCount` is always passed as `0` in `getUserProfile()`, meaning the achievement count feature is not implemented. This could mislead users viewing profiles.

**Fix:** Either implement the achievement count lookup or remove the field from ProfileVO until implemented.

### WR-02: Inconsistent Null Check Pattern

**File:** `backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java:378`
**Issue:** `followMapper` is checked for null with `if (followMapper != null)`, suggesting it may not always be injected. This inconsistent dependency injection could cause different behavior in different environments.

**Fix:** Ensure `followMapper` is always injected via constructor (using `@RequiredArgsConstructor`). If the follow feature is optional, this should be documented and handled consistently.

### WR-03: No File Size Validation on Avatar Upload

**File:** `backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java:398-405`
**Issue:** Only content type is validated; there is no check on file size. Large file uploads could cause denial of service or consume excessive disk space.

**Fix:**
```java
if (file == null || file.isEmpty()) {
    throw new BusinessException(ErrorCode.BAD_REQUEST, "File is required");
}

// Validate file size (e.g., max 5MB)
long maxSize = 5 * 1024 * 1024; // 5MB
if (file.getSize() > maxSize) {
    throw new BusinessException(ErrorCode.BAD_REQUEST, "File size exceeds 5MB limit");
}
```

### WR-04: Potential Race Condition in Avatar Update

**File:** `backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java:426-429`
**Issue:** Between fetching the user (line 426) and updating it (line 429), another request could modify the user's data, causing that data to be lost (time-of-check to time-of-use race condition).

**Fix:** Use optimistic locking with a version field, or perform the update in a single atomic operation:
```java
User user = new User();
user.setId(userId);
user.setAvatar(avatarUrl);
userMapper.updateById(user);
// This uses MyBatis-Plus to only update the avatar field
```

## Info

### IN-01: BeanUtils.copyProperties Includes Null Values

**File:** `backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java:308`
**Issue:** `BeanUtils.copyProperties` copies null values from DTO to entity, potentially overwriting existing data with nulls when DTO fields are null.

**Fix:** Use a more selective copy or manually set only non-null fields (as done in `updateCurrentUser`).

### IN-02: Avatar Upload URL Not Accessible

**File:** `backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java:424`
**Issue:** The avatar URL `/uploads/avatars/filename` is returned and stored, but there is no indication that this path is configured as a static resource handler in the application. The uploaded files may not be publicly accessible.

**Fix:** Verify that the Spring Boot application has static resource configuration to serve files from the `uploads/avatars/` directory.

### IN-03: Missing `@Transactional` on Avatar Upload

**File:** `backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java:391`
**Issue:** The `uploadAvatar` method updates the user entity after saving the file, but is not marked `@Transactional`. If the database update fails, the file remains on disk orphaned.

**Fix:** Add `@Transactional` annotation to ensure atomicity:
```java
@Override
@Transactional
public String uploadAvatar(MultipartFile file) {
```

---

_Reviewed: 2026-04-21T10:50:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
