package me.dzusill.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import be.seeseemelk.mockbukkit.MockBukkit;

/**
 * Real player skins, not just head-database ones.
 *
 * <p>
 * Mojang's textures value is pretty-printed JSON, {@code "url" : "..."}. The old parse looked for {@code "url":"} with
 * no spaces, missed every real skin, and fell through to the authlib path that Paper 26.3 broke — the OberonSusMenu
 * list stopped opening as soon as one real player was on it.
 * </p>
 */
class SkullTexturesTest {

    private static final String SKIN = "http://textures.minecraft.net/texture/"
            + "1a4af718455d4aab528e7a61f86fa25e6a369d1768dcb13f7df319a713eb810b";

    /** How Mojang's session server writes it. */
    private static final String MOJANG = base64("{\n  \"timestamp\" : 1792238455511,\n  \"profileId\" : "
            + "\"516909b1f17048f9aa7739553553ba3d\",\n  \"profileName\" : \"joerg327\",\n  \"textures\" : {\n    "
            + "\"SKIN\" : {\n      \"url\" : \"" + SKIN + "\"\n    }\n  }\n}");

    /** How head databases write it. */
    private static final String COMPACT = base64("{\"textures\":{\"SKIN\":{\"url\":\"" + SKIN + "\"}}}");

    private static String base64(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void readsTheUrlFromAMojangTexture() {
        assertEquals(SKIN, SkullTextures.extractSkinUrl(MOJANG).toString());
    }

    @Test
    void readsTheUrlFromACompactTexture() {
        assertEquals(SKIN, SkullTextures.extractSkinUrl(COMPACT).toString());
    }

    @Test
    void garbageHasNoUrl() {
        assertNull(SkullTextures.extractSkinUrl("not base64 at all"));
        assertNull(SkullTextures.extractSkinUrl(base64("{\"textures\":{}}")));
    }

    @Test
    void aRealPlayersSkinMakesAHead() {
        ItemStack head = ItemBuilder.head(MOJANG).build();

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        assertTrue(meta.hasOwner() || meta.getPlayerProfile() != null, "the head carries no profile");
    }
}
