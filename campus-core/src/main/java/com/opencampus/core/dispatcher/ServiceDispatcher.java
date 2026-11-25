package com.opencampus.core.dispatcher;

import com.opencampus.api.CampusService;
import com.opencampus.api.ErrorCode;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ServiceMetadata;
import com.opencampus.core.registry.ServiceRegistry;

import java.util.Optional;
import java.util.function.Predicate;

public class ServiceDispatcher {

    private final ServiceRegistry registry;
    private final Predicate<String> enabled;

    public ServiceDispatcher(ServiceRegistry registry) {
        this(registry, serviceId -> true);
    }

    public ServiceDispatcher(ServiceRegistry registry, Predicate<String> enabled) {
        if (registry == null) {
            throw new IllegalArgumentException("服务注册中心不能为空");
        }

        if (enabled == null) {
            throw new IllegalArgumentException("启用判断不能为空");
        }

        this.registry = registry;
        this.enabled = enabled;
    }

    public QueryResult dispatch(QueryRequest request) {
        if (request == null || isBlank(request.serviceId())) {
            return QueryResult.failure(
                    null,
                    ErrorCode.INVALID_PARAMETER,
                    "服务 ID 不能为空"
            );
        }

        String serviceId = request.serviceId();
        Optional<CampusService> found = registry.find(serviceId);

        if (found.isEmpty()) {
            return QueryResult.failure(
                    serviceId,
                    ErrorCode.SERVICE_NOT_FOUND,
                    "未找到服务：" + serviceId
            );
        }

        if (!enabled.test(serviceId)) {
            return QueryResult.failure(
                    serviceId,
                    ErrorCode.SERVICE_DISABLED,
                    "服务已禁用：" + serviceId
            );
        }

        CampusService service = found.get();

        QueryResult metadataFailure = checkMetadata(service, serviceId);

        if (metadataFailure != null) {
            return metadataFailure;
        }

        QueryResult result;

        try {
            result = service.query(request);
        } catch (Exception exception) {
            return QueryResult.failure(
                    serviceId,
                    ErrorCode.PLUGIN_EXECUTION_FAILED,
                    "服务执行失败：" + describe(exception)
            );
        }

        if (result == null) {
            return QueryResult.failure(
                    serviceId,
                    ErrorCode.PLUGIN_EXECUTION_FAILED,
                    "服务未返回查询结果"
            );
        }

        String violation = contractViolation(result, serviceId);

        if (violation != null) {
            return QueryResult.failure(
                    serviceId,
                    ErrorCode.PLUGIN_EXECUTION_FAILED,
                    "服务返回了不符合契约的结果：" + violation
            );
        }

        return result;
    }

    private static QueryResult checkMetadata(CampusService service, String serviceId) {
        ServiceMetadata metadata;

        try {
            metadata = service.getMetadata();
        } catch (Exception exception) {
            return QueryResult.failure(
                    serviceId,
                    ErrorCode.INTERNAL_ERROR,
                    "无法读取服务元信息：" + describe(exception)
            );
        }

        if (metadata == null) {
            return QueryResult.failure(
                    serviceId,
                    ErrorCode.INTERNAL_ERROR,
                    "服务元信息为空：" + serviceId
            );
        }

        if (!serviceId.equals(metadata.serviceId())) {
            return QueryResult.failure(
                    serviceId,
                    ErrorCode.INTERNAL_ERROR,
                    "请求的服务 ID 与元信息不一致：请求 " + serviceId
                            + "，元信息 " + metadata.serviceId()
            );
        }

        return null;
    }

    private static String contractViolation(QueryResult result, String requestedId) {
        if (result.items() == null) {
            return "items 不能为 null";
        }

        if (!requestedId.equals(result.serviceId())) {
            return "serviceId 与请求不一致：" + result.serviceId();
        }

        if (result.success() && result.error() != null) {
            return "success 为 true 但 error 不为 null";
        }

        if (!result.success() && result.error() == null) {
            return "success 为 false 但 error 为 null";
        }

        return null;
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
