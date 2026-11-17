package com.opencampus.core.dispatcher;

import com.opencampus.api.CampusService;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ServiceMetadata;
import com.opencampus.api.ErrorCode;
import com.opencampus.core.registry.ServiceRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceDispatcherTest {

    @Test
    void shouldReturnNotFoundWhenServiceDoesNotExist() {
        ServiceRegistry registry = new ServiceRegistry();
        ServiceDispatcher dispatcher = new ServiceDispatcher(registry);

        QueryRequest request = new QueryRequest(
                "unknown_service",
                Map.of()
        );

        QueryResult result = dispatcher.dispatch(request);

        assertTrue(!result.success());
        assertTrue(result.error() != null);
        assertTrue(result.error().code().name().equals("SERVICE_NOT_FOUND"));
    }

    @Test
    void shouldReturnPluginErrorWhenServiceThrowsException() {
        ServiceRegistry registry = new ServiceRegistry();
    
        CampusService brokenService = new CampusService() {
            @Override
            public ServiceMetadata getMetadata() {
               return new ServiceMetadata(
                        "broken_service",
                        "故障服务",
                        "用于测试异常处理",
                        List.of(),
                        "1.0"
                );
            }

            @Override
            public QueryResult query(QueryRequest request) {
                throw new IllegalStateException("模拟插件故障");
            }
        };

        registry.register(brokenService);

        ServiceDispatcher dispatcher = new ServiceDispatcher(registry);

        QueryResult result = dispatcher.dispatch(
                new QueryRequest("broken_service", Map.of())
        );

        assertTrue(!result.success());
        assertTrue(result.error() != null);
        assertEquals(
                ErrorCode.PLUGIN_EXECUTION_FAILED,
                result.error().code()
        );
    }
    @Test
    void shouldReturnInvalidParameterWhenRequestIsNull() {
        ServiceRegistry registry = new ServiceRegistry();
        ServiceDispatcher dispatcher = new ServiceDispatcher(registry);
    
        QueryResult result = dispatcher.dispatch(null);

        assertTrue(!result.success());
        assertEquals(
                ErrorCode.INVALID_PARAMETER,
                result.error().code()
        );
    }
    @Test
    void shouldReturnInvalidParameterWhenServiceIdIsBlank() {
        ServiceRegistry registry = new ServiceRegistry();
        ServiceDispatcher dispatcher = new ServiceDispatcher(registry);
    
        QueryRequest request = new QueryRequest("  ", Map.of());
    
        QueryResult result = dispatcher.dispatch(request);
    
        assertTrue(!result.success());
        assertEquals(
                ErrorCode.INVALID_PARAMETER,
                result.error().code()
        );
    }

}