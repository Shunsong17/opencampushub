# 图书查询

服务 ID：`library_query`，API 版本 1.0，仅依赖 campus-api。

在仓库根目录运行 `mvn -pl library-plugin -am clean package`，输出 `library-plugin/target/library-plugin-0.1.0-SNAPSHOT.jar`。

参数：`name`、`author`、`category`。至少一个条件，取交集，首尾空白忽略。name、author 按名称包含匹配且英文忽略大小写，其余参数精确匹配。日期严格使用 yyyy-MM-dd，餐次为早餐、午餐或晚餐（若适用）。无匹配成功返回空列表。

数据位于插件资源 `/com/opencampus/plugin/library/data.csv`。UTF-8 简单 CSV，支持 BOM 和空行，不支持字段内逗号、引号、换行。所有列必填，ID 唯一；坏行使整份数据失败并报告行号。缺失或坏数据返回 DATA_LOAD_FAILED；非法请求返回 INVALID_PARAMETER。

服务仅查询本地样例，不提供真实系统接口或写入操作。接入及多插件验证见根 README。
