package com.ulticode.app.user.port;

import java.time.LocalDateTime;

/**
 * Narrow Q-read view of the {@code users} table for the relocated
 * user-surface projections (P7-RELOCATE-USER-REMAINDER-001).
 *
 * <p>Captures only the columns the projections need to build
 * {@code UserVO}/{@code ProfileVO}. This is a Q-classified shared read
 * of the Auth-owned {@code users} table — same precedent as
 * {@code UserSearchReadPort} and {@code GlobalRankingMapper}.
 *
 * <p>The {@code users} table retains both account columns (username,
 * email, password, role — Auth-owned) and profile columns (name,
 * avatar, bio, company — mirrored in {@code user_profiles}). This view
 * reads the profile columns for read-side projection; mutations go
 * through {@code UserProfileMapper} (App-owned write path).
 *
 * @param id                user id
 * @param username          unique login handle
 * @param name              display name
 * @param email             email address
 * @param avatar            avatar URL
 * @param bio               bio text
 * @param company           company name
 * @param github            github handle
 * @param joinedAt          registration timestamp
 * @param location          location string
 * @param twitter           twitter handle
 * @param website           website URL
 * @param preferredLanguage  preferred UI language
 * @param role              role enum string
 * @param isActive          active flag
 * @param isBanned          ban flag
 * @param lastLoginAt       last login timestamp
 */
public record UserSummaryView(
        String id,
        String username,
        String name,
        String email,
        String avatar,
        String bio,
        String company,
        String github,
        LocalDateTime joinedAt,
        String location,
        String twitter,
        String website,
        String preferredLanguage,
        String role,
        Boolean isActive,
        Boolean isBanned,
        LocalDateTime lastLoginAt) {
}
