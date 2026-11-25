package com.opencampus.plugin.activity;

import com.opencampus.api.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActivityServiceTest {
    private QueryResult query(ActivityService service, Map<String, String> parameters) {
        return service.query(new QueryRequest("activity_query", parameters));
    }

    @Test
    void findsExpectedRecordsAndFields() {
        QueryResult result = query(new ActivityService(), Map.of("category", "讲座", "date", "2026-11-25"));
        assertTrue(result.success());
        assertEquals(2, result.items().size());
        assertEquals("人工智能讲座", result.items().get(0).title());
        assertEquals("A001", result.items().get(0).fields().get("id"));
        assertEquals(6, result.items().get(0).fields().size());
    }

    @Test
    void normalizesAndCombinesConditions() {
        ActivityService service = new ActivityService();
        assertEquals(1, query(service, Map.of("keyword", " 人工智能讲座 ")).items().size());
        assertTrue(query(service, Map.of("keyword", "人工智能讲座", "category", "不存在")).items().isEmpty());
        QueryResult empty = query(service, Map.of("keyword", "不存在"));
        assertTrue(empty.success());
        assertNull(empty.error());
        assertTrue(empty.items().isEmpty());
    }

    @Test
    void rejectsInvalidRequests() {
        ActivityService service = new ActivityService();
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, Map.of()).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, Map.of("keyword", " ")).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, Map.of("date", "2026-02-30")).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, Map.of("unknown", "x")).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, null).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, service.query(null).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, service.query(new QueryRequest("wrong", Map.of())).error().code());
    }

    @Test
    void reportsMissingAndMalformedData() {
        assertEquals(ErrorCode.DATA_LOAD_FAILED, query(new ActivityService(null), Map.of("category", "讲座", "date", "2026-11-25")).error().code());
        String header = "id,name,category,date,time,location,organizer\n";
        for (String csv : new String[]{"wrong", header + "broken", header + "A001,人工智能讲座,讲座,2026-11-25,14:00,报告厅,计算机学院\nA001,人工智能讲座,讲座,2026-11-25,14:00,报告厅,计算机学院"}) {
            ActivityService service = new ActivityService(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
            assertEquals(ErrorCode.DATA_LOAD_FAILED, query(service, Map.of("category", "讲座", "date", "2026-11-25")).error().code());
        }
        assertTrue(query(new ActivityService(new ByteArrayInputStream(header.getBytes(StandardCharsets.UTF_8))), Map.of("category", "讲座", "date", "2026-11-25")).success());
    }

    @Test
    void discoversUsingSpi() {
        CampusService service = ServiceLoader.load(CampusService.class).stream().map(ServiceLoader.Provider::get)
                .filter(candidate -> candidate.getMetadata().serviceId().equals("activity_query")).findFirst().orElseThrow();
        assertEquals("1.0", service.getMetadata().apiVersion());
        assertTrue(service.query(new QueryRequest("activity_query", Map.of("category", "讲座", "date", "2026-11-25"))).success());
    }
}
