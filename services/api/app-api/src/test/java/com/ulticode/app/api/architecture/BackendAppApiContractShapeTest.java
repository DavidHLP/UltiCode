package com.ulticode.app.api.architecture;

import com.ulticode.app.api.command.ApplyModerationCommand;
import com.ulticode.app.api.command.ApplyModerationCommand.ModerationAction;
import com.ulticode.app.api.command.CreateContestCommand;
import com.ulticode.app.api.command.CreateProblemCommand;
import com.ulticode.app.api.command.DeleteContestCommand;
import com.ulticode.app.api.command.EndContestCommand;
import com.ulticode.app.api.command.PublishProblemCommand;
import com.ulticode.app.api.command.StartContestCommand;
import com.ulticode.app.api.command.UpdateContestCommand;
import com.ulticode.app.api.command.UpdateProblemCommand;
import com.ulticode.app.api.command.UpdateProfileCommand;
import com.ulticode.app.api.command.UploadAvatarCommand;
import com.ulticode.app.api.dto.ContestAdminViewDTO;
import com.ulticode.app.api.dto.ContentLifecycleState;
import com.ulticode.app.api.dto.ModerationApplyResultDTO;
import com.ulticode.app.api.dto.ProblemAdminViewDTO;
import com.ulticode.app.api.dto.ProfileWriteResult;
import com.ulticode.app.api.error.AppErrorCode;
import com.ulticode.app.api.service.ContestAdministrationService;
import com.ulticode.app.api.service.ContentModerationService;
import com.ulticode.app.api.service.ProblemAdministrationService;
import com.ulticode.app.api.service.ProfileWriteService;
import com.ulticode.app.api.service.UserProfileQueryService;
import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.error.NamespacedErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BackendAppApiContractShapeTest {

    @Test
    void expected_app_contract_types_are_present() {
        Set<String> required = Set.of(
                ProblemAdministrationService.class.getName(),
                ContentModerationService.class.getName(),
                ContestAdministrationService.class.getName(),
                ProblemAdminViewDTO.class.getName(),
                ModerationApplyResultDTO.class.getName(),
                ContentLifecycleState.class.getName(),
                ActorDelegation.class.getName(),
                WriteCommand.class.getName(),
                CreateProblemCommand.class.getName(),
                UpdateProblemCommand.class.getName(),
                PublishProblemCommand.class.getName(),
                ApplyModerationCommand.class.getName(),
                CreateContestCommand.class.getName(),
                UpdateContestCommand.class.getName(),
                DeleteContestCommand.class.getName(),
                StartContestCommand.class.getName(),
                EndContestCommand.class.getName(),
                ContestAdminViewDTO.class.getName(),
                UploadAvatarCommand.class.getName(),
                ProfileWriteResult.class.getName(),
                ProfileWriteService.class.getName(),
                UserProfileDTO.class.getName(),
                UserProfileQueryService.class.getName(),
                AppErrorCode.class.getName());
        Set<String> missing = new HashSet<>();
        ClassLoader cl = getClass().getClassLoader();
        for (String fqcn : required) {
            try {
                Class.forName(fqcn, false, cl);
            } catch (ClassNotFoundException e) {
                missing.add(fqcn);
            }
        }
        assertThat(missing).isEmpty();
    }

    @Test
    void id_typed_fields_are_String_on_app_commands_and_dtos() {
        Set<Class<?>> scanned = Set.of(
                ProblemAdminViewDTO.class,
                ContestAdminViewDTO.class,
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
                UpdateProfileCommand.class,
                UploadAvatarCommand.class,
                ProfileWriteResult.class);
        Set<String> violations = new HashSet<>();
        for (Class<?> type : scanned) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getName().endsWith("Id")
                        && !field.getType().equals(String.class)) {
                    violations.add(type.getSimpleName() + "#" + field.getName()
                            + " declared as " + field.getType().getName());
                }
            }
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void app_write_commands_carry_common_metadata() {
        String uuid = UUID.randomUUID().toString();
        ActorDelegation actor = new ActorDelegation("ADMIN", uuid, uuid, "lock test");
        List<WriteCommand> samples = List.of(
                new CreateProblemCommand(uuid, IdMetadata.mint(), actor,
                        TraceMetadata.EMPTY, "two-sum", "Two Sum", uuid),
                new UpdateProblemCommand(uuid, IdMetadata.mint(), actor,
                        TraceMetadata.EMPTY, uuid, 1L, "Two Sum (revised)", "fix typo"),
                new PublishProblemCommand(uuid, IdMetadata.mint(), actor,
                        TraceMetadata.EMPTY, uuid, 1L, true, "go-live"),
                new ApplyModerationCommand(uuid, IdMetadata.mint(), actor,
                        TraceMetadata.EMPTY, uuid, uuid, "forum_post",
                        ModerationAction.HIDE, "policy violation"),
                new UpdateProfileCommand(uuid, IdMetadata.mint(), actor,
                        TraceMetadata.EMPTY, uuid, "Jane Doe", null,
                        "Software engineer", null, null, null, null, null, null),
                new UploadAvatarCommand(uuid, IdMetadata.mint(), actor,
                        TraceMetadata.EMPTY, uuid, "/avatars/" + uuid + ".png"));
        for (WriteCommand command : samples) {
            assertThat(command.commandId()).isNotBlank();
            assertThat(command.idempotency()).isNotNull();
            assertThat(command.actor()).isNotNull();
            assertThat(command.trace()).isNotNull();
        }
    }

    @Test
    void app_error_codes_use_the_app_namespace() {
        for (AppErrorCode code : AppErrorCode.values()) {
            assertThat(code.namespace()).isEqualTo("app");
            assertThat(code.message()).isNotBlank();
        }
        assertThat((Object) AppErrorCode.CONTENT_NOT_FOUND)
                .isInstanceOf(NamespacedErrorCode.class);
    }

    @Test
    void app_service_methods_return_rpc_result() {
        for (Class<?> serviceClass : List.of(
                ProblemAdministrationService.class,
                ContentModerationService.class,
                ContestAdministrationService.class)) {
            for (Method method : serviceClass.getDeclaredMethods()) {
                assertThat(method.getReturnType())
                        .as("%s#%s", serviceClass.getSimpleName(), method.getName())
                        .isEqualTo(RpcResult.class);
                assertThat(method.getParameterCount()).isEqualTo(1);
                assertThat(WriteCommand.class)
                        .isAssignableFrom(method.getParameterTypes()[0]);
            }
        }
    }

    @Test
    void moderation_result_applied_action_is_typed_enum() throws Exception {
        Field field = ModerationApplyResultDTO.class.getDeclaredField("appliedAction");
        assertThat(field.getType()).isEqualTo(ModerationAction.class);
        assertThat(ContentLifecycleState.class.isEnum()).isTrue();
    }
}
