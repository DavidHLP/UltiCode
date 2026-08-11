package com.ulticode.app.api.architecture;

import com.ulticode.app.api.command.ForumCommentModerationCommand;
import com.ulticode.app.api.command.ForumTagMutationCommand;
import com.ulticode.app.api.service.AdminForumReadPort;
import com.ulticode.app.api.service.ForumCommentAdministrationService;
import com.ulticode.app.api.service.ForumCommentReadPort;
import com.ulticode.app.api.service.ForumOwnerPort;
import com.ulticode.app.api.service.ForumPostVoteCountReadPort;
import com.ulticode.app.api.service.ForumTagAdministrationService;
import com.ulticode.app.api.service.ForumTagReadPort;
import com.ulticode.app.api.service.ForumVoteReadPort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForumApiContractShapeTest {

    private static final List<Class<?>> CONTRACTS = List.of(
            AdminForumReadPort.class,
            ForumCommentAdministrationService.class,
            ForumCommentReadPort.class,
            ForumPostVoteCountReadPort.class,
            ForumTagAdministrationService.class,
            ForumTagReadPort.class,
            ForumVoteReadPort.class,
            ForumOwnerPort.class);

    @Test
    void forumContractsExposeNoAppPrivateEntityOrMapperTypes() {
        for (Class<?> contract : CONTRACTS) {
            for (Method method : contract.getDeclaredMethods()) {
                assertThat(method.getReturnType().getName())
                        .as("return type of %s#%s", contract.getName(), method.getName())
                        .doesNotStartWith("com.ulticode.modules.");
                assertThat(Arrays.stream(method.getParameterTypes()).map(Class::getName).toList())
                        .as("parameter types of %s#%s", contract.getName(), method.getName())
                        .noneMatch(type -> type.startsWith("com.ulticode.modules."));
            }
        }
    }

    @Test
    void mutationCommandsCarryCrossOwnerMetadata() {
        assertThat(Arrays.stream(ForumCommentModerationCommand.class.getRecordComponents())
                .map(component -> component.getName()).toList())
                .containsExactly("commandId", "idempotency", "actor", "trace", "commentId",
                        "action", "reason", "deletedBy");
        assertThat(Arrays.stream(ForumTagMutationCommand.class.getRecordComponents())
                .map(component -> component.getName()).toList())
                .containsExactly("commandId", "idempotency", "actor", "trace", "action", "tagId",
                        "sourceTagId", "targetTagId", "name", "slug", "description", "color");
    }
}
