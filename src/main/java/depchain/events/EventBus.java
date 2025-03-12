package depchain.events;

import depchain.config.DepChainConfig;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class EventBus {
    private final ConcurrentMap<Class<? extends Event>, List<Consumer<? extends Event>>> listeners =
            new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final DepChainConfig config;

    public EventBus(DepChainConfig config) {
        this.config = config;
    }

    public <E extends Event> void register(Class<E> eventType, Consumer<E> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public <E extends Event> void unregister(Class<E> eventType, Consumer<E> listener) {
        listeners.computeIfPresent(eventType, (k, v) -> {
            v.removeIf(l -> l == listener);
            return v.isEmpty() ? null : v;
        });
    }

    public <E extends Event> void publish(E event) {
        List<Consumer<? extends Event>> eventListeners = this.listeners.getOrDefault(event.getClass(),
                Collections.emptyList());
        for (Consumer<? extends Event> listener : eventListeners) {
            @SuppressWarnings("unchecked")
            Consumer<E> consumer = (Consumer<E>) listener;

            executor.submit(() -> consumer.accept(event));
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
