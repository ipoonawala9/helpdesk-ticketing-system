package com.ibrahim.helpdesk.common.paging;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * One page of a list. A stable JSON shape owned by this API rather than
 * Spring Data's Page implementation, whose serialised form is not guaranteed.
 *
 * @param content       the items on this page
 * @param page          zero-based page number
 * @param size          requested page size
 * @param totalElements items across all pages
 * @param totalPages    number of pages
 * @param first         whether this is the first page
 * @param last          whether this is the last page
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
