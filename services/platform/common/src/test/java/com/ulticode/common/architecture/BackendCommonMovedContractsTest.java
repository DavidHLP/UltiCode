package com.ulticode.common.architecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.auth.AccountInfo;
import com.ulticode.common.auth.JwtPayload;
import com.ulticode.common.command.ActorDelegation;
import com.ulticode.common.command.WriteCommand;
import com.ulticode.common.dto.DifficultyCountDTO;
import com.ulticode.common.security.AccountReadPort;
import com.ulticode.common.security.DelegationAssertionContract;
import com.ulticode.common.security.JwtValidationPort;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackendCommonMovedContractsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void command_metadata_keeps_validation_and_serialization_shape() throws Exception {
        ActorDelegation actor = new ActorDelegation("ADMIN", "actor-1", "admin-1", "test");
        SampleCommand command = new SampleCommand(
                "command-1", IdMetadata.mint(), actor, TraceMetadata.EMPTY);

        assertThat(command).isInstanceOf(Serializable.class);
        assertThat(command.actor()).isEqualTo(actor);
        assertThat(command.idempotency().hasKey()).isTrue();
        assertThat(mapper.writeValueAsString(actor))
                .contains("\"actorType\":\"ADMIN\"")
                .contains("\"delegatorId\":\"admin-1\"");
        assertThatThrownBy(() -> new ActorDelegation(" ", "actor-1", "admin-1", "test"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void value_and_auth_projection_shapes_remain_wire_safe() throws Exception {
        DifficultyCountDTO count = new DifficultyCountDTO("EASY", 3L);
        assertThat(count.getDifficulty()).isEqualTo("EASY");
        assertThat(count.getCount()).isEqualTo(3L);
        assertThat(mapper.readTree(mapper.writeValueAsString(count)).get("count").asLong())
                .isEqualTo(3L);

        AccountInfo account = new AccountInfo("user-1", "alice", "USER", true, false);
        JwtPayload payload = new JwtPayload("user-1", "alice", "USER");
        assertThat(mapper.readTree(mapper.writeValueAsString(account)).get("isActive").asBoolean())
                .isTrue();
        assertThat(payload.userId()).isEqualTo(account.id());
        assertThat(Serializable.class.isAssignableFrom(AccountInfo.class)).isTrue();
        assertThat(Serializable.class.isAssignableFrom(JwtPayload.class)).isTrue();
    }

    @Test
    void security_ports_and_delegation_names_are_common_contracts() {
        AccountReadPort accounts = userId -> Optional.of(
                new AccountInfo(userId, "alice", "USER", true, false));
        JwtValidationPort jwt = new JwtValidationPort() {
            @Override
            public Optional<JwtPayload> validateToken(String token) {
                return Optional.of(new JwtPayload("user-1", "alice", "USER"));
            }

            @Override
            public Optional<String> extractUserId(String token) {
                return Optional.empty();
            }
        };

        assertThat(accounts.findById("user-1")).isPresent();
        assertThat(jwt.validateToken("token")).isPresent();
        assertThat(jwt.extractUserId("token")).isEmpty();
        assertThat(List.of(
                DelegationAssertionContract.ATTACHMENT_KEY,
                DelegationAssertionContract.ISSUER,
                DelegationAssertionContract.AUDIENCE,
                DelegationAssertionContract.ACTOR_SERVICE_CLAIM,
                DelegationAssertionContract.ACTOR_TYPE_CLAIM,
                DelegationAssertionContract.BOOTSTRAP_CLAIM))
                .containsExactly(
                        "ulticode-delegation-assertion",
                        "backend-admin",
                        "backend-app",
                        "actor_service",
                        "actor_type",
                        "bootstrap");
    }

    private record SampleCommand(
            String commandId,
            IdMetadata idempotency,
            ActorDelegation actor,
            TraceMetadata trace) implements WriteCommand {
    }
}
