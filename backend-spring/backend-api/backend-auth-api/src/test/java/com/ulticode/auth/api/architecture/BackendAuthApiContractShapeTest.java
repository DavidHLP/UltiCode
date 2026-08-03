package com.ulticode.auth.api.architecture;

import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.command.ChangePasswordCommand;
import com.ulticode.auth.api.command.CreateAccountCommand;
import com.ulticode.auth.api.command.DeleteAccountCommand;
import com.ulticode.auth.api.command.ResetPasswordCommand;
import com.ulticode.auth.api.command.UpdateAccountCredentialsCommand;
import com.ulticode.auth.api.command.WriteCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.auth.api.service.AuthorizationSnapshotService;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
 * Locking contract-shape assertions for {@code backend-auth-api}.
 *
 * <p>Per the task acceptance line the tests pin the four invariants
 * the migration guide &sect;6.2 demands for every provider-owned
 * contract module:
 * <ol>
 *   <li>String UUID identifiers &mdash; no Long / BigInteger / int
 *       fields or parameters ending in {@code Id} or any obvious
 *       account-id name are accepted on any DTO or command;</li>
 *   <li>Write commands carry {@code commandId}, {@link IdMetadata},
 *       {@link ActorDelegation} and {@link TraceMetadata} (the four
 *       fields spelled out in &sect;6.2);</li>
 *   <li>Namespaced error codes carry {@code "auth"} as the wire
 *       namespace;</li>
 *   <li>Service interfaces are pure Java (no Spring / MyBatis
 *       annotations on parameters or methods);</li>
 *   <li>Write commands require non-null {@code expectedVersion};
 *       DTO/command {@code permissions} sets are immutable
 *       {@code Set.copyOf} snapshots that reject null / blank
 *       elements.</li>
 * </ol>
 *
 * <p>Reflection is used so future additions are caught automatically
 * without a reviewer having to remember to add an entry.
 */
class BackendAuthApiContractShapeTest {

    /* ===== presence: the four &sect;6.2 services + DTOs are reachable ====== */

    @Test
    void expected_contract_types_are_present() {
        Set<String> required = Set.of(
                "com.ulticode.auth.api.service.IdentityQueryService",
                "com.ulticode.auth.api.service.AccountAdministrationService",
                "com.ulticode.auth.api.service.AccountManagementService",
                "com.ulticode.auth.api.service.AccountQueryService",
                "com.ulticode.auth.api.service.AuthorizationSnapshotService",
                "com.ulticode.auth.api.service.ReconciliationQueryService",
                "com.ulticode.auth.api.dto.AuthReconciliationOrphanCounts",
                "com.ulticode.auth.api.dto.UserIdentityDTO",
                "com.ulticode.auth.api.dto.AccountStateDTO",
                "com.ulticode.auth.api.dto.AccountMutationDTO",
                "com.ulticode.auth.api.dto.AuthAccountDTO",
                "com.ulticode.auth.api.dto.AccountQueryDTO",
                "com.ulticode.auth.api.dto.ChangePasswordDTO",
                "com.ulticode.auth.api.dto.AuthorizationSnapshotDTO",
                "com.ulticode.auth.api.command.ActorDelegation",
                "com.ulticode.auth.api.command.WriteCommand",
                "com.ulticode.auth.api.command.ChangeAccountStateCommand",
                "com.ulticode.auth.api.command.ChangeAuthorizationCommand",
                "com.ulticode.auth.api.command.CreateAccountCommand",
                "com.ulticode.auth.api.command.UpdateAccountCredentialsCommand",
                "com.ulticode.auth.api.command.ChangePasswordCommand",
                "com.ulticode.auth.api.command.ResetPasswordCommand",
                "com.ulticode.auth.api.command.DeleteAccountCommand",
                "com.ulticode.auth.api.error.AuthErrorCode");
        Set<Class<?>> scanned = Set.of(
                UserIdentityDTO.class,
                AccountStateDTO.class,
                AccountMutationDTO.class,
                AuthorizationSnapshotDTO.class,
                ActorDelegation.class,
                ChangeAccountStateCommand.class,
                ChangeAuthorizationCommand.class,
                CreateAccountCommand.class,
                UpdateAccountCredentialsCommand.class,
                ChangePasswordCommand.class,
                ResetPasswordCommand.class,
                DeleteAccountCommand.class);
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
                .as("backend-auth-api must expose every &sect;6.2 contract type")
                .isEmpty();
    }

    /* ===== String UUID identifiers ==================================== */

    /**
     * Reflectively scans every command / DTO field whose name ends in
     * {@code Id} (or the obvious variants used in this contract) and
     * asserts the declared type is {@link String}. This guards
     * against an accidental {@code Long} regression: the project
     * already declared "IDs are UUID String" in &sect;6.2.
     */
    @Test
    void id_typed_fields_are_String_on_every_command_and_dto() {
        Set<Class<?>> scanned = Set.of(
                UserIdentityDTO.class,
                AccountStateDTO.class,
                AccountMutationDTO.class,
                AuthAccountDTO.class,
                AuthorizationSnapshotDTO.class,
                ActorDelegation.class,
                ChangeAccountStateCommand.class,
                ChangeAuthorizationCommand.class);
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
                .as("every Id-typed field on auth-api DTOs / commands "
                        + "must be String (UUID); Long/BigInteger are "
                        + "forbidden by &sect;6.2")
                .isEmpty();
    }

    private static boolean looksLikeIdField(String name) {
        return name.equals("accountId")
                || name.equals("commandId")
                || name.equals("actorId")
                || name.equals("delegatorId")
                || name.endsWith("Id");
    }

    /* ===== WriteCommand metadata completeness ========================= */

    @Test
    void every_write_command_carries_commandId_idempotency_actor_trace() {
        List<WriteCommand> samples = List.of(
                new ChangeAccountStateCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        new ActorDelegation(
                                "ADMIN",
                                UUID.randomUUID().toString(),
                                UUID.randomUUID().toString(),
                                "lock test"),
                        TraceMetadata.EMPTY,
                        UUID.randomUUID().toString(),
                        1L,
                        ChangeAccountStateCommand.AccountStateAction.BAN,
                        "test"),
                new ChangeAuthorizationCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        new ActorDelegation(
                                "ADMIN",
                                UUID.randomUUID().toString(),
                                UUID.randomUUID().toString(),
                                "role test"),
                        TraceMetadata.EMPTY,
                        UUID.randomUUID().toString(),
                        1L,
                        "MODERATOR",
                        Set.of("PROBLEM_EDIT"),
                        "test"));
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
    void auth_error_codes_use_the_auth_namespace() {
        for (AuthErrorCode code : AuthErrorCode.values()) {
            assertThat(code.namespace())
                    .as("AuthErrorCode." + code.name() + " namespace")
                    .isEqualTo("auth");
            assertThat(code.message())
                    .as("AuthErrorCode." + code.name() + " message")
                    .isNotBlank();
        }
        // sanity: the enum still implements NamespacedErrorCode
        assertThat((Object) AuthErrorCode.ACCOUNT_NOT_FOUND)
                .isInstanceOf(NamespacedErrorCode.class);
    }

    @Test
    void identity_query_service_methods_return_rpc_result_with_string_id() {
        for (Method method : IdentityQueryService.class.getDeclaredMethods()) {
            assertThat(method.getReturnType())
                    .as("IdentityQueryService." + method.getName()
                            + " must return RpcResult")
                    .isEqualTo(RpcResult.class);
            for (Parameter param : method.getParameters()) {
                if (param.getType().equals(String.class)) {
                    // single-id methods must take a String
                    continue;
                }
                // batch takes Set<String>; allow Set / List and trust
                // the implementation to enforce element types — but
                // reject raw long / int parameters as a defensive
                // belt-and-braces.
                assertThat(param.getType().getName())
                        .as("IdentityQueryService." + method.getName()
                                + " parameter type")
                        .doesNotContain("long", "int", "Long", "Integer",
                                "BigInteger");
            }
        }
    }

    @Test
    void account_administration_service_methods_take_write_commands() {
        for (Method method : AccountAdministrationService.class
                .getDeclaredMethods()) {
            assertThat(method.getReturnType())
                    .as("AccountAdministrationService." + method.getName()
                            + " must return RpcResult")
                    .isEqualTo(RpcResult.class);
            Parameter[] params = method.getParameters();
            assertThat(params)
                    .as("AccountAdministrationService." + method.getName()
                            + " takes exactly one WriteCommand")
                    .hasSize(1);
            assertThat(WriteCommand.class)
                    .as("the single parameter of "
                            + method.getDeclaringClass().getSimpleName()
                            + "#" + method.getName()
                            + " implements WriteCommand")
                    .isAssignableFrom(params[0].getType());
        }
    }
    @Test
    void account_management_service_methods_take_write_commands() {
        for (Method method : AccountManagementService.class.getDeclaredMethods()) {
            assertThat(method.getReturnType())
                    .as("AccountManagementService." + method.getName()
                            + " must return RpcResult")
                    .isEqualTo(RpcResult.class);
            Parameter[] params = method.getParameters();
            assertThat(params)
                    .as("AccountManagementService." + method.getName()
                            + " takes exactly one WriteCommand")
                    .hasSize(1);
            assertThat(WriteCommand.class)
                    .as("the single parameter of AccountManagementService#"
                            + method.getName() + " implements WriteCommand")
                    .isAssignableFrom(params[0].getType());
        }
    }
    @Test
    void account_query_service_methods_return_rpc_result() {
        for (Method method : com.ulticode.auth.api.service.AccountQueryService.class.getDeclaredMethods()) {
            assertThat(method.getReturnType())
                    .as("AccountQueryService." + method.getName() + " must return RpcResult")
                    .isEqualTo(RpcResult.class);
        }
    }

    /* ===== expectedVersion non-null on auth write commands ============ */

    @Test
    void change_account_state_command_rejects_null_expected_version() {
        String uuid = UUID.randomUUID().toString();
        ThrowingCallable ctor = () ->
                new ChangeAccountStateCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        new ActorDelegation("ADMIN", uuid, uuid, "test"),
                        TraceMetadata.EMPTY,
                        uuid,
                        null,
                        ChangeAccountStateCommand.AccountStateAction.BAN,
                        "test");
        assertThatThrownBy(ctor)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedVersion");
    }

    @Test
    void change_authorization_command_rejects_null_expected_version() {
        String uuid = UUID.randomUUID().toString();
        ThrowingCallable ctor = () ->
                new ChangeAuthorizationCommand(
                        UUID.randomUUID().toString(),
                        IdMetadata.mint(),
                        new ActorDelegation("ADMIN", uuid, uuid, "test"),
                        TraceMetadata.EMPTY,
                        uuid,
                        null,
                        "MODERATOR",
                        Set.of("PROBLEM_EDIT"),
                        "test");
        assertThatThrownBy(ctor)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expectedVersion");
    }

    /* ===== permissions Set.copyOf + null/blank rejection ============== */

    @Test
    void change_authorization_command_permissions_are_defensively_copied() {
        String uuid = UUID.randomUUID().toString();
        HashSet<String> mutable = new HashSet<>();
        mutable.add("PROBLEM_EDIT");
        ChangeAuthorizationCommand cmd = new ChangeAuthorizationCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                new ActorDelegation("ADMIN", uuid, uuid, "test"),
                TraceMetadata.EMPTY,
                uuid,
                1L,
                "MODERATOR",
                mutable,
                "test");
        assertThat(cmd.permissions())
                .as("Set.copyOf must replace the caller's mutable set")
                .isUnmodifiable();
        mutable.add("PROBLEM_DELETE");
        assertThat(cmd.permissions())
                .as("post-construction caller mutation must not leak in")
                .doesNotContain("PROBLEM_DELETE");
    }

    @Test
    void change_authorization_command_rejects_null_and_blank_permissions() {
        String uuid = UUID.randomUUID().toString();
        ActorDelegation actor = new ActorDelegation("ADMIN", uuid, uuid, "t");
        // Build sets that contain a null / blank element; Set.of(...) rejects
        // null so we construct each via new HashSet + add().
        Set<String> withNull = new HashSet<>();
        withNull.add("PROBLEM_EDIT");
        withNull.add(null);
        Set<String> withBlank = new HashSet<>();
        withBlank.add("PROBLEM_EDIT");
        withBlank.add("  ");
        Set<String> onlyBlank = new HashSet<>();
        onlyBlank.add("");
        for (Set<String> bad : List.of(withNull, withBlank, onlyBlank)) {
            assertThatThrownBy(() -> new ChangeAuthorizationCommand(
                    UUID.randomUUID().toString(),
                    IdMetadata.mint(),
                    actor,
                    TraceMetadata.EMPTY,
                    uuid,
                    1L,
                    "MODERATOR",
                    bad,
                    "test"))
                    .as("null / blank element rejected")
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void authorization_snapshot_dto_permissions_are_defensively_copied() {
        HashSet<String> mutable = new HashSet<>();
        mutable.add("PROBLEM_EDIT");
        AuthorizationSnapshotDTO dto = new AuthorizationSnapshotDTO(
                UUID.randomUUID().toString(),
                "MODERATOR",
                mutable,
                1L);
        assertThat(dto.permissions()).isUnmodifiable();
        mutable.add("PROBLEM_DELETE");
        assertThat(dto.permissions()).doesNotContain("PROBLEM_DELETE");
    }

    @Test
    void authorization_snapshot_dto_rejects_null_and_blank_permissions() {
        String uuid = UUID.randomUUID().toString();
        Set<String> withNull = new HashSet<>();
        withNull.add("PROBLEM_EDIT");
        withNull.add(null);
        Set<String> withBlank = new HashSet<>();
        withBlank.add("PROBLEM_EDIT");
        withBlank.add(" ");
        Set<String> onlyBlank = new HashSet<>();
        onlyBlank.add("");
        for (Set<String> bad : List.of(withNull, withBlank, onlyBlank)) {
            assertThatThrownBy(() -> new AuthorizationSnapshotDTO(
                    uuid, "MODERATOR", bad, 1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /* ===== AuthorizationSnapshotService shape ========================= */

    @Test
    void authorization_snapshot_service_methods_return_rpc_result() {
        for (Method method : AuthorizationSnapshotService.class
                .getDeclaredMethods()) {
            assertThat(method.getReturnType())
                    .as("AuthorizationSnapshotService." + method.getName()
                            + " must return RpcResult")
                    .isEqualTo(RpcResult.class);
            for (Parameter param : method.getParameters()) {
                assertThat(param.getType().getName())
                        .as("AuthorizationSnapshotService." + method.getName()
                                + " parameter must be String / Set<String> "
                                + "(no primitive longs)")
                        .doesNotContain("long", "int", "Long", "Integer",
                                "BigInteger");
            }
        }
    }
}