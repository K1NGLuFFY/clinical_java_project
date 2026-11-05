package com.clinic.cli;

import com.clinic.exception.ClinicException;
import com.clinic.model.Role;
import com.clinic.model.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The REPL of the clinic console. It owns the input loop, the login-aware prompt, the role gate
 * and the one place that turns exceptions into friendly lines; all business rules stay in the
 * services.
 */
public final class ConsoleUI {

    private static final String LOGIN_HINT = "Please login first (login <userId> <password>).";

    private final CommandContext context;
    private final CommandRegistry registry;
    private final CommandParser parser;
    /** When input is piped (no console) the prompt is suppressed so scripted output stays clean. */
    private final boolean interactive = System.console() != null;

    public ConsoleUI(CommandContext context) {
        this.context = Objects.requireNonNull(context, "context");
        this.registry = new CommandRegistry();
        this.parser = new CommandParser(registry);
    }

    /** Reads commands until {@code exit} or end of input, then prints the goodbye line. */
    public void run() {
        expireWaitlistOffers();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            while (true) {
                if (interactive) {
                    System.out.print(prompt());
                    System.out.flush();
                }
                String line;
                try {
                    line = reader.readLine();
                } catch (IOException ex) {
                    System.out.println();
                    break;
                }
                if (line == null) {
                    System.out.println();
                    break;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue; // blank lines and script comments (demo/*.txt) are ignored
                }
                if (!handle(line)) {
                    break;
                }
            }
        } catch (IOException ex) {
            System.out.println("Unexpected error: " + ex.getMessage());
        }
        System.out.println("Goodbye.");
    }

    /** Handles one line; returns {@code false} when the console must stop. */
    private boolean handle(String line) {
        ParsedCommand parsed;
        try {
            parsed = parser.parse(line);
        } catch (ClinicException ex) {
            System.out.println("Error: " + ex.getMessage());
            return true;
        } catch (RuntimeException ex) {
            printUnexpected(ex);
            return true;
        }
        Optional<Command> found = registry.find(parsed.name());
        if (found.isEmpty()) {
            System.out.println("Unknown command '" + parsed.name()
                    + "'. Type help to see the available commands.");
            return true;
        }
        Command command = found.get();
        if ("exit".equals(command.name())) {
            return false;
        }
        if (!isAllowed(command)) {
            return true;
        }
        try {
            expireWaitlistOffers();
            command.execute(context, parsed);
        } catch (ClinicException ex) {
            System.out.println("Error: " + ex.getMessage());
        } catch (RuntimeException ex) {
            printUnexpected(ex);
        }
        return true;
    }

    /** Registry role gate, run before the command body. */
    private boolean isAllowed(Command command) {
        Set<Role> roles = command.allowedRoles();
        if (roles.isEmpty()) {
            return true;
        }
        User user = context.getCurrentUser();
        if (user == null) {
            System.out.println(LOGIN_HINT);
            return false;
        }
        if (!roles.contains(user.getRole())) {
            System.out.println("This command is not available for the "
                    + user.getRole().name() + " role.");
            return false;
        }
        return true;
    }

    private String prompt() {
        User user = context.getCurrentUser();
        if (user == null) {
            return "clinic> ";
        }
        return "clinic[" + user.getRole().name() + "]> ";
    }

    /** Expires overdue waiting-list offers lazily, as the brief requires; never blocks the console. */
    private void expireWaitlistOffers() {
        try {
            context.waitlist().expireOffers();
        } catch (ClinicException | RuntimeException ex) {
            // A failing housekeeping call must never close the console; the command still runs.
        }
    }

    private static void printUnexpected(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        System.out.println("Unexpected error: " + message);
    }
}
