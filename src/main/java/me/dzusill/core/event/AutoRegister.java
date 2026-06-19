package me.dzusill.core.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a listener as eligible for automatic registration. The {@link ListenerRegistry} can be handed a batch of
 * candidate listeners and will only register those carrying this annotation, letting plugins declare intent on the
 * class rather than maintaining a separate wiring list.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AutoRegister {
}
