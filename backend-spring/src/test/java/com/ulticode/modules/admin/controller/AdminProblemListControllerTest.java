package com.ulticode.modules.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.config.CorsProperties;
import com.ulticode.common.config.MapperConfig;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.admin.service.AdminProblemListService;
import com.ulticode.modules.problemlist.dto.ProblemListSummaryVO;
import com.ulticode.modules.problemlist.dto.UpdateBasicInfoDTO;
import com.ulticode.modules.problemlist.dto.UpdateBannerDTO;
import com.ulticode.modules.problemlist.dto.UpdateVisibilityDTO;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
@WebMvcTest(
        value = AdminProblemListController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MapperConfig.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminProblemListController")
class AdminProblemListControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminProblemListService adminProblemListService;

    // SecurityConfig dependencies
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private JwtProperties jwtProperties;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private AuthenticationEntryPointImpl authenticationEntryPoint;
    @MockBean
    private CorsProperties corsProperties;
    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    private ProblemListSummaryVO createSummaryVO() {
        ProblemListSummaryVO vo = new ProblemListSummaryVO();
        vo.setId("list-1");
        vo.setName("Test List");
        vo.setDescription("A test problem list");
        vo.setIsPublic(true);
        vo.setIsFeatured(false);
        vo.setBannerTag("Hot");
        vo.setBannerTheme("blue");
        vo.setBannerOrder(1);
        vo.setProblemCount(5);
        return vo;
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

    @Nested
    @DisplayName("PATCH /admin/problem-lists/{id}/basic-info")
    class UpdateBasicInfoTests {

        @Test
        @DisplayName("should return 200 with updated problem list on valid request")
        void updateBasicInfo_success() throws Exception {
            ProblemListSummaryVO vo = createSummaryVO();
            when(adminProblemListService.updateBasicInfo(eq("list-1"), any(), any(UpdateBasicInfoDTO.class)))
                    .thenReturn(vo);

            UpdateBasicInfoDTO dto = new UpdateBasicInfoDTO();
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
            when(adminProblemListService.updateBasicInfo(eq("missing-id"), any(), any(UpdateBasicInfoDTO.class)))
                    .thenThrow(new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND));

            UpdateBasicInfoDTO dto = new UpdateBasicInfoDTO();
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
            ProblemListSummaryVO vo = createSummaryVO();
            when(adminProblemListService.updateVisibility(eq("list-1"), any(), any(UpdateVisibilityDTO.class)))
                    .thenReturn(vo);

            UpdateVisibilityDTO dto = new UpdateVisibilityDTO();
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
            when(adminProblemListService.updateVisibility(eq("missing-id"), any(), any(UpdateVisibilityDTO.class)))
                    .thenThrow(new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND));

            UpdateVisibilityDTO dto = new UpdateVisibilityDTO();
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
            ProblemListSummaryVO vo = createSummaryVO();
            when(adminProblemListService.updateBanner(eq("list-1"), any(), any(UpdateBannerDTO.class)))
                    .thenReturn(vo);

            UpdateBannerDTO dto = new UpdateBannerDTO();
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
            ProblemListSummaryVO vo = createSummaryVO();
            when(adminProblemListService.updateBanner(eq("list-1"), any(), any(UpdateBannerDTO.class)))
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
            when(adminProblemListService.updateBanner(eq("missing-id"), any(), any(UpdateBannerDTO.class)))
                    .thenThrow(new BusinessException(ErrorCode.PROBLEM_LIST_NOT_FOUND));

            UpdateBannerDTO dto = new UpdateBannerDTO();
            dto.setBannerTag("Hot");

            mockMvc.perform(withPrincipal(
                    patch("/admin/problem-lists/missing-id/banner"), "admin-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isNotFound());
        }
    }
}
