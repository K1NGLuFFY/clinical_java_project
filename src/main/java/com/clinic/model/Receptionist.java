package com.clinic.model;

import java.time.LocalDateTime;

/** Front-desk user. Receptionists manage patients, doctors, bookings and reports. */
public class Receptionist extends User {

    public Receptionist() {
        super();
    }

    public Receptionist(int id, String name, String passwordHash, String passwordSalt, LocalDateTime createdAt) {
        super(id, name, passwordHash, passwordSalt, createdAt);
    }

    @Override
    public Role getRole() {
        return Role.RECEPTIONIST;
    }

    @Override
    public String getDisplayName() {
        return getName() + " (receptionist)";
    }
}
