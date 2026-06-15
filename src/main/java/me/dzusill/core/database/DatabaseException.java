package me.dzusill.core.database;

/**
 * Unchecked wrapper for {@link java.sql.SQLException} and related failures. Database operations
 * return {@link java.util.concurrent.CompletableFuture}s; on failure they complete exceptionally
 * with this type, so callers handle errors via the future rather than checked exceptions.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public DatabaseException(String message) {
        super(message);
    }
}
