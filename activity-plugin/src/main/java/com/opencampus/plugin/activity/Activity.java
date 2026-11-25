package com.opencampus.plugin.activity;

import java.time.LocalDate;
import java.time.LocalTime;

record Activity(String id, String name, String category, LocalDate date, LocalTime time, String location,
        String organizer) {
}
