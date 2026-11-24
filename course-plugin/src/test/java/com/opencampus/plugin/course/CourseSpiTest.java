package com.opencampus.plugin.course;

import com.opencampus.api.CampusService;
import com.opencampus.api.QueryRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

class CourseSpiTest {
    @Test
    void discoversServiceThroughPublicContract() {
        CampusService service = ServiceLoader.load(CampusService.class).stream()
                .map(ServiceLoader.Provider::get)
                .filter(candidate -> candidate.getMetadata().serviceId().equals("course_query"))
                .findFirst().orElseThrow();
        assertEquals("1.0", service.getMetadata().apiVersion());
        assertEquals(3, service.getMetadata().parameters().size());
        assertTrue(service.getMetadata().parameters().stream().noneMatch(parameter -> parameter.required()));
        assertEquals(2, service.query(new QueryRequest("course_query", Map.of("day", "周三")))
                .items().size());
    }
}
