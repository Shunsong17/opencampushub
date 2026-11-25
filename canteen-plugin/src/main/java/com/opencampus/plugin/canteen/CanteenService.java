package com.opencampus.plugin.canteen;

import com.opencampus.api.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CanteenService implements CampusService {
    public static final String SERVICE_ID = "canteen_query";
    private static final Set<String> PARAMETERS = Set.of("name", "canteen", "date", "meal");
    private final List<MenuItem> records;
    private final String loadError;

    public CanteenService() {
        this(CanteenService.class.getResourceAsStream("/com/opencampus/plugin/canteen/data.csv"));
    }

    CanteenService(InputStream input) {
        List<MenuItem> loaded = List.of();
        String failure = null;
        try {
            loaded = CanteenCsvLoader.load(input);
        } catch (IOException exception) {
            failure = exception.getMessage();
        }
        records = loaded;
        loadError = failure;
    }

    @Override
    public ServiceMetadata getMetadata() {
        return new ServiceMetadata(SERVICE_ID, "食堂菜单", "至少一个条件，多条件取交集",
                List.of(new QueryParameter("name", "包含匹配，英文忽略大小写", false),
                        new QueryParameter("canteen", "精确匹配", false),
                        new QueryParameter("date", "日期 yyyy-MM-dd", false),
                        new QueryParameter("meal", "早餐、午餐或晚餐", false)), "1.0");
    }

    @Override
    public QueryResult query(QueryRequest request) {
        if (request == null || !SERVICE_ID.equals(request.serviceId()) || request.parameters() == null) {
            return invalid("请求或服务 ID 不合法");
        }
        Map<String, String> conditions = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, String> entry : request.parameters().entrySet()) {
                if (entry.getKey() == null || !PARAMETERS.contains(entry.getKey())) return invalid("未知参数");
                String value = entry.getValue() == null ? "" : entry.getValue().strip();
                if (!value.isEmpty()) {
                    CanteenCsvLoader.validateParameter(entry.getKey(), value);
                    conditions.put(entry.getKey(), value);
                }
            }
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            return invalid(exception.getMessage());
        }
        if (conditions.isEmpty()) return invalid("至少提供一个有效查询条件");
        if (loadError != null) return QueryResult.failure(SERVICE_ID, ErrorCode.DATA_LOAD_FAILED, loadError);
        List<ResultItem> items = records.stream()
                .filter(record -> conditions.entrySet().stream().allMatch(entry -> matches(record, entry.getKey(), entry.getValue())))
                .map(CanteenService::toItem).toList();
        return QueryResult.success(SERVICE_ID, items.isEmpty() ? "未找到匹配记录" : "查询完成，共找到 " + items.size() + " 条记录", items);
    }

    private static boolean matches(MenuItem record, String key, String value) {
        String actual = switch (key) {
            case "name" -> record.name();
            case "canteen" -> record.canteen();
            case "date" -> record.date().toString();
            case "meal" -> record.meal();
            default -> throw new IllegalArgumentException("未知参数");
        };
        return Set.of("name").contains(key)
                ? actual.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT)) : actual.equals(value);
    }

    private static ResultItem toItem(MenuItem record) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", record.id());
        fields.put("canteen", record.canteen());
        fields.put("date", record.date().toString());
        fields.put("meal", record.meal());
        fields.put("price", record.price().setScale(2).toPlainString());
        fields.put("window", record.window());
        return new ResultItem(record.name(), Collections.unmodifiableMap(fields));
    }

    private static QueryResult invalid(String message) {
        return QueryResult.failure(SERVICE_ID, ErrorCode.INVALID_PARAMETER, message);
    }
}
