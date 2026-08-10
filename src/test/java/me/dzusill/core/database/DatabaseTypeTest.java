package me.dzusill.core.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DatabaseTypeTest {

    private static final DatabaseCredentials CREDS = new DatabaseCredentials("localhost", 3306, "mc", "user", "pass",
            10, 30000L, Map.of());

    @Test
    void buildsMysqlUrl() {
        assertEquals("jdbc:mysql://localhost:3306/mc", DatabaseType.MYSQL.jdbcUrl(CREDS));
    }

    @Test
    void buildsPostgresUrl() {
        DatabaseCredentials creds = new DatabaseCredentials("db", 5432, "mc", "u", "p", 5, 1000L, Map.of());
        assertEquals("jdbc:postgresql://db:5432/mc", DatabaseType.POSTGRESQL.jdbcUrl(creds));
    }

    @Test
    void mysqlUpsertUsesDuplicateKeySyntax() {
        String sql = DatabaseType.MYSQL.upsert("t", List.of("id", "a", "b"), List.of("id"));
        assertTrue(sql.contains("ON DUPLICATE KEY UPDATE"), sql);
        assertTrue(sql.contains("a = VALUES(a)"), sql);
        assertTrue(sql.contains("b = VALUES(b)"), sql);
    }

    @Test
    void postgresUpsertUsesOnConflictSyntax() {
        String sql = DatabaseType.POSTGRESQL.upsert("t", List.of("id", "a", "b"), List.of("id"));
        assertTrue(sql.contains("ON CONFLICT (id) DO UPDATE SET"), sql);
        assertTrue(sql.contains("a = EXCLUDED.a"), sql);
    }

    @Test
    void h2UrlPointsAtTheConfiguredFileInMysqlMode() {
        DatabaseCredentials creds = new DatabaseCredentials("ignored", 0, "mc", "sa", "", 5, 1000L, Map.of(),
                "/srv/plugins/Demo/data");

        assertEquals("jdbc:h2:file:/srv/plugins/Demo/data;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                DatabaseType.H2.jdbcUrl(creds));
    }

    @Test
    void h2UrlFallsBackToTheDatabaseNameWhenNoFileIsSet() {
        assertEquals("jdbc:h2:file:mc;MODE=MySQL;DATABASE_TO_LOWER=TRUE", DatabaseType.H2.jdbcUrl(CREDS));
    }

    @Test
    void h2UpsertUsesMergeSyntax() {
        String sql = DatabaseType.H2.upsert("t", List.of("id", "a", "b"), List.of("id"));

        assertEquals("MERGE INTO t (id, a, b) KEY (id) VALUES (?, ?, ?)", sql);
    }

    @Test
    void h2DropsConnectionPropertiesItsDriverWouldReject() {
        DatabaseCredentials creds = new DatabaseCredentials("localhost", 0, "mc", "sa", "", 5, 1000L,
                Map.of("useSSL", "false"), "data");

        assertTrue(DatabaseType.H2.connectionProperties(creds).isEmpty());
        assertEquals(Map.of("useSSL", "false"), DatabaseType.MYSQL.connectionProperties(creds));
    }

    @Test
    void fromStringIsCaseInsensitive() {
        assertEquals(DatabaseType.MYSQL, DatabaseType.fromString("mysql"));
        assertEquals(DatabaseType.POSTGRESQL, DatabaseType.fromString("PostgreSQL"));
        assertEquals(DatabaseType.H2, DatabaseType.fromString("h2"));
    }

    @Test
    void fromStringRejectsUnknown() {
        assertThrows(DatabaseException.class, () -> DatabaseType.fromString("oracle"));
    }
}
