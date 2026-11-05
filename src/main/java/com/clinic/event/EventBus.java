package com.clinic.event;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Synchronous observer bus. A failing listener must never break a booking, so listener
 * exceptions are reported to stderr and swallowed.
 */
public class EventBus {

    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

    public void register(EventListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void unregister(EventListener listener) {
        listeners.remove(listener);
    }

    public void publish(ClinicEvent event) {
        if (event == null) {
            return;
        }
        for (EventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException ex) {
                System.err.println("[event] listener failed for " + event.getType() + ": " + ex.getMessage());
            }
        }
    }

    public int listenerCount() {
        return listeners.size();
    }
}
