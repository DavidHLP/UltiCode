package com.ulticode.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.UlticodeBackendApplication;
import com.ulticode.common.config.CorsProperties;
import com.ulticode.common.config.MapperConfig;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.modules.admin.dto.tag.TagListResponse;
import com.ulticode.modules.admin.dto.tag.TagVO;
import com.ulticode.modules.admin.service.AdminTagService;
import com.ulticode.security.AuthenticationEntryPointImpl;
import com.ulticode.security.jwt.JwtAuthenticationFilter;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest for AdminTagController.
 *
 * <p>Mirrors AdminSettingsControllerTest: addFilters=false bypasses security;
 * auth is tested separately. The MockBean AdminTagService lets the tests focus
 * on routing, JSON shape, and validation wiring — service-layer guards are
 * covered in AdminTagServiceImplTest.</p>
 *
 * <p>Each bug from docs/admin-tags-test-plan.md §7 has at least one regression
 * test:</p>
 * <ul>
 *   <li>Bug #1 → missing-type returns 400 (was 500)</li>
 *   <li>Bug #2 → unknown type returns 400 (was 200 with PROBLEM fallback)</li>
 *   <li>Bug #4 → PATCH with empty name returns 400 (was 200 silent ignore)</li>
 * </ul>
 */
@WebMvcTest(
        value = AdminTagController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MapperConfig.class
        )
)
// Disambiguate from BackendAdminApplication on the test classpath (P7-ADMIN-BULK-001)
@ContextConfiguration(classes = UlticodeBackendApplication.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminTagController")
class AdminTagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminTagService service;

    // SecurityConfig mocks (mirror AdminSettingsControllerTest lines 73-77)
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private JwtProperties jwtProperties;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private AuthenticationEntryPointImpl authenticationEntryPoint;
    @MockBean private CorsProperties corsProperties;
    @MockBean private StringRedisTemplate stringRedisTemplate;

    private static TagVO sampleTag(String id, String name, String type) {
        TagVO v = new TagVO();
        v.setId(id);
        v.setName(name);
        v.setSlug(name.toLowerCase());
        v.setType(type);
        v.setUsageCount(0);
        v.setCreatedAt(LocalDateTime.now());
        return v;
    }

    @Nested
    @DisplayName("GET /admin/tags")
    class ListTags {

        @Test
        @DisplayName("returns paged payload for PROBLEM default")
        void returnsPagedPayload() throws Exception {
            TagListResponse resp = TagListResponse.of(
                    List.of(sampleTag("t1", "alpha", "PROBLEM")), 1L, 1, 20);
            when(service.getTags(any())).thenReturn(resp);

            mockMvc.perform(get("/admin/tags"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.data[0].name").value("alpha"));
        }

        @Test
        @DisplayName("Bug #2: unknown type returns 400")
        void unknownTypeReturns400() throws Exception {
            mockMvc.perform(get("/admin/tags").param("type", "GARBAGE"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.message").value("Validation failed"))
                    .andExpect(jsonPath("$.data.type").value("type must be one of PROBLEM, FORUM"));
            verify(service, never()).getTags(any());
        }

        @Test
        @DisplayName("accepts lowercase type (case-insensitive)")
        void lowercaseTypeAccepted() throws Exception {
            when(service.getTags(any())).thenReturn(TagListResponse.of(List.of(), 0L, 1, 20));
            mockMvc.perform(get("/admin/tags").param("type", "problem"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /admin/tags/{id}")
    class GetTag {

        @Test
        @DisplayName("happy path returns VO")
        void happyPath() throws Exception {
            when(service.getTag(eq("t1"), eq("PROBLEM"))).thenReturn(sampleTag("t1", "alpha", "PROBLEM"));

            mockMvc.perform(get("/admin/tags/t1").param("type", "PROBLEM"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value("t1"))
                    .andExpect(jsonPath("$.data.type").value("PROBLEM"));
        }

        @Test
        @DisplayName("Bug #1: missing type returns 400 (was 500)")
        void missingTypeReturns400() throws Exception {
            mockMvc.perform(get("/admin/tags/t1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.data.type")
                            .value(org.hamcrest.Matchers.containsString("Missing required parameter 'type'")));
            verify(service, never()).getTag(anyString(), anyString());
        }

        @Test
        @DisplayName("Bug #2: unknown type returns 400")
        void unknownTypeReturns400() throws Exception {
            mockMvc.perform(get("/admin/tags/t1").param("type", "BOGUS"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000));
            verify(service, never()).getTag(anyString(), anyString());
        }

        @Test
        @DisplayName("not found returns 404")
        void notFoundReturns404() throws Exception {
            when(service.getTag(eq("missing"), eq("PROBLEM")))
                    .thenThrow(new BusinessException(AdminErrorCode.PROBLEM_TAG_NOT_FOUND));

            mockMvc.perform(get("/admin/tags/missing").param("type", "PROBLEM"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(30010));
        }

        @Test
        @DisplayName("wrong type returns 404 (PROBLEM id queried as FORUM)")
        void wrongTypeReturns404() throws Exception {
            when(service.getTag(eq("t1"), eq("FORUM")))
                    .thenThrow(new BusinessException(AdminErrorCode.FORUM_TAG_NOT_FOUND));

            mockMvc.perform(get("/admin/tags/t1").param("type", "FORUM"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /admin/tags")
    class CreateTag {

        @Test
        @DisplayName("happy path returns VO")
        void happyPath() throws Exception {
            String body = "{\"name\":\"new-tag\",\"type\":\"PROBLEM\",\"color\":\"#FF0000\"}";
            when(service.createTag(any())).thenReturn(sampleTag("nt1", "new-tag", "PROBLEM"));

            mockMvc.perform(post("/admin/tags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("new-tag"));
        }

        @Test
        @DisplayName("missing type returns 400")
        void missingTypeReturns400() throws Exception {
            mockMvc.perform(post("/admin/tags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"x\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.data.type").value("Tag type is required"));
            verify(service, never()).createTag(any());
        }

        @Test
        @DisplayName("missing name returns 400")
        void missingNameReturns400() throws Exception {
            mockMvc.perform(post("/admin/tags")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"type\":\"PROBLEM\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.data.name").value("Tag name is required"));
            verify(service, never()).createTag(any());
        }
    }

    @Nested
    @DisplayName("PATCH /admin/tags/{id}")
    class UpdateTag {

        @Test
        @DisplayName("happy path returns updated VO")
        void happyPath() throws Exception {
            when(service.updateTag(eq("t1"), any())).thenReturn(sampleTag("t1", "renamed", "PROBLEM"));

            mockMvc.perform(patch("/admin/tags/t1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"renamed\",\"type\":\"PROBLEM\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("renamed"));
        }

        @Test
        @DisplayName("Bug #4: empty name returns 400 (was 200 silent ignore)")
        void emptyNameReturns400() throws Exception {
            mockMvc.perform(patch("/admin/tags/t1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"\",\"type\":\"PROBLEM\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.data.name").value("name must not be empty if provided"));
            verify(service, never()).updateTag(anyString(), any());
        }

        @Test
        @DisplayName("empty slug returns 400")
        void emptySlugReturns400() throws Exception {
            mockMvc.perform(patch("/admin/tags/t1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"slug\":\"\",\"type\":\"PROBLEM\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.data.slug").value("slug must not be empty if provided"));
        }

        @Test
        @DisplayName("missing type returns 400")
        void missingTypeReturns400() throws Exception {
            mockMvc.perform(patch("/admin/tags/t1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"x\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.type").value("Tag type is required"));
            verify(service, never()).updateTag(anyString(), any());
        }

        @Test
        @DisplayName("name collision returns 409")
        void nameCollisionReturns409() throws Exception {
            when(service.updateTag(eq("t1"), any()))
                    .thenThrow(new BusinessException(AdminErrorCode.PROBLEM_TAG_NAME_EXISTS));

            mockMvc.perform(patch("/admin/tags/t1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"taken\",\"type\":\"PROBLEM\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(30011));
        }
    }

    @Nested
    @DisplayName("DELETE /admin/tags/{id}")
    class DeleteTag {

        @Test
        @DisplayName("happy path returns 200")
        void happyPath() throws Exception {
            mockMvc.perform(delete("/admin/tags/t1").param("type", "PROBLEM"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            verify(service).deleteTag("t1", "PROBLEM");
        }

        @Test
        @DisplayName("Bug #1: missing type returns 400 (was 500)")
        void missingTypeReturns400() throws Exception {
            mockMvc.perform(delete("/admin/tags/t1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(40000))
                    .andExpect(jsonPath("$.data.type")
                            .value(org.hamcrest.Matchers.containsString("Missing required parameter 'type'")));
            verify(service, never()).deleteTag(anyString(), anyString());
        }

        @Test
        @DisplayName("Bug #2: unknown type returns 400")
        void unknownTypeReturns400() throws Exception {
            mockMvc.perform(delete("/admin/tags/t1").param("type", "BOGUS"))
                    .andExpect(status().isBadRequest());
            verify(service, never()).deleteTag(anyString(), anyString());
        }

        @Test
        @DisplayName("not found returns 404")
        void notFoundReturns404() throws Exception {
            org.mockito.Mockito.doThrow(new BusinessException(AdminErrorCode.PROBLEM_TAG_NOT_FOUND))
                    .when(service).deleteTag(eq("missing"), eq("PROBLEM"));

            mockMvc.perform(delete("/admin/tags/missing").param("type", "PROBLEM"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /admin/tags/merge")
    class MergeTag {

        @Test
        @DisplayName("happy path returns 200")
        void happyPath() throws Exception {
            mockMvc.perform(post("/admin/tags/merge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sourceId\":\"a\",\"targetTagId\":\"b\",\"type\":\"PROBLEM\"}"))
                    .andExpect(status().isOk());
            verify(service).mergeTag(any());
        }

        @Test
        @DisplayName("missing sourceId returns 400")
        void missingSourceIdReturns400() throws Exception {
            mockMvc.perform(post("/admin/tags/merge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"targetTagId\":\"b\",\"type\":\"PROBLEM\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.sourceId").value("Source tag ID is required"));
            verify(service, never()).mergeTag(any());
        }

        @Test
        @DisplayName("missing targetTagId returns 400")
        void missingTargetReturns400() throws Exception {
            mockMvc.perform(post("/admin/tags/merge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sourceId\":\"a\",\"type\":\"PROBLEM\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.targetTagId").value("Target tag ID is required"));
        }

        @Test
        @DisplayName("missing type returns 400")
        void missingTypeReturns400() throws Exception {
            mockMvc.perform(post("/admin/tags/merge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"sourceId\":\"a\",\"targetTagId\":\"b\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.type").value("Tag type is required"));
            verify(service, never()).mergeTag(any());
        }
    }
}