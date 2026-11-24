package com.opencampus.core.registry;

import com.opencampus.api.CampusService;
import com.opencampus.api.QueryParameter;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ServiceMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceRegistryTest {

    @Test
    void shouldRegisterAndFindService() {
        ServiceRegistry registry = new ServiceRegistry();
        CampusService service = service(metadata("test_service", "测试服务", List.of(), "1.0"));

        registry.register(service);

        assertTrue(registry.find("test_service").isPresent());
        assertEquals(service, registry.find("test_service").get());
    }

    @Test
    void shouldRejectDuplicateServiceId() {
        ServiceRegistry registry = new ServiceRegistry();

        registry.register(service(metadata("same_id", "测试服务", List.of(), "1.0")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(service(metadata("same_id", "测试服务", List.of(), "1.0")))
        );

        assertTrue(exception.getMessage().contains("服务 ID 已存在"));
    }

    @Test
    void shouldListServicesInRegistrationOrder() {
        ServiceRegistry registry = new ServiceRegistry();

        registry.register(service(metadata("announcement_query", "公告查询", List.of(), "1.0")));
        registry.register(service(metadata("course_query", "课程查询", List.of(), "1.0")));

        List<ServiceMetadata> metadataList = registry.listMetadata();

        assertEquals(2, metadataList.size());
        assertEquals("announcement_query", metadataList.get(0).serviceId());
        assertEquals("course_query", metadataList.get(1).serviceId());
    }

    @Test
    void shouldNotFindServiceAfterUnregister() {
        ServiceRegistry registry = new ServiceRegistry();

        registry.register(service(metadata("temporary_service", "临时服务", List.of(), "1.0")));
        registry.unregister("temporary_service");

        assertTrue(registry.find("temporary_service").isEmpty());
        assertTrue(registry.listMetadata().isEmpty());
    }

    @Test
    void shouldRejectNullService() {
        ServiceRegistry registry = new ServiceRegistry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(null)
        );

        assertTrue(exception.getMessage().contains("服务不能为空"));
    }

    @Test
    void shouldRejectBlankServiceId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ServiceRegistry()
                        .register(service(metadata("  ", "测试服务", List.of(), "1.0")))
        );

        assertTrue(exception.getMessage().contains("服务 ID"));
    }

    @Test
    void shouldRejectNullMetadata() {
        ServiceRegistry registry = new ServiceRegistry();
        CampusService service = new CampusService() {
            @Override
            public ServiceMetadata getMetadata() {
                return null;
            }

            @Override
            public QueryResult query(QueryRequest request) {
                return QueryResult.success("none", "不会调用", List.of());
            }
        };

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> registry.register(service)
        );

        assertTrue(exception.getMessage().contains("元信息"));
    }

    @Test
    void shouldRejectBlankServiceName() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ServiceRegistry()
                        .register(service(metadata("no_name", " ", List.of(), "1.0")))
        );

        assertTrue(exception.getMessage().contains("名称"));
    }

    @Test
    void shouldRejectNullParameterList() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ServiceRegistry()
                        .register(service(metadata("no_params", "测试服务", null, "1.0")))
        );

        assertTrue(exception.getMessage().contains("参数列表"));
    }

    @Test
    void shouldRejectBlankParameterName() {
        List<QueryParameter> parameters = List.of(new QueryParameter(" ", "无名参数", false));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ServiceRegistry()
                        .register(service(metadata("bad_param", "测试服务", parameters, "1.0")))
        );

        assertTrue(exception.getMessage().contains("参数名"));
    }

    @Test
    void shouldRejectIncompatibleApiMajorVersion() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ServiceRegistry()
                        .register(service(metadata("future_service", "未来服务", List.of(), "2.0")))
        );

        assertTrue(exception.getMessage().contains("不兼容"));
        assertTrue(exception.getMessage().contains("2.0"));
    }

    @Test
    void shouldAcceptCompatibleApiVersion() {
        ServiceRegistry registry = new ServiceRegistry();

        registry.register(service(metadata("minor_service", "次要版本服务", List.of(), "1.7")));

        assertTrue(registry.find("minor_service").isPresent());
    }

    @Test
    void shouldRejectMalformedApiVersion() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new ServiceRegistry()
                        .register(service(metadata("bad_version", "版本非法", List.of(), "v1")))
        );

        assertTrue(exception.getMessage().contains("API 版本"));
    }

    private static ServiceMetadata metadata(
            String serviceId,
            String name,
            List<QueryParameter> parameters,
            String apiVersion
    ) {
        return new ServiceMetadata(serviceId, name, "仅用于注册中心测试", parameters, apiVersion);
    }

    private static CampusService service(ServiceMetadata metadata) {
        return new CampusService() {
            @Override
            public ServiceMetadata getMetadata() {
                return metadata;
            }

            @Override
            public QueryResult query(QueryRequest request) {
                return QueryResult.success(metadata.serviceId(), "测试成功", List.of());
            }
        };
    }
}
