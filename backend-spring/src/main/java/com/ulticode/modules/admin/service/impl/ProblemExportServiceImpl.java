package com.ulticode.modules.admin.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.admin.service.ProblemExportService;
import com.ulticode.modules.problem.dto.ProblemQueryDTO;
import com.ulticode.modules.problem.dto.ProblemVO;
import com.ulticode.modules.problem.projection.ProblemProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default {@link ProblemExportService} implementation.
 *
 * <p>Owns every line of business logic the controller used to carry:
 * <ul>
 *   <li>format normalisation + validation (throws
 *       {@link BusinessException} {@link ErrorCode#BAD_REQUEST} on unknown
 *       format, so the global handler shapes the response);</li>
 *   <li>the {@link ProblemExportService#MAX_EXPORT_SIZE} cap;</li>
 *   <li>the CSV header literal + per-field escaping;</li>
 *   <li>the export date stamp, sourced from the injected {@link Clock}
 *       (closes the {@code LocalDate.now()} time-leak the {@code ClockConfig}
 *       seam was added to remove).</li>
 * </ul>
 *
 * <p>The controller is left with 8&ndash;10 lines of pure response-writing.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemExportServiceImpl implements ProblemExportService {

    private static final String CSV_HEADER =
        "id,slug,title,difficulty,status,isPremium,isPublished,submissionCount,solutionCount,"
            + "createdAt,updatedAt,tags";

    private final ProblemProjection problemProjection;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    public ExportPayload export(ProblemQueryDTO query, String format) {
        String normalizedFormat = normalizeFormat(format);
        String date = LocalDate.now(clock).toString();

        List<ProblemVO> problems = problemProjection.listAllProblems(query);
        if (problems.size() > MAX_EXPORT_SIZE) {
            problems = problems.subList(0, MAX_EXPORT_SIZE);
        }

        if ("json".equals(normalizedFormat)) {
            return ExportPayload.json("problems-export-" + date + ".json", toJson(problems));
        }
        return ExportPayload.csv("problems-export-" + date + ".csv", toCsv(problems));
    }

    private static String normalizeFormat(String format) {
        String normalized = format != null ? format.trim().toLowerCase() : "json";
        if (!"csv".equals(normalized) && !"json".equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                "Unsupported format: " + format + ". Use 'json' or 'csv'.");
        }
        return normalized;
    }

    private byte[] toJson(List<ProblemVO> problems) {
        try {
            return objectMapper.writeValueAsBytes(problems);
        } catch (JsonProcessingException e) {
            // Serialization of a list of POJO VOs should never fail in
            // production; surface as a 500 rather than swallowing.
            throw new UncheckedIOException(e);
        }
    }

    private String toCsv(List<ProblemVO> problems) {
        StringBuilder sb = new StringBuilder(CSV_HEADER.length() * (problems.size() + 1));
        sb.append(CSV_HEADER).append('\n');
        for (ProblemVO problem : problems) {
            String tags = problem.getTags() != null
                ? problem.getTags().stream().map(ProblemVO.ProblemTagVO::getLabel)
                    .collect(Collectors.joining(";"))
                : "";
            sb.append(String.join(",",
                String.valueOf(problem.getId()),
                escapeCsvField(problem.getSlug()),
                escapeCsvField(problem.getTitle()),
                escapeCsvField(problem.getDifficulty()),
                escapeCsvField(problem.getStatus()),
                String.valueOf(problem.getIsPremium()),
                String.valueOf(problem.getIsPublished()),
                String.valueOf(problem.getSubmissionCount()),
                String.valueOf(problem.getSolutionCount()),
                problem.getCreatedAt() != null ? problem.getCreatedAt().toString() : "",
                problem.getUpdatedAt() != null ? problem.getUpdatedAt().toString() : "",
                escapeCsvField(tags)
            )).append('\n');
        }
        return sb.toString();
    }

    /**
     * Escape a single CSV field per RFC 4180: wrap in quotes when the field
     * contains a comma, quote, newline, or carriage return; double any
     * embedded quote.
     */
    private static String escapeCsvField(String field) {
        if (field == null || field.isEmpty()) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
