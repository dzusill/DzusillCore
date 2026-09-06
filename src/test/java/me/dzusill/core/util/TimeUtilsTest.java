package me.dzusill.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The countdown shape every plugin on the framework shares. */
class TimeUtilsTest {

    private static String of(long days, long hours, long minutes, long seconds) {
        return TimeUtils.format(TimeUnit.DAYS.toMillis(days) + TimeUnit.HOURS.toMillis(hours)
                + TimeUnit.MINUTES.toMillis(minutes) + TimeUnit.SECONDS.toMillis(seconds));
    }

    @Test
    @DisplayName("a leading zero unit is dropped")
    void nothingIsPrintedAboveTheLargestNonZeroUnit() {
        assertEquals("52m 42s", of(0, 0, 52, 42), "no 0h in front");
        assertEquals("30s", of(0, 0, 0, 30), "no 0h 0m in front");
        assertEquals("2h 0m 0s", of(0, 2, 0, 0), "no 0d in front");
    }

    @Test
    @DisplayName("every unit below the largest one is printed, zero or not")
    void smallerUnitsAlwaysFollow() {
        // The width then only changes when the duration crosses a unit boundary, not on an ordinary
        // tick: dropping from "5h 1m 42s" to "5h 42s" looked like a lost column.
        assertEquals("5h 0m 42s", of(0, 5, 0, 42));
        assertEquals("1h 0m 0s", of(0, 1, 0, 0), "an exact hour still shows its minutes and seconds");
        assertEquals("1d 0h 0m 5s", of(1, 0, 0, 5));
        assertEquals("2d 0h 30m 0s", of(2, 0, 30, 0));
        assertEquals("1m 0s", of(0, 0, 1, 0));
    }

    @Test
    @DisplayName("every unit present")
    void allFourUnits() {
        assertEquals("1d 2h 3m 4s", of(1, 2, 3, 4));
        assertEquals("3h 4m 2s", of(0, 3, 4, 2));
    }

    @Test
    @DisplayName("under a second is 0s, not empty")
    void subSecondDurations() {
        assertEquals("0s", TimeUtils.format(0));
        assertEquals("0s", TimeUtils.format(999));
        assertEquals("0s", TimeUtils.format(-5_000));
        assertEquals("1s", TimeUtils.format(1_000));
    }

    @Test
    @DisplayName("days do not roll into a larger unit")
    void longDurationsStayInDays() {
        assertEquals("400d 0h 0m 0s", of(400, 0, 0, 0));
    }
}
