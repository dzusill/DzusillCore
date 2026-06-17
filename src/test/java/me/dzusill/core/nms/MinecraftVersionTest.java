package me.dzusill.core.nms;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftVersionTest {

    private static MinecraftVersion of(int major, int minor, int patch) {
        return new MinecraftVersion(major, minor, patch, Optional.empty());
    }

    @Test
    void isAtLeastCrossesTheHistoricalBreakpoints() {
        MinecraftVersion v1_16_5 = of(1, 16, 5);
        MinecraftVersion v1_20_4 = of(1, 20, 4);
        MinecraftVersion v1_21_1 = of(1, 21, 1);

        assertFalse(v1_16_5.isAtLeast(1, 17), "1.16.5 is before the 1.17 NMS package move");
        assertTrue(v1_20_4.isAtLeast(1, 17));
        assertTrue(v1_20_4.isAtLeast(1, 20));
        assertFalse(v1_20_4.isAtLeast(1, 20, 5), "1.20.4 is before the 1.20.5 mapping change");
        assertTrue(v1_21_1.isAtLeast(1, 20, 5));
    }

    @Test
    void isBeforeIsTheInverseOfIsAtLeast() {
        assertTrue(of(1, 16, 5).isBefore(1, 17));
        assertFalse(of(1, 17, 0).isBefore(1, 17));
    }

    @Test
    void comparesByMajorThenMinorThenPatch() {
        assertTrue(of(1, 20, 4).compareTo(of(1, 21, 0)) < 0);
        assertTrue(of(1, 21, 1).compareTo(of(1, 21, 0)) > 0);
        assertEquals(0, of(1, 21, 1).compareTo(of(1, 21, 1)));
    }

    @Test
    void toStringOmitsZeroPatch() {
        assertEquals("1.21", of(1, 21, 0).toString());
        assertEquals("1.16.5", of(1, 16, 5).toString());
    }
}
