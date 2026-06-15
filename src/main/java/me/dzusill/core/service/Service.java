package me.dzusill.core.service;

/**
 * Marker interface for any component that can be registered in the {@link ServiceRegistry}
 * and resolved by other parts of the plugin without a hard compile-time dependency.
 *
 * <p>Services are the primary mechanism for decoupling subsystems: a module publishes the
 * services it owns, and other modules look them up by type. This keeps the dependency graph
 * explicit and testable.</p>
 */
public interface Service {
}
