package com.opencampus.core.registry;

import com.opencampus.api.CampusService;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ServiceMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceRegistryTest {

    @Test
    void shouldRegisterAndFindService() {
        ServiceRegistry registry = new ServiceRegistry();
        CampusService service = new TestService("test_service");

        registry.register(service);

        assertTrue(registry.find("test_service").isPresent());
        assertEquals(service, registry.find("test_service").get());
    }

    private static class TestService implements CampusService {

        private final String serviceId;

        private TestService(String serviceId) {
            this.serviceId = serviceId;
        }

        @Override
        public ServiceMetadata getMetadata() {
            return new ServiceMetadata(
                    serviceId,
                    "测试服务",
                    "仅用于注册中心测试",
                    List.of(),
                    "1.0"
            );
        }

        @Override
        public QueryResult query(QueryRequest request) {
            return QueryResult.success(
                    serviceId,
                    "测试成功",
                    List.of()
            );
        }
    }
}