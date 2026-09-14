package com.ibrahim.helpdesk.common.paging;

import com.ibrahim.helpdesk.exception.BusinessRuleException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;
import java.util.Map;

/**
 * Turns {@code page}, {@code size} and {@code sort} query parameters into a
 * validated Pageable. Only whitelisted sort fields are accepted, so a client
 * cannot sort on arbitrary or internal properties.
 */
public final class PageRequests {

    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    /**
     * @param sort          {@code field} or {@code field,asc|desc}
     * @param sortableFields API sort names mapped to the entity property they sort on
     */
    public static Pageable of(int page, int size, String sort, Map<String, String> sortableFields) {
        if (page < 0) {
            throw new BusinessRuleException("page must be 0 or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessRuleException("size must be between 1 and " + MAX_SIZE);
        }

        String[] parts = sort.split(",", -1);
        String field = parts[0].strip();
        String property = sortableFields.get(field);
        if (property == null || parts.length > 2) {
            throw new BusinessRuleException("sort must be one of " + String.join(", ", sortableFields.keySet())
                    + ", optionally followed by ,asc or ,desc");
        }

        Sort.Direction direction = Sort.Direction.ASC;
        if (parts.length == 2) {
            direction = switch (parts[1].strip().toLowerCase(Locale.ROOT)) {
                case "asc" -> Sort.Direction.ASC;
                case "desc" -> Sort.Direction.DESC;
                default -> throw new BusinessRuleException("sort direction must be asc or desc");
            };
        }

        // The id breaks ties, so rows with equal sort values keep a stable
        // order and never move between pages.
        return PageRequest.of(page, size, Sort.by(direction, property).and(Sort.by(direction, "id")));
    }

    /**
     * A LIKE pattern matching {@code text} anywhere, case-insensitively, with
     * the wildcard characters % and _ in the text matched literally. Use with
     * {@code '\\'} as the escape character.
     */
    public static String containsPattern(String text) {
        String escaped = text.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** Validates a free-text search term; blank means no search. */
    public static String searchTerm(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        if (q.strip().length() > 100) {
            throw new BusinessRuleException("q must be at most 100 characters");
        }
        return q.strip();
    }
}
