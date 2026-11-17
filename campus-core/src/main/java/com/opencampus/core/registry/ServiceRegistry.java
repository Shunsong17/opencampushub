package com.opencampus.core.registry;

import com.opencampus.api.CampusService;
import com.opencampus.api.ServiceMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ServiceRegistry {

    private final Map<String, CampusService> services = new LinkedHashMap<>();

    public void register(CampusService service) {
        if (service == null) {
            throw new IllegalArgumentException("服务不能为空");
        }

        ServiceMetadata metadata = service.getMetadata();

        if (metadata == null
                || metadata.serviceId() == null
                || metadata.serviceId().isBlank()) {
            throw new IllegalArgumentException("服务 ID 不能为空");
        }

        String serviceId = metadata.serviceId();

        if (services.containsKey(serviceId)) {
            throw new IllegalArgumentException(
                    "服务 ID 已存在：" + serviceId
            );
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
}