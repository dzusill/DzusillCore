package me.dzusill.core.database.query;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A unit of work executed against a borrowed {@link Connection}, allowed to throw {@link SQLException}. Used for
 * multi-statement operations and transactions where the caller needs control over the connection.
 *
 * @param <T>
 *            the result type
 */
@FunctionalInterface
public interface SqlFunction<T> {

    T apply(Connection connection) throws SQLException;
}
