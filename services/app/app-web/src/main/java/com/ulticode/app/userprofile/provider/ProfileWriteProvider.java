package com.ulticode.app.userprofile.provider;

import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ProfileWriteService;
import com.ulticode.app.idempotency.entity.AppCommandReceiptEntity;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.TraceMetadata;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Dubbo provider implementing {@link ProfileWriteService}.
 *
 * <p>Writes exclusively to the {@code user_profiles} table. Never reads
 * or writes the Auth-owned {@code users} table.
 *
 * <p>Implements provider-side replay-dedup per §6.2: a command carrying
 * an idempotency key is claimed atomically (receipt lookup) before
 * execution and finalized (receipt insert) in the same transaction as
 * the profile mutation. Retried commands replay the stored result.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0", timeout = 5000, retries = 0)
@RequiredArgsConstructor
public class ProfileWriteProvider implements ProfileWriteService {

    private static final String SERVICE_NAME = "ProfileWriteService";
    private static final String OP_UPDATE = "updateProfile";
    private static final String OP_UPLOAD_AVATAR = "uploadAvatar";

    private final UserProfileMapper userProfileMapper;
    private final AppCommandReceiptMapper receiptMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public RpcResult<ProfileWriteResult> updateProfile(UpdateProfileCommand command) {
        String traceId = safeTraceId(command);
        String idempotencyKey = extractIdempotencyKey(command);
        String fingerprint = computeFingerprint(command);

        try {
            // 1. Idempotency claim: check for existing receipt
            if (idempotencyKey != null) {
                AppCommandReceiptEntity existing = receiptMapper.findByReceiptKey(
                        SERVICE_NAME, OP_UPDATE, idempotencyKey);
                if (existing != null && "SUCCESS".equals(existing.getStatus())) {
                    String storedFp = existing.getRequestFingerprint();
                    if (storedFp != null && !storedFp.equals(fingerprint)) {
                        log.warn("Idempotency key conflict: key={} stored_fp={} received_fp={}",
                                idempotencyKey, storedFp, fingerprint);
                        return RpcResult.failure(AppErrorCode.IDEMPOTENCY_KEY_CONFLICT, traceId);
                    }
                    // Replay stored result
                    try {
                        ProfileWriteResult replayed = objectMapper.readValue(
                                existing.getResultPayload(), ProfileWriteResult.class);
                        log.info("Profile update replayed for account: {} (idempotencyKey={})",
                                command.accountId(), idempotencyKey);
                        return RpcResult.success(replayed, traceId);
                    } catch (Exception e) {
                        log.warn("Failed to replay stored result for key={}, re-executing: {}",
                                idempotencyKey, e.getMessage());
                        // Fall through to fresh execution
                    }
                }
            }

            // 2. Execute profile upsert (null-skip semantics, same as legacy)
            String accountId = command.accountId();

            UserProfile profile = userProfileMapper.selectById(accountId);
            boolean isNew = profile == null;
            if (isNew) {
                profile = new UserProfile();
                profile.setAccountId(accountId);
            }

            if (command.name() != null) {
                profile.setName(command.name());
            }
            if (command.avatar() != null) {
                profile.setAvatar(command.avatar());
            }
            if (command.bio() != null) {
                profile.setBio(command.bio());
            }
            if (command.company() != null) {
                profile.setCompany(command.company());
            }
            if (command.github() != null) {
                profile.setGithub(command.github());
            }
            if (command.location() != null) {
                profile.setLocation(command.location());
            }
            if (command.twitter() != null) {
                profile.setTwitter(command.twitter());
            }
            if (command.website() != null) {
                profile.setWebsite(command.website());
            }
            if (command.preferredLanguage() != null) {
                profile.setPreferredLanguage(command.preferredLanguage());
            }

            if (isNew) {
                userProfileMapper.insert(profile);
            } else {
                userProfileMapper.updateById(profile);
            }

            log.info("Profile updated for account: {}", accountId);

            ProfileWriteResult result = new ProfileWriteResult(
                    profile.getAccountId(),
                    profile.getName(),
                    profile.getAvatar(),
                    profile.getBio(),
                    profile.getCompany(),
                    profile.getGithub(),
                    profile.getLocation(),
                    profile.getTwitter(),
                    profile.getWebsite(),
                    profile.getPreferredLanguage());

            // 3. Finalize: record receipt in same transaction (atomic with mutation)
            if (idempotencyKey != null) {
                recordReceipt(command, idempotencyKey, fingerprint, result, traceId);
            }

            return RpcResult.success(result, traceId);

        } catch (Exception e) {
            log.error("Profile update failed for account: {}", command.accountId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
    }


    @Override
    @Transactional
    public RpcResult<ProfileWriteResult> uploadAvatar(UploadAvatarCommand command) {
        String traceId = command.trace() != null ? command.trace().traceId() : null;
        String idempotencyKey = command.idempotency() != null
                ? command.idempotency().idempotencyKey() : null;
        String fingerprint = sha256Hex(command.accountId() + "|" + command.avatarUrl());

        try {
            // 1. Idempotency claim
            if (idempotencyKey != null) {
                AppCommandReceiptEntity existing = receiptMapper.findByReceiptKey(
                        SERVICE_NAME, OP_UPLOAD_AVATAR, idempotencyKey);
                if (existing != null && "SUCCESS".equals(existing.getStatus())) {
                    String storedFp = existing.getRequestFingerprint();
                    if (storedFp != null && !storedFp.equals(fingerprint)) {
                        return RpcResult.failure(AppErrorCode.IDEMPOTENCY_KEY_CONFLICT, traceId);
                    }
                    try {
                        ProfileWriteResult replayed = objectMapper.readValue(
                                existing.getResultPayload(), ProfileWriteResult.class);
                        return RpcResult.success(replayed, traceId);
                    } catch (Exception e) {
                        log.warn("Failed to replay avatar upload result for key={}", idempotencyKey);
                    }
                }
            }

            // 2. Execute avatar upsert
            String accountId = command.accountId();
            UserProfile profile = userProfileMapper.selectById(accountId);
            boolean isNew = profile == null;
            if (isNew) {
                profile = new UserProfile();
                profile.setAccountId(accountId);
            }
            profile.setAvatar(command.avatarUrl());

            if (isNew) {
                userProfileMapper.insert(profile);
            } else {
                userProfileMapper.updateById(profile);
            }

            log.info("Avatar updated for account: {}", accountId);

            ProfileWriteResult result = new ProfileWriteResult(
                    profile.getAccountId(), profile.getName(), profile.getAvatar(),
                    profile.getBio(), profile.getCompany(), profile.getGithub(),
                    profile.getLocation(), profile.getTwitter(), profile.getWebsite(),
                    profile.getPreferredLanguage());

            // 3. Finalize receipt in same transaction
            if (idempotencyKey != null) {
                recordAvatarReceipt(command, idempotencyKey, fingerprint, result, traceId);
            }

            return RpcResult.success(result, traceId);

        } catch (Exception e) {
            log.error("Avatar update failed for account: {}", command.accountId(), e);
            return RpcResult.failure(AppErrorCode.UNEXPECTED_APP_STATE, traceId);
        }
    }

    private void recordReceipt(UpdateProfileCommand command, String idempotencyKey,
                               String fingerprint, ProfileWriteResult result, String traceId) {
        try {
            AppCommandReceiptEntity receipt = new AppCommandReceiptEntity();
            receipt.setId(UUID.randomUUID().toString());
            receipt.setCommandId(command.commandId());
            receipt.setService(SERVICE_NAME);
            receipt.setOperation(OP_UPDATE);
            receipt.setIdempotencyKey(idempotencyKey);
            receipt.setRequestFingerprint(fingerprint);
            receipt.setStatus("SUCCESS");
            receipt.setResultPayload(objectMapper.writeValueAsString(result));
            receipt.setActorType("USER");
            receipt.setActorId(command.actor() != null ? command.actor().actorId() : null);
            receipt.setTraceId(traceId);
            receiptMapper.insert(receipt);
        } catch (Exception e) {
            log.error("Failed to record idempotency receipt for commandId={}, key={}: {}",
                    command.commandId(), idempotencyKey, e.getMessage());
            // Re-throw to rollback the transaction (receipt failure must nullify mutation)
            throw new RuntimeException("Idempotency receipt insert failed", e);
        }
    }


    private void recordAvatarReceipt(UploadAvatarCommand command, String idempotencyKey,
                                     String fingerprint, ProfileWriteResult result, String traceId) {
        try {
            AppCommandReceiptEntity receipt = new AppCommandReceiptEntity();
            receipt.setId(UUID.randomUUID().toString());
            receipt.setCommandId(command.commandId());
            receipt.setService(SERVICE_NAME);
            receipt.setOperation(OP_UPLOAD_AVATAR);
            receipt.setIdempotencyKey(idempotencyKey);
            receipt.setRequestFingerprint(fingerprint);
            receipt.setStatus("SUCCESS");
            receipt.setResultPayload(objectMapper.writeValueAsString(result));
            receipt.setActorType("USER");
            receipt.setActorId(command.actor() != null ? command.actor().actorId() : null);
            receipt.setTraceId(traceId);
            receiptMapper.insert(receipt);
        } catch (Exception e) {
            log.error("Failed to record avatar receipt for commandId={}, key={}: {}",
                    command.commandId(), idempotencyKey, e.getMessage());
            throw new RuntimeException("Idempotency receipt insert failed", e);
        }
    }

    private static String extractIdempotencyKey(UpdateProfileCommand command) {
        if (command.idempotency() != null) {
            return command.idempotency().idempotencyKey();
        }
        return null;
    }

    private static String computeFingerprint(UpdateProfileCommand command) {
        String payload = String.join("|",
                nullSafe(command.accountId()),
                nullSafe(command.name()),
                nullSafe(command.avatar()),
                nullSafe(command.bio()),
                nullSafe(command.company()),
                nullSafe(command.github()),
                nullSafe(command.location()),
                nullSafe(command.twitter()),
                nullSafe(command.website()),
                nullSafe(command.preferredLanguage()));
        return sha256Hex(payload);
    }

    private static String nullSafe(String s) {
        return s != null ? s : "";
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private static String safeTraceId(UpdateProfileCommand command) {
        TraceMetadata trace = command.trace();
        return trace != null ? trace.traceId() : null;
    }
}
