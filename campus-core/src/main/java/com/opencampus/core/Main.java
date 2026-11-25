package com.opencampus.core;

import com.opencampus.api.CampusService;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.ServiceMetadata;
import com.opencampus.core.cli.CommandLineApp;
import com.opencampus.core.config.PluginConfig;
import com.opencampus.core.dispatcher.ServiceDispatcher;
import com.opencampus.core.display.ResultPrinter;
import com.opencampus.core.plugin.PluginLoader;
import com.opencampus.core.registry.ServiceRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 程序入口：装配配置、注册中心、插件加载器、调度器和 CLI，然后按参数选择运行方式。
 *
 * <p>支持三种运行方式：交互式菜单（默认）、{@code --list} 列出已启用服务、
 * {@code --query <服务ID> 参数=值 ...} 执行一次查询。后两种不需要键盘输入，
 * 便于演示脚本和 CI 冒烟验证。
 */
public final class Main {

    private static final int EXIT_OK = 0;
    private static final int EXIT_FAILURE = 1;
    private static final int EXIT_USAGE = 2;

    private static final String MODE_INTERACTIVE = "interactive";
    private static final String MODE_LIST = "list";
    private static final String MODE_QUERY = "query";

    private static final String USAGE = """
            用法：java -cp "lib/*" com.opencampus.core.Main [选项]

              （不加选项）              启动交互式命令行
              --list                    列出已启用的服务后退出
              --query <服务ID> [参数=值 ...]
                                        执行一次查询后退出
                                        例如 --query course_query name=软件 day=周三
              --config <文件>           指定配置文件；默认用工作目录的 plugins.properties，
                                        没有则用程序内置默认配置
              -h, --help                显示本说明

            退出码：0 正常；1 无法加载配置；2 参数用法错误。
            """;

    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = run(args, System.in, System.out, System.err);

        if (exitCode != EXIT_OK) {
            System.exit(exitCode);
        }
    }

    static int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
        Path configPath = null;
        String mode = MODE_INTERACTIVE;
        String queryServiceId = null;
        Map<String, String> queryParameters = new LinkedHashMap<>();

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];

            switch (argument) {
                case "-h", "--help" -> {
                    out.print(USAGE);
                    return EXIT_OK;
                }
                case "--list" -> mode = MODE_LIST;
                case "--config" -> {
                    if (index + 1 >= args.length) {
                        return usageError(err, "缺少 --config 的参数");
                    }

                    configPath = Path.of(args[++index]);
                }
                case "--query" -> {
                    if (index + 1 >= args.length) {
                        return usageError(err, "缺少 --query 的服务 ID");
                    }

                    mode = MODE_QUERY;
                    queryServiceId = args[++index];
                }
                default -> {
                    if (argument.startsWith("--")) {
                        return usageError(err, "未知选项：" + argument);
                    }

                    int separator = argument.indexOf('=');

                    if (separator <= 0) {
                        return usageError(err, "查询条件应写成 参数=值：" + argument);
                    }

                    queryParameters.put(
                            argument.substring(0, separator),
                            argument.substring(separator + 1)
                    );
                }
            }
        }

        if (MODE_QUERY.equals(mode) && (queryServiceId == null || queryServiceId.isBlank())) {
            return usageError(err, "服务 ID 不能为空");
        }

        PluginConfig config;

        try {
            config = configPath == null ? PluginConfig.loadDefault() : PluginConfig.load(configPath);
        } catch (IOException | IllegalArgumentException exception) {
            err.println("无法加载配置：" + describe(exception));
            return EXIT_FAILURE;
        }

        try (PluginLoader pluginLoader = new PluginLoader()) {
            List<String> diagnostics = new ArrayList<>();
            ServiceRegistry registry = configure(config, pluginLoader, diagnostics);
            ServiceDispatcher dispatcher = new ServiceDispatcher(registry, config::isEnabled);

            return switch (mode) {
                case MODE_LIST -> runList(config, registry, out, err, diagnostics);
                case MODE_QUERY -> runQuery(
                        dispatcher,
                        queryServiceId,
                        queryParameters,
                        out,
                        err,
                        diagnostics
                );
                default -> runInteractive(in, out, registry, dispatcher, config, diagnostics);
            };
        }
    }

    /**
     * 装配注册中心：先注册内置服务，再按目录加载插件并逐个注册。
     *
     * <p>任何一个插件加载或注册失败只写一条诊断信息，其余服务照常可用。
     */
    private static ServiceRegistry configure(
            PluginConfig config,
            PluginLoader pluginLoader,
            List<String> diagnostics
    ) {
        diagnostics.add("配置：" + config.source().map(Path::toString).orElse("内置默认值"));
        diagnostics.add("插件目录：" + config.pluginsDirectory());
        config.warnings().forEach(warning -> diagnostics.add("配置警告：" + warning));

        ServiceRegistry registry = new ServiceRegistry();
        registerBuiltinServices(registry, config, diagnostics);

        PluginLoader.Report report = pluginLoader.loadWithDiagnostics(config.pluginsDirectory());
        diagnostics.addAll(report.failures());

        for (CampusService service : report.services()) {
            try {
                registry.register(service);
            } catch (IllegalArgumentException exception) {
                diagnostics.add("插件注册失败：" + describe(exception));
            }
        }

        for (ServiceMetadata metadata : registry.listMetadata()) {
            if (!config.isEnabled(metadata.serviceId())) {
                diagnostics.add("服务未启用：" + metadata.serviceId()
                        + "（在配置中设置 " + metadata.serviceId() + ".enabled=true 后重启）");
            }
        }

        if (!Files.isDirectory(config.pluginsDirectory())) {
            diagnostics.add("插件目录不存在，未加载任何插件：" + config.pluginsDirectory());
        }

        return registry;
    }

    /**
     * 内置服务的接入点。
     *
     * <p>公告查询属于核心内置能力（成员 B 负责），不经过插件目录；实现后在这里注册
     * {@code announcement_query}，并遵守 {@link PluginConfig#isEnabled(String)}。
     * 当前该类尚未提交，因此保持空实现——核心在只有插件的情况下也能完整运行。
     */
    private static void registerBuiltinServices(
            ServiceRegistry registry,
            PluginConfig config,
            List<String> diagnostics
    ) {
    }

    private static int runList(
            PluginConfig config,
            ServiceRegistry registry,
            PrintStream out,
            PrintStream err,
            List<String> diagnostics
    ) {
        diagnostics.forEach(err::println);

        List<ServiceMetadata> enabled = CommandLineApp.enabledServices(registry, config);

        if (enabled.isEmpty()) {
            out.println("当前没有已启用的服务。");
            return EXIT_OK;
        }

        new ResultPrinter(out).printCatalog(enabled);

        return EXIT_OK;
    }

    private static int runQuery(
            ServiceDispatcher dispatcher,
            String serviceId,
            Map<String, String> parameters,
            PrintStream out,
            PrintStream err,
            List<String> diagnostics
    ) {
        diagnostics.forEach(err::println);

        new ResultPrinter(out).printResult(
                dispatcher.dispatch(new QueryRequest(serviceId, parameters))
        );

        return EXIT_OK;
    }

    private static int runInteractive(
            InputStream in,
            PrintStream out,
            ServiceRegistry registry,
            ServiceDispatcher dispatcher,
            PluginConfig config,
            List<String> diagnostics
    ) {
        new CommandLineApp(in, out, registry, dispatcher, config, diagnostics).run();

        return EXIT_OK;
    }

    private static int usageError(PrintStream err, String message) {
        err.println(message);
        err.println("用 --help 查看用法。");

        return EXIT_USAGE;
    }

    private static String describe(Exception exception) {
        String message = exception.getMessage();

        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
