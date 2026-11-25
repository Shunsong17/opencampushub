package com.opencampus.core.registry;

import com.opencampus.api.CampusService;
import com.opencampus.api.QueryParameter;
import com.opencampus.api.ServiceMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ServiceRegistry {

    /** 本项目实现的公开契约主版本，插件声明的 apiVersion 主版本必须与此一致。 */
    public static final int SUPPORTED_API_MAJOR = 1;

    private final Map<String, CampusService> services = new LinkedHashMap<>();

    public void register(CampusService service) {
        if (service == null) {
            throw new IllegalArgumentException("服务不能为空");
        }

        ServiceMetadata metadata = service.getMetadata();
        validateMetadata(metadata);

        String serviceId = metadata.serviceId();

        if (services.containsKey(serviceId)) {
            throw new IllegalArgumentException("服务 ID 已存在：" + serviceId);
        }

        services.put(serviceId, service);
    }

    public Optional<CampusService> find(String serviceId) {
        return Optional.ofNullable(services.get(serviceId));
    }

    public void unregister(String serviceId) {
        services.remove(serviceId);
    }

    public List<ServiceMetadata> listMetadata() {
        List<ServiceMetadata> metadataList = new ArrayList<>();

        for (CampusService service : services.values()) {
            metadataList.add(service.getMetadata());
        }

        return List.copyOf(metadataList);
    }

    private static void validateMetadata(ServiceMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("服务元信息不能为空");
        }

        String serviceId = metadata.serviceId();

        if (isBlank(serviceId)) {
            throw new IllegalArgumentException("服务 ID 不能为空");
        }

        if (isBlank(metadata.name())) {
            throw new IllegalArgumentException("服务名称不能为空：" + serviceId);
        }

        if (metadata.parameters() == null) {
            throw new IllegalArgumentException("查询参数列表不能为空：" + serviceId);
        }

        for (QueryParameter parameter : metadata.parameters()) {
            if (parameter == null || isBlank(parameter.name())) {
                throw new IllegalArgumentException("查询参数名不能为空：" + serviceId);
            }
        }

        int major = apiMajor(metadata);

        if (major != SUPPORTED_API_MAJOR) {
            throw new IllegalArgumentException(
                    "不兼容的 API 主版本：" + metadata.apiVersion()
                            + "，当前支持 " + SUPPORTED_API_MAJOR + ".x（服务 "
                            + serviceId + "）"
            );
        }
    }

    private static int apiMajor(ServiceMetadata metadata) {
        String version = metadata.apiVersion();

        if (isBlank(version)) {
            throw new IllegalArgumentException("API 版本不能为空：" + metadata.serviceId());
        }

        String[] parts = version.trim().split("\\.");

        try {
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "API 版本格式不合法：" + version + "（服务 "
                            + metadata.serviceId() + "）",
                    exception
            );
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
