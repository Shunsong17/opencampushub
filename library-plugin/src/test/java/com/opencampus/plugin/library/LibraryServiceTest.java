package com.opencampus.plugin.library;

import com.opencampus.api.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LibraryServiceTest {
    private QueryResult query(LibraryService service, Map<String, String> parameters) {
        return service.query(new QueryRequest("library_query", parameters));
    }

    @Test
    void findsExpectedRecordsAndFields() {
        QueryResult result = query(new LibraryService(), Map.of("name", "java"));
        assertTrue(result.success());
        assertEquals(1, result.items().size());
        assertEquals("Java程序设计", result.items().get(0).title());
        assertEquals("B001", result.items().get(0).fields().get("id"));
        assertEquals(5, result.items().get(0).fields().size());
    }

    @Test
    void normalizesAndCombinesConditions() {
        LibraryService service = new LibraryService();
        assertEquals(1, query(service, Map.of("name", " JAVA ")).items().size());
        assertTrue(query(service, Map.of("name", "Java程序设计", "author", "不存在")).items().isEmpty());
        QueryResult empty = query(service, Map.of("name", "不存在"));
        assertTrue(empty.success());
        assertNull(empty.error());
        assertTrue(empty.items().isEmpty());
    }

    @Test
    void rejectsInvalidRequests() {
        LibraryService service = new LibraryService();
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, Map.of()).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, Map.of("name", " ")).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, Map.of("other", "x")).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, Map.of("unknown", "x")).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, query(service, null).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, service.query(null).error().code());
        assertEquals(ErrorCode.INVALID_PARAMETER, service.query(new QueryRequest("wrong", Map.of())).error().code());
    }

    @Test
    void reportsMissingAndMalformedData() {
        assertEquals(ErrorCode.DATA_LOAD_FAILED, query(new LibraryService(null), Map.of("name", "java")).error().code());
        String header = "id,name,author,category,location,available\n";
        for (String csv : new String[]{"wrong", header + "broken", header + "B001,Java程序设计,张老师,计算机,三楼A区,3\nB001,Java程序设计,张老师,计算机,三楼A区,3"}) {
            LibraryService service = new LibraryService(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
            assertEquals(ErrorCode.DATA_LOAD_FAILED, query(service, Map.of("name", "java")).error().code());
        }
        assertTrue(query(new LibraryService(new ByteArrayInputStream(header.getBytes(StandardCharsets.UTF_8))), Map.of("name", "java")).success());
    }

    @Test
    void discoversUsingSpi() {
        CampusService service = ServiceLoader.load(CampusService.class).stream().map(ServiceLoader.Provider::get)
                .filter(candidate -> candidate.getMetadata().serviceId().equals("library_query")).findFirst().orElseThrow();
        assertEquals("1.0", service.getMetadata().apiVersion());
        assertTrue(service.query(new QueryRequest("library_query", Map.of("name", "java"))).success());
    }
}
