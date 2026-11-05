package com.clinic.cli;

import com.clinic.exception.InvalidInputException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns one raw console line into a {@link ParsedCommand}.
 *
 * <p>The tokeniser splits on whitespace, treats a double-quoted section as one argument
 * (so {@code visit note 5 "patient reports headache"} has the note text as a single
 * positional) and understands options in both {@code --key=value} and {@code --key value}
 * forms anywhere on the line. An option echo of {@code \"} inside a quoted section is the
 * only escape; everything else is literal.</p>
 *
 * <p>The parser never throws anything except {@link InvalidInputException}, which the console
 * loop turns into a friendly {@code Error: ...} line.</p>
 */
public final class CommandParser {

    /** Registry used to resolve multi-word command names and aliases. */
    private final CommandRegistry registry;

    /** Creates a parser with its own command catalogue (used by tests and standalone callers). */
    public CommandParser() {
        this(new CommandRegistry());
    }

    /** Creates a parser that resolves names through the given registry. */
    public CommandParser(CommandRegistry registry) {
        this.registry = java.util.Objects.requireNonNull(registry, "registry");
    }

    /**
     * Parses one line.
     *
     * @param line the raw console line
     * @return the parsed command
     * @throws InvalidInputException when the line is empty, a quote is unterminated or an
     *                               option has no name
     */
    public ParsedCommand parse(String line) throws InvalidInputException {
        if (line == null || line.trim().isEmpty()) {
            throw new InvalidInputException("Please type a command. Type \"help\" to see the command list.");
        }
        List<String> words = new ArrayList<>();
        Map<String, String> flags = new LinkedHashMap<>();
        int index = 0;
        while (index < line.length()) {
            char current = line.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (current == '"') {
                StringBuilder quoted = new StringBuilder();
                index = readQuoted(line, index, quoted);
                words.add(quoted.toString());
                continue;
            }
            if (line.startsWith("--", index)) {
                index = readFlag(line, index, flags);
                continue;
            }
            int start = index;
            while (index < line.length() && !Character.isWhitespace(line.charAt(index))) {
                index++;
            }
            words.add(line.substring(start, index));
        }
        if (words.isEmpty()) {
            throw new InvalidInputException(
                    "A command is required before any option. Type \"help\" to see the command list.");
        }

        String name = words.get(0);
        int consumed = 1;
        CommandRegistry.Resolved resolved = registry.resolve(String.join(" ", words));
        if (resolved != null) {
            name = resolved.command().name();
            consumed = resolved.wordCount();
        }
        List<String> positionals = consumed >= words.size()
                ? List.of()
                : words.subList(consumed, words.size());
        return new ParsedCommand(name, positionals, flags);
    }

    /** Reads a quoted section starting at {@code index}; returns the index after the closing quote. */
    private static int readQuoted(String line, int index, StringBuilder out) throws InvalidInputException {
        StringBuilder value = new StringBuilder();
        int cursor = index + 1;
        while (cursor < line.length()) {
            char current = line.charAt(cursor);
            if (current == '\\' && cursor + 1 < line.length() && line.charAt(cursor + 1) == '"') {
                value.append('"');
                cursor += 2;
                continue;
            }
            if (current == '"') {
                out.append(value);
                return cursor + 1;
            }
            value.append(current);
            cursor++;
        }
        throw new InvalidInputException(
                "Unterminated quote in the input. Close it with a second double quote.");
    }

    /** Reads one {@code --key=value} or {@code --key value} option; returns the next index. */
    private static int readFlag(String line, int index, Map<String, String> flags) throws InvalidInputException {
        int cursor = index + 2;
        if (cursor >= line.length() || Character.isWhitespace(line.charAt(cursor))) {
            throw new InvalidInputException("Missing option name after \"--\".");
        }
        StringBuilder key = new StringBuilder();
        while (cursor < line.length()
                && !Character.isWhitespace(line.charAt(cursor))
                && line.charAt(cursor) != '=') {
            key.append(line.charAt(cursor));
            cursor++;
        }
        String flagName = key.toString().trim().toLowerCase(Locale.ROOT);
        if (flagName.isEmpty()) {
            throw new InvalidInputException("Missing option name after \"--\".");
        }
        String value = "";
        if (cursor < line.length() && line.charAt(cursor) == '=') {
            cursor++;
            if (cursor < line.length() && line.charAt(cursor) == '"') {
                StringBuilder quoted = new StringBuilder();
                cursor = readQuoted(line, cursor, quoted);
                value = quoted.toString();
            } else {
                int start = cursor;
                while (cursor < line.length() && !Character.isWhitespace(line.charAt(cursor))) {
                    cursor++;
                }
                value = line.substring(start, cursor);
            }
        } else {
            int afterKey = cursor;
            while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
                cursor++;
            }
            if (cursor < line.length() && line.charAt(cursor) == '"') {
                StringBuilder quoted = new StringBuilder();
                cursor = readQuoted(line, cursor, quoted);
                value = quoted.toString();
            } else if (cursor < line.length() && !line.startsWith("--", cursor)) {
                int start = cursor;
                while (cursor < line.length() && !Character.isWhitespace(line.charAt(cursor))) {
                    cursor++;
                }
                value = line.substring(start, cursor);
            } else {
                cursor = afterKey;
            }
        }
        flags.put(flagName, value);
        return cursor;
    }
}
