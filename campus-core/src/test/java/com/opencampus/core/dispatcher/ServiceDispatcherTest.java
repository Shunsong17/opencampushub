package com.opencampus.core.dispatcher;

import com.opencampus.api.CampusService;
import com.opencampus.api.ErrorCode;
import com.opencampus.api.ErrorInfo;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ServiceMetadata;
import com.opencampus.core.registry.ServiceRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceDispatcherTest {

    private final ServiceRegistry registry = new ServiceRegistry();
    private final ServiceDispatcher dispatcher = new ServiceDispatcher(registry);

    @Test
    void shouldReturnNotFoundWhenServiceDoesNotExist() {
        QueryResult result = dispatcher.dispatch(new QueryRequest("unknown_service", Map.of()));

        assertFalse(result.success());
        assertEquals(ErrorCode.SERVICE_NOT_FOUND, result.error().code());
    }

    @Test
    void shouldReturnPluginErrorWhenServiceThrowsException() {
        registry.register(service("broken_service", request -> {
            throw new IllegalStateException("模拟插件故障");
        }));

        QueryResult result = dispatcher.dispatch(new QueryRequest("broken_service", Map.of()));

        assertFalse(result.success());
        assertEquals(ErrorCode.PLUGIN_EXECUTION_FAILED, result.error().code());
        assertTrue(result.message().contains("模拟插件故障"));
    }

    @Test
    void shouldReturnInvalidParameterWhenRequestIsNull() {
        QueryResult result = dispatcher.dispatch(null);

        assertFalse(result.success());
        assertEquals(ErrorCode.INVALID_PARAMETER, result.error().code());
    }

    @Test
    void shouldReturnInvalidParameterWhenServiceIdIsBlank() {
        QueryResult result = dispatcher.dispatch(new QueryRequest("  ", Map.of()));

        assertFalse(result.success());
        assertEquals(ErrorCode.INVALID_PARAMETER, result.error().code());
    }

    @Test
    void shouldReturnServiceResultWhenServiceSucceeds() {
        registry.register(service("working_service", request ->
                QueryResult.success("working_service", "查询成功", List.of())));

        QueryResult result = dispatcher.dispatch(new QueryRequest("working_service", Map.of()));

        assertTrue(result.success());
        assertEquals("working_service", result.serviceId());
        assertEquals("查询成功", result.message());
        assertTrue(result.items().isEmpty());
        assertNull(result.error());
    }

    @Test
    void shouldReturnPluginErrorWhenServiceReturnsNull() {
        registry.register(service("null_service", request -> null));

        QueryResult result = dispatcher.dispatch(new QueryRequest("null_service", Map.of()));

        assertFalse(result.success());
        assertEquals(ErrorCode.PLUGIN_EXECUTION_FAILED, result.error().code());
        assertEquals("服务未返回查询结果", result.message());
    }

    @Test
    void shouldReturnDisabledWhenServiceIsNotEnabled() {
        registry.register(service("disabled_service", request ->
                QueryResult.success("disabled_service", "不该被调用", List.of())));

        ServiceDispatcher disabledDispatcher = new ServiceDispatcher(
                registry,
                serviceId -> !serviceId.equals("disabled_service")
        );

        QueryResult result = disabledDispatcher.dispatch(
                new QueryRequest("disabled_service", Map.of())
        );

        assertFalse(result.success());
        assertEquals(ErrorCode.SERVICE_DISABLED, result.error().code());
        assertTrue(result.message().contains("已禁用"));
    }

    @Test
    void shouldNotInvokeQueryOfDisabledService() {
        AtomicBoolean invoked = new AtomicBoolean(false);

        registry.register(service("disabled_service", request -> {
            invoked.set(true);
            return QueryResult.success("disabled_service", "不该被调用", List.of());
        }));

        ServiceDispatcher disabledDispatcher = new ServiceDispatcher(registry, serviceId -> false);

        disabledDispatcher.dispatch(new QueryRequest("disabled_service", Map.of()));

        assertFalse(invoked.get());
    }

    @Test
    void shouldPassRequestedServiceIdToEnabledPredicate() {
        AtomicReference<String> seen = new AtomicReference<>();

        registry.register(service("announcement_query", request ->
                QueryResult.success("announcement_query", "查询成功", List.of())));

        ServiceDispatcher guarded = new ServiceDispatcher(registry, serviceId -> {
            seen.set(serviceId);
            return true;
        });

        guarded.dispatch(new QueryRequest("announcement_query", Map.of()));

        assertEquals("announcement_query", seen.get());
    }

    @Test
    void shouldReturnInternalErrorWhenMetadataIdDiffersFromRequest() {
        AtomicReference<String> reportedId = new AtomicReference<>("registered_id");

        registry.register(new CampusService() {
            @Override
            public ServiceMetadata getMetadata() {
                return metadata(reportedId.get());
            }

            @Override
            public QueryResult query(QueryRequest request) {
                return QueryResult.success("registered_id", "查询成功", List.of());
            }
        });

        reportedId.set("hijacked_id");

        QueryResult result = dispatcher.dispatch(new QueryRequest("registered_id", Map.of()));

        assertFalse(result.success());
        assertEquals(ErrorCode.INTERNAL_ERROR, result.error().code());
        assertTrue(result.message().contains("不一致"));
    }

    @Test
    void shouldReturnInternalErrorWhenMetadataCannotBeRead() {
        AtomicInteger calls = new AtomicInteger();

        registry.register(new CampusService() {
            @Override
            public ServiceMetadata getMetadata() {
                if (calls.incrementAndGet() > 1) {
                    throw new IllegalStateException("元信息读取失败");
                }

                return metadata("fragile_service");
            }

            @Override
            public QueryResult query(QueryRequest request) {
                return QueryResult.success("fragile_service", "查询成功", List.of());
            }
        });

        QueryResult result = dispatcher.dispatch(new QueryRequest("fragile_service", Map.of()));

        assertFalse(result.success());
        assertEquals(ErrorCode.INTERNAL_ERROR, result.error().code());
        assertTrue(result.message().contains("元信息读取失败"));
    }

    @Test
    void shouldReturnPluginErrorWhenResultServiceIdDiffers() {
        registry.register(service("working_service", request ->
                QueryResult.success("other_service", "查询成功", List.of())));

        QueryResult result = dispatcher.dispatch(new QueryRequest("working_service", Map.of()));

        assertFalse(result.success());
        assertEquals(ErrorCode.PLUGIN_EXECUTION_FAILED, result.error().code());
        assertTrue(result.message().contains("serviceId"));
    }

    @Test
    void shouldReturnPluginErrorWhenSuccessResultCarriesError() {
        registry.register(service("working_service", request -> new QueryResult(
                true,
                "working_service",
                "自相矛盾的结果",
                List.of(),
                new ErrorInfo(ErrorCode.INVALID_PARAMETER, "不该出现")
        )));

        QueryResult result = dispatcher.dispatch(new QueryRequest("working_service", Map.of()));

        assertFalse(result.success());
        assertEquals(ErrorCode.PLUGIN_EXECUTION_FAILED, result.error().code());
    }

    @Test
    void shouldReturnPluginErrorWhenFailureResultHasNoError() {
        registry.register(service("working_service", request -> new QueryResult(
                false,
                "working_service",
                "缺少错误信息",
                List.of(),
                null
        )));

        QueryResult result = dispatcher.dispatch(new QueryRequest("working_service", Map.of()));

        assertFalse(result.success());
        assertEquals(ErrorCode.PLUGIN_EXECUTION_FAILED, result.error().code());
    }

    @Test
    void shouldReturnPluginErrorWhenItemsAreNull() {
        registry.register(service("working_service", request -> new QueryResult(
                true,
                "working_service",
                "条目为空",
                null,
                null
        )));

        QueryResult result = dispatcher.dispatch(new QueryRequest("working_service", Map.of()));

        assertFalse(result.success());
        assertEquals(ErrorCode.PLUGIN_EXECUTION_FAILED, result.error().code());
        assertTrue(result.message().contains("items"));
    }

    private static ServiceMetadata metadata(String serviceId) {
        return new ServiceMetadata(serviceId, "测试服务", "仅用于调度测试", List.of(), "1.0");
    }

    private static CampusService service(
            String serviceId,
            Function<QueryRequest, QueryResult> behavior
    ) {
        return new CampusService() {
            @Override
            public ServiceMetadata getMetadata() {
                return metadata(serviceId);
            }

            @Override
            public QueryResult query(QueryRequest request) {
                return behavior.apply(request);
            }
        };
    }
}
