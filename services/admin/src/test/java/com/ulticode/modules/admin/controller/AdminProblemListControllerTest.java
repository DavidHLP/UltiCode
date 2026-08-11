package com.ulticode.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.admin.error.AdminWebExceptionHandler;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.admin.dto.UpdateBannerRequest;
import com.ulticode.modules.admin.dto.UpdateBasicInfoRequest;
import com.ulticode.modules.admin.dto.UpdateProblemsRequest;
import com.ulticode.modules.admin.dto.UpdateVisibilityRequest;
import com.ulticode.modules.admin.service.AdminProblemListService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest for AdminProblemListController.
 *
 * <p>Uses addFilters=false to bypass all security filters, isolating the
 * controller layer for request/response contract testing.</p>
 *
 * <p>Note: @PreAuthorize annotations require method security. With addFilters=false
 * and no security context, these endpoints are accessible without authentication.
 * Authorization is tested separately in integration tests.</p>
 */
@WebMvcTest(AdminProblemListController.class)
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = {AdminProblemListController.class, AdminWebExceptionHandler.class})
@DisplayName("AdminProblemListController")
class AdminProblemListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminProblemListService adminProblemListService;

    private ProblemListSummaryDTO createSummaryDTO() {
        ProblemListSummaryDTO dto = new ProblemListSummaryDTO();
        dto.setId("list-1");
        dto.setName("Test List");
        dto.setDescription("A test problem list");
        dto.setIsPublic(true);
        dto.setIsFeatured(false);
        dto.setBannerTag("Hot");
        dto.setBannerTheme("blue");
        dto.setBannerOrder(1);
        dto.setProblemCount(5);
        return dto;
    }

    /**
     * Mock principal that resolves to the given user id. The controller
     * endpoints call {@code principal.getName()} to extract the user id
     * before delegating to the service. With {@code addFilters = false}
     * there is no real SecurityContext, so we attach a stub principal
     * directly to the request.
     */
    private Principal stubPrincipal(String userId) {
        return new Principal() {
            @Override
            public String getName() {
                return userId;
            }
        };
    }

    /**
     * Shortcut to attach a {@link Principal} to a MockMvc request builder.
     * Spring's {@code PrincipalArgumentResolver} reads the principal from
     * the request, not from a security context, when no security filter
     * chain is active.
     */
    private MockHttpServletRequestBuilder withPrincipal(MockHttpServletRequestBuilder builder, String userId) {
        return builder.principal(stubPrincipal(userId));
    }

    @Test
    @DisplayName("problem replacement requires ADMIN or SUPER_ADMIN")
    void updateListProblemsRequiresAdminRole() throws NoSuchMethodException {
        PreAuthorize authorization = AdminProblemListController.class
                .getMethod("updateListProblems", String.class, UpdateProblemsRequest.class,
                        Principal.class, String.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAnyRole('ADMIN', 'SUPER_ADMIN')");
    }

    @Nested
    @DisplayName("PATCH /admin/problem-lists/{id}/basic-info")
    class UpdateBasicInfoTests {

        @Test
        @DisplayName("should return 200 with updated problem list on valid request")
        void updateBasicInfo_success() throws Exception {
            ProblemListSummaryDTO vo = createSummaryDTO();
            when(adminProblemListService.updateBasicInfo(
                    eq("list-1"), any(), any(UpdateBasicInfoRequest.class)))
                    .thenReturn(vo);

            UpdateBasicInfoRequest dto = new UpdateBasicInfoRequest();
            dto.setName("Updated Name");
            dto.setDescription("Updated description");

            mockMvc.perform(withPrincipal(
                    patch("/admin/problem-lists/list-1/basic-info"), "admin-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("list-1"))
                    .andExpect(jsonPath("$.data.name").value("Test List"));
        }

        @Test
        @DisplayName("should forward an Idempotency-Key when supplied")
        void forwardsIdempotencyKey() throws Exception {
            ProblemListSummaryDTO vo = createSummaryDTO();
            when(adminProblemListService.updateBasicInfo(
                    eq("list-1"), eq("admin-001"), any(UpdateBasicInfoRequest.class), eq("retry-1")))
                    .thenReturn(vo);

            UpdateBasicInfoRequest dto = new UpdateBasicInfoRequest();
            dto.setName("Updated Name");
            dto.setDescription("Updated description");

            mockMvc.perform(withPrincipal(
                    patch("/admin/problem-lists/list-1/basic-info"), "admin-001")
                            .header("Idempotency-Key", "retry-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value("list-1"));

            verify(adminProblemListService).updateBasicInfo(
                    eq("list-1"), eq("admin-001"), any(UpdateBasicInfoRequest.class), eq("retry-1"));
        }

        @Test
        @DisplayName("should return 400 when name is blank")
        void updateBasicInfo_validationError_blankName() throws Exception {
            String json = "{\"name\":\"\",\"description\":\"desc\"}";

            mockMvc.perform(patch("/admin/problem-lists/list-1/basic-info")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return 404 when problem list not found")
        void updateBasicInfo_notFound() throws Exception {
            when(adminProblemListService.updateBasicInfo(eq("missing-id"), any(), any(UpdateBasicInfoRequest.class)))
                    .thenThrow(new BusinessException(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));

            UpdateBasicInfoRequest dto = new UpdateBasicInfoRequest();
            dto.setName("Updated Name");
            dto.setDescription("Updated description");

            mockMvc.perform(withPrincipal(
                    patch("/admin/problem-lists/missing-id/basic-info"), "admin-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /admin/problem-lists/{id}/visibility")
    class UpdateVisibilityTests {

        @Test
        @DisplayName("should return 200 with updated problem list on valid request")
        void updateVisibility_success() throws Exception {
            ProblemListSummaryDTO vo = createSummaryDTO();
            when(adminProblemListService.updateVisibility(eq("list-1"), any(), any(UpdateVisibilityRequest.class)))
                    .thenReturn(vo);

            UpdateVisibilityRequest dto = new UpdateVisibilityRequest();
            dto.setIsPublic(false);
            dto.setIsFeatured(true);

            mockMvc.perform(withPrincipal(
                    patch("/admin/problem-lists/list-1/visibility"), "admin-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("list-1"));
        }

        @Test
        @DisplayName("should return 404 when problem list not found")
        void updateVisibility_notFound() throws Exception {
            when(adminProblemListService.updateVisibility(eq("missing-id"), any(), any(UpdateVisibilityRequest.class)))
                    .thenThrow(new BusinessException(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));

            UpdateVisibilityRequest dto = new UpdateVisibilityRequest();
            dto.setIsPublic(true);

            mockMvc.perform(withPrincipal(
                    patch("/admin/problem-lists/missing-id/visibility"), "admin-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PATCH /admin/problem-lists/{id}/banner")
    class UpdateBannerTests {

        @Test
        @DisplayName("should return 200 with updated problem list on valid request")
        void updateBanner_success() throws Exception {
            ProblemListSummaryDTO vo = createSummaryDTO();
            when(adminProblemListService.updateBanner(eq("list-1"), any(), any(UpdateBannerRequest.class)))
                    .thenReturn(vo);

            UpdateBannerRequest dto = new UpdateBannerRequest();
            dto.setBannerTag("New");
            dto.setBannerTheme("red");
            dto.setBannerOrder(2);

            mockMvc.perform(withPrincipal(
                    patch("/admin/problem-lists/list-1/banner"), "admin-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value("list-1"));
        }

        @Test
        @DisplayName("should return 200 with empty body (all fields null)")
        void updateBanner_nullFieldsIgnored() throws Exception {
            ProblemListSummaryDTO vo = createSummaryDTO();
            when(adminProblemListService.updateBanner(eq("list-1"), any(), any(UpdateBannerRequest.class)))
                    .thenReturn(vo);

            String json = "{}";

            mockMvc.perform(withPrincipal(
                    patch("/admin/problem-lists/list-1/banner"), "admin-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("should return 404 when problem list not found")
        void updateBanner_notFound() throws Exception {
            when(adminProblemListService.updateBanner(eq("missing-id"), any(), any(UpdateBannerRequest.class)))
                    .thenThrow(new BusinessException(AdminErrorCode.PROBLEM_LIST_NOT_FOUND));

            UpdateBannerRequest dto = new UpdateBannerRequest();
            dto.setBannerTag("Hot");

            mockMvc.perform(withPrincipal(
                    patch("/admin/problem-lists/missing-id/banner"), "admin-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound());
        }
    }
}
