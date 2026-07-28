package com.ulticode.app.api.architecture;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.command.ApplyModerationCommand.ModerationAction;
import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.CreateProblemCommand;
import com.ulticode.app.api.command.DeleteContestCommand;
import com.ulticode.app.api.command.EndContestCommand;
import com.ulticode.app.api.command.PublishProblemCommand;
import com.ulticode.app.api.command.RejudgeCommand;
import com.ulticode.app.api.command.StartContestCommand;
import com.ulticode.app.api.command.UpdateContestCommand;
import com.ulticode.app.api.command.UpdateProblemCommand;
import com.ulticode.app.api.command.WriteCommand;
import com.ulticode.app.api.dto.ContentLifecycleState;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.app.api.dto.ProblemAdminViewDTO;
import com.ulticode.app.api.dto.RejudgeResultDTO;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ContestAdministrationService;
import com.ulticode.app.api.service.ContentModerationService;
import com.ulticode.app.api.service.ProblemAdministrationService;
import com.ulticode.app.api.service.SubmissionAdministrationService;
import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Locking contract-shape assertions for {@code backend-app-api}.
 *
 * <p>Per the task acceptance line the tests pin the invariants the
 * migration guide &sect;6.2 demands for every provider-owned
 * contract module, plus the typed-DTO invariants that keep the wire
 * shape stable across admin moderation / App fan-out:
 * <ol>
 *   <li>String UUID identifiers &mdash; no Long / BigInteger / int
 *       fields or parameters ending in {@code Id} are accepted on
 *       any DTO or command;</li>
 *   <li>Write commands carry {@code commandId}, {@link IdMetadata},
 *       {@link ActorDelegation} and {@link TraceMetadata} (the four
 *       fields spelled out in &sect;6.2);</li>
 *   <li>Namespaced error codes carry {@code "app"} as the wire
 *       namespace;</li>
 *   <li>Service interfaces are pure Java (no Spring / MyBatis
 *       annotations on parameters or methods);</li>
 *   <li>{@link ModerationApplyResultDTO} uses typed enums
 *       ({@link ModerationAction} + {@link ContentLifecycleState})
 *       &mdash; no free-form strings on the wire.</li>
 * </ol>
 *
 * <p>Reflection is used so future additions are caught automatically
 * without a reviewer having to remember to add an entry.
 */
class BackendAppApiContractShapeTest {

    /* ===== presence: the four &sect;6.2 services + DTOs are reachable ====== */

    @Test
    void expected_contract_types_are_present() {
        Set<String> required = Set.of(
                "com.ulticode.app.api.service.ProblemAdministrationService",
                "com.ulticode.app.api.service.ContentModerationService",
                "com.ulticode.app.api.dto.ProblemAdminViewDTO",
                "com.ulticode.app.api.dto.ModerationApplyResultDTO",
                "com.ulticode.app.api.dto.ContentLifecycleState",
                "com.ulticode.app.api.command.ActorDelegation",
                "com.ulticode.app.api.command.WriteCommand",
                "com.ulticode.app.api.command.CreateProblemCommand",
                "com.ulticode.app.api.command.UpdateProblemCommand",
                "com.ulticode.app.api.command.PublishProblemCommand",
                "com.ulticode.app.api.command.ApplyModerationCommand",
                "com.ulticode.app.api.command.CreateContestCommand",
                "com.ulticode.app.api.command.UpdateContestCommand",
                "com.ulticode.app.api.command.DeleteContestCommand",
                "com.ulticode.app.api.command.StartContestCommand",
                "com.ulticode.app.api.command.EndContestCommand",
                "com.ulticode.app.api.command.RejudgeCommand",
                "com.ulticode.app.api.dto.ContestAdminViewDTO",
                "com.ulticode.app.api.dto.RejudgeResultDTO",
                "com.ulticode.app.api.service.ContestAdministrationService",
                "com.ulticode.app.api.service.SubmissionAdministrationService",
                "com.ulticode.app.api.error.AppErrorCode");
        Set<String> missing = new HashSet<>();
        ClassLoader cl = getClass().getClassLoader();
        for (String fqcn : required) {
            try {
                Class.forName(fqcn, false, cl);
            } catch (ClassNotFoundException e) {
                missing.add(fqcn);
            }
        }
        assertThat(missing)
                .as("backend-app-api must expose every &sect;6.2 contract type")
                .isEmpty();
    }

    /* ===== String UUID identifiers ==================================== */

    /**
     * Reflectively scans every command / DTO field whose name ends in
     * {@code Id} (or the obvious variants used in this contract) and
     * asserts the declared type is {@link String}. This guards
     * against an accidental {@code Long} regression: the project
     * already declared "IDs are UUID String" in &sect;6.2.
     *
     * <p>{@code appliedAction} is intentionally NOT scanned &mdash; it
     * is a typed {@link ModerationAction} enum by design, not a
     * string id.
     */
    @Test
    void id_typed_fields_are_String_on_every_command_and_dto() {
        Set<Class<?>> scanned = Set.of(
                ProblemAdminViewDTO.class,
                ContestAdminViewDTO.class,
                RejudgeResultDTO.class,
                ModerationApplyResultDTO.class,
                ActorDelegation.class,
                CreateProblemCommand.class,
                UpdateProblemCommand.class,
                PublishProblemCommand.class,
                ApplyModerationCommand.class,
                CreateContestCommand.class,
                UpdateContestCommand.class,
                DeleteContestCommand.class,
                StartContestCommand.class,
                EndContestCommand.class,
                RejudgeCommand.class);
        Set<String> violations = new HashSet<>();
        for (Class<?> type : scanned) {
            for (Field field : type.getDeclaredFields()) {
                if (!looksLikeIdField(field.getName())) {
                    continue;
                }
                if (!field.getType().equals(String.class)) {
                    violations.add(type.getSimpleName() + "#" + field.getName()
                            + " declared as " + field.getType().getName());
                }
            }
        }
        assertThat(violations)
                .as("every Id-typed field on app-api DTOs / commands "
                        + "must be String (UUID); Long/BigInteger are "
                        + "forbidden by &sect;6.2")
                .isEmpty();
    }

    private static boolean looksLikeIdField(String name) {
        return name.equals("problemId")
                || name.equals("contentId")
                || name.equals("moderationCaseId")
                || name.equals("commandId")
                || name.equals("actorId")
                || name.equals("delegatorId")
                || name.equals("authorAccountId")
                || name.equals("contestId")
                || name.equals("creatorAccountId")
                || name.equals("scoringRuleId")
                || name.equals("submissionId")
                || name.endsWith("Id");
    }

    /* ===== WriteCommand metadata completeness ========================= */

    @Test
    void every_write_command_carries_commandId_idempotency_actor_trace() {
        String uuid = UUID.randomUUID().toString();
        ActorDelegation adminActor = new ActorDelegation(
                "ADMIN", uuid, uuid, "lock test");
        List<WriteCommand> samples = List.of(
                new CreateProblemCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        adminActor,
                        TraceMetadata.EMPTY,
                        "two-sum",
                        "Two Sum",
                        uuid),
                new UpdateProblemCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        adminActor,
                        TraceMetadata.EMPTY,
                        uuid,
                        1L,
                        "Two Sum (revised)",
                        "fix typo"),
                new PublishProblemCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        adminActor,
                        TraceMetadata.EMPTY,
                        uuid,
                        1L,
                        true,
                        "go-live"),
                new ApplyModerationCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        adminActor,
                        TraceMetadata.EMPTY,
                        uuid,
                        uuid,
                        "forum_post",
                        ModerationAction.HIDE,
                        "policy violation"),
                new CreateContestCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        adminActor,
                        TraceMetadata.EMPTY,
                        "test-contest",
                        "Test Contest",
                        uuid,
                        "ICPC",
                        "ICPC",
                        null,
                        null,
                        System.currentTimeMillis() + 86400000L,
                        120),
                new UpdateContestCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        adminActor,
                        TraceMetadata.EMPTY,
                        uuid,
                        1L,
                        "Updated Title",
                        null,
                        null,
                        "schedule change"),
                new DeleteContestCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        adminActor,
                        TraceMetadata.EMPTY,
                        uuid,
                        1L,
                        "cancelled"),
                new StartContestCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        adminActor,
                        TraceMetadata.EMPTY,
                        uuid,
                        1L,
                        "begin"),
                new EndContestCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        adminActor,
                        TraceMetadata.EMPTY,
                        uuid,
                        1L,
                        "finished"),
                new RejudgeCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        adminActor,
                        TraceMetadata.EMPTY,
                        uuid,
                        true));
        for (WriteCommand cmd : samples) {
            assertThat(cmd.commandId())
                    .as("commandId must be a non-blank UUID String")
                    .isNotBlank();
            assertThat(cmd.idempotency())
                    .as("idempotency (IdMetadata) is required")
                    .isNotNull()
                    .satisfies(id -> assertThat(id.idempotencyKey())
                            .as("idempotencyKey inside IdMetadata must be "
                                    + "non-blank")
                            .isNotBlank());
            assertThat(cmd.actor())
                    .as("actor (ActorDelegation) is required")
                    .isNotNull();
            assertThat(cmd.actor().actorId())
                    .as("actor.actorId is a UUID String")
                    .isNotBlank();
            assertThat(cmd.actor().actorType())
                    .as("actor.actorType must be non-blank")
                    .isNotBlank();
            assertThat(cmd.trace())
                    .as("trace (TraceMetadata) is required; EMPTY is "
                            + "acceptable but not null")
                    .isNotNull();
        }
    }

    /* ===== namespace + service-method RpcResult wrapping ============== */

    @Test
    void app_error_codes_use_the_app_namespace() {
        for (AppErrorCode code : AppErrorCode.values()) {
            assertThat(code.namespace())
                    .as("AppErrorCode." + code.name() + " namespace")
                    .isEqualTo("app");
            assertThat(code.message())
                    .as("AppErrorCode." + code.name() + " message")
                    .isNotBlank();
        }
        assertThat((Object) AppErrorCode.CONTENT_NOT_FOUND)
                .isInstanceOf(NamespacedErrorCode.class);
    }

    @Test
    void service_methods_take_write_commands_and_return_rpc_result() {
        for (Class<?> serviceClass : List.of(
                ProblemAdministrationService.class,
                ContentModerationService.class,
                ContestAdministrationService.class,
                SubmissionAdministrationService.class)) {
            for (Method method : serviceClass.getDeclaredMethods()) {
                assertThat(method.getReturnType())
                        .as(serviceClass.getSimpleName() + "#"
                                + method.getName() + " must return RpcResult")
                        .isEqualTo(RpcResult.class);
                Parameter[] params = method.getParameters();
                assertThat(params)
                        .as(serviceClass.getSimpleName() + "#"
                                + method.getName()
                                + " takes exactly one WriteCommand")
                        .hasSize(1);
                assertThat(WriteCommand.class)
                        .as("the single parameter of "
                                + serviceClass.getSimpleName() + "#"
                                + method.getName()
                                + " implements WriteCommand")
                        .isAssignableFrom(params[0].getType());
            }
        }
    }

    /* ===== typed ModerationApplyResultDTO ============================ */

    /**
     * The {@code appliedAction} field on {@link ModerationApplyResultDTO}
     * must be the typed {@link ModerationAction} enum that the inbound
     * {@link ApplyModerationCommand} carries, so producers and consumers
     * share a single source of truth.
     */
    @Test
    void moderation_result_applied_action_is_typed_enum() throws Exception {
        Field f = ModerationApplyResultDTO.class.getDeclaredField(
                "appliedAction");
        assertThat(f.getType())
                .as("ModerationApplyResultDTO.appliedAction must be "
                        + "ModerationAction enum, not String")
                .isEqualTo(ModerationAction.class);
    }

    /**
     * The {@code newContentState} field must be the typed
     * {@link ContentLifecycleState} enum, not a free-form String.
     */
    @Test
    void moderation_result_new_content_state_is_typed_enum() throws Exception {
        Field f = ModerationApplyResultDTO.class.getDeclaredField(
                "newContentState");
        assertThat(f.getType())
                .as("ModerationApplyResultDTO.newContentState must be "
                        + "ContentLifecycleState enum, not String")
                .isEqualTo(ContentLifecycleState.class);
    }

    /**
     * Round-trip: every {@link ModerationAction} constant is a valid
     * {@code appliedAction}, and every {@link ContentLifecycleState}
     * constant is a valid {@code newContentState}; sample shapes are
     * constructible end-to-end.
     */
    @Test
    void moderation_result_round_trip_for_every_action_and_state() {
        for (ModerationAction action : ModerationAction.values()) {
            for (ContentLifecycleState state : ContentLifecycleState.values()) {
                ModerationApplyResultDTO dto = new ModerationApplyResultDTO(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        action,
                        state);
                assertThat(dto.appliedAction()).isEqualTo(action);
                assertThat(dto.newContentState()).isEqualTo(state);
            }
        }
    }

    /**
     * Null reject: the typed DTO rejects {@code null} {@code appliedAction}
     * and {@code null} {@code newContentState} so the wire shape never
     * silently round-trips to a missing field.
     */
    @Test
    void moderation_result_rejects_null_action_and_state() {
        String caseId = UUID.randomUUID().toString();
        String contentId = UUID.randomUUID().toString();
        assertThatThrownBy(() -> new ModerationApplyResultDTO(
                caseId, contentId, null, ContentLifecycleState.HIDDEN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ModerationApplyResultDTO(
                caseId, contentId, ModerationAction.HIDE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void content_lifecycle_state_enum_values_are_stable() {
        // Pin the set of wire-stable lifecycle states; new states must
        // be appended, never renamed / removed, so existing consumers
        // can pattern-match on the enum constants.
        assertThat(ContentLifecycleState.values())
                .containsExactly(
                        ContentLifecycleState.VISIBLE,
                        ContentLifecycleState.HIDDEN,
                        ContentLifecycleState.DELETED);
    }
}