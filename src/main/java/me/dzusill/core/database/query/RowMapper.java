package me.dzusill.core.database.query;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps the current row of a {@link ResultSet} to a value of type {@code T}. This is the polymorphic seam of the query
 * layer: callers describe how to turn a row into a domain object, and the database executes and applies it uniformly.
 *
 * @param <T>
 *            the mapped type
 */
@FunctionalInterface
public interface RowMapper<T> {

    /**
     * Maps the row the result set is currently positioned on. Must not call {@code next()}.
     */
    T map(ResultSet resultSet) throws SQLException;
}
