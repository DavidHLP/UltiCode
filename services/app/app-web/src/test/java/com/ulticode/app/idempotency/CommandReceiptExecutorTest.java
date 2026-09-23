package com.ulticode.app.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.idempotency.entity.AppCommandReceiptEntity;
import com.ulticode.app.idempotency.mapper.AppCommandReceiptMapper;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.submission.api.command.RejudgeCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandReceiptExecutorTest {

    @Mock
    private AppCommandReceiptMapper receiptMapper;

    private CommandReceiptExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new CommandReceiptExecutor(
                receiptMapper,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void claimsBeforeMutationAndFinalizesSuccessfulResultWithGenericFingerprint() {
        when(receiptMapper.insertClaim(any())).thenReturn(1);
        when(receiptMapper.markSuccess(anyString(), eq("\"ok\""))).thenReturn(1);
        RejudgeCommand command = command("key-1");

        RpcResult<String> result = executor.execute(
                "rejudge", command, String.class,
                traceId -> RpcResult.success("ok", traceId));

        assertThat(result.success()).isTrue();
        ArgumentCaptor<AppCommandReceiptEntity> claim = ArgumentCaptor.forClass(AppCommandReceiptEntity.class);
        InOrder order = inOrder(receiptMapper);
        order.verify(receiptMapper).insertClaim(claim.capture());
        order.verify(receiptMapper).markSuccess(anyString(), eq("\"ok\""));
        assertThat(claim.getValue().getRequestFingerprint())
                .isEqualTo(CommandReceiptExecutor.fingerprint(command));
    }

    @Test
    void replaysExistingSuccessWithoutRunningMutation() {
        RejudgeCommand command = command("key-2");
        AppCommandReceiptEntity existing = successReceipt(
                CommandReceiptExecutor.fingerprint(command), "\"replayed\"");
        when(receiptMapper.insertClaim(any())).thenReturn(0);
        when(receiptMapper.findByReceiptKey("SubmissionAdministrationService", "rejudge", "key-2"))
                .thenReturn(existing);

        RpcResult<String> result = executor.execute(
                "rejudge", command, String.class,
                traceId -> {
                    throw new AssertionError("replay must not mutate");
                });

        assertThat(result.data()).isEqualTo("replayed");
        verify(receiptMapper, never()).markSuccess(anyString(), anyString());
    }

    @Test
    void replaysVoidSuccessWithoutRequiringNullPayload() {
        RejudgeCommand command = command("key-void");
        AppCommandReceiptEntity existing = successReceipt(
                CommandReceiptExecutor.fingerprint(command), "null");
        when(receiptMapper.insertClaim(any())).thenReturn(0);
        when(receiptMapper.findByReceiptKey("SubmissionAdministrationService", "delete", "key-void"))
                .thenReturn(existing);

        RpcResult<Void> result = executor.execute(
                "delete", command, Void.class,
                traceId -> {
                    throw new AssertionError("void replay must not mutate");
                });

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isNull();
        verify(receiptMapper, never()).markSuccess(anyString(), anyString());
    }

    @Test
    void removesClaimWhenMutationReturnsFailure() {
        when(receiptMapper.insertClaim(any())).thenReturn(1);
        RejudgeCommand command = command("key-3");

        RpcResult<String> result = executor.execute(
                "rejudge", command, String.class,
                traceId -> RpcResult.failure(AppErrorCode.CONTENT_NOT_FOUND, traceId));

        assertThat(result.success()).isFalse();
        verify(receiptMapper).deleteClaim(anyString());
        verify(receiptMapper, never()).markSuccess(anyString(), anyString());
    }

    @Test
    void replaysLegacyProfileUpdateFingerprintWithoutRunningMutation() {
        UpdateProfileCommand command = profileUpdate("legacy-update", "Alice", "Engineer");
        AppCommandReceiptEntity existing = successReceipt(legacyUpdateFingerprint(command),
                "{\"accountId\":\"account-1\",\"name\":\"Alice\"}");
        when(receiptMapper.insertClaim(any())).thenReturn(0);
        when(receiptMapper.findByReceiptKey("ProfileWriteService", "updateProfile", "legacy-update"))
                .thenReturn(existing);

        RpcResult<ProfileWriteResult> result = executor.execute(
                "ProfileWriteService", "updateProfile", command, ProfileWriteResult.class,
                traceId -> {
                    throw new AssertionError("legacy replay must not mutate");
                });

        assertThat(result.success()).isTrue();
        assertThat(result.data().accountId()).isEqualTo("account-1");
        verify(receiptMapper, never()).markSuccess(anyString(), anyString());
    }

    @Test
    void replaysLegacyAvatarFingerprintWithoutRunningMutation() {
        UploadAvatarCommand command = avatar("legacy-avatar", "app/avatars/account-2.png");
        AppCommandReceiptEntity existing = successReceipt(legacyAvatarFingerprint(command),
                "{\"accountId\":\"account-2\",\"avatar\":\"app/avatars/account-2.png\"}");
        when(receiptMapper.insertClaim(any())).thenReturn(0);
        when(receiptMapper.findByReceiptKey("ProfileWriteService", "uploadAvatar", "legacy-avatar"))
                .thenReturn(existing);

        RpcResult<ProfileWriteResult> result = executor.execute(
                "ProfileWriteService", "uploadAvatar", command, ProfileWriteResult.class,
                traceId -> {
                    throw new AssertionError("legacy replay must not mutate");
                });

        assertThat(result.success()).isTrue();
        assertThat(result.data().avatar()).isEqualTo("app/avatars/account-2.png");
        verify(receiptMapper, never()).markSuccess(anyString(), anyString());
    }

    @Test
    void rejectsConcurrentClaimWhileExistingCommandIsProcessing() {
        RejudgeCommand command = command("key-processing");
        AppCommandReceiptEntity existing = successReceipt(
                CommandReceiptExecutor.fingerprint(command), null);
        existing.setStatus("PROCESSING");
        when(receiptMapper.insertClaim(any())).thenReturn(0);
        when(receiptMapper.findByReceiptKey("SubmissionAdministrationService", "rejudge", "key-processing"))
                .thenReturn(existing);

        RpcResult<String> result = executor.execute(
                "rejudge", command, String.class,
                traceId -> {
                    throw new AssertionError("processing duplicate must not mutate");
                });

        assertThat(result.success()).isFalse();
        assertThat(result.error().code()).isEqualTo(AppErrorCode.UNEXPECTED_APP_STATE.code());
        verify(receiptMapper, never()).markSuccess(anyString(), anyString());
        verify(receiptMapper, never()).deleteClaim(anyString());
    }

    private static AppCommandReceiptEntity successReceipt(String fingerprint, String payload) {
        AppCommandReceiptEntity receipt = new AppCommandReceiptEntity();
        receipt.setStatus("SUCCESS");
        receipt.setRequestFingerprint(fingerprint);
        receipt.setResultPayload(payload);
        return receipt;
    }

    private static UpdateProfileCommand profileUpdate(String key, String name, String bio) {
        return new UpdateProfileCommand(
                "profile-" + key,
                new IdMetadata(key, null, null),
                adminActor(),
                new TraceMetadata("trace-" + key, null, null, null),
                "account-1", name, null, bio, null, null, null, null, null, null);
    }

    private static UploadAvatarCommand avatar(String key, String avatarUrl) {
        return new UploadAvatarCommand(
                "avatar-" + key,
                new IdMetadata(key, null, null),
                adminActor(),
                new TraceMetadata("trace-" + key, null, null, null),
                "account-2", avatarUrl);
    }

    private static ActorDelegation adminActor() {
        return new ActorDelegation("ADMIN", "admin-1", "admin-1", "test");
    }

    private static String legacyUpdateFingerprint(UpdateProfileCommand command) {
        return sha256(String.join("|",
                nullSafe(command.accountId()),
                nullSafe(command.name()),
                nullSafe(command.avatar()),
                nullSafe(command.bio()),
                nullSafe(command.company()),
                nullSafe(command.github()),
                nullSafe(command.location()),
                nullSafe(command.twitter()),
                nullSafe(command.website()),
                nullSafe(command.preferredLanguage())));
    }

    private static String legacyAvatarFingerprint(UploadAvatarCommand command) {
        return sha256(command.accountId() + "|" + command.avatarUrl());
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private static RejudgeCommand command(String key) {
        return new RejudgeCommand(
                "cmd-" + key,
                IdMetadata.of(key, null),
                new ActorDelegation("ADMIN", "admin-1", "admin-1", "rejudge"),
                new TraceMetadata("t-1", null, null, null),
                "submission-1",
                false);
    }
}
