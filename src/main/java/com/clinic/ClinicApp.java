package com.clinic;

import com.clinic.cli.CommandContext;
import com.clinic.event.ActivityLog;
import com.clinic.event.EventBus;
import com.clinic.event.NotificationInbox;
import com.clinic.exception.ClinicException;
import com.clinic.exception.StorageException;
import com.clinic.factory.PricingPolicyFactory;
import com.clinic.repository.AppointmentRepository;
import com.clinic.repository.JsonActivityLogRepository;
import com.clinic.repository.JsonAppointmentRepository;
import com.clinic.repository.JsonNotificationRepository;
import com.clinic.repository.JsonSupport;
import com.clinic.repository.JsonUserRepository;
import com.clinic.repository.JsonVisitRecordRepository;
import com.clinic.repository.JsonWaitlistRepository;
import com.clinic.repository.NotificationRepository;
import com.clinic.repository.SequenceStore;
import com.clinic.repository.UserRepository;
import com.clinic.repository.VisitRecordRepository;
import com.clinic.repository.WaitlistRepository;
import com.clinic.scheduling.SlotFinder;
import com.clinic.service.AuthService;
import com.clinic.service.BillingService;
import com.clinic.service.BookingService;
import com.clinic.service.ReportService;
import com.clinic.service.SchedulingService;
import com.clinic.service.VisitService;
import com.clinic.service.WaitlistService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;

/**
 * Manual dependency injection: builds the repository implementations, services, event bus and
 * CLI context for one data folder. Used by {@link Main} and by the self-test harness.
 */
public final class ClinicApp {

    public final Path dataDirectory;
    public final Clock clock;
    public final UserRepository users;
    public final AppointmentRepository appointments;
    public final VisitRecordRepository visits;
    public final WaitlistRepository waitlist;
    public final NotificationRepository notifications;
    public final EventBus events;
    public final NotificationInbox inbox;
    public final AuthService auth;
    public final SchedulingService scheduling;
    public final BookingService booking;
    public final WaitlistService waitlistService;
    public final VisitService visitService;
    public final ReportService reports;
    public final CommandContext context;
    public final boolean seeded;

    private ClinicApp(Path dataDirectory, Clock clock, UserRepository users,
                      AppointmentRepository appointments, VisitRecordRepository visits,
                      WaitlistRepository waitlist, NotificationRepository notifications,
                      EventBus events, NotificationInbox inbox, AuthService auth,
                      SchedulingService scheduling, BookingService booking,
                      WaitlistService waitlistService, VisitService visitService,
                      ReportService reports, CommandContext context, boolean seeded) {
        this.dataDirectory = dataDirectory;
        this.clock = clock;
        this.users = users;
        this.appointments = appointments;
        this.visits = visits;
        this.waitlist = waitlist;
        this.notifications = notifications;
        this.events = events;
        this.inbox = inbox;
        this.auth = auth;
        this.scheduling = scheduling;
        this.booking = booking;
        this.waitlistService = waitlistService;
        this.visitService = visitService;
        this.reports = reports;
        this.context = context;
        this.seeded = seeded;
    }

    /**
     * Opens (and creates when needed) the clinic data folder, wires every component and seeds
     * the demo data on a fresh folder.
     */
    public static ClinicApp open(Path dataDirectory, Clock clock) throws ClinicException {
        try {
            Files.createDirectories(dataDirectory);
        } catch (IOException ex) {
            throw new StorageException("Cannot create data folder '" + dataDirectory + "': " + ex.getMessage(), ex);
        }

        com.google.gson.Gson gson = JsonSupport.createGson();
        SequenceStore sequences = new SequenceStore(dataDirectory);
        JsonUserRepository users = new JsonUserRepository(dataDirectory, gson, sequences);
        JsonAppointmentRepository appointments = new JsonAppointmentRepository(dataDirectory, gson, sequences);
        JsonWaitlistRepository waitlist = new JsonWaitlistRepository(dataDirectory, gson, sequences);
        JsonVisitRecordRepository visits = new JsonVisitRecordRepository(dataDirectory, gson, sequences);
        JsonNotificationRepository notifications = new JsonNotificationRepository(dataDirectory, gson, sequences);
        JsonActivityLogRepository activityLog = new JsonActivityLogRepository(dataDirectory);

        EventBus events = new EventBus();
        NotificationInbox inbox = new NotificationInbox(notifications);
        events.register(inbox);
        events.register(new ActivityLog(activityLog));

        SlotFinder slotFinder = new SlotFinder(clock);
        PricingPolicyFactory pricingPolicies = new PricingPolicyFactory();
        BillingService billing = new BillingService(pricingPolicies);
        AuthService auth = new AuthService(users, clock);
        SchedulingService scheduling = new SchedulingService(users, appointments, slotFinder, clock);
        WaitlistService waitlistService = new WaitlistService(waitlist, users, appointments, events, clock);
        BookingService booking = new BookingService(users, appointments, waitlist, slotFinder, billing, events, clock);
        VisitService visitService = new VisitService(visits, appointments, users, events, clock);
        ReportService reports = new ReportService(users, appointments, visits, clock);

        booking.setWaitlistService(waitlistService);
        waitlistService.setBookingGateway(booking);

        SeedData seedData = new SeedData(users, appointments, visits, billing, clock);
        boolean seeded = seedData.seedIfEmpty();
        // Safety net for a partially wiped data folder: the brief requires a default receptionist.
        auth.ensureDefaultReceptionist();

        // Expiry is lazy: checked at startup and before every command (no background threads).
        waitlistService.expireOffers();

        CommandContext context = new CommandContext(auth, scheduling, booking, waitlistService,
                visitService, reports, inbox, clock);

        return new ClinicApp(dataDirectory, clock, users, appointments, visits, waitlist,
                notifications, events, inbox, auth, scheduling, booking, waitlistService,
                visitService, reports, context, seeded);
    }
}
