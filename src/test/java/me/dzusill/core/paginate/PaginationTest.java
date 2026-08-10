package me.dzusill.core.paginate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

/** The arithmetic behind a paged log, and the flag parsing that narrows one. */
class PaginationTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    /** 2026-08-10 12:00:00 UTC — a fixed clock, so relative forms are checkable. */
    private static final long NOW = 1_786_363_200_000L;

    private static List<Integer> numbers(int count) {
        List<Integer> all = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            all.add(i);
        }
        return all;
    }

    // --- PageRequest ---------------------------------------------------------

    @Test
    void offsetIsZeroBasedEvenThoughPagesAreNot() {
        assertEquals(0, new PageRequest(1, 10).offset());
        assertEquals(20, new PageRequest(3, 10).offset());
    }

    @Test
    void aPageBelowOneBecomesTheFirst() {
        assertEquals(1, new PageRequest(0, 10).page());
        assertEquals(1, new PageRequest(-5, 10).page());
    }

    @Test
    void pageSizeIsClampedToSomethingChatCanHold() {
        assertEquals(PageRequest.MAX_SIZE, new PageRequest(1, 5000).size());
        assertEquals(PageRequest.MIN_SIZE, new PageRequest(1, 0).size());
    }

    @Test
    void clampingKeepsAStalePageNumberInsideTheResultSet() {
        // Someone clicks Next on a footer from before rows were cleared. Show them the edge, not an error.
        assertEquals(3, new PageRequest(1, 10).at(9, 3).page());
        assertEquals(1, new PageRequest(1, 10).at(0, 3).page());
    }

    // --- Page ----------------------------------------------------------------

    @Test
    void aFullResultSetSplitsIntoTheRightPages() {
        Page<Integer> page = Page.of(numbers(63), new PageRequest(2, 10));

        assertEquals(List.of(11, 12, 13, 14, 15, 16, 17, 18, 19, 20), page.rows());
        assertEquals(7, page.pageCount());
        assertEquals(63, page.total());
    }

    @Test
    void theLastPageIsShortRatherThanPadded() {
        Page<Integer> page = Page.of(numbers(63), new PageRequest(7, 10));

        assertEquals(List.of(61, 62, 63), page.rows());
        assertFalse(page.hasNext());
        assertTrue(page.hasPrevious());
    }

    @Test
    void oneShortPageHasNoNeighbours() {
        Page<Integer> page = Page.of(numbers(4), new PageRequest(1, 10));

        assertEquals(1, page.pageCount());
        assertFalse(page.hasPrevious());
        assertFalse(page.hasNext());
    }

    @Test
    void nothingMatchedIsStillPageOneOfOne() {
        Page<Integer> page = Page.of(List.of(), new PageRequest(1, 10));

        assertTrue(page.isEmpty());
        assertEquals(1, page.pageCount(), "\"1/0\" reads like a bug; \"1/1\" reads like an empty list");
        assertEquals(0, page.firstRowNumber());
    }

    @Test
    void askingBeyondTheEndLandsOnTheLastPage() {
        Page<Integer> page = Page.of(numbers(25), new PageRequest(99, 10));

        assertEquals(3, page.page());
        assertEquals(List.of(21, 22, 23, 24, 25), page.rows());
    }

    @Test
    void rowNumbersAreTheOnesAModeratorWouldCount() {
        Page<Integer> page = Page.of(numbers(63), new PageRequest(2, 10));

        assertEquals(11, page.firstRowNumber());
        assertEquals(20, page.lastRowNumber());
    }

    // --- TimeSpec ------------------------------------------------------------

    @Test
    void relativeFormsCountBackFromNow() {
        assertEquals(NOW - 3_600_000L, TimeSpec.parse("1h", NOW, UTC).orElseThrow());
        assertEquals(NOW - 604_800_000L, TimeSpec.parse("7d", NOW, UTC).orElseThrow());
        assertEquals(NOW - 1_209_600_000L, TimeSpec.parse("2w", NOW, UTC).orElseThrow());
    }

    @Test
    void anAbsoluteDateIsMidnightLocal() {
        long parsed = TimeSpec.parse("2026-08-10", NOW, UTC).orElseThrow();

        assertEquals(NOW - 43_200_000L, parsed, "noon minus twelve hours is midnight the same day");
    }

    @Test
    void anAbsoluteDateAndTimeIsThatMinute() {
        assertEquals(NOW, TimeSpec.parse("2026-08-10 12:00", NOW, UTC).orElseThrow());
    }

    @Test
    void aTypoIsEmptyRatherThanAnException() {
        assertTrue(TimeSpec.parse("yesterday", NOW, UTC).isEmpty());
        assertTrue(TimeSpec.parse("2026-13-45", NOW, UTC).isEmpty(), "right shape, not a real date");
        assertTrue(TimeSpec.parse("", NOW, UTC).isEmpty());
        assertTrue(TimeSpec.parse(null, NOW, UTC).isEmpty());
    }

    @Test
    void anAbsurdNumberIsRejectedRatherThanOverflowed() {
        assertTrue(TimeSpec.parse("99999999999999999999d", NOW, UTC).isEmpty());
    }

    // --- LogFilter -----------------------------------------------------------

    @Test
    void flagsAreReadOutOfTheArguments() {
        List<String> problems = new ArrayList<>();
        LogFilter filter = LogFilter.parse(List.of("Steve", "2", "--since", "7d", "--find", "idiot"), NOW, UTC,
                problems);

        assertTrue(problems.isEmpty());
        assertEquals(NOW - 604_800_000L, filter.since().orElseThrow());
        assertEquals("idiot", filter.find());
        assertTrue(filter.until().isEmpty());
    }

    @Test
    void whatIsLeftAfterTheFlagsIsThePositionalArguments() {
        assertEquals(List.of("Steve", "2"),
                LogFilter.stripFlags(List.of("Steve", "2", "--since", "7d", "--find", "idiot")));
    }

    @Test
    void anUnparseableFilterIsReportedRatherThanDropped() {
        // The worst outcome would be reading a full log while believing it was filtered.
        List<String> problems = new ArrayList<>();
        LogFilter filter = LogFilter.parse(List.of("--since", "yesterday"), NOW, UTC, problems);

        assertEquals(List.of("--since yesterday"), problems);
        assertTrue(filter.since().isEmpty());
    }

    @Test
    void anUnknownFlagIsReported() {
        List<String> problems = new ArrayList<>();
        LogFilter.parse(List.of("--wat", "x"), NOW, UTC, problems);

        assertEquals(List.of("--wat"), problems);
    }

    @Test
    void aFlagWithNoValueIsReported() {
        List<String> problems = new ArrayList<>();
        LogFilter.parse(List.of("--since"), NOW, UTC, problems);

        assertEquals(List.of("--since (no value)"), problems);
    }

    @Test
    void searchWildcardsTypedByAPlayerAreEscaped() {
        LogFilter filter = new LogFilter(OptionalLong.empty(), OptionalLong.empty(), "100%_off");

        // Unescaped, "%" and "_" would make this match most of the table.
        assertEquals("%100!%!_off%", filter.likePattern());
    }

    @Test
    void quotesCannotReachTheFooterCommand() {
        // The footer rebuilds the command inside a MiniMessage click:run_command:'...' argument.
        LogFilter filter = new LogFilter(OptionalLong.empty(), OptionalLong.empty(), "it's <red>bad");

        assertEquals("its redbad", filter.find());
    }

    @Test
    void aSearchTermIsBounded() {
        LogFilter filter = new LogFilter(OptionalLong.empty(), OptionalLong.empty(), "x".repeat(500));

        assertEquals(64, filter.find().length());
    }

    // --- LogQuery ------------------------------------------------------------

    @Test
    void noFilterAddsNoPredicate() {
        LogQuery query = LogQuery.of(LogFilter.none(), "created_at", List.of("message"));

        assertEquals("", query.where());
        assertTrue(query.params().isEmpty());
    }

    @Test
    void eachFilterAddsItsOwnPredicateAndParameter() {
        List<String> problems = new ArrayList<>();
        LogFilter filter = LogFilter.parse(List.of("--since", "7d", "--until", "1h", "--find", "ip"), NOW, UTC,
                problems);
        LogQuery query = LogQuery.of(filter, "created_at", List.of("reason", "message"));

        assertEquals(" AND created_at >= ? AND created_at <= ?"
                + " AND (LOWER(reason) LIKE ? ESCAPE '!' OR LOWER(message) LIKE ? ESCAPE '!')", query.where());
        assertEquals(4, query.params().size(), "one bound parameter per placeholder");
    }

    @Test
    void parametersComeOutInPlaceholderOrder() {
        List<String> problems = new ArrayList<>();
        LogFilter filter = LogFilter.parse(List.of("--since", "7d"), NOW, UTC, problems);
        LogQuery query = LogQuery.of(filter, "created_at", List.of("message"));

        Object[] params = query.paramsFor(new PageRequest(3, 10), "uuid-here");

        assertEquals("uuid-here", params[0]);
        assertEquals(NOW - 604_800_000L, params[1]);
        assertEquals(10, params[2], "LIMIT");
        assertEquals(20, params[3], "OFFSET");
    }

    @Test
    void theCountQueryTakesTheSameParametersWithoutThePagingPair() {
        List<String> problems = new ArrayList<>();
        LogFilter filter = LogFilter.parse(List.of("--find", "x"), NOW, UTC, problems);
        LogQuery query = LogQuery.of(filter, "created_at", List.of("message"));

        assertEquals(2, query.countParams("uuid-here").length);
    }
}
