package com.opencampus.core.dispatcher;

import com.opencampus.api.CampusService;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ServiceMetadata;
import com.opencampus.core.registry.ServiceRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
}