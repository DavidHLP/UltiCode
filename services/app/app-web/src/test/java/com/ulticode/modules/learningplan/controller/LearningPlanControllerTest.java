package com.ulticode.modules.learningplan.controller;

import com.ulticode.app.security.AppTestSecurityConfig;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.learningplan.dto.LearningPlanVO;
import com.ulticode.modules.learningplan.service.LearningPlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code @WebMvcTest} for {@link LearningPlanController}: strict header/body
 * validation, principal derivation and owner-scoped error mapping.
 */
@WebMvcTest(controllers = LearningPlanController.class)
@Import({
        AppTestSecurityConfig.class,
        com.ulticode.app.error.ProblemWebExceptionHandler.class
})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("LearningPlanController")
class LearningPlanControllerTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";
    private static final String SUBMISSION = "22222222-2222-2222-2222-222222222222";
    private static final String KEY = "33333333-3333-3333-3333-333333333333";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LearningPlanService learningPlanService;

    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private static String validBody() {
        return "{"
                + "\"sourceSubmissionId\":\"" + SUBMISSION + "\","
                + "\"draftVersion\":1,"
                + "\"title\":\"Two pointers plan\","
                + "\"content\":\"Read the statement.\""
                + "}";
    }

    private static LearningPlanVO vo() {
        LearningPlanVO vo = new LearningPlanVO();
        vo.setId("plan-1");
        vo.setSourceSubmissionId(SUBMISSION);
        vo.setDraftVersion(1);
        vo.setTitle("Two pointers plan");
        vo.setContent("Read the statement.");
        vo.setCreatedAt(LocalDateTime.parse("2026-10-01T00:00:00"));
        return vo;
    }

    @Test
    @DisplayName("POST saves with the authenticated principal and normalised key")
    void saveUsesPrincipalAndNormalisedKey() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);
        when(learningPlanService.save(eq(USER), eq(KEY), any())).thenReturn(vo());

        mockMvc.perform(post("/learning-plans")
                        .header("Idempotency-Key", KEY.toUpperCase(Locale.ROOT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("plan-1"));

        verify(learningPlanService).save(eq(USER), eq(KEY), any());
    }

    @Test
    @DisplayName("POST without an Idempotency-Key header is rejected")
    void missingIdempotencyKeyRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);

        mockMvc.perform(post("/learning-plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()));
    }

    @Test
    @DisplayName("POST with a non-canonical Idempotency-Key is rejected")
    void nonCanonicalIdempotencyKeyRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);

        mockMvc.perform(post("/learning-plans")
                        .header("Idempotency-Key", "1-1-1-1-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()));
    }

    @Test
    @DisplayName("POST with an unknown body field is rejected")
    void unknownBodyFieldRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);

        String body = "{"
                + "\"sourceSubmissionId\":\"" + SUBMISSION + "\","
                + "\"draftVersion\":1,"
                + "\"title\":\"t\","
                + "\"content\":\"c\","
                + "\"userId\":\"attacker\""
                + "}";

        mockMvc.perform(post("/learning-plans")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()));
    }

    @Test
    @DisplayName("POST rejects a duplicated draftVersion instead of taking the last value")
    void duplicateDraftVersionRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);

        String body = "{"
                + "\"sourceSubmissionId\":\"" + SUBMISSION + "\","
                + "\"draftVersion\":1,"
                + "\"draftVersion\":2,"
                + "\"title\":\"t\","
                + "\"content\":\"c\""
                + "}";

        mockMvc.perform(post("/learning-plans")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()));

        verifyNoInteractions(learningPlanService);
    }

    @Test
    @DisplayName("POST rejects a duplicated sourceSubmissionId instead of taking the last value")
    void duplicateSourceSubmissionIdRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);

        String body = "{"
                + "\"sourceSubmissionId\":\"" + SUBMISSION + "\","
                + "\"sourceSubmissionId\":\"44444444-4444-4444-4444-444444444444\","
                + "\"draftVersion\":1,"
                + "\"title\":\"t\","
                + "\"content\":\"c\""
                + "}";

        mockMvc.perform(post("/learning-plans")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()));

        verifyNoInteractions(learningPlanService);
    }

    @Test
    @DisplayName("POST with a non-positive draft version is rejected")
    void nonPositiveDraftVersionRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);

        String body = "{"
                + "\"sourceSubmissionId\":\"" + SUBMISSION + "\","
                + "\"draftVersion\":0,"
                + "\"title\":\"t\","
                + "\"content\":\"c\""
                + "}";

        mockMvc.perform(post("/learning-plans")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()));
    }


    @Test
    @DisplayName("POST rejects Python-strip blank Unicode text")
    void unicodeSpaceOnlyTextRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);
        String pythonStripWhitespace = "\u00a0\u0085";
        String titleBlank = "{\"sourceSubmissionId\":\"" + SUBMISSION
                + "\",\"draftVersion\":1,\"title\":\"" + pythonStripWhitespace
                + "\",\"content\":\"c\"}";
        String contentBlank = "{\"sourceSubmissionId\":\"" + SUBMISSION
                + "\",\"draftVersion\":1,\"title\":\"t\",\"content\":\""
                + pythonStripWhitespace + "\"}";

        for (String body : new String[]{titleBlank, contentBlank}) {
            mockMvc.perform(post("/learning-plans")
                            .header("Idempotency-Key", KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()));
        }
        verifyNoInteractions(learningPlanService);
    }

    @Test
    @DisplayName("POST with a null or malformed body is rejected")
    void malformedBodyRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);

        mockMvc.perform(post("/learning-plans")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()));
    }

    @Test
    @DisplayName("POST rejects a float draft version instead of truncating it")
    void floatDraftVersionRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);

        String body = "{"
                + "\"sourceSubmissionId\":\"" + SUBMISSION + "\","
                + "\"draftVersion\":1.5,"
                + "\"title\":\"t\","
                + "\"content\":\"c\""
                + "}";

        mockMvc.perform(post("/learning-plans")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()));
    }

    @Test
    @DisplayName("POST rejects a string draft version instead of coercing it")
    void stringDraftVersionRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);

        String body = "{"
                + "\"sourceSubmissionId\":\"" + SUBMISSION + "\","
                + "\"draftVersion\":\"1\","
                + "\"title\":\"t\","
                + "\"content\":\"c\""
                + "}";

        mockMvc.perform(post("/learning-plans")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()));
    }

    @Test
    @DisplayName("POST rejects numeric title/content instead of coercing them to text")
    void numericTextFieldsRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);

        String body = "{"
                + "\"sourceSubmissionId\":\"" + SUBMISSION + "\","
                + "\"draftVersion\":1,"
                + "\"title\":123,"
                + "\"content\":1.5"
                + "}";

        mockMvc.perform(post("/learning-plans")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()));
    }

    @Test
    @DisplayName("POST rejects trailing tokens after the JSON body")
    void trailingTokensRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);

        mockMvc.perform(post("/learning-plans")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody() + "{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()));
    }

    /** JSON escape sequence for U+1F600 as a surrogate pair, assembled from ASCII parts. */
    private static String emojiJsonEscape() {
        return "\\" + "uD83D" + "\\" + "uDE00";
    }

    @Test
    @DisplayName("POST counts supplementary-plane characters as one code point")
    void supplementaryPlaneTitleWithinLimitAccepted() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);
        when(learningPlanService.save(eq(USER), eq(KEY), any())).thenReturn(vo());

        // 150 emoji = 150 code points but 300 UTF-16 units: a UTF-16 @Size(max=200)
        // would wrongly reject this payload.
        String title = emojiJsonEscape().repeat(150);
        String body = "{"
                + "\"sourceSubmissionId\":\"" + SUBMISSION + "\","
                + "\"draftVersion\":1,"
                + "\"title\":\"" + title + "\","
                + "\"content\":\"c\""
                + "}";

        mockMvc.perform(post("/learning-plans")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("POST rejects a title over 200 code points")
    void supplementaryPlaneTitleOverLimitRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);

        String title = emojiJsonEscape().repeat(201);
        String body = "{"
                + "\"sourceSubmissionId\":\"" + SUBMISSION + "\","
                + "\"draftVersion\":1,"
                + "\"title\":\"" + title + "\","
                + "\"content\":\"c\""
                + "}";

        mockMvc.perform(post("/learning-plans")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()));
    }

    @Test
    @DisplayName("POST without an authenticated principal is unauthorized")
    void unauthenticatedSaveRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(null);

        mockMvc.perform(post("/learning-plans")
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    @DisplayName("GET with a malformed plan id is rejected")
    void getWithMalformedIdRejected() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);

        mockMvc.perform(get("/learning-plans/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.BAD_REQUEST.code()));
    }

    @Test
    @DisplayName("GET hides a plan the caller does not own")
    void getForeignPlanNotFound() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);
        when(learningPlanService.get(USER, KEY))
                .thenThrow(new BusinessException(BaseErrorCode.NOT_FOUND, "Learning plan not found"));

        mockMvc.perform(get("/learning-plans/" + KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(BaseErrorCode.NOT_FOUND.code()));
    }

    @Test
    @DisplayName("GET /by-key returns the owner-scoped plan")
    void getByKeyReturnsPlan() throws Exception {
        when(currentUserProvider.getCurrentUserId()).thenReturn(USER);
        when(learningPlanService.getByKey(USER, KEY)).thenReturn(vo());

        mockMvc.perform(get("/learning-plans/by-key/" + KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("plan-1"));
    }
}
