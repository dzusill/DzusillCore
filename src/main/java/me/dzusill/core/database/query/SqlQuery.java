package me.dzusill.core.database.query;

import java.util.Arrays;
import java.util.List;

/**
 * An immutable pairing of a parameterized SQL string and its positional parameters. Bundling them
 * keeps a statement and its bindings together when passing queries around (e.g. from a repository
 * to the database).
 */
public final class SqlQuery {

    private final String sql;
    private final List<Object> parameters;

    private SqlQuery(String sql, List<Object> parameters) {
        this.sql = sql;
        this.parameters = parameters;
    }

    public static SqlQuery of(String sql, Object... parameters) {
        return new SqlQuery(sql, List.of(parameters));
    }

    public String sql() {
        return sql;
    }

    public Object[] parameters() {
        return parameters.toArray();
    }

    @Override
    public String toString() {
        return "SqlQuery{sql='" + sql + "', parameters=" + Arrays.toString(parameters.toArray()) + '}';
    }
}
