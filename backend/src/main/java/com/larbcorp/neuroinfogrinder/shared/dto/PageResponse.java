package com.larbcorp.neuroinfogrinder.shared.dto;

import java.util.List;

/**
 * Generic paginated response wrapper matching the API spec format:
 * { content, page, size, totalElements, totalPages }
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> springPage) {
        return new PageResponse<>(
            springPage.getContent(),
            springPage.getNumber(),
            springPage.getSize(),
            springPage.getTotalElements(),
            springPage.getTotalPages()
        );
    }
}
