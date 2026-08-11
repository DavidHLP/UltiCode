package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.ProblemListDetailDTO;
import com.ulticode.app.api.dto.ProblemListItemDTO;
import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.app.api.service.ProblemListChainReadPort;
import com.ulticode.app.api.service.ProblemListReadPort;
import com.ulticode.modules.problemlist.entity.ProblemList;
import com.ulticode.modules.problemlist.entity.ProblemListProblemRelation;
import com.ulticode.modules.problemlist.mapper.ProblemListMapper;
import com.ulticode.modules.problemlist.mapper.ProblemListProblemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dubbo Provider implementation of {@link ProblemListChainReadPort} in
 * {@code backend-app}. Executes every query inside the App owner; the
 * Admin consumer depends only on the entity-free contract.
 *
 * <p>Non-throwing contract: {@link #findSummary} / {@link #findAdminDetail}
 * return {@code null} for a missing list; the Admin edge maps to its own
 * 404 semantics.
 */
@Slf4j
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ProblemListChainReadProvider implements ProblemListChainReadPort {

    private final ProblemListMapper problemListMapper;
    private final ProblemListProblemMapper problemListProblemMapper;
    private final ProblemListReadPort problemListReadPort;

    @Override
    public ProblemListSummaryDTO findSummary(String listId) {
        return problemListMapper.findById(listId)
                .map(this::toSummaryDTO)
                .orElse(null);
    }

    @Override
    public ProblemListDetailDTO findAdminDetail(String listId) {
        ProblemList list = problemListMapper.findById(listId).orElse(null);
        if (list == null) {
            return null;
        }
        ProblemListDetailDTO dto = new ProblemListDetailDTO();
        dto.setId(list.getId());
        dto.setName(list.getName());
        dto.setDescription(list.getDescription());
        dto.setAuthorId(list.getAuthorId());
        dto.setIsPublic(list.getIsPublic());
        dto.setIsFeatured(list.getIsFeatured());
        dto.setBannerTag(list.getBannerTag());
        dto.setBannerIcon(list.getBannerIcon());
        dto.setBannerTheme(list.getBannerTheme());
        dto.setBannerOrder(list.getBannerOrder());
        dto.setCreatedAt(list.getCreatedAt());
        dto.setUpdatedAt(list.getUpdatedAt());

        List<ProblemListProblemRelation> relations =
                problemListProblemMapper.findByListId(list.getId());
        if (relations == null || relations.isEmpty()) {
            dto.setProblems(Collections.emptyList());
        } else {
            dto.setProblems(assembleChain(relations));
        }
        return dto;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private ProblemListSummaryDTO toSummaryDTO(ProblemList list) {
        ProblemListSummaryDTO dto = new ProblemListSummaryDTO();
        dto.setId(list.getId());
        dto.setName(list.getName());
        dto.setDescription(list.getDescription());
        dto.setAuthorId(list.getAuthorId());
        dto.setIsPublic(list.getIsPublic());
        dto.setIsFeatured(list.getIsFeatured());
        dto.setBannerTag(list.getBannerTag());
        dto.setBannerIcon(list.getBannerIcon());
        dto.setBannerTheme(list.getBannerTheme());
        dto.setBannerOrder(list.getBannerOrder());
        dto.setProblemCount((int) problemListProblemMapper.countByListId(list.getId()));
        dto.setCreatedAt(list.getCreatedAt());
        dto.setUpdatedAt(list.getUpdatedAt());
        return dto;
    }

    /**
     * Join the ordered relations with the Problem-owned item columns
     * (batch-load, tag-enriched). Relations whose problem row is missing
     * are omitted.
     */
    private List<ProblemListDetailDTO.ProblemInListDTO> assembleChain(
            List<ProblemListProblemRelation> relations) {
        Set<Long> problemIds = relations.stream()
                .map(ProblemListProblemRelation::getProblemId)
                .collect(Collectors.toSet());
        List<ProblemListItemDTO> items = problemListReadPort.findByIds(problemIds);
        Map<Long, ProblemListItemDTO> byId = items.stream()
                .collect(Collectors.toMap(ProblemListItemDTO::id, Function.identity()));

        List<ProblemListDetailDTO.ProblemInListDTO> chain = new ArrayList<>(relations.size());
        for (ProblemListProblemRelation rel : relations) {
            ProblemListItemDTO item = byId.get(rel.getProblemId());
            if (item == null) {
                continue;
            }
            chain.add(new ProblemListDetailDTO.ProblemInListDTO(
                    item.id(),
                    item.slug(),
                    item.title(),
                    item.difficulty(),
                    item.status(),
                    rel.getSortOrder(),
                    rel.getAddedAt(),
                    item.acceptanceRate(),
                    item.isPremium(),
                    item.hasSolution(),
                    item.tags().stream()
                            .map(t -> new ProblemListDetailDTO.TagDTO(t.id(), t.label()))
                            .toList()));
        }
        return chain;
    }
}
