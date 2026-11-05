package com.clinic.cli;

import com.clinic.event.NotificationInbox;
import com.clinic.exception.UnauthorizedActionException;
import com.clinic.model.User;
import com.clinic.service.AuthService;
import com.clinic.service.BookingService;
import com.clinic.service.ReportService;
import com.clinic.service.SchedulingService;
import com.clinic.service.VisitService;
import com.clinic.service.WaitlistService;
import java.time.Clock;
import java.util.Objects;

/**
 * Everything a command needs: the eight wired services plus the login state of this console
 * session. The state lives here so a command never has to be a singleton or keep a static user.
 */
public final class CommandContext {

    private final AuthService auth;
    private final SchedulingService scheduling;
    private final BookingService booking;
    private final WaitlistService waitlist;
    private final VisitService visits;
    private final ReportService reports;
    private final NotificationInbox inbox;
    private final Clock clock;

    private User currentUser;

    public CommandContext(AuthService auth, SchedulingService scheduling, BookingService booking,
                          WaitlistService waitlist, VisitService visits, ReportService reports,
                          NotificationInbox inbox, Clock clock) {
        this.auth = Objects.requireNonNull(auth, "auth");
        this.scheduling = Objects.requireNonNull(scheduling, "scheduling");
        this.booking = Objects.requireNonNull(booking, "booking");
        this.waitlist = Objects.requireNonNull(waitlist, "waitlist");
        this.visits = Objects.requireNonNull(visits, "visits");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The user logged in on this console, or {@code null}. */
    public User getCurrentUser() {
        return currentUser;
    }

    /** Sets the logged-in user; {@code null} means logged out. */
    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    /** Fails with a friendly login hint when nobody is logged in. */
    public void requireLogin() throws UnauthorizedActionException {
        if (currentUser == null) {
            throw new UnauthorizedActionException(
                    "Please login first (login <userId> <password>).");
        }
    }

    public AuthService auth() {
        return auth;
    }

    public SchedulingService scheduling() {
        return scheduling;
    }

    public BookingService booking() {
        return booking;
    }

    public WaitlistService waitlist() {
        return waitlist;
    }

    public VisitService visits() {
        return visits;
    }

    public ReportService reports() {
        return reports;
    }

    public NotificationInbox inbox() {
        return inbox;
    }

    public Clock clock() {
        return clock;
    }
}
