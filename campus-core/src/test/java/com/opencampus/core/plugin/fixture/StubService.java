package com.opencampus.core.plugin.fixture;

import com.opencampus.api.CampusService;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ServiceMetadata;

import java.util.List;

/**
 * 供 PluginLoaderTest 打包进临时 JAR 的桩实现，验证 SPI 发现链路。
 */
public class StubService implements CampusService {

    public static final String SERVICE_ID = "stub_query";

    @Override
    public ServiceMetadata getMetadata() {
        return new ServiceMetadata(
                SERVICE_ID,
                "桩服务",
                "仅用于插件加载测试",
                List.of(),
                "1.0"
        );
    }

    @Override
    public QueryResult query(QueryRequest request) {
        return QueryResult.success(SERVICE_ID, "桩服务查询成功", List.of());
    }
}
