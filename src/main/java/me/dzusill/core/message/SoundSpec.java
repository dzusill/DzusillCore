package me.dzusill.core.message;

import java.util.Locale;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * A sound to play alongside a message.
 *
 * <p>
 * The name is kept as a string and resolved when it is played, rather than parsed into {@link Sound} at load. Sound
 * constants come and go between Minecraft versions, and a server owner writing a name this build has never heard of
 * should get silence, not a stack trace in the middle of a teleport.
 * </p>
 *
 * @param name
 *            a namespaced key ({@code entity.villager.no}) or an enum constant ({@code ENTITY_VILLAGER_NO}); both are
 *            accepted because both appear in the wild and neither is obviously the right one to demand
 */
public record SoundSpec(String name, float volume, float pitch) {

    /** No sound. Distinct from a misconfigured one, which is also silent but worth a different explanation. */
    public static final SoundSpec NONE = new SoundSpec("", 1f, 1f);

    public boolean silent() {
        return name == null || name.isBlank();
    }

    /**
     * Plays this at the player's own location, so it follows them rather than arriving from where they were.
     *
     * <p>
     * Failure is swallowed on purpose. A wrong sound name is a config typo, and the message it accompanies is the part
     * that matters — dropping that as well would turn a cosmetic mistake into a functional one.
     * </p>
     */
    public void play(Player player) {
        if (player == null || silent()) {
            return;
        }
        try {
            player.playSound(player.getLocation(), resolved(), volume, pitch);
        } catch (IllegalArgumentException | NullPointerException unknownSound) {
            // A name this server does not have. Silence is the right outcome.
        }
    }

    /**
     * The name in the form {@code playSound} wants.
     *
     * <p>
     * The string overload takes a namespaced key, so {@code ENTITY_VILLAGER_NO} — how the enum spells it, and how half
     * the configs on the internet spell it — is folded to {@code entity.villager.no} rather than rejected.
     * </p>
     */
    private String resolved() {
        String trimmed = name.trim();
        return trimmed.indexOf('_') >= 0 && trimmed.indexOf('.') < 0
                ? trimmed.toLowerCase(Locale.ROOT).replace('_', '.')
                : trimmed.toLowerCase(Locale.ROOT);
    }
}
