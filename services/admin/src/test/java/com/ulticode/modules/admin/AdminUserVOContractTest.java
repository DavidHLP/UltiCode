package com.ulticode.modules.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.modules.admin.dto.AdminUserVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUserVOContractTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void oldPayloadDeserializesWithNewFieldsAbsent() throws Exception {
        AdminUserVO user = mapper.readValue(
                "{\"id\":\"u-1\",\"username\":\"alice\","
                        + "\"email\":\"alice@example.com\",\"role\":\"USER\","
                        + "\"isActive\":true,\"isBanned\":false}",
                AdminUserVO.class);

        assertThat(user.getId()).isEqualTo("u-1");
        assertThat(user.getUsername()).isEqualTo("alice");
        assertThat(user.getDetailStatus()).isNull();
        assertThat(user.getProfileStatus()).isNull();
        assertThat(user.getStatsStatus()).isNull();
        assertThat(user.getPermissionsStatus()).isNull();
    }

    @Test
    void statusFieldsAreAdditiveAndKeepExistingFields() throws Exception {
        AdminUserVO user = new AdminUserVO();
        user.setId("u-1");
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setRole("USER");
        user.setIsActive(true);
        user.setIsBanned(false);
        user.setDetailStatus(DegradationStatus.PARTIAL);
        user.setProfileStatus(DegradationStatus.OK);
        user.setStatsStatus(DegradationStatus.UNAVAILABLE);
        user.setStatsReason("Submission stats query unavailable");
        user.setPermissionsStatus(DegradationStatus.OK);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(user));

        assertThat(json.get("id").asText()).isEqualTo("u-1");
        assertThat(json.get("username").asText()).isEqualTo("alice");
        assertThat(json.get("detailStatus").asText()).isEqualTo("PARTIAL");
        assertThat(json.get("profileStatus").asText()).isEqualTo("OK");
        assertThat(json.get("statsStatus").asText()).isEqualTo("UNAVAILABLE");
        assertThat(json.get("statsReason").asText())
                .isEqualTo("Submission stats query unavailable");
        assertThat(json.get("permissionsStatus").asText()).isEqualTo("OK");
    }
}
