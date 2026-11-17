package com.opencampus.plugin.course;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Loads the documented, unquoted six-column CSV format. */
final class CourseCsvLoader {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm")
            .withResolverStyle(ResolverStyle.STRICT);

    private CourseCsvLoader() {
    }

    static boolean validDay(String day) {
        return day.matches("周[一二三四五六日]");
    }

    static LocalTime parseTime(String text) {
        if (!text.matches("[0-9]{2}:[0-9]{2}")) {
            throw new IllegalArgumentException("时间必须采用 HH:mm 格式");
        }
        return LocalTime.parse(text, TIME);
    }

    static List<Course> load(InputStream input) throws IOException {
        if (input == null) {
            throw new IOException("找不到 courses.csv");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header != null && header.startsWith("\uFEFF")) {
                header = header.substring(1);
            }
            if (!"id,name,teacher,day,time,location".equals(header)) {
                throw new IOException("课程 CSV 表头不正确");
            }
            List<Course> courses = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    String[] fields = line.split(",", -1);
                    if (fields.length != 6 || line.contains("\"")) {
                        throw new IllegalArgumentException("必须为六列且不含引号");
                    }
                    for (int i = 0; i < fields.length; i++) {
                        fields[i] = fields[i].strip();
                        if (fields[i].isEmpty()) {
                            throw new IllegalArgumentException("必填字段为空");
                        }
                    }
                    if (!ids.add(fields[0]) || !validDay(fields[3])) {
                        throw new IllegalArgumentException("课程 ID 重复或星期非法");
                    }
                    String[] interval = fields[4].split("-", -1);
                    if (interval.length != 2) {
                        throw new IllegalArgumentException("时间区间格式错误");
                    }
                    LocalTime start = parseTime(interval[0]);
                    LocalTime end = parseTime(interval[1]);
                    if (!start.isBefore(end)) {
                        throw new IllegalArgumentException("结束时间必须晚于开始时间");
                    }
                    courses.add(new Course(fields[0], fields[1], fields[2], fields[3],
                            start, end, fields[5]));
                } catch (IllegalArgumentException | java.time.DateTimeException exception) {
                    throw new IOException("课程 CSV 第 " + lineNumber + " 行错误："
                            + exception.getMessage(), exception);
                }
            }
            return List.copyOf(courses);
        }
    }
}
