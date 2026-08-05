package com.ulticode.app.user.port;

import java.time.LocalDateTime;

/**
 * Narrow Q-read view joining the Auth-owned {@code users} table
 * (account columns) with the App-owned {@code user_profiles} table
 * (profile columns).
 *
 * <p>Account columns (username, email, role, isActive, isBanned,
 * lastLoginAt, joinedAt) come from {@code users}; profile columns
 * (name, avatar, bio, company, github, location, twitter, website,
 * preferredLanguage) come from {@code user_profiles}.
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
