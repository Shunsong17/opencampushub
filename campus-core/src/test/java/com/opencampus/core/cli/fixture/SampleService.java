package com.opencampus.core.cli.fixture;

import com.opencampus.api.CampusService;
import com.opencampus.api.QueryParameter;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ResultItem;
import com.opencampus.api.ServiceMetadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供 CLI 测试使用的普通服务：带一个可选参数，返回一条固定记录。
 */
public class SampleService implements CampusService {

    public static final String SERVICE_ID = "sample_query";

    @Override
    public ServiceMetadata getMetadata() {
        return new ServiceMetadata(
                SERVICE_ID,
                "样例服务",
                "仅用于命令行测试",
                List.of(new QueryParameter("keyword", "关键词", false)),
                "1.0"
        );
    }

    @Override
    public QueryResult query(QueryRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("teacher", "张老师");
        fields.put("time", "周三 08:00-09:35");

        return QueryResult.success(
                SERVICE_ID,
                "查询完成，共找到 1 条记录",
                List.of(new ResultItem("软件工程", fields))
        );
    }
}
