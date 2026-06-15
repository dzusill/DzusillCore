package me.dzusill.core.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A minimal, type-keyed service locator. Modules register the services they own and resolve
 * services owned by other modules through this registry, avoiding direct references between
 * subsystems.
 *
 * <p>This is intentionally simple (no scopes, no proxies): the goal is decoupling and
 * testability, not a full dependency-injection container.</p>
 */
public final class ServiceRegistry {

    private final Map<Class<?>, Service> services = new LinkedHashMap<>();

    /**
     * Registers a service under the given contract type.
     *
     * @param type    the interface or class other components will look the service up by
     * @param service the implementation instance
     * @param <T>     the service contract type
     * @throws IllegalStateException if a service is already registered for {@code type}
     */
    public <T extends Service> void register(Class<T> type, T service) {
        if (services.containsKey(type)) {
            throw new IllegalStateException("Service already registered for type " + type.getName());
        }
        services.put(type, service);
    }

    /**
     * Resolves a required service, failing fast if it is missing.
     *
     * @throws IllegalStateException if no service is registered for {@code type}
     */
    public <T extends Service> T get(Class<T> type) {
        return find(type).orElseThrow(() ->
                new IllegalStateException("No service registered for type " + type.getName()));
    }

    /**
     * Resolves an optional service without throwing when absent.
     */
    public <T extends Service> Optional<T> find(Class<T> type) {
        return Optional.ofNullable(type.cast(services.get(type)));
    }

    /**
     * @return {@code true} if a service is registered for the given type
     */
    public boolean has(Class<? extends Service> type) {
        return services.containsKey(type);
    }

    /**
     * Removes all registered services. Called during plugin shutdown.
     */
    public void clear() {
        services.clear();
    }
}
