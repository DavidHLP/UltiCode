package com.ulticode.modules.learningplan.controller;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.response.Result;
import com.ulticode.modules.learningplan.dto.LearningPlanVO;
import com.ulticode.modules.learningplan.dto.SaveLearningPlanDTO;
import com.ulticode.modules.learningplan.service.LearningPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Learning-plan write/read endpoints for the current authenticated user.
 *
 * <p>The principal always comes from {@link CurrentUserProvider}; there is no
 * request-controlled {@code userId}. The {@code Idempotency-Key} header is a
 * canonical 36-character UUID (server-normalised to lower case) and the body is
 * deserialized with a strict copy of the shared mapper: unknown fields,
 * duplicate keys, trailing tokens, scalar coercions (string/float into an
 * integer, numbers into text) are all rejected without changing the global
 * Jackson compatibility behaviour. Length limits are counted in Unicode code
 * points.
 */
@Tag(name = "Learning Plan", description = "Confirmed learning plan storage")
@RestController
@RequestMapping("/learning-plans")
@SecurityRequirement(name = "Bearer")
public class LearningPlanController {

    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final int MAX_TITLE_CODE_POINTS = 200;
    private static final int MAX_CONTENT_CODE_POINTS = 16000;

    private final LearningPlanService learningPlanService;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper strictObjectMapper;
    private final Validator validator;

    public LearningPlanController(LearningPlanService learningPlanService,
                                  CurrentUserProvider currentUserProvider,
                                  ObjectMapper objectMapper,
                                  Validator validator) {
        this.learningPlanService = learningPlanService;
        this.currentUserProvider = currentUserProvider;
        this.validator = validator;
        // Strict copy of the shared mapper with parser-level duplicate-key
        // detection. The factory is rebuilt (never mutated in place) and swapped
        // in via copyWith, so the global mapper's parser behaviour is unchanged.
        JsonFactory strictFactory = objectMapper.getFactory().rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.strictObjectMapper = objectMapper.copyWith(strictFactory)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        // Text fields must be JSON strings: numbers/booleans are rejected
        // instead of being coerced into "123"/"true".
        this.strictObjectMapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    @Operation(summary = "Save (or idempotently replay) a confirmed learning plan")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content = @Content(schema = @Schema(implementation = SaveLearningPlanDTO.class)))
    @PostMapping
    public Result<LearningPlanVO> save(
            @Parameter(description = "Canonical UUID idempotency key", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody String body) {
        String userId = requirePrincipal();
        String key = requireCanonicalUuid(idempotencyKey, "Idempotency-Key");
        SaveLearningPlanDTO dto = parseAndValidate(body);
        return Result.success(learningPlanService.save(userId, key, dto));
    }

    @Operation(summary = "Get a learning plan owned by the current user")
    @GetMapping("/{id}")
    public Result<LearningPlanVO> get(@PathVariable String id) {
        String userId = requirePrincipal();
        return Result.success(learningPlanService.get(userId, requireCanonicalUuid(id, "id")));
    }

    @Operation(summary = "Get a learning plan by its idempotency key")
    @GetMapping("/by-key/{key}")
    public Result<LearningPlanVO> getByKey(@PathVariable String key) {
        String userId = requirePrincipal();
        return Result.success(learningPlanService.getByKey(userId, requireCanonicalUuid(key, "key")));
    }

    private String requirePrincipal() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(BaseErrorCode.UNAUTHORIZED, "Authentication required");
        }
        return userId;
    }

    private static String requireCanonicalUuid(String value, String field) {
        if (value == null || !CANONICAL_UUID.matcher(value).matches()) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, field + " must be a canonical UUID");
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private SaveLearningPlanDTO parseAndValidate(String body) {
        SaveLearningPlanDTO dto;
        try {
            dto = strictObjectMapper.readValue(body, SaveLearningPlanDTO.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Malformed request body");
        }
        if (dto == null) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Request body is required");
        }
        Set<ConstraintViolation<SaveLearningPlanDTO>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            ConstraintViolation<SaveLearningPlanDTO> first = violations.iterator().next();
            throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Validation failed: " + first.getPropertyPath() + " " + first.getMessage());
        }
        if (isBlankLikePythonStrip(dto.getTitle())) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Validation failed: title is required");
        }
        if (isBlankLikePythonStrip(dto.getContent())) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST, "Validation failed: content is required");
        }
        requireCodePointLimit(dto.getTitle(), MAX_TITLE_CODE_POINTS, "title");
        requireCodePointLimit(dto.getContent(), MAX_CONTENT_CODE_POINTS, "content");
        return dto;
    }

    private static boolean isBlankLikePythonStrip(String value) {
        return value.codePoints().allMatch(codePoint ->
                Character.isWhitespace(codePoint)
                        || Character.isSpaceChar(codePoint)
                        || codePoint == 0x85
                        || (codePoint >= 0x1c && codePoint <= 0x1f));
    }

    /**
     * Length limits are counted in Unicode code points so supplementary-plane
     * characters count once, matching the Python client and the MySQL utf8mb4
     * storage. {@code String.length()} / {@code @Size} would count UTF-16 units
     * and reject valid payloads.
     */
    private static void requireCodePointLimit(String value, int maxCodePoints, String field) {
        if (value.codePointCount(0, value.length()) > maxCodePoints) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Validation failed: " + field + " must be at most " + maxCodePoints + " characters");
        }
    }
}
