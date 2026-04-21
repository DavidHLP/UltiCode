# Phase 27: Profile Backend - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-21
**Phase:** 27-profile-backend
**Areas discussed:** Avatar Storage, Profile Data Structure, Social Stats Integration, Achievements Placeholder

---

## Avatar Storage

| Option | Description | Selected |
|--------|-------------|----------|
| Local filesystem | Store uploaded avatar as file, save URL path in `user.avatar` column | ✓ |
| S3/OSS | Upload to cloud storage, store CDN URL | |
| URL only | User provides avatar URL directly in UpdateUserDTO (already supported) | |

**User's choice:** Local filesystem — simpler for v1, avatar stored at `/uploads/avatars/{uuid}.{ext}`
**Notes:** POST /users/me/avatar accepts MultipartFile, stores to local disk, returns URL string

---

## Profile Data Structure

| Option | Description | Selected |
|--------|-------------|----------|
| ProfileVO record | New dedicated profile response DTO with stats + social + achievements | ✓ |
| Extend UserVO | Add stats/social fields to existing UserVO | |

**User's choice:** New ProfileVO combining UserVO + UserStatsDTO + social counts + achievement count
**Notes:** ProfileVO fields: username, name, avatar, bio, company, location, website, joinedAt, preferredLanguage, totalSolved, submissionCount, globalRank, acceptanceRate, followerCount, followingCount, achievementCount

---

## Social Stats Integration

| Option | Description | Selected |
|--------|-------------|----------|
| Query Phase 26 FollowService | Inject FollowMapper into UserService for counts | ✓ |
| Placeholder 0 | Use hardcoded 0 until Phase 26 follow system is available | |

**User's choice:** Query Phase 26 FollowService for follower/following counts
**Notes:** If Phase 26 entity/mapper exists but service not built, use mapper directly — Phase 27 can build against Phase 26's existing entity/mapper

---

## Achievements in Profile

| Option | Description | Selected |
|--------|-------------|----------|
| Placeholder 0 | Show achievementCount: 0 until Phase 28 | ✓ |
| Query Phase 28 | Try to join achievements table even though Phase 28 not built | |

**User's choice:** Placeholder 0 — Phase 28 will populate full achievement data
**Notes:** ProfileVO includes achievementCount: 0 now, Phase 28 backend fills in real data later

---

## Profile Edit Fields

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse UpdateUserDTO | UpdateUserDTO already has bio, company, location, website | ✓ |
| New UpdateProfileDTO | Create separate DTO for profile-only fields | |

**User's choice:** Reuse existing UpdateUserDTO — PUT /users/me/profile works with existing DTO
**Notes:** Only bio, company, location, website are editable; username/email/role immutable

---

## Stats Endpoint

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse UserStatsDTO | Existing getUserStatsById already has all needed stats | ✓ |
| New aggregated stats method | Create separate aggregation in ProfileVO | |

**User's choice:** Reuse existing UserStatsDTO — stats already computed in UserService
**Notes:** UserStatsDTO already covers problems solved, submissions, streak, heatmap, globalRank, acceptanceRate

---

## Deferred Ideas

- **Cloud avatar storage (S3/OSS)** — future phase when production deployment needed
- **Custom avatar URL** — user provides URL instead of file upload — already supported via existing avatar String field in UpdateUserDTO

---

*Discussion log complete — decisions captured in 27-CONTEXT.md*
