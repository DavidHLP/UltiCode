package com.ulticode.app.api.architecture;

import com.ulticode.app.api.command.CreateProblemListCommand;
import com.ulticode.app.api.command.DeleteProblemListCommand;
import com.ulticode.app.api.command.ReplaceListProblemsCommand;
import com.ulticode.app.api.command.UpdateBannerCommand;
import com.ulticode.app.api.command.UpdateBasicInfoCommand;
import com.ulticode.app.api.command.UpdateProblemListCommand;
import com.ulticode.app.api.command.UpdateVisibilityCommand;
import com.ulticode.app.api.command.WriteCommand;
import com.ulticode.app.api.service.ProblemListAdministrationService;
import com.ulticode.app.api.service.ProblemListChainReadPort;
import com.ulticode.app.api.service.ProblemListSearchReadPort;
import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemListApiContractShapeTest {

    private static final List<Class<? extends WriteCommand>> COMMANDS = List.of(
            CreateProblemListCommand.class,
            UpdateProblemListCommand.class,
            DeleteProblemListCommand.class,
            UpdateBasicInfoCommand.class,
            UpdateVisibilityCommand.class,
            UpdateBannerCommand.class,
            ReplaceListProblemsCommand.class);

    @Test
    void everyProblemListMutationCarriesStableWriteMetadata() {
        for (Class<? extends WriteCommand> command : COMMANDS) {
            RecordComponent[] components = command.getRecordComponents();
            assertThat(components).isNotNull();
            assertThat(Arrays.stream(components).map(RecordComponent::getName))
                    .contains("commandId", "idempotency", "actor", "trace");
            assertThat(Arrays.stream(components)
                    .filter(component -> component.getName().equals("commandId"))
                    .findFirst().orElseThrow().getType()).isEqualTo(String.class);
            assertThat(Arrays.stream(components)
                    .filter(component -> component.getName().equals("idempotency"))
                    .findFirst().orElseThrow().getType()).isEqualTo(IdMetadata.class);
            assertThat(Arrays.stream(components)
                    .filter(component -> component.getName().equals("actor"))
                    .findFirst().orElseThrow().getType()).isEqualTo(ActorDelegation.class);
            assertThat(Arrays.stream(components)
                    .filter(component -> component.getName().equals("trace"))
                    .findFirst().orElseThrow().getType()).isEqualTo(TraceMetadata.class);
        }
    }

    @Test
    void exposesOwnerWriteAndReadSeamsWithoutPrivateModuleTypes() {
        assertThat(Arrays.stream(ProblemListAdministrationService.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .containsExactlyInAnyOrder(
                        "createProblemList", "updateProblemList", "deleteProblemList",
                        "updateBasicInfo", "updateVisibility", "updateBanner", "replaceListProblems");
        assertThat(ProblemListSearchReadPort.class.getDeclaredMethods()).hasSize(1);
        assertThat(ProblemListChainReadPort.class.getDeclaredMethods()).hasSize(2);
        for (Class<?> type : List.of(
                ProblemListAdministrationService.class,
                ProblemListSearchReadPort.class,
                ProblemListChainReadPort.class)) {
            assertThat(Arrays.stream(type.getDeclaredMethods())
                    .flatMap(method -> Arrays.stream(method.getParameterTypes()))
                    .map(Class::getName)
                    .filter(name -> name.startsWith("com.ulticode.modules.")))
                    .isEmpty();
        }
    }

    @Test
    void replacementCommandNormalizesNullProblemsToAnEmptyList() {
        ReplaceListProblemsCommand command = new ReplaceListProblemsCommand(
                "cmd-1", IdMetadata.mint(),
                new com.ulticode.app.api.command.ActorDelegation("ADMIN", "admin-1", "admin-1", "test"),
                TraceMetadata.EMPTY, "list-1", null);

        assertThat(command.problems()).isEmpty();
        assertThat(command.idempotency().hasKey()).isTrue();
        assertThat(command.actor().actorId()).isEqualTo("admin-1");
    }
}
