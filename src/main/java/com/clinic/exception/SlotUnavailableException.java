package com.clinic.exception;

import java.time.LocalDateTime;
import java.util.List;

/** The requested slot is taken or otherwise unavailable; the system suggests alternatives. */
public class SlotUnavailableException extends ClinicException {

    private final List<LocalDateTime> suggestions;

    public SlotUnavailableException(String message, List<LocalDateTime> suggestions) {
        super(message);
        this.suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    public List<LocalDateTime> getSuggestions() {
        return suggestions;
    }

    public boolean hasSuggestions() {
        return !suggestions.isEmpty();
    }
}