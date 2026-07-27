package me.dzusill.core.scheduler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.example.ExamplePlugin;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

/**
 * Pins the {@code retired} contract of the one-shot
 * {@link SchedulerService#atEntity(org.bukkit.entity.Entity, Runnable, Runnable)}.
 *
 * <p>
 * Anything that hands an off-thread result back to a player hits this: if they log out mid-request, the task is dropped
 * (silently, on Folia — the entity owns no thread any more) and whatever the handler was going to settle stays stuck.
 * The fallback gives callers one guaranteed exit either way.
 * </p>
 */
class SchedulerServiceRetiredTest {

    private ServerMock server;
    private CorePlugin plugin;
    private SchedulerService scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ExamplePlugin.class);
        scheduler = new SchedulerService(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void runsTheTaskWhileThePlayerIsOnline() {
        PlayerMock player = server.addPlayer("Steve");
        AtomicBoolean ran = new AtomicBoolean();
        AtomicBoolean retired = new AtomicBoolean();

        scheduler.atEntity(player, () -> ran.set(true), () -> retired.set(true));
        server.getScheduler().performOneTick();

        assertTrue(ran.get(), "task should run for an online player");
        assertFalse(retired.get(), "retired must not fire while the player is online");
    }

    @Test
    void runsTheRetiredFallbackOnceThePlayerHasLoggedOut() {
        PlayerMock player = server.addPlayer("Steve");
        AtomicBoolean ran = new AtomicBoolean();
        AtomicBoolean retired = new AtomicBoolean();

        scheduler.atEntity(player, () -> ran.set(true), () -> retired.set(true));
        player.disconnect();
        server.getScheduler().performOneTick();

        assertFalse(ran.get(), "the task must not run against a player who has gone");
        assertTrue(retired.get(), "retired is the caller's only chance to release state");
    }

    @Test
    void asyncThenAtEntityFallsBackWhenThePlayerLeavesMidRequest() {
        PlayerMock player = server.addPlayer("Steve");
        AtomicBoolean delivered = new AtomicBoolean();
        AtomicBoolean retired = new AtomicBoolean();

        scheduler.asyncThenAtEntity(player, () -> "result", result -> delivered.set(true), () -> retired.set(true));
        player.disconnect();
        server.getScheduler().waitAsyncTasksFinished();
        server.getScheduler().performTicks(2);

        assertFalse(delivered.get(), "the consumer must not see an offline player");
        assertTrue(retired.get(), "the caller must still be told the result went nowhere");
    }
}
