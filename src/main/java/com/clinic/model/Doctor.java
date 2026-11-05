package com.clinic.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A clinician with a weekly schedule, leave periods and a daily appointment capacity.
 *
 * <p>A working day may define a break (for example 13:00-14:00). Emergency appointments
 * are the only type allowed to use a break period.</p>
 */
public class Doctor extends User {

    private String specialty;
    private Map<DayOfWeek, WorkingDay> weeklySchedule = new LinkedHashMap<>();
    private List<LeavePeriod> leavePeriods = new ArrayList<>();
    private int dailyCapacity;

    public Doctor() {
        super();
    }

    public Doctor(int id, String name, String specialty, int dailyCapacity,
                  String passwordHash, String passwordSalt, LocalDateTime createdAt) {
        super(id, name, passwordHash, passwordSalt, createdAt);
        this.specialty = specialty;
        this.dailyCapacity = dailyCapacity;
    }

    @Override
    public Role getRole() {
        return Role.DOCTOR;
    }

    @Override
    public String getDisplayName() {
        return "Dr. " + getName() + " (" + specialty + ")";
    }

    public Optional<WorkingDay> workingDay(DayOfWeek day) {
        return Optional.ofNullable(weeklySchedule.get(day));
    }

    public boolean worksOn(DayOfWeek day) {
        return weeklySchedule.containsKey(day);
    }

    /** Replaces the schedule of one weekday. */
    public void setWorkingDay(WorkingDay workingDay) {
        if (weeklySchedule == null) {
            weeklySchedule = new LinkedHashMap<>();
        }
        weeklySchedule.put(workingDay.getDay(), workingDay);
    }

    public void clearWorkingDay(DayOfWeek day) {
        if (weeklySchedule != null) {
            weeklySchedule.remove(day);
        }
    }

    public boolean isOnLeave(LocalDate date) {
        if (leavePeriods == null) {
            return false;
        }
        for (LeavePeriod period : leavePeriods) {
            if (period.covers(date)) {
                return true;
            }
        }
        return false;
    }

    public void addLeave(LeavePeriod period) {
        if (leavePeriods == null) {
            leavePeriods = new ArrayList<>();
        }
        leavePeriods.add(period);
    }

    public List<LeavePeriod> getLeavePeriods() {
        return leavePeriods;
    }

    public void setLeavePeriods(List<LeavePeriod> leavePeriods) {
        this.leavePeriods = leavePeriods;
    }

    public Map<DayOfWeek, WorkingDay> getWeeklySchedule() {
        return weeklySchedule;
    }

    public void setWeeklySchedule(Map<DayOfWeek, WorkingDay> weeklySchedule) {
        this.weeklySchedule = weeklySchedule;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public int getDailyCapacity() {
        return dailyCapacity;
    }

    public void setDailyCapacity(int dailyCapacity) {
        this.dailyCapacity = dailyCapacity;
    }

    /** Earliest start time of the weekly schedule, used by reports and slot search. */
    public Optional<LocalTime> earliestStart() {
        return weeklySchedule.values().stream().map(WorkingDay::getStart).min(LocalTime::compareTo);
    }
}
