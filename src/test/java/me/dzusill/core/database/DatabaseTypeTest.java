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
    void fromStringIsCaseInsensitive() {
        assertEquals(DatabaseType.MYSQL, DatabaseType.fromString("mysql"));
        assertEquals(DatabaseType.POSTGRESQL, DatabaseType.fromString("PostgreSQL"));
    }

    @Test
    void fromStringRejectsUnknown() {
        assertThrows(DatabaseException.class, () -> DatabaseType.fromString("oracle"));
    }
}
