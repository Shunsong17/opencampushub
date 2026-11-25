package com.opencampus.plugin.activity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ActivityCsvLoader {
    private ActivityCsvLoader() {
    }

    static void validateParameter(String key, String value) {
        if (key.equals("date")) {
            if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("日期格式为 yyyy-MM-dd");
            java.time.LocalDate.parse(value);
        }
    }

    static List<Activity> load(InputStream input) throws IOException {
        if (input == null) throw new IOException("数据资源不存在");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header != null && header.startsWith("\uFEFF")) header = header.substring(1);
            if (!"id,name,category,date,time,location,organizer".equals(header)) throw new IOException("CSV 表头错误");
            List<Activity> records = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                try {
                    String[] fields = line.split(",", -1);
                    if (fields.length != 7 || line.contains("\"")) throw new IllegalArgumentException("CSV 列数错误或含不支持的引号");
                    for (int index = 0; index < fields.length; index++) {
                        fields[index] = fields[index].strip();
                        if (fields[index].isEmpty()) throw new IllegalArgumentException("字段不能为空");
                    }
                    if (!ids.add(fields[0])) throw new IllegalArgumentException("ID 重复");
                    validateParameter("date", fields[3]);
                    if (!fields[4].matches("[0-9]{2}:[0-9]{2}")) {
                        throw new IllegalArgumentException("时间格式为 HH:mm");
                    }
                    records.add(new Activity(fields[0], fields[1], fields[2], java.time.LocalDate.parse(fields[3]),
                            java.time.LocalTime.parse(fields[4]), fields[5], fields[6]));
                } catch (IllegalArgumentException | java.time.DateTimeException exception) {
                    throw new IOException("CSV 第 " + lineNumber + " 行错误：" + exception.getMessage(), exception);
                }
            }
            return List.copyOf(records);
        }
    }
}
