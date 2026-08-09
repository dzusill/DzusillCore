package me.dzusill.core.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Chaining is what lets a plugin override one dialog shape without silently disabling the rest.
 */
class DialogFallbackChainTest {

    private static final DialogSpec SPEC = DialogSpec.builder(DialogKind.Notice.ok("OK"), "T").build();

    private final DialogHandler noop = (buttonId, values) -> {
    };

    @Test
    @DisplayName("the second fallback runs only when the first declines")
    void secondRunsOnlyOnDecline() {
        AtomicInteger secondCalls = new AtomicInteger();
        DialogFallback declines = (player, spec, handler) -> false;
        DialogFallback counts = (player, spec, handler) -> {
            secondCalls.incrementAndGet();
            return true;
        };

        assertTrue(declines.orElse(counts).handle(null, SPEC, noop));
        assertEquals(1, secondCalls.get());
    }

    @Test
    @DisplayName("a claiming fallback short-circuits, so a dialog is never handled twice")
    void firstClaimShortCircuits() {
        AtomicInteger secondCalls = new AtomicInteger();
        DialogFallback claims = (player, spec, handler) -> true;
        DialogFallback counts = (player, spec, handler) -> {
            secondCalls.incrementAndGet();
            return true;
        };

        assertTrue(claims.orElse(counts).handle(null, SPEC, noop));
        assertEquals(0, secondCalls.get(), "the dialog would have been served twice");
    }

    @Test
    @DisplayName("if both decline the chain declines, so the caller keeps its own path")
    void bothDecline() {
        DialogFallback declines = (player, spec, handler) -> false;
        assertFalse(declines.orElse(declines).handle(null, SPEC, noop));
    }

    @Test
    @DisplayName("chaining onto null is a no-op rather than an NPE at dialog time")
    void nullNext() {
        DialogFallback declines = (player, spec, handler) -> false;
        assertSame(declines, declines.orElse(null));
    }

    @Test
    @DisplayName("none() declines everything")
    void noneDeclines() {
        assertFalse(DialogFallback.none().handle(null, SPEC, noop));
    }
}
