package com.opencampus.plugin.canteen;

import com.opencampus.api.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CanteenServiceTest {
    private QueryResult query(CanteenService service, Map<String, String> parameters) {
        return service.query(new QueryRequest("canteen_query", parameters));
    }

    @Test
    void findsExpectedRecordsAndFields() {
        QueryResult result = query(new CanteenService(), Map.of("canteen", "第一食堂", "date", "2026-11-25", "meal", "午餐"));
        assertTrue(result.success());
        assertEquals(2, result.items().size());
        assertEquals("番茄炒蛋", result.items().get(0).title());
        assertEquals("M001", result.items().get(0).fields().get("id"));
        assertEquals(6, result.items().get(0).fields().size());
    }

    @Test
    void normalizesAndCombinesConditions() {
        CanteenService service = new CanteenService();
        assertEquals(1, query(service, Map.of("name", " 番茄炒蛋 ")).items().size());
        assertTrue(query(service, Map.of("name", "番茄炒蛋", "canteen", "不存在")).items().isEmpty());
        QueryResult empty = query(service, Map.of("name", "不存在"));
        assertTrue(empty.success());
        assertNull(empty.error());
        assertTrue(empty.items().isEmpty());
    }

    @Test
    void rejectsInvalidRequests() {
        CanteenService service = new CanteenService();
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, Map.of()).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, Map.of("name", " ")).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, Map.of("date", "2026-02-30")).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, Map.of("unknown", "x")).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, null).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, service.query(null).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, service.query(new QueryRequest("wrong", Map.of())).error().code());
    }

    @Test
    void reportsMissingAndMalformedData() {
        assertEquals(ErrorCode.DATA_LOAD_FAILED, query(new CanteenService(null), Map.of("canteen", "第一食堂", "date", "2026-11-25", "meal", "午餐")).error().code());
        String header = "id,name,canteen,date,meal,price,window\n";
        for (String csv : new String[]{"wrong", header + "broken", header + "M001,番茄炒蛋,第一食堂,2026-11-25,午餐,8.50,一号窗口\nM001,番茄炒蛋,第一食堂,2026-11-25,午餐,8.50,一号窗口"}) {
            CanteenService service = new CanteenService(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
            assertEquals(ErrorCode.DATA_LOAD_FAILED, query(service, Map.of("canteen", "第一食堂", "date", "2026-11-25", "meal", "午餐")).error().code());
        }
        assertTrue(query(new CanteenService(new ByteArrayInputStream(header.getBytes(StandardCharsets.UTF_8))), Map.of("canteen", "第一食堂", "date", "2026-11-25", "meal", "午餐")).success());
    }

    @Test
    void discoversUsingSpi() {
        CampusService service = ServiceLoader.load(CampusService.class).stream().map(ServiceLoader.Provider::get)
                .filter(candidate -> candidate.getMetadata().serviceId().equals("canteen_query")).findFirst().orElseThrow();
        assertEquals("1.0", service.getMetadata().apiVersion());
        assertTrue(service.query(new QueryRequest("canteen_query", Map.of("canteen", "第一食堂", "date", "2026-11-25", "meal", "午餐"))).success());
    }
}
