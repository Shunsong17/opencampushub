package com.opencampus.plugin.activity;

import com.opencampus.api.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ActivityService implements CampusService {
    public static final String SERVICE_ID = "activity_query";
    private static final Set<String> PARAMETERS = Set.of("keyword", "category", "date");
    private final List<Activity> records;
    private final String loadError;

    public ActivityService() {
        this(ActivityService.class.getResourceAsStream("/com/opencampus/plugin/activity/data.csv"));
    }

    ActivityService(InputStream input) {
        List<Activity> loaded = List.of();
        String failure = null;
        try {
            loaded = ActivityCsvLoader.load(input);
        } catch (IOException exception) {
            failure = exception.getMessage();
        }
        records = loaded;
        loadError = failure;
    }

    @Override
    public ServiceMetadata getMetadata() {
        return new ServiceMetadata(SERVICE_ID, "校园活动", "至少一个条件，多条件取交集",
                List.of(new QueryParameter("keyword", "包含匹配，英文忽略大小写", false),
                        new QueryParameter("category", "精确匹配", false),
                        new QueryParameter("date", "日期 yyyy-MM-dd", false)), "1.0");
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
                    ActivityCsvLoader.validateParameter(entry.getKey(), value);
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
                .map(ActivityService::toItem).toList();
        return QueryResult.success(SERVICE_ID, items.isEmpty() ? "未找到匹配记录" : "查询完成，共找到 " + items.size() + " 条记录", items);
    }

    private static boolean matches(Activity record, String key, String value) {
        String actual = switch (key) {
            case "keyword" -> record.name();
            case "category" -> record.category();
            case "date" -> record.date().toString();
            default -> throw new IllegalArgumentException("未知参数");
        };
        return Set.of("keyword").contains(key)
                ? actual.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT)) : actual.equals(value);
    }

    private static ResultItem toItem(Activity record) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", record.id());
        fields.put("category", record.category());
        fields.put("date", record.date().toString());
        fields.put("time", record.time().toString());
        fields.put("location", record.location());
        fields.put("organizer", record.organizer());
        return new ResultItem(record.name(), Collections.unmodifiableMap(fields));
    }

    private static QueryResult invalid(String message) {
        return QueryResult.failure(SERVICE_ID, ErrorCode.INVALID_PARAMETER, message);
    }
}
