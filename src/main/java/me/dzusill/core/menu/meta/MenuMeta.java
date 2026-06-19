package me.dzusill.core.menu.meta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a menu's title, size and access rule without boilerplate, the GUI analogue of
 * {@link me.dzusill.core.command.meta.CommandMeta}. A {@link me.dzusill.core.menu.Menu} annotated with this no longer
 * needs to override {@code title()} / {@code size()}; the framework reads the values reflectively in the {@code Menu}
 * constructor.
 *
 * <pre>{@code
 * &#64;MenuMeta(title = "<dark_purple>Example Shop", size = 27, permission = "core.shop")
 * public final class ShopMenu extends Menu { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MenuMeta {

    /** Inventory title as a MiniMessage string; parsed via {@code ColorUtils.parse}. */
    String title();

    /** Inventory size in slots (a multiple of 9). */
    int size();

    /** Permission node required to open through the registry; empty means no check. */
    String permission() default "";
}
