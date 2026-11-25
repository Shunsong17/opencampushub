package com.opencampus.core.display;

import com.opencampus.api.ErrorCode;
import com.opencampus.api.QueryParameter;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ResultItem;
import com.opencampus.api.ServiceMetadata;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultPrinterTest {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

    @Test
    void shouldRenderItemWithChineseLabels() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("teacher", "张老师");
        fields.put("time", "周三 08:00-09:35");
        fields.put("location", "A201");

        printer().printResult(QueryResult.success(
                "course_query",
                "查询完成，共找到 1 条记录",
                List.of(new ResultItem("软件工程", fields))
        ));

        String text = text();
        assertTrue(text.contains("查询完成，共找到 1 条记录"), text);
        assertTrue(text.contains("[1] 软件工程"), text);
        assertTrue(text.contains("教师：张老师"), text);
        assertTrue(text.contains("时间：周三 08:00-09:35"), text);
        assertTrue(text.contains("地点：A201"), text);
    }

    @Test
    void shouldKeepUnknownFieldKeyAsIs() {
        printer().printResult(QueryResult.success(
                "other_query",
                "查询完成",
                List.of(new ResultItem("其他记录", Map.of("customField", "值")))
        ));

        assertTrue(text().contains("customField：值"), text());
    }

    @Test
    void shouldPrintErrorCodeAndReadableMessage() {
        printer().printResult(QueryResult.failure(
                "course_query",
                ErrorCode.INVALID_PARAMETER,
                "课程查询至少需要提供 name、day 或 time 中的一项"
        ));

        String text = text();
        assertTrue(text.contains("查询失败：INVALID_PARAMETER"), text);
        assertTrue(text.contains("至少需要提供"), text);
    }

    @Test
    void shouldNotThrowWhenItemFieldsAreNull() {
        assertDoesNotThrow(() -> printer().printResult(QueryResult.success(
                "bad_query",
                "查询完成",
                List.of(new ResultItem("无字段记录", null))
        )));

        assertTrue(text().contains("[1] 无字段记录"), text());
    }

    @Test
    void shouldRenderCatalogWithRequiredAndOptionalParameters() {
        ServiceMetadata metadata = new ServiceMetadata(
                "course_query",
                "课程查询",
                "按课程名称、星期或时间查询课程",
                List.of(
                        new QueryParameter("name", "课程名称关键词", false),
                        new QueryParameter("day", "周一至周日", true)
                ),
                "1.0"
        );

        printer().printCatalog(List.of(metadata));

        String text = text();
        assertTrue(text.contains("课程查询（course_query，API 1.0）"), text);
        assertTrue(text.contains("参数：name（可选）：课程名称关键词、day（必填）：周一至周日"), text);
    }

    @Test
    void shouldLabelKnownKeys() {
        assertEquals("教师", ResultPrinter.label("teacher"));
        assertEquals("未知键", ResultPrinter.label("未知键"));
        assertEquals("字段", ResultPrinter.label(null));
    }

    private ResultPrinter printer() {
        return new ResultPrinter(out);
    }

    private String text() {
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
