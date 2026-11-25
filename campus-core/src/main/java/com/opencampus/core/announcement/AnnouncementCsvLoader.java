package com.opencampus.core.announcement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 读取五列 CSV，支持带引号的逗号和双引号转义，不支持字段内换行。 */
final class AnnouncementCsvLoader {
    private static final String HEADER = "id,title,category,date,content";

    private AnnouncementCsvLoader() {
    }

    /** 接管并关闭输入流；任意坏行使整份数据加载失败。 */
    static List<Announcement> load(InputStream input) throws IOException {
        if (input == null) {
            throw new IOException("找不到 announcements.csv");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header != null && header.startsWith("\uFEFF")) {
                header = header.substring(1);
            }
            if (!HEADER.equals(header)) {
                throw new IOException("公告 CSV 表头必须为 " + HEADER);
            }

            List<Announcement> announcements = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    List<String> fields = parseLine(line);
                    if (fields.size() != 5 || fields.stream().anyMatch(String::isEmpty)) {
                        throw new IllegalArgumentException("必须为五列且所有字段非空");
                    }
                    if (!ids.add(fields.get(0))) {
                        throw new IllegalArgumentException("公告 ID 重复：" + fields.get(0));
                    }
                    if (!fields.get(3).matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                        throw new IllegalArgumentException("日期必须采用 yyyy-MM-dd 格式");
                    }
                    LocalDate date = LocalDate.parse(fields.get(3));
                    announcements.add(new Announcement(fields.get(0), fields.get(1),
                            fields.get(2), date, fields.get(4)));
                } catch (IllegalArgumentException | DateTimeParseException exception) {
                    throw new IOException("公告 CSV 第 " + lineNumber + " 行错误："
                            + exception.getMessage(), exception);
                }
            }
            return List.copyOf(announcements);
        }
    }

    private static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean quoteClosed = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (quoted) {
                if (character == '"') {
                    if (index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                        quoteClosed = true;
                    }
                } else {
                    field.append(character);
                }
            } else if (character == ',') {
                fields.add(field.toString().strip());
                field.setLength(0);
                quoteClosed = false;
            } else if (character == '"' && field.length() == 0 && !quoteClosed) {
                quoted = true;
            } else {
                if (quoteClosed || character == '"') {
                    throw new IllegalArgumentException("CSV 引号位置错误");
                }
                field.append(character);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("CSV 引号未闭合或字段包含换行");
        }
        fields.add(field.toString().strip());
        return fields;
    }
}
