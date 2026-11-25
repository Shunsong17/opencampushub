package com.opencampus.plugin.activity;

import com.opencampus.api.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ActivityDataTest {
    @ParameterizedTest
    @ValueSource(strings = {"A001,活动,讲座,2026-02-30,14:00,报告厅,学院",
            "A001,活动,讲座,2026-11-25,24:00,报告厅,学院",
            "A001,活动,讲座,2026-11-25,8:00,报告厅,学院"})
    void rejectsInvalidDomainData(String row) {
        String csv = "id,name,category,date,time,location,organizer\n" + row;
        var service = new ActivityService(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        var result = service.query(new QueryRequest("activity_query", Map.of("keyword", "x")));
        assertEquals(ErrorCode.DATA_LOAD_FAILED, result.error().code());
        assertTrue(result.error().message().contains("2"));
        assertTrue(result.items().isEmpty());
    }

    @Test
    void closesInputAndAcceptsBomAndBlankLines() {
        String csv = "\uFEFFid,name,category,date,time,location,organizer\n\nA001,人工智能讲座,讲座,2026-11-25,14:00,报告厅,计算机学院\n";
        boolean[] closed = {false};
        InputStream input = new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };
        var service = new ActivityService(input);
        assertTrue(closed[0]);
        assertEquals(1, service.query(new QueryRequest("activity_query",
                Map.of("keyword", "人工智能讲座"))).items().size());
    }

    @Test
    void mapsReadFailure() {
        InputStream input = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("unreadable");
            }
        };
        var service = new ActivityService(input);
        assertEquals(ErrorCode.DATA_LOAD_FAILED, service.query(new QueryRequest("activity_query",
                Map.of("keyword", "x"))).error().code());
    }
}
