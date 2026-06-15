package me.dzusill.core.database.query;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Small helpers for building and binding prepared statements: generating placeholder lists,
 * joining column names, and binding positional parameters. Shared by the database and repository
 * layers to keep statement construction DRY.
 */
public final class Statements {

    private Statements() {
    }

    /**
     * @return a comma-separated list of {@code count} {@code ?} placeholders, e.g. {@code "?, ?, ?"}
     */
    public static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }

    /**
     * Joins column names with commas.
     */
    public static String columns(List<String> columns) {
        return String.join(", ", columns);
    }

    /**
     * Joins columns as {@code "col = ?"} assignments with commas.
     */
    public static String assignments(List<String> columns) {
        return columns.stream().map(column -> column + " = ?").collect(Collectors.joining(", "));
    }

    /**
     * Binds positional parameters onto a prepared statement (1-based).
     */
    public static void bind(PreparedStatement statement, Object... parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            statement.setObject(i + 1, parameters[i]);
        }
    }
}
