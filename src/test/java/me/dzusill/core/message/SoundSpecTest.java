package me.dzusill.core.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import be.seeseemelk.mockbukkit.MockBukkit;

class SoundSpecTest {

    @BeforeAll
    static void server() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stop() {
        MockBukkit.unmock();
    }

    private static String resolved(String name) {
        return new SoundSpec(name, 1f, 1f).resolved();
    }

    @Test
    void enumNameWithUnderscoreInTheKeyResolvesThroughTheRegistry() {
        // The client's config: folding every _ to . gave entity.experience.orb.pickup, which plays nothing.
        assertEquals("entity.experience_orb.pickup", resolved("ENTITY_EXPERIENCE_ORB_PICKUP"));
        assertEquals("block.note_block.bass", resolved("BLOCK_NOTE_BLOCK_BASS"));
    }

    @Test
    void plainEnumNamesStillWork() {
        assertEquals("entity.villager.no", resolved("ENTITY_VILLAGER_NO"));
        assertEquals("entity.villager.no", resolved("entity_villager_no"));
    }

    @Test
    void keysAreKeptAsWritten() {
        assertEquals("entity.experience_orb.pickup", resolved("entity.experience_orb.pickup"));
        assertEquals("minecraft:entity.player.levelup", resolved("minecraft:entity.player.levelup"));
        assertEquals("custom.my_pack_sound", resolved("custom.my_pack_sound"));
    }

    @Test
    void unknownEnumNameFallsBackToTheFold() {
        assertEquals("not.a.sound", resolved("NOT_A_SOUND"));
    }
}
