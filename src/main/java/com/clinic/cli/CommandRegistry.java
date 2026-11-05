package com.clinic.cli;

import com.clinic.model.Role;
import com.clinic.model.User;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The command catalogue. Resolution is "longest multi-word name first", so {@code waitlist join}
 * wins over the {@code waitlist} alias and {@code report daily} wins over {@code report}.
 */
public final class CommandRegistry {

    /** A command matched from the leading words of a parsed line. */
    public record Resolved(Command command, int wordCount) {
    }

    private final List<Command> commands = new ArrayList<>();
    private final Map<String, Command> index = new LinkedHashMap<>();

    public CommandRegistry() {
        registerDefaults();
    }

    /** Registers one command under its canonical name and every alias. */
    public void register(Command command) {
        Objects.requireNonNull(command, "command");
        commands.add(command);
        addKey(command.name(), command);
        for (String alias : command.aliases()) {
            addKey(alias, command);
        }
    }

    private void addKey(String key, Command command) {
        if (key == null || key.isBlank()) {
            return;
        }
        String normalised = key.trim().toLowerCase(Locale.ROOT);
        Command existing = index.putIfAbsent(normalised, command);
        if (existing != null && existing != command) {
            throw new IllegalStateException("Duplicate command name or alias: " + normalised);
        }
    }

    /** Resolves the longest registered name or alias at the start of {@code name}. */
    public Optional<Command> find(String name) {
        Resolved resolved = resolve(name);
        return resolved == null ? Optional.empty() : Optional.of(resolved.command());
    }

    /** Resolves a command and reports how many leading words its name used. */
    public Resolved resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String[] words = name.trim().toLowerCase(Locale.ROOT).split("\\s+");
        for (int count = words.length; count >= 1; count--) {
            String candidate = String.join(" ", Arrays.copyOfRange(words, 0, count));
            Command command = index.get(candidate);
            if (command != null) {
                return new Resolved(command, count);
            }
        }
        return null;
    }

    /** Commands a user may run, sorted by canonical name; {@code null} means logged out. */
    public List<Command> visibleTo(User user) {
        List<Command> visible = new ArrayList<>();
        for (Command command : commands) {
            if (command.allowedRoles().isEmpty()) {
                visible.add(command);
            } else if (user != null && command.allowedRoles().contains(user.getRole())) {
                visible.add(command);
            }
        }
        visible.sort(Comparator.comparing(Command::name));
        return visible;
    }

    private void registerDefaults() {
        register(new LoginCommand());
        register(new LogoutCommand());
        register(new HelpCommand(this));
        register(new ExitCommand());
        register(new InboxCommand());
        register(new SlotsCommand());
        register(new BookCommand());
        register(new RescheduleCommand());
        register(new CancelCommand());
        register(new MyAppointmentsCommand());
        register(new WaitlistJoinCommand());
        register(new WaitlistAcceptCommand());
        register(new WaitlistLeaveCommand());
        register(new WaitlistDeclineCommand());
        register(new WaitlistListCommand());
        register(new PatientAddCommand());
        register(new PatientUnblockCommand());
        register(new PatientListCommand());
        register(new DoctorAddCommand());
        register(new DoctorScheduleCommand());
        register(new DoctorLeaveCommand());
        register(new DoctorListCommand());
        register(new CheckInCommand());
        register(new CompleteCommand());
        register(new NoShowCommand());
        register(new VisitNoteCommand());
        register(new HistoryCommand());
        register(new ReportDailyCommand());
        register(new ReportUtilisationCommand());
        register(new ReportNoShowCommand());
        register(new ReportRevenueCommand());
    }
}
