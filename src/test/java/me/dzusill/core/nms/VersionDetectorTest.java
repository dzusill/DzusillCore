package me.dzusill.core.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import be.seeseemelk.mockbukkit.MockBukkit;

class VersionDetectorTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void detectsAVersionInTheSupportedRange() {
        MinecraftVersion version = VersionDetector.detect();

        assertEquals(1, version.major());
        assertTrue(version.isAtLeast(1, 16),
                "MockBukkit emulates a modern server; detection should land in the supported range");
        assertFalse(version.craftBukkitTag().isPresent(),
                "MockBukkit's server class is not in a relocated vX_Y_RZ package");
    }
}
