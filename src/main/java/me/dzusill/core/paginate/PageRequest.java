package me.dzusill.core.paginate;

/**
 * Which slice of a result set to fetch.
 *
 * <p>
 * Pages are 1-based, because the number goes in front of a player: {@code Page 1/7}. The {@link #offset()} it hands to
 * SQL is 0-based, which is the only place the distinction matters.
 * </p>
 *
 * @param page
 *            1-based page number; anything below 1 is treated as 1
 * @param size
 *            rows per page; clamped to {@value #MIN_SIZE}..{@value #MAX_SIZE}
 */
public record PageRequest(int page, int size) {

    /** A page of nothing is not a page. */
    public static final int MIN_SIZE = 1;

    /**
     * Chat holds twenty lines. A page larger than that scrolls its own header off screen, which defeats the point of
     * paging at all, so the setting is capped rather than trusted.
     */
    public static final int MAX_SIZE = 50;

    public PageRequest {
        page = Math.max(1, page);
        size = Math.min(MAX_SIZE, Math.max(MIN_SIZE, size));
    }

    /** First page at the given size. */
    public static PageRequest first(int size) {
        return new PageRequest(1, size);
    }

    /** 0-based row offset, for {@code OFFSET ?}. */
    public int offset() {
        return (page - 1) * size;
    }

    /** Row count, for {@code LIMIT ?}. */
    public int limit() {
        return size;
    }

    /**
     * The same request moved to {@code page}, clamped into a result set of {@code pageCount} pages.
     *
     * <p>
     * Clamping rather than failing is deliberate: a player who clicks Next on the last page, or types a page number out
     * of a stale header, should see the edge of the list rather than an error.
     * </p>
     */
    public PageRequest at(int target, int pageCount) {
        return new PageRequest(Math.min(Math.max(1, target), Math.max(1, pageCount)), size);
    }
}
