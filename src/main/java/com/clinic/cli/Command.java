package com.clinic.cli;

import com.clinic.exception.ClinicException;
import com.clinic.model.Role;
import java.util.List;
import java.util.Set;

/**
 * One console command. Implementations only parse arguments, call the service layer, format
 * results and print them; every business rule lives in a service.
 */
public interface Command {

    /** Canonical command name, for example {@code waitlist join}. */
    String name();

    /** Alternative names, for example {@code quit} for {@code exit}; may be empty. */
    List<String> aliases();

    /**
     * Roles allowed to run the command. An empty set means "no login required" (login, help,
     * exit); every other command requires a logged-in user with one of the listed roles.
     */
    Set<Role> allowedRoles();

    /** Canonical syntax shown by help and appended to "missing argument" errors. */
    String usage();

    /** One short sentence shown next to the usage line in help. */
    String summary();

    /**
     * Runs the command.
     *
     * @throws ClinicException when the command cannot be completed; the console prints the
     *                         message without a stack trace
     */
    void execute(CommandContext ctx, ParsedCommand command) throws ClinicException;
}
