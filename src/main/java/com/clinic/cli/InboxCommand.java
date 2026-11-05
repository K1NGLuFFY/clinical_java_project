package com.clinic.cli;

import com.clinic.exception.ClinicException;
import com.clinic.model.Notification;
import com.clinic.model.User;
import com.clinic.util.Formats;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Shows the notification inbox oldest-first, then marks every message read. */
final class InboxCommand extends CommandSupport {

    InboxCommand() {
        super("inbox", ALL_ROLES, "inbox", "Read your notifications (marks them read)");
    }

    @Override
    protected void perform(CommandContext ctx, ParsedCommand command) throws ClinicException {
        User user = user(ctx);
        List<Notification> notifications = new ArrayList<>(ctx.inbox().inbox(user.getId()));
        if (notifications.isEmpty()) {
            System.out.println("Your inbox is empty.");
            return;
        }
        notifications.sort(Comparator
                .comparing(Notification::getCreatedAt,
                        Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
                .thenComparingInt(Notification::getId));
        for (Notification notification : notifications) {
            System.out.println(Formats.displayDateTime(notification.getCreatedAt())
                    + "  " + notification.getMessage());
        }
        ctx.inbox().markAllRead(user.getId());
    }
}
