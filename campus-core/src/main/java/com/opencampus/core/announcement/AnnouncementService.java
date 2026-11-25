package com.opencampus.core.announcement;

import com.opencampus.api.CampusService;
import com.opencampus.api.ErrorCode;
import com.opencampus.api.QueryParameter;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ResultItem;
import com.opencampus.api.ServiceMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 内置公告查询服务，只通过 campus-api 契约与调用方协作。 */
public final class AnnouncementService implements CampusService {
    public static final String SERVICE_ID = "announcement_query";
    private static final Set<String> PARAMETERS = Set.of("keyword", "category");
    private static final ServiceMetadata METADATA = new ServiceMetadata(
            SERVICE_ID, "公告查询", "按关键词或分类查询校园公告，空条件列出全部公告",
            List.of(new QueryParameter("keyword", "标题或正文包含的关键词，英文忽略大小写", false),
                    new QueryParameter("category", "公告分类，精确匹配", false)), "1.0");

    private final List<Announcement> announcements;
    private final String loadError;

    public AnnouncementService() {
        this(AnnouncementService.class.getResourceAsStream("/announcements.csv"));
    }

    // 供同包测试注入数据；CSV 加载器负责关闭输入流。
    AnnouncementService(InputStream input) {
        List<Announcement> loaded = List.of();
        String failure = null;
        try {
            loaded = AnnouncementCsvLoader.load(input);
        } catch (IOException exception) {
            failure = "公告数据加载失败：" + exception.getMessage();
        }
        announcements = loaded;
        loadError = failure;
    }

    @Override
    public ServiceMetadata getMetadata() {
        return METADATA;
    }

    @Override
    public QueryResult query(QueryRequest request) {
        if (request == null || !SERVICE_ID.equals(request.serviceId())) {
            return invalid(request, "请求服务 ID 必须为 " + SERVICE_ID);
        }
        Map<String, String> parameters = request.parameters();
        if (parameters == null || parameters.keySet().stream()
                .anyMatch(key -> key == null || !PARAMETERS.contains(key))) {
            return invalid(request, "参数只能包含 keyword、category；无筛选条件时请传空参数表");
        }
        String keyword = normalized(parameters.get("keyword")).toLowerCase(Locale.ROOT);
        String category = normalized(parameters.get("category"));
        if (loadError != null) {
            return QueryResult.failure(SERVICE_ID, ErrorCode.DATA_LOAD_FAILED, loadError);
        }

        List<ResultItem> items = announcements.stream()
                .filter(announcement -> keyword.isEmpty()
                        || announcement.title().toLowerCase(Locale.ROOT).contains(keyword)
                        || announcement.content().toLowerCase(Locale.ROOT).contains(keyword))
                .filter(announcement -> category.isEmpty() || announcement.category().equals(category))
                .map(AnnouncementService::toItem)
                .toList();
        return QueryResult.success(SERVICE_ID,
                items.isEmpty() ? "未找到匹配结果" : "查询完成，共找到 " + items.size() + " 条记录", items);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip();
    }

    private static QueryResult invalid(QueryRequest request, String message) {
        return QueryResult.failure(request == null ? null : request.serviceId(),
                ErrorCode.INVALID_PARAMETER, message);
    }

    private static ResultItem toItem(Announcement announcement) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", announcement.id());
        fields.put("category", announcement.category());
        fields.put("date", announcement.date().toString());
        fields.put("content", announcement.content());
        return new ResultItem(announcement.title(), Collections.unmodifiableMap(fields));
    }
}
