package me.dzusill.core.database.query;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlQueryTest {

    @Test
    void retainsSqlAndParameters() {
        SqlQuery query = SqlQuery.of("SELECT * FROM t WHERE a = ? AND b = ?", 1, "x");
        assertEquals("SELECT * FROM t WHERE a = ? AND b = ?", query.sql());
        assertArrayEquals(new Object[]{1, "x"}, query.parameters());
    }

    @Test
    void placeholdersMatchCount() {
        assertEquals("?, ?, ?", Statements.placeholders(3));
    }

    @Test
    void assignmentsBuildSetClause() {
        assertEquals("a = ?, b = ?", Statements.assignments(List.of("a", "b")));
    }
}
