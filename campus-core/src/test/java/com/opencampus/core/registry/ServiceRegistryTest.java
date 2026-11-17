package com.opencampus.core.registry;

import com.opencampus.api.CampusService;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ServiceMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceRegistryTest {

    @Test
    void shouldRegisterAndFindService() {
        ServiceRegistry registry = new ServiceRegistry();
        CampusService service = new TestService("test_service");

        registry.register(service);

        assertTrue(registry.find("test_service").isPresent());
        assertEquals(service, registry.find("test_service").get());
    }

    @Test
    void shouldRejectDuplicateServiceId() {
        ServiceRegistry registry = new ServiceRegistry();
    
        registry.register(new TestService("same_id"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(new TestService("same_id"))
        );

        assertTrue(exception.getMessage().contains("服务 ID 已存在"));
    }

    @Test
    void shouldListServicesInRegistrationOrder() {
        ServiceRegistry registry = new ServiceRegistry();
    
        registry.register(new TestService("announcement_query"));
        registry.register(new TestService("course_query"));
    
        List<ServiceMetadata> metadataList = registry.listMetadata();

        assertEquals(2, metadataList.size());
        assertEquals(
                "announcement_query",
                metadataList.get(0).serviceId()
       );
        assertEquals(
                "course_query",
                metadataList.get(1).serviceId()
        );
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

    @Test
    void shouldNotFindServiceAfterUnregister() {
        ServiceRegistry registry = new ServiceRegistry();
    
        registry.register(new TestService("temporary_service"));
        registry.unregister("temporary_service");
    
        assertTrue(registry.find("temporary_service").isEmpty());
        assertTrue(registry.listMetadata().isEmpty());
    }

}