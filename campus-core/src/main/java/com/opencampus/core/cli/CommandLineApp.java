package com.opencampus.core.cli;

import com.opencampus.api.CampusService;
import com.opencampus.api.QueryParameter;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.ServiceMetadata;
import com.opencampus.core.config.PluginConfig;
import com.opencampus.core.dispatcher.ServiceDispatcher;
import com.opencampus.core.display.ResultPrinter;
import com.opencampus.core.registry.ServiceRegistry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 交互式命令行：查看服务目录、按服务元信息提示输入参数、展示统一结果。
 *
 * <p>所有输入输出都通过构造函数注入，方便用字符串脚本测试整个菜单流程。
 * CLI 自己不做业务校验（例如"课程查询至少要一个条件"），只负责收集参数并交给调度器，
 * 这样插件的规则变化不需要改 CLI。
 */
public final class CommandLineApp {

    private static final String MENU = """
            ================================
                   OpenCampus Hub
            ================================
            1. 查看可用服务
            2. 查询服务
            0. 退出
            请选择：""";

    private final BufferedReader input;
    private final PrintStream out;
    private final ServiceRegistry registry;
    private final ServiceDispatcher dispatcher;
    private final PluginConfig config;
    private final List<String> startupDiagnostics;
    private final ResultPrinter printer;

    public CommandLineApp(
            InputStream input,
            PrintStream out,
            ServiceRegistry registry,
            ServiceDispatcher dispatcher,
            PluginConfig config,
            List<String> startupDiagnostics
    ) {
        if (input == null || out == null) {
            throw new IllegalArgumentException("输入流与输出流不能为空");
        }

        if (registry == null || dispatcher == null || config == null) {
            throw new IllegalArgumentException("注册中心、调度器与配置不能为空");
        }

        this.input = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        this.out = out;
        this.registry = registry;
        this.dispatcher = dispatcher;
        this.config = config;
        this.startupDiagnostics = List.copyOf(startupDiagnostics);
        this.printer = new ResultPrinter(out);
    }

    /** 目录里只展示已启用的服务；未启用的仍可通过 ID 触发 SERVICE_DISABLED 错误。 */
    public static List<ServiceMetadata> enabledServices(ServiceRegistry registry, PluginConfig config) {
        List<ServiceMetadata> enabled = new ArrayList<>();

        for (ServiceMetadata metadata : registry.listMetadata()) {
            if (config.isEnabled(metadata.serviceId())) {
                enabled.add(metadata);
            }
        }

        return List.copyOf(enabled);
    }

    /** 主循环：读到 EOF 或用户选择 0 时返回。 */
    public void run() {
        printStartup();

        while (true) {
            out.print(MENU);

            String choice = readLine();

            if (choice == null) {
                out.println();
                return;
            }

            switch (choice.strip()) {
                case "1" -> showServices();
                case "2" -> queryService();
                case "0" -> {
                    out.println("再见。");
                    return;
                }
                default -> out.println("无效选项，请输入 0、1 或 2。");
            }

            out.println();
        }
    }

    private void printStartup() {
        for (String diagnostic : startupDiagnostics) {
            out.println(diagnostic);
        }

        if (!startupDiagnostics.isEmpty()) {
            out.println();
        }
    }

    private void showServices() {
        List<ServiceMetadata> services = enabledServices(registry, config);

        if (services.isEmpty()) {
            out.println("当前没有已启用的服务。请检查 " + PluginConfig.FILE_NAME
                    + " 中的 <serviceId>.enabled 设置和插件目录。");
            return;
        }

        printer.printCatalog(services);
    }

    private void queryService() {
        List<ServiceMetadata> services = enabledServices(registry, config);

        if (services.isEmpty()) {
            out.println("当前没有已启用的服务，无法查询。");
            return;
        }

        out.println("可用服务：" + names(services));

        String serviceId = readLine("请输入服务 ID（留空返回）：");

        if (serviceId == null || serviceId.isBlank()) {
            return;
        }

        String requestedId = serviceId.strip();
        ServiceMetadata metadata = findMetadata(requestedId);
        Map<String, String> parameters = new LinkedHashMap<>();

        if (metadata == null) {
            out.println("注册中心没有 " + requestedId + "，将直接提交请求以得到统一错误。");
        } else {
            out.println();
            out.println("请输入查询条件（留空表示不提供该条件，全部留空会由插件返回参数错误）：");
        }

        if (metadata != null) {
            for (QueryParameter parameter : metadata.parameters()) {
                String value = readLine(parameterPrompt(parameter));

                if (value == null) {
                    return;
                }

                if (!value.isBlank()) {
                    parameters.put(parameter.name(), value.strip());
                }
            }
        }

        out.println();
        printer.printResult(dispatcher.dispatch(new QueryRequest(requestedId, parameters)));
    }

    private ServiceMetadata findMetadata(String serviceId) {
        return registry.find(serviceId)
                .map(CampusService::getMetadata)
                .orElse(null);
    }

    private static String parameterPrompt(QueryParameter parameter) {
        StringBuilder prompt = new StringBuilder()
                .append(parameter.name())
                .append(parameter.required() ? "（必填" : "（可选");

        if (parameter.description() != null && !parameter.description().isBlank()) {
            prompt.append("，").append(parameter.description());
        }

        return prompt.append("）：").toString();
    }

    private static String names(List<ServiceMetadata> services) {
        StringBuilder names = new StringBuilder();

        for (ServiceMetadata metadata : services) {
            if (names.length() > 0) {
                names.append("、");
            }

            names.append(metadata.serviceId()).append("（").append(metadata.name()).append("）");
        }

        return names.toString();
    }

    /** 读一行并去掉首尾空白与 Windows 的 \r；EOF 返回 null。 */
    private String readLine() {
        try {
            String line = input.readLine();

            if (line == null) {
                return null;
            }

            // 从 PowerShell 管道或带 BOM 的批处理文件读入时，首行可能带 U+FEFF。
            if (line.startsWith("\uFEFF")) {
                line = line.substring(1);
            }

            return line.strip();
        } catch (IOException exception) {
            throw new IllegalStateException("读取输入失败", exception);
        }
    }

    private String readLine(String prompt) {
        out.print(prompt);
        out.flush();

        return readLine();
    }
}
