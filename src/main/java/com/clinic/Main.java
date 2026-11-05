package com.clinic;

import com.clinic.cli.ConsoleUI;
import com.clinic.exception.ClinicException;
import com.clinic.util.Formats;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Entry point of the console clinic system.
 *
 * <pre>
 * java -jar target/clinic-app.jar [--data=&lt;folder&gt;] [--now=&lt;yyyy-MM-ddTHH:mm&gt;]
 * </pre>
 *
 * {@code --now} pins the injected clock, which makes the documented demo session reproducible.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = new Main().run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private int run(String[] args) {
        Path dataDirectory = Path.of("data");
        Clock clock = Clock.systemDefaultZone();

        for (String arg : args) {
            if (arg.startsWith("--data=")) {
                String value = arg.substring("--data=".length());
                if (value.isBlank()) {
                    System.err.println("--data needs a folder path, for example --data=data");
                    return 2;
                }
                dataDirectory = Path.of(value);
            } else if (arg.startsWith("--now=")) {
                String value = arg.substring("--now=".length());
                try {
                    LocalDateTime fixed = Formats.parseDateTime(value);
                    ZoneId zone = ZoneId.systemDefault();
                    clock = Clock.fixed(fixed.atZone(zone).toInstant(), zone);
                } catch (ClinicException ex) {
                    System.err.println("Invalid --now value: " + ex.getMessage());
                    return 2;
                }
            } else if (arg.equals("--help") || arg.equals("-h")) {
                printUsage();
                return 0;
            } else {
                System.err.println("Unknown option: " + arg);
                printUsage();
                return 2;
            }
        }

        try {
            ClinicApp app = ClinicApp.open(dataDirectory, clock);
            printBanner(app);
            new ConsoleUI(app.context).run();
            return 0;
        } catch (ClinicException ex) {
            System.err.println();
            System.err.println("Cannot start the clinic system: " + ex.getMessage());
            System.err.println("Check the data folder and try again.");
            return 1;
        } catch (RuntimeException ex) {
            System.err.println();
            System.err.println("Unexpected startup error: " + ex);
            return 1;
        }
    }

    private void printBanner(ClinicApp app) {
        System.out.println("================================================================");
        System.out.println("  Console Clinic Appointment System");
        System.out.println("================================================================");
        System.out.println("Data folder : " + app.dataDirectory.toAbsolutePath());
        System.out.println("Clinic time : " + Formats.displayDateTime(LocalDateTime.now(app.clock)));
        System.out.println("Loaded      : " + app.users.findAllDoctors().size() + " doctors, "
                + app.users.findAllPatients().size() + " patients, "
                + app.appointments.findAll().size() + " appointments");
        if (app.seeded) {
            System.out.println("Seed data   : created on this first run");
            System.out.println();
            System.out.println("Demo logins : receptionist 1 / admin123");
            System.out.println("              doctors      2-5 / doctor123");
            System.out.println("              patients     6-15 / patient123");
        }
        System.out.println();
        System.out.println("Type HELP for the commands allowed for your role, EXIT to quit.");
        System.out.println();
    }

    private void printUsage() {
        System.out.println("Console Clinic Appointment System");
        System.out.println("Usage: java -jar target/clinic-app.jar [options]");
        System.out.println("  --data=<folder>   Data folder for the JSON files (default: data)");
        System.out.println("  --now=<datetime>  Pin the clock, e.g. --now=2026-11-02T08:00");
        System.out.println("  --help            Show this help");
    }
}
