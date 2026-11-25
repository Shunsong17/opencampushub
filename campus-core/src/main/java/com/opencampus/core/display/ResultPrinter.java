package com.opencampus.core.display;

import com.opencampus.api.QueryParameter;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ResultItem;
import com.opencampus.api.ServiceMetadata;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/**
 * 把服务目录和查询结果渲染成中文文本。
 *
 * <p>输出全部走构造函数传入的 {@link PrintStream}，不直接使用 System.out，
 * 这样测试可以捕获文本，也便于以后换成别的输出端。
 *
 * <p>结果里的字段键是英文字段名（契约里没有标签字段），这里对常见字段做中文映射，
 * 未识别的键原样输出，未知插件也能正常显示。
 */
public final class ResultPrinter {

    private static final Map<String, String> FIELD_LABELS = Map.ofEntries(
            Map.entry("id", "编号"),
            Map.entry("title", "标题"),
            Map.entry("teacher", "教师"),
            Map.entry("time", "时间"),
            Map.entry("location", "地点"),
            Map.entry("category", "分类"),
            Map.entry("date", "日期"),
            Map.entry("content", "内容"),
            Map.entry("publisher", "发布单位"),
            Map.entry("status", "状态")
    );

    private final PrintStream out;

    public ResultPrinter(PrintStream out) {
        if (out == null) {
            throw new IllegalArgumentException("输出流不能为空");
        }

        this.out = out;
    }

    /** 打印服务目录：编号、名称、服务 ID、API 版本、说明和参数。 */
    public void printCatalog(List<ServiceMetadata> services) {
        out.println("可用服务（" + services.size() + " 个）：");

        for (int index = 0; index < services.size(); index++) {
            ServiceMetadata metadata = services.get(index);

            out.println();
            out.println((index + 1) + ". " + text(metadata.name())
                    + "（" + text(metadata.serviceId()) + "，API " + text(metadata.apiVersion()) + "）");

            if (!isBlank(metadata.description())) {
                out.println("   说明：" + metadata.description());
            }

            out.println("   参数：" + describeParameters(metadata.parameters()));
        }
    }

    /** 打印查询结果：成功列出条目，失败给出错误码与可读原因。 */
    public void printResult(QueryResult result) {
        if (result == null) {
            out.println("查询失败：核心没有返回结果。");
            return;
        }

        if (!result.success()) {
            printFailure(result);
            return;
        }

        out.println(isBlank(result.message()) ? "查询完成。" : result.message());

        List<ResultItem> items = result.items() == null ? List.of() : result.items();

        for (int index = 0; index < items.size(); index++) {
            ResultItem item = items.get(index);

            out.println();
            out.println("[" + (index + 1) + "] " + text(item.title()));

            Map<String, String> fields = item.fields() == null ? Map.of() : item.fields();

            for (Map.Entry<String, String> field : fields.entrySet()) {
                out.println(label(field.getKey()) + "：" + text(field.getValue()));
            }
        }
    }

    /** 字段键的中文标签，未识别的键原样返回。 */
    public static String label(String fieldKey) {
        if (fieldKey == null || fieldKey.isBlank()) {
            return "字段";
        }

        return FIELD_LABELS.getOrDefault(fieldKey, fieldKey);
    }

    private void printFailure(QueryResult result) {
        if (result.error() == null) {
            out.println("查询失败：" + text(result.message()));
            return;
        }

        out.println("查询失败：" + result.error().code());
        out.println(text(result.error().message()));
    }

    private static String describeParameters(List<QueryParameter> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return "无";
        }

        StringBuilder description = new StringBuilder();

        for (QueryParameter parameter : parameters) {
            if (description.length() > 0) {
                description.append("、");
            }

            description.append(text(parameter.name()))
                    .append(parameter.required() ? "（必填）" : "（可选）");

            if (!isBlank(parameter.description())) {
                description.append("：").append(parameter.description());
            }
        }

        return description.toString();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
