package me.dzusill.core.database;

import java.util.concurrent.Executor;

/**
 * PostgreSQL-backed {@link Database}. All behaviour is inherited from {@link AbstractSqlDatabase}; this subclass only
 * fixes the {@link DatabaseType}.
 */
public final class PostgreSqlDatabase extends AbstractSqlDatabase {

    public PostgreSqlDatabase(DatabaseCredentials credentials, Executor asyncExecutor) {
        super(DatabaseType.POSTGRESQL, credentials, asyncExecutor);
    }
}
