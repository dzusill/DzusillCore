package me.dzusill.core.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import me.dzusill.core.CorePlugin;
import me.dzusill.core.example.ExamplePlugin;
import me.dzusill.core.message.MessageService;
import me.dzusill.core.scheduler.SchedulerService;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;

/**
 * Pins the exactly-once contract, which is what four of the six implementations this replaces got wrong.
 */
class ChatPromptServiceTest {

    private ServerMock server;
    private CorePlugin plugin;
    private ChatPromptService prompts;
    private PlayerMock alice;
    private long[] clock;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ExamplePlugin.class);
        clock = new long[]{1_000L};
        prompts = new ChatPromptService(plugin, new SchedulerService(plugin), new MessageService(plugin),
                () -> clock[0]);
        alice = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private List<String> capture() {
        List<String> results = new ArrayList<>();
        prompts.prompt(alice, "Name?", results::add);
        return results;
    }

    /**
     * Fires the chat event synchronously. {@code PlayerMock#chat} dispatches asynchronously, which makes assertions
     * immediately afterwards a race - and this also exercises the listener directly, which is the thing under test.
     */
    @SuppressWarnings("deprecation")
    private void chat(String message) {
        server.getPluginManager().callEvent(new org.bukkit.event.player.AsyncPlayerChatEvent(false, alice, message,
                new java.util.HashSet<>(java.util.Set.of(alice))));
    }

    @Test
    @DisplayName("an answer reaches the callback")
    void answerDelivered() {
        List<String> results = capture();
        chat("Steve");
        server.getScheduler().performTicks(2);
        assertEquals(List.of("Steve"), results);
    }

    @Test
    @DisplayName("the cancel keyword resolves as empty rather than dropping the callback")
    void cancelKeywordResolves() {
        List<String> results = capture();
        chat("cancel");
        server.getScheduler().performTicks(2);
        assertEquals(List.of(""), results);
    }

    @Test
    @DisplayName("a timeout resolves as empty")
    void timeoutResolves() {
        List<String> results = capture();
        clock[0] += PromptOptions.DEFAULT_TIMEOUT_TICKS * 50L + 1;
        server.getScheduler().performTicks(40);
        assertEquals(List.of(""), results);
    }

    @Test
    @DisplayName("quitting resolves as empty")
    void quitResolves() {
        List<String> results = capture();
        server.getPluginManager().callEvent(new org.bukkit.event.player.PlayerQuitEvent(alice, "quit"));
        server.getScheduler().performTicks(2);
        assertEquals(List.of(""), results);
    }

    @Test
    @DisplayName("an explicit cancel resolves as empty")
    void explicitCancelResolves() {
        List<String> results = capture();
        assertTrue(prompts.cancel(alice));
        server.getScheduler().performTicks(2);
        assertEquals(List.of(""), results);
    }

    @Test
    @DisplayName("only the first ending wins - a chat answer after a timeout is ignored")
    void exactlyOnce() {
        List<String> results = capture();
        clock[0] += PromptOptions.DEFAULT_TIMEOUT_TICKS * 50L + 1;
        server.getScheduler().performTicks(40);
        chat("too late");
        server.getScheduler().performTicks(2);
        assertEquals(List.of(""), results);
    }

    @Test
    @DisplayName("a stale timeout must not clobber a newly issued prompt")
    void staleTimeoutGuard() {
        AtomicInteger firstCalls = new AtomicInteger();
        List<String> second = new ArrayList<>();

        prompts.prompt(alice, PromptOptions.of("First?").withTimeout(20L), answer -> firstCalls.incrementAndGet());
        // Replacing the prompt resolves the first one immediately...
        prompts.prompt(alice, PromptOptions.of("Second?").withTimeout(1_200L), second::add);
        server.getScheduler().performTicks(2);
        assertEquals(1, firstCalls.get(), "the replaced prompt must resolve, not vanish");

        // ...and the first prompt's expiry must not then take the second one down with it.
        clock[0] += 20L * 50L + 1;
        server.getScheduler().performTicks(40);
        assertTrue(second.isEmpty(), "the first prompt's timeout resolved the second prompt");

        chat("Steve");
        server.getScheduler().performTicks(2);
        assertEquals(List.of("Steve"), second);
    }

    @Test
    @DisplayName("over-long input is truncated, not rejected")
    void truncates() {
        List<String> results = new ArrayList<>();
        prompts.prompt(alice, PromptOptions.of("Name?").withMaxLength(5), results::add);
        chat("abcdefghij");
        server.getScheduler().performTicks(2);
        assertEquals(List.of("abcde"), results);
    }

    @Test
    @DisplayName("a captured message is hidden from chat, and an uncaptured one is not")
    void consumesOnlyWhilePending() {
        assertFalse(prompts.isPending(alice));
        capture();
        assertTrue(prompts.isPending(alice));
        chat("Steve");
        server.getScheduler().performTicks(2);
        assertFalse(prompts.isPending(alice));
    }

    @Test
    @DisplayName("shutdown resolves everything so no caller is left waiting")
    void shutdownResolves() {
        List<String> results = capture();
        prompts.shutdown();
        server.getScheduler().performTicks(2);
        assertEquals(List.of(""), results);
    }
}
