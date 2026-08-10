package me.dzusill.core.paginate;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a {@link LogFilter} into the SQL fragment and parameters a paged log query needs.
 *
 * <p>
 * Every repository that pages a log needs the same three predicates and the same {@code LIMIT}/{@code OFFSET} tail.
 * Written once here so the three of them cannot drift apart - and so the {@code LIKE} escaping, which is the part with
 * teeth, exists in exactly one place. Column names come from the caller and are never player input; the values are
 * always bound parameters.
 * </p>
 */
public final class LogQuery {

    /**
     * Only these dialects' shared {@code LIKE ... ESCAPE} form is used, which H2, MySQL, MariaDB and PostgreSQL all
     * accept. The character matches {@link LogFilter#likePattern()}.
     */
    private static final String LIKE_ESCAPE = " ESCAPE '!'";

    private final String where;
    private final List<Object> params;

    private LogQuery(String where, List<Object> params) {
        this.where = where;
        this.params = List.copyOf(params);
    }

    /**
     * Builds the fragment for {@code filter}.
     *
     * @param filter
     *            what the staff member narrowed to
     * @param timestampColumn
     *            the column {@code --since} and {@code --until} compare against
     * @param searchColumns
     *            the columns {@code --find} looks in; matching any one of them is a hit
     */
    public static LogQuery of(LogFilter filter, String timestampColumn, List<String> searchColumns) {
        StringBuilder sql = new StringBuilder();
        List<Object> params = new ArrayList<>(4);

        filter.since().ifPresent(since -> {
            sql.append(" AND ").append(timestampColumn).append(" >= ?");
            params.add(since);
        });
        filter.until().ifPresent(until -> {
            sql.append(" AND ").append(timestampColumn).append(" <= ?");
            params.add(until);
        });
        if (filter.hasFind() && !searchColumns.isEmpty()) {
            sql.append(" AND (");
            for (int i = 0; i < searchColumns.size(); i++) {
                if (i > 0) {
                    sql.append(" OR ");
                }
                sql.append("LOWER(").append(searchColumns.get(i)).append(") LIKE ?").append(LIKE_ESCAPE);
                params.add(filter.likePattern());
            }
            sql.append(')');
        }
        return new LogQuery(sql.toString(), params);
    }

    /** The predicate to splice in after an existing {@code WHERE}; empty when nothing was filtered. */
    public String where() {
        return where;
    }

    /** The parameters for {@link #where()}, in order. */
    public List<Object> params() {
        return params;
    }

    /**
     * Concatenates leading parameters, this filter's, and a trailing {@code LIMIT}/{@code OFFSET} pair, in the order
     * the {@code ?} placeholders appear.
     */
    public Object[] paramsFor(PageRequest request, Object... leading) {
        List<Object> all = new ArrayList<>(leading.length + params.size() + 2);
        java.util.Collections.addAll(all, leading);
        all.addAll(params);
        all.add(request.limit());
        all.add(request.offset());
        return all.toArray();
    }

    /** The same, without the paging pair — for the matching {@code COUNT(*)}. */
    public Object[] countParams(Object... leading) {
        List<Object> all = new ArrayList<>(leading.length + params.size());
        java.util.Collections.addAll(all, leading);
        all.addAll(params);
        return all.toArray();
    }

    /** The {@code LIMIT ? OFFSET ?} tail, spelled the way every supported dialect accepts. */
    public static String limitOffset() {
        return " LIMIT ? OFFSET ?";
    }
}
