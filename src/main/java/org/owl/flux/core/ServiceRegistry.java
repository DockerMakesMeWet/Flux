package org.owl.flux.core;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ServiceRegistry {
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    public <T> void register(Class<T> type, T instance) {
        services.put(type, type.cast(instance));
    }

    public <T> Optional<T> find(Class<T> type) {
        Object value = services.get(type);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(type.cast(value));
    }

    public <T> T require(Class<T> type) {
        return find(type)
                .orElseThrow(() -> new IllegalStateException("Missing required service: " + type.getName()));
    }

    public void clear() {
        services.clear();
    }
}
