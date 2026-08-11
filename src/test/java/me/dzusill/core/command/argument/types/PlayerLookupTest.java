package me.dzusill.core.command.argument.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;

/**
 * Typing the start of a name, the way staff actually use a teleport command.
 *
 * <p>
 * The ambiguity rule is the one with teeth: these lookups sit behind {@code /tp} and {@code /tphere}, so guessing
 * between two candidates moves the wrong player and nobody notices.
 * </p>
 */
class PlayerLookupTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aWholeNameResolves() {
        server.addPlayer("elz1one");

        assertEquals("elz1one", PlayerLookup.online("elz1one").orElseThrow().getName());
    }

    @Test
    void theStartOfANameResolves() {
        server.addPlayer("elz1one");

        assertEquals("elz1one", PlayerLookup.online("elz").orElseThrow().getName());
    }

    @Test
    void caseDoesNotMatter() {
        server.addPlayer("elz1one");

        assertEquals("elz1one", PlayerLookup.online("ELZ").orElseThrow().getName());
    }

    @Test
    void anExactNameBeatsALongerOneItIsAPrefixOf() {
        // Somebody called "el" must stay reachable while "elz1one" is online.
        server.addPlayer("el");
        server.addPlayer("elz1one");

        assertEquals("el", PlayerLookup.online("el").orElseThrow().getName());
    }

    @Test
    void anAmbiguousFragmentResolvesToNothing() {
        server.addPlayer("elz1one");
        server.addPlayer("elzabeth");

        assertTrue(PlayerLookup.online("elz").isEmpty(), "guessing here teleports the wrong player");
    }

    @Test
    void anAmbiguousFragmentNamesItsCandidates() {
        server.addPlayer("elz1one");
        server.addPlayer("elzabeth");

        assertEquals("elz1one, elzabeth", PlayerLookup.names(PlayerLookup.ambiguous("elz")));
    }

    @Test
    void anExactMatchIsNeverReportedAsAmbiguous() {
        server.addPlayer("el");
        server.addPlayer("elz1one");

        assertTrue(PlayerLookup.ambiguous("el").isEmpty());
    }

    @Test
    void aFragmentMatchingNobodyResolvesToNothing() {
        server.addPlayer("elz1one");

        assertTrue(PlayerLookup.online("steve").isEmpty());
        assertTrue(PlayerLookup.ambiguous("steve").isEmpty(), "nothing matched, so there is nothing to disambiguate");
    }

    @Test
    void onlyThePrefixCountsNotTheMiddleOfAName() {
        server.addPlayer("elz1one");

        assertTrue(PlayerLookup.online("1one").isEmpty(), "matching inside a name would surprise people");
    }

    @Test
    void blankAndNullResolveToNothing() {
        server.addPlayer("elz1one");

        assertTrue(PlayerLookup.online("").isEmpty());
        assertTrue(PlayerLookup.online(null).isEmpty());
        assertFalse(PlayerLookup.online(" ").isPresent());
    }
}
