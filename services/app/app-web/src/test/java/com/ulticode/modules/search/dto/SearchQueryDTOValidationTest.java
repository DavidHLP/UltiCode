package com.ulticode.modules.search.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class SearchQueryDTOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsSingleCharacterQueries() {
        SearchQueryDTO query = new SearchQueryDTO();
        query.setQuery("a");
        query.setPage(1);
        query.setLimit(20);

        assertThat(validator.validate(query))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("query");
    }
}
