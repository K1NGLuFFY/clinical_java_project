package com.clinic.cli;

import com.clinic.exception.InvalidInputException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One parsed console line: the resolved command name, its positional arguments and the
 * {@code --key=value} / {@code --key value} options.
 *
 * <p>The parser resolves multi-word command names (for example {@code visit note}) through the
 * {@link CommandRegistry}, so {@link #name()} is always the canonical command name when the
 * registry recognises it. Quoted arguments become a single positional value.</p>
 */
public final class ParsedCommand {

    private final String name;
    private final List<String> positionals;
    private final Map<String, String> flags;

    public ParsedCommand(String name, List<String> positionals, Map<String, String> flags) {
        this.name = name == null ? "" : name;
        this.positionals = Collections.unmodifiableList(
                new ArrayList<>(positionals == null ? List.of() : positionals));
        Map<String, String> copy = new LinkedHashMap<>();
        if (flags != null) {
            copy.putAll(flags);
        }
        this.flags = Collections.unmodifiableMap(copy);
    }

    /** Canonical command name, for example {@code waitlist join}. */
    public String name() {
        return name;
    }

    /** Positional arguments in the order they were typed, without the command words. */
    public List<String> positionals() {
        return positionals;
    }

    /** Options by name (without the leading {@code --}). */
    public Map<String, String> flags() {
        return flags;
    }

    /** The positional at {@code index}, or {@code null} when it was not supplied. */
    public String positional(int index) {
        if (index < 0 || index >= positionals.size()) {
            return null;
        }
        return positionals.get(index);
    }

    /**
     * The positional at {@code index}; fails with a friendly message when it is missing.
     */
    public String requiredPositional(int index, String label) throws InvalidInputException {
        String value = positional(index);
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("Missing required argument <" + label + ">.");
        }
        return value;
    }

    /** The value of {@code --name}, or {@code null} when the option was not supplied. */
    public String flag(String name) {
        return flags.get(name);
    }

    /** True when the option was supplied, even with an empty value. */
    public boolean hasFlag(String name) {
        return flags.containsKey(name);
    }

    /** The value of {@code --name}; fails with a friendly message when the option is missing. */
    public String requiredFlag(String name, String label) throws InvalidInputException {
        String value = flags.get(name);
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("Missing required option --" + name + "=<" + label + ">.");
        }
        return value;
    }

    @Override
    public String toString() {
        return name + " " + positionals + " " + flags;
    }
}
