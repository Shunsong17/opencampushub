package com.opencampus.core.dispatcher;

import com.opencampus.api.CampusService;
import com.opencampus.api.ErrorCode;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.core.registry.ServiceRegistry;

import java.util.Optional;

public class ServiceDispatcher {

    private final ServiceRegistry registry;

    public ServiceDispatcher(ServiceRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("服务注册中心不能为空");
        }

        this.registry = registry;
    }

    public QueryResult dispatch(QueryRequest request) {
        if (request == null
                || request.serviceId() == null
                || request.serviceId().isBlank()) {
            return QueryResult.failure(
                    null,
                    ErrorCode.INVALID_PARAMETER,
                    "服务 ID 不能为空"
            );
        }

        Optional<CampusService> service = registry.find(request.serviceId());

        if (service.isEmpty()) {
            return QueryResult.failure(
                    request.serviceId(),
                    ErrorCode.SERVICE_NOT_FOUND,
                    "未找到服务：" + request.serviceId()
            );
        }

        try {
            QueryResult result = service.get().query(request);

            if (result == null) {
                return QueryResult.failure(
                        request.serviceId(),
                        ErrorCode.PLUGIN_EXECUTION_FAILED,
                        "服务未返回查询结果"
                );
            }

            return result;
        } catch (Exception exception) {
            return QueryResult.failure(
                    request.serviceId(),
                    ErrorCode.PLUGIN_EXECUTION_FAILED,
                    "服务执行失败：" + exception.getMessage()
            );
        }
    }
}