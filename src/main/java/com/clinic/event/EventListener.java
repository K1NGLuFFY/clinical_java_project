package com.clinic.event;

/** Observer contract: listeners receive every event published on the bus. */
@FunctionalInterface
public interface EventListener {

    void onEvent(ClinicEvent event);
}
