package com.fpt.cinema.enums;

public enum ShowtimeSeatStatus {
    AVAILABLE("AVAILABLE"),
    HELD("LOCKED"),
    BOOKED("BOOKED");

    private final String databaseValue;

    ShowtimeSeatStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String getDatabaseValue() {
        return databaseValue;
    }

    public static ShowtimeSeatStatus fromDatabaseValue(String value) {
        if (value == null) {
            return null;
        }
        for (ShowtimeSeatStatus status : values()) {
            if (status.databaseValue.equalsIgnoreCase(value) || status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown showtime-seat status: " + value);
    }
}
