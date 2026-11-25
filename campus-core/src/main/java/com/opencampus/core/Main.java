package com.opencampus.core;

import com.opencampus.api.QueryRequest;
import com.opencampus.core.config.PluginConfig;
import com.opencampus.core.dispatcher.ServiceDispatcher;
import com.opencampus.core.plugin.PluginLoader;
import com.opencampus.core.registry.ServiceRegistry;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (args.length < 2 || (!args[1].equals("list") && !args[1].equals("query"))
                || (args[1].equals("list") && args.length != 2)
                || (args[1].equals("query") && args.length < 3)) {
            System.err.println("用法：Main <配置文件> list | Main <配置文件> query <服务ID> [参数=值 ...]");
            return;
        }
        try (PluginLoader loader = new PluginLoader()) {
            PluginConfig config = PluginConfig.load(Path.of(args[0]));
            ServiceRegistry registry = new ServiceRegistry();
            for (var service : loader.loadFromDirectory(config.directory())) {
                if (!config.disabledServices().contains(service.getMetadata().serviceId())) registry.register(service);
            }
            loader.diagnostics().forEach(diagnostic -> System.err.println(diagnostic.code() + " "
                    + diagnostic.jar().getFileName() + " " + diagnostic.serviceId() + ": " + diagnostic.message()));
            if (args[1].equals("list")) {
                registry.listMetadata().forEach(metadata -> {
                    System.out.println(metadata.serviceId() + " | " + metadata.name());
                    metadata.parameters().forEach(parameter -> System.out.println("  " + parameter.name()
                            + ": " + parameter.description()));
                });
                return;
            }
            Map<String, String> parameters = new LinkedHashMap<>();
            for (int index = 3; index < args.length; index++) {
                String[] pair = args[index].split("=", 2);
                if (pair.length != 2 || pair[0].isBlank() || parameters.putIfAbsent(pair[0], pair[1]) != null) {
                    throw new IllegalArgumentException("参数须采用 name=value 格式且不能重复");
                }
            }
            var result = new ServiceDispatcher(registry, config.disabledServices())
                    .dispatch(new QueryRequest(args[2], parameters));
            System.out.println(result.message());
            if (!result.success()) System.out.println(result.error().code());
            result.items().forEach(item -> {
                System.out.println(item.title());
                item.fields().forEach((key, value) -> System.out.println("  " + key + ": " + value));
            });
        } catch (Exception exception) {
            System.err.println("启动或查询失败：" + exception.getMessage());
        }
    }
}
