package com.opencampus.integration.fixture;

import com.opencampus.api.CampusService;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ServiceMetadata;

import java.util.List;

/**
 * 故意抛异常的插件替身，验证核心把插件异常收敛成统一错误，而不是把堆栈抛给用户。
 */
public class ThrowingService implements CampusService {

    public static final String SERVICE_ID = "broken_query";

    @Override
    public ServiceMetadata getMetadata() {
        return new ServiceMetadata(
                SERVICE_ID,
                "异常服务",
                "仅用于验证插件异常隔离",
                List.of(),
                "1.0"
        );
    }

    @Override
    public QueryResult query(QueryRequest request) {
        throw new IllegalStateException("插件内部错误");
    }
}
