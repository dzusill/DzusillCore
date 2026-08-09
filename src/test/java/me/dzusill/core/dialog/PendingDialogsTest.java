package me.dzusill.core.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The security contract. A dialog response is a client-initiated packet, so every rule here is load-bearing.
 */
class PendingDialogsTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID MALLORY = UUID.randomUUID();

    private long now;
    private PendingDialogs pending;
    private AtomicInteger cancels;
    private DialogHandler handler;

    @BeforeEach
    void setUp() {
        now = 1_000L;
        pending = new PendingDialogs(1_000L, () -> now);
        cancels = new AtomicInteger();
        handler = new DialogHandler() {

            @Override
            public void onSubmit(String buttonId, me.dzusill.core.dialog.spi.DialogValues values) {
                // not used here
            }

            @Override
            public void onCancel() {
                cancels.incrementAndGet();
            }
        };
    }

    private DialogSpec spec() {
        return DialogSpec.builder(DialogKind.Notice.ok("OK"), "T").build();
    }

    private DialogSpec repeatableSpec() {
        return DialogSpec.builder(DialogKind.Notice.ok("OK"), "T").afterAction(AfterAction.NONE).build();
    }

    @Nested
    @DisplayName("token hygiene")
    class Tokens {

        @Test
        @DisplayName("tokens are unique and unguessable-looking")
        void unique() {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 500; i++)
                assertTrue(seen.add(pending.issue(ALICE, spec(), handler)), "token collision");
        }

        @Test
        @DisplayName("tokens are safe to embed in a namespaced-id path")
        void charset() {
            for (int i = 0; i < 50; i++) {
                String token = pending.issue(ALICE, spec(), handler);
                assertTrue(token.matches("[0-9a-z]+"), "token not path-safe: " + token);
            }
        }

        @Test
        @DisplayName("consecutive tokens share no prefix - not sequential, not derived from the player")
        void notSequential() {
            String first = pending.issue(ALICE, spec(), handler);
            String second = pending.issue(ALICE, spec(), handler);
            assertNotEquals(first, second);
            assertNotEquals(first.substring(0, 4), second.substring(0, 4));
        }
    }

    @Nested
    @DisplayName("ownership")
    class Ownership {

        @Test
        @DisplayName("the owning player can claim")
        void ownerClaims() {
            String token = pending.issue(ALICE, spec(), handler);
            assertTrue(pending.claim(token, ALICE).isPresent());
        }

        @Test
        @DisplayName("a forged packet from another player is rejected")
        void foreignPlayerRejected() {
            String token = pending.issue(ALICE, spec(), handler);
            assertTrue(pending.claim(token, MALLORY).isEmpty());
        }

        @Test
        @DisplayName("a rejected foreign claim must NOT burn the token - otherwise it is a denial-of-service")
        void foreignClaimDoesNotConsume() {
            String token = pending.issue(ALICE, spec(), handler);
            assertTrue(pending.claim(token, MALLORY).isEmpty());
            assertTrue(pending.claim(token, ALICE).isPresent(), "Mallory burned Alice's dialog");
        }

        @Test
        @DisplayName("an unknown token is simply ignored")
        void unknownToken() {
            assertTrue(pending.claim("nope", ALICE).isEmpty());
            assertTrue(pending.claim(null, ALICE).isEmpty());
        }
    }

    @Nested
    @DisplayName("exactly-once")
    class ExactlyOnce {

        @Test
        @DisplayName("a token can only be claimed once")
        void singleUse() {
            String token = pending.issue(ALICE, spec(), handler);
            assertTrue(pending.claim(token, ALICE).isPresent());
            assertTrue(pending.claim(token, ALICE).isEmpty(), "replay accepted");
        }

        @Test
        @DisplayName("claiming then cancelling does not double-resolve")
        void claimThenCancel() {
            String token = pending.issue(ALICE, spec(), handler);
            assertTrue(pending.claim(token, ALICE).isPresent());
            assertTrue(pending.cancel(token).isEmpty());
            assertEquals(0, cancels.get());
        }

        @Test
        @DisplayName("cancelling then claiming does not double-resolve")
        void cancelThenClaim() {
            String token = pending.issue(ALICE, spec(), handler);
            pending.cancel(token).ifPresent(p -> p.handler().onCancel());
            assertEquals(1, cancels.get());
            assertTrue(pending.claim(token, ALICE).isEmpty());
        }

        @Test
        @DisplayName("expiry and quit cleanup cannot both fire for the same dialog")
        void expiryAndQuitRace() {
            pending.issue(ALICE, spec(), handler);
            now += 5_000L;
            pending.sweepExpired().forEach(p -> p.handler().onCancel());
            pending.cancelAllFor(ALICE).forEach(p -> p.handler().onCancel());
            assertEquals(1, cancels.get());
        }

        @Test
        @DisplayName("afterAction NONE deliberately stays claimable for repeated submission")
        void repeatableStaysOpen() {
            String token = pending.issue(ALICE, repeatableSpec(), handler);
            assertTrue(pending.claim(token, ALICE).isPresent());
            assertTrue(pending.claim(token, ALICE).isPresent());
            assertTrue(pending.claim(token, ALICE).isPresent());
        }

        @Test
        @DisplayName("a repeatable dialog is still cancellable exactly once")
        void repeatableCancels() {
            String token = pending.issue(ALICE, repeatableSpec(), handler);
            assertTrue(pending.cancel(token).isPresent());
            assertTrue(pending.cancel(token).isEmpty());
        }
    }

    @Nested
    @DisplayName("lifetime")
    class Lifetime {

        @Test
        @DisplayName("an expired token cannot be claimed")
        void expiredRejected() {
            String token = pending.issue(ALICE, spec(), handler);
            now += 1_000L;
            assertTrue(pending.claim(token, ALICE).isEmpty());
        }

        @Test
        @DisplayName("claiming an expired token cancels it, so the handler still resolves")
        void expiredClaimCancels() {
            String token = pending.issue(ALICE, spec(), handler);
            now += 1_000L;
            pending.claim(token, ALICE);
            assertEquals(0, pending.size());
            assertFalse(pending.isKnown(token));
        }

        @Test
        @DisplayName("sweep only takes expired entries")
        void sweepIsSelective() {
            pending.issue(ALICE, spec(), handler);
            now += 500L;
            pending.issue(ALICE, spec(), handler);
            now += 600L;
            assertEquals(1, pending.sweepExpired().size());
            assertEquals(1, pending.size());
        }

        @Test
        @DisplayName("quit cleanup only takes that player's dialogs")
        void quitIsScoped() {
            pending.issue(ALICE, spec(), handler);
            pending.issue(MALLORY, spec(), handler);
            assertEquals(1, pending.cancelAllFor(ALICE).size());
            assertEquals(1, pending.size());
        }

        @Test
        @DisplayName("cancelAll drains everything, so a reload leaves no handler dangling")
        void cancelAllDrains() {
            pending.issue(ALICE, spec(), handler);
            pending.issue(MALLORY, spec(), handler);
            assertEquals(2, pending.cancelAll().size());
            assertEquals(0, pending.size());
        }
    }
}
