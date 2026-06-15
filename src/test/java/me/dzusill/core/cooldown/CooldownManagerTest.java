package me.dzusill.core.cooldown;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CooldownManagerTest {

    @Test
    void notActiveBeforeStart() {
        CooldownManager<UUID> cooldown = new CooldownManager<>(10, TimeUnit.SECONDS);
        assertFalse(cooldown.isActive(UUID.randomUUID()));
    }

    @Test
    void activeAfterStartWithRemainingTime() {
        CooldownManager<String> cooldown = new CooldownManager<>(10, TimeUnit.SECONDS);
        cooldown.start("player");
        assertTrue(cooldown.isActive("player"));
        assertTrue(cooldown.remaining("player") > 0);
    }

    @Test
    void resetClearsCooldown() {
        CooldownManager<String> cooldown = new CooldownManager<>(10, TimeUnit.SECONDS);
        cooldown.start("player");
        cooldown.reset("player");
        assertFalse(cooldown.isActive("player"));
        assertEquals(0L, cooldown.remaining("player"));
    }

    @Test
    void zeroDurationIsImmediatelyExpired() {
        CooldownManager<String> cooldown = new CooldownManager<>(0, TimeUnit.MILLISECONDS);
        cooldown.start("player");
        assertFalse(cooldown.isActive("player"));
    }
}
