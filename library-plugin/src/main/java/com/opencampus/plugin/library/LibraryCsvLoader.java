package com.opencampus.plugin.library;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class LibraryCsvLoader {
    private LibraryCsvLoader() {
    }

    static void validateParameter(String key, String value) {
    }

    static List<Book> load(InputStream input) throws IOException {
        if (input == null) {
            throw new IOException("数据资源不存在");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header != null && header.startsWith("\uFEFF")) {
                header = header.substring(1);
            }
            if (!"id,name,author,category,location,available".equals(header)) {
                throw new IOException("CSV 表头错误");
            }
            List<Book> records = new ArrayList<>();
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
                        throw new IllegalArgumentException("CSV 列数错误或含不支持的引号");
                    }
                    for (int index = 0; index < fields.length; index++) {
                        fields[index] = fields[index].strip();
                        if (fields[index].isEmpty()) {
                            throw new IllegalArgumentException("字段不能为空");
                        }
                    }
                    if (!ids.add(fields[0])) {
                        throw new IllegalArgumentException("ID 重复");
                    }
                    if (Integer.parseInt(fields[5]) < 0) {
                        throw new IllegalArgumentException("可借数量不能为负");
                    }
                    records.add(new Book(fields[0], fields[1], fields[2], fields[3], fields[4],
                            Integer.parseInt(fields[5])));
                } catch (IllegalArgumentException | java.time.DateTimeException exception) {
                    throw new IOException("CSV 第 " + lineNumber + " 行错误：" + exception.getMessage(), exception);
                }
            }
            return List.copyOf(records);
        }
    }
}
