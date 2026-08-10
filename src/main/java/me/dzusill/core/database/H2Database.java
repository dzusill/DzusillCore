package me.dzusill.core.database;

import java.io.File;
import java.util.concurrent.Executor;

/**
 * Embedded H2-backed {@link Database}. Behaviour is inherited from {@link AbstractSqlDatabase}; this subclass fixes the
 * {@link DatabaseType} and makes sure the directory holding the database file exists, since H2 creates the file but not
 * its parent folders.
 */
public final class H2Database extends AbstractSqlDatabase {

    public H2Database(DatabaseCredentials credentials, Executor asyncExecutor) {
        super(DatabaseType.H2, ensureParentDirectory(credentials), asyncExecutor);
    }

    /**
     * Creates the parent directory of the database file, so a configured path such as {@code data/chat} works on a
     * fresh install instead of failing the first connection.
     *
     * @return the unchanged credentials, so this can be called inline from the constructor
     */
    private static DatabaseCredentials ensureParentDirectory(DatabaseCredentials credentials) {
        String file = credentials.file();
        if (file == null || file.isBlank()) {
            return credentials;
        }
        File parent = new File(file).getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        return credentials;
    }
}
