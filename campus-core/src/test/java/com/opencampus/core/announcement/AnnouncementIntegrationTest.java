package com.opencampus.core.announcement;

import com.opencampus.api.ErrorCode;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.core.dispatcher.ServiceDispatcher;
import com.opencampus.core.registry.ServiceRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnouncementIntegrationTest {
    @Test
    void registersAndQueriesThroughPublicContract() {
        ServiceRegistry registry = new ServiceRegistry();
        registry.register(new AnnouncementService());
        assertEquals(List.of(AnnouncementService.SERVICE_ID),
                registry.listMetadata().stream().map(metadata -> metadata.serviceId()).toList());

        QueryResult result = new ServiceDispatcher(registry).dispatch(new QueryRequest(
                AnnouncementService.SERVICE_ID, Map.of("keyword", "图书馆", "category", "服务通知")));
        assertTrue(result.success(), result.message());
        assertNull(result.error());
        assertEquals(AnnouncementService.SERVICE_ID, result.serviceId());
        assertEquals(1, result.items().size());
        assertEquals("A002", result.items().get(0).fields().get("id"));
        assertEquals("图书馆闭馆通知", result.items().get(0).title());
    }

    @Test
    void dispatcherPreservesNoMatchAsSuccess() {
        ServiceRegistry registry = new ServiceRegistry();
        registry.register(new AnnouncementService());
        QueryResult result = new ServiceDispatcher(registry).dispatch(new QueryRequest(
                AnnouncementService.SERVICE_ID, Map.of("keyword", "不存在的公告")));
        assertTrue(result.success(), result.message());
        assertTrue(result.items().isEmpty());
        assertNull(result.error());
        assertEquals("未找到匹配结果", result.message());
    }

    @Test
    void dispatcherPreservesDataLoadFailureCode() {
        ServiceRegistry registry = new ServiceRegistry();
        registry.register(new AnnouncementService(null));
        QueryResult result = new ServiceDispatcher(registry).dispatch(new QueryRequest(
                AnnouncementService.SERVICE_ID, Map.of()));
        assertFalse(result.success());
        assertEquals(ErrorCode.DATA_LOAD_FAILED, result.error().code());
        assertEquals(AnnouncementService.SERVICE_ID, result.serviceId());
        assertTrue(result.items().isEmpty());
    }
}
