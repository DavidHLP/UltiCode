# Phase 27: Profile Backend - Context

**Gathered:** 2026-04-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Profile Backend delivers user profile viewing, editing, and avatar upload. It depends on Phase 26 (follow system for social counts).

**In scope:**
1. GET /users/{id}/profile — public profile with username, avatar, bio, social stats, achievements
2. PUT /users/me/profile — edit own bio, company, location, website
3. Avatar upload via MultipartFile
4. Aggregated stats: problems solved, submissions, contest rating, follower/following

**Out of scope:**
- Profile page frontend (Phase 29)
- Achievement backend (Phase 28) — achievements in profile use placeholder/empty for now
- Social stats computation (handled by Phase 26 follow system)

</domain>

<decisions>
## Implementation Decisions

### Avatar Storage
- **D-01:** Avatar uploaded via MultipartFile → stored as URL string in `user.avatar` column pointing to `/uploads/avatars/{uuid}.{ext}` on local filesystem
- **D-02:** Avatar upload endpoint: `POST /users/me/avatar` — returns avatar URL string

### Profile Data Structure
- **D-03:** GET /users/{id}/profile returns a new `ProfileVO` record (or DTO) combining UserVO fields + social stats + achievements
- **D-04:** ProfileVO includes: username, name, avatar, bio, company, location, website, joinedAt, preferredLanguage, plus stats (totalSolved, submissionCount, globalRank, acceptanceRate, followerCount, followingCount, achievementCount)

### Profile Edit
- **D-05:** PUT /users/me/profile reuses existing `UpdateUserDTO` — already has all needed fields (bio, company, location, website)
- **D-06:** Only bio, company, location, website are editable; username/email/role are immutable

### Social Stats Aggregation
- **D-07:** Follower/following counts fetched from Phase 26 FollowService — inject FollowMapper or FollowService into UserService
- **D-08:** If Phase 26 follow counts are unavailable (not yet built), use 0 as placeholder — Phase 27 can build against Phase 26's existing entity/mapper

### Achievements in Profile
- **D-09:** Achievement count included in profile (Phase 28 will populate full achievement data) — placeholder: 0 achievements until Phase 28

### Stats Endpoint
- **D-10:** Existing `UserStatsDTO` (getUserStatsById) already covers problems solved, submissions, streak, heatmap, globalRank, acceptanceRate — reused in ProfileVO

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Backend Architecture
- `backend-spring/src/main/java/com/ulticode/modules/user/entity/User.java` — User entity with avatar, bio, company, location, website fields
- `backend-spring/src/main/java/com/ulticode/modules/user/dto/UserVO.java` — existing user view DTO
- `backend-spring/src/main/java/com/ulticode/modules/user/dto/UpdateUserDTO.java` — existing profile update DTO
- `backend-spring/src/main/java/com/ulticode/modules/user/dto/UserStatsDTO.java` — existing stats DTO (reused in profile)
- `backend-spring/src/main/java/com/ulticode/modules/user/service/impl/UserServiceImpl.java` — existing service with updateCurrentUser
- `backend-spring/src/main/java/com/ulticode/modules/follow/mapper/FollowMapper.java` — Phase 26 follow counts
- `backend-spring/src/main/java/com/ulticode/common/util/SecurityUtil.java` — getCurrentUserId helper

### Java Standards
- `~/.claude/rules/java/coding-style.md` — prefer `record` for DTOs, constructor injection, immutable patterns
- `~/.claude/rules/java/patterns.md` — DTO mapping with records, service layer patterns
- `~/.claude/rules/java/security.md` — input validation, no hardcoded secrets

### Project Standards
- `backend-spring/src/main/java/com/ulticode/common/response/Result.java` — API response envelope
- `.planning/PROJECT.md` — project overview
- `.planning/REQUIREMENTS.md` — requirements PROFILE-01, PROFILE-03

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **User entity** (`User.java`): already has avatar, bio, company, location, website fields
- **UserVO**: already has most public profile fields
- **UpdateUserDTO**: already supports bio, company, location, website updates
- **UserStatsDTO**: already has totalSolved, submissionCount, globalRank, acceptanceRate, streak, heatmap
- **SecurityUtil.getCurrentUserId()**: already available for @CurrentUser replacement

### Established Patterns
- Controller returns `Result<T>` wrapper (via common response)
- Service layer business logic, mapper for DB access
- Constructor injection via `@RequiredArgsConstructor`
- DTOs use `@Data` Lombok + `@JsonInclude(NON_NULL)`

### Integration Points
- FollowService/FollowMapper for follower/following counts (Phase 26)
- Existing stats (UserStatsDTO) for problem/submission counts
- File upload via Spring MultipartFile — likely needs `WebMvcConfig` for static resource serving

</code_context>

<specifics>
## Specific Ideas

- Profile page at `/profile/{username}` — handled by Phase 29 frontend, backend just needs the API
- Achievements in profile — Phase 28 backend needed for real data; Phase 27 includes the field as placeholder (0 count)

</specifics>

<deferred>
## Deferred Ideas

- Avatar storage on cloud (S3/OSS) — local filesystem is simpler for v1; cloud storage can be Phase 33+ if needed
- Custom avatar URL (user provides URL instead of uploading file) — UpdateUserDTO already has avatar field as String

</deferred>

---

*Phase: 27-profile-backend*
*Context gathered: 2026-04-21*
