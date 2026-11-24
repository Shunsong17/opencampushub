package com.opencampus.plugin.course;

import com.opencampus.api.CampusService;
import com.opencampus.api.ErrorCode;
import com.opencampus.api.QueryParameter;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ResultItem;
import com.opencampus.api.ServiceMetadata;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CourseService implements CampusService {
    public static final String SERVICE_ID = "course_query";
    private static final Set<String> PARAMETERS = Set.of("name", "day", "time");
    private final List<Course> courses;
    private final String loadError;

    public CourseService() {
        this(CourseService.class.getResourceAsStream("/courses.csv"));
    }

    // The loader owns and closes this stream, including on malformed data.
    CourseService(InputStream input) {
        List<Course> loaded = List.of();
        String failure = null;
        try {
            loaded = CourseCsvLoader.load(input);
        } catch (IOException exception) {
            failure = exception.getMessage();
        }
        courses = loaded;
        loadError = failure;
    }

    @Override
    public ServiceMetadata getMetadata() {
        return new ServiceMetadata(SERVICE_ID, "课程查询", "按课程名称、星期或时间查询课程",
                List.of(new QueryParameter("name", "课程名称关键词，英文忽略大小写", false),
                        new QueryParameter("day", "周一至周日", false),
                        new QueryParameter("time", "HH:mm，包含开始时刻、不包含结束时刻", false)),
                "1.0");
    }

    @Override
    public QueryResult query(QueryRequest request) {
        if (request == null || !SERVICE_ID.equals(request.serviceId())) {
            return invalid("请求服务 ID 必须为 " + SERVICE_ID);
        }
        Map<String, String> parameters = request.parameters();
        if (parameters == null || parameters.keySet().stream()
                .anyMatch(key -> key == null || !PARAMETERS.contains(key))) {
            return invalid("参数只能包含 name、day、time");
        }
        String name = normalized(parameters.get("name"));
        String day = normalized(parameters.get("day"));
        String time = normalized(parameters.get("time"));
        if (name.isEmpty() && day.isEmpty() && time.isEmpty()) {
            return invalid("课程查询至少需要提供 name、day 或 time 中的一项");
        }
        if (!day.isEmpty() && !CourseCsvLoader.validDay(day)) {
            return invalid("day 必须为周一至周日");
        }
        LocalTime queryTime;
        try {
            queryTime = time.isEmpty() ? null : CourseCsvLoader.parseTime(time);
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            return invalid("time 必须为有效的 HH:mm 时间，例如 08:00");
        }
        if (loadError != null) {
            return QueryResult.failure(SERVICE_ID, ErrorCode.DATA_LOAD_FAILED, loadError);
        }
        List<ResultItem> items = courses.stream()
                .filter(course -> name.isEmpty() || course.name().toLowerCase(Locale.ROOT)
                        .contains(name.toLowerCase(Locale.ROOT)))
                .filter(course -> day.isEmpty() || course.day().equals(day))
                .filter(course -> queryTime == null || (!queryTime.isBefore(course.start())
                        && queryTime.isBefore(course.end())))
                .map(CourseService::toItem)
                .toList();
        return QueryResult.success(SERVICE_ID,
                items.isEmpty() ? "未找到匹配课程" : "查询完成，共找到 " + items.size() + " 条记录", items);
    }

    private static String normalized(String value) {
        return value == null ? "" : value.strip();
    }

    private static QueryResult invalid(String message) {
        return QueryResult.failure(SERVICE_ID, ErrorCode.INVALID_PARAMETER, message);
    }

    private static ResultItem toItem(Course course) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("id", course.id());
        fields.put("teacher", course.teacher());
        fields.put("time", course.day() + " " + course.start() + "-" + course.end());
        fields.put("location", course.location());
        return new ResultItem(course.name(), java.util.Collections.unmodifiableMap(fields));
    }
}
