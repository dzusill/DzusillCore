package me.dzusill.core.paginate;

import java.util.List;

/**
 * One page of rows, plus enough context to draw a footer.
 *
 * <p>
 * {@code total} is the count across the whole filtered result set, not this page - it is what lets the footer say
 * {@code Page 2/7} without a second round trip, and what tells a moderator whether the four lines in front of them are
 * the whole story or the tip of it.
 * </p>
 *
 * @param rows
 *            the rows on this page, already in display order
 * @param request
 *            the request that produced them, after clamping
 * @param total
 *            how many rows match in total, across every page
 */
public record Page<T>(List<T> rows, PageRequest request, long total) {

    public Page {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    /** An empty page, for "the database is off" and "nothing matched" alike. */
    public static <T> Page<T> empty(PageRequest request) {
        return new Page<>(List.of(), request, 0);
    }

    /**
     * Pages a list already held in memory. Only for sets small enough that loading them whole is not a problem -
     * anything backed by a growing table should page in SQL instead.
     */
    public static <T> Page<T> of(List<T> all, PageRequest request) {
        int total = all.size();
        int pageCount = pageCount(total, request.size());
        PageRequest clamped = request.at(request.page(), pageCount);
        int from = Math.min(clamped.offset(), total);
        int to = Math.min(from + clamped.size(), total);
        return new Page<>(all.subList(from, to), clamped, total);
    }

    /** How many pages {@code total} rows fill at {@code size} per page; always at least 1, so "1/1" beats "1/0". */
    public static int pageCount(long total, int size) {
        return total <= 0 ? 1 : (int) Math.ceil((double) total / Math.max(1, size));
    }

    public int page() {
        return request.page();
    }

    public int pageCount() {
        return pageCount(total, request.size());
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public boolean hasPrevious() {
        return page() > 1;
    }

    public boolean hasNext() {
        return page() < pageCount();
    }

    /** 1-based index of the first row on this page, for a "showing 11-20 of 63" header. */
    public long firstRowNumber() {
        return isEmpty() ? 0 : request.offset() + 1L;
    }

    /** 1-based index of the last row on this page. */
    public long lastRowNumber() {
        return isEmpty() ? 0 : request.offset() + (long) rows.size();
    }
}
