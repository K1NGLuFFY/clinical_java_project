package com.clinic.cli;

import com.clinic.exception.ClinicException;
import com.clinic.exception.InvalidInputException;
import com.clinic.model.User;
import java.util.ArrayList;
import java.util.List;

/**
 * Commands that manage the console session itself: {@code login}, {@code logout},
 * {@code help} and {@code exit}.
 */
final class LoginCommand extends CommandSupport {

    LoginCommand() {
        super("login", NO_ROLES, "login <userId> <password>",
                "Sign in with your user id and password", "signin");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        if (ctx.isLoggedIn()) {
            throw new InvalidInputException("You are already logged in as "
                    + ctx.getCurrentUser().getDisplayName() + ". Type logout first.");
        }
        int userId = requireInt(command, 0, "userId");
        String password = requirePositional(command, 1, "password");
        User user = ctx.auth().login(userId, password);
        ctx.setCurrentUser(user);
        System.out.println("Welcome, " + user.getDisplayName() + "!");
        int unread = ctx.inbox().unreadCount(user.getId());
        System.out.println("You have " + unread + " unread message(s). Type INBOX.");
    }
}

/** Clears the logged-in user of the console session. */
final class LogoutCommand extends CommandSupport {

    LogoutCommand() {
        super("logout", ALL_ROLES, "logout", "Sign out of the clinic system", "signout");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        ctx.requireLogin();
        ctx.setCurrentUser(null);
        System.out.println("Logged out.");
    }
}

/** Lists the commands the current (or anonymous) user is allowed to run. */
final class HelpCommand extends CommandSupport {

    private final CommandRegistry registry;

    HelpCommand(CommandRegistry registry) {
        super("help", NO_ROLES, "help", "Show the commands available to you", "?");
        this.registry = registry;
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) {
        User user = ctx.getCurrentUser();
        List<Command> visible = new ArrayList<>(registry.visibleTo(user));
        if (user != null) {
            visible.removeIf(candidate -> "login".equals(candidate.name()));
            System.out.println("Available commands for the " + user.getRole() + " role:");
        } else {
            System.out.println("Available commands:");
        }
        int width = 0;
        for (Command candidate : visible) {
            width = Math.max(width, candidate.usage().length());
        }
        for (Command candidate : visible) {
            System.out.println("  " + pad(candidate.usage(), width) + "  " + candidate.summary());
        }
        if (user == null) {
            System.out.println();
            System.out.println("Login first, for example: login 1 admin123");
        }
    }

    private static String pad(String value, int width) {
        return value + " ".repeat(Math.max(0, width - value.length()));
    }
}

/** Ends the console loop; the console itself prints the goodbye line. */
final class ExitCommand extends CommandSupport {

    ExitCommand() {
        super("exit", NO_ROLES, "exit", "Close the clinic console", "quit", "bye");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) {
        // Nothing to do: the console loop stops when it sees the exit command.
    }
}
