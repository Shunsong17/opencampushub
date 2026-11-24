package com.opencampus.plugin.course;

import java.time.LocalTime;

public record Course(String id, String name, String teacher, String day,
                     LocalTime start, LocalTime end, String location) {
}
