package com.opencampus.core.plugin;

import com.opencampus.api.CampusService;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 按目录加载插件 JAR。单个插件失败只记入诊断信息，不影响同目录下其他插件。
 */
public class PluginLoader implements AutoCloseable {

    private final List<URLClassLoader> classLoaders = new ArrayList<>();

    /**
     * 一次加载的结果：成功发现的服务，以及失败的诊断信息。
     */
    public record Report(List<CampusService> services, List<String> failures) {

        public Report {
            services = List.copyOf(services);
            failures = List.copyOf(failures);
        }
    }

    public List<CampusService> loadFromDirectory(Path pluginDirectory) {
        return loadWithDiagnostics(pluginDirectory).services();
    }

    public Report loadWithDiagnostics(Path pluginDirectory) {
        if (pluginDirectory == null) {
            throw new IllegalArgumentException("插件目录不能为空");
        }

        List<CampusService> services = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        if (!Files.isDirectory(pluginDirectory)) {
            return new Report(services, failures);
        }

        try (Stream<Path> paths = Files.list(pluginDirectory)) {
            List<Path> pluginJars = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jar"))
                    .sorted()
                    .toList();

            for (Path pluginJar : pluginJars) {
                try {
                    services.addAll(loadFromJar(pluginJar));
                } catch (IOException | RuntimeException | ServiceConfigurationError error) {
                    failures.add(
                            "插件加载失败：" + pluginJar.getFileName()
                                    + "（" + describe(error) + "）"
                    );
                }
            }
        } catch (IOException exception) {
            failures.add("无法读取插件目录：" + pluginDirectory + "（" + describe(exception) + "）");
        }

        return new Report(services, failures);
    }

    private List<CampusService> loadFromJar(Path pluginJar) throws IOException {
        // ServiceLoader 会静默跳过无法读取的 JAR，这里先显式校验，保证坏文件产生诊断信息而不是被忽略。
        try (JarFile ignored = new JarFile(pluginJar.toFile())) {
            ignored.entries();
        }

        URL pluginUrl = pluginJar.toUri().toURL();

        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{pluginUrl},
                CampusService.class.getClassLoader()
        );

        classLoaders.add(classLoader);

        List<CampusService> services = new ArrayList<>();

        for (CampusService service : ServiceLoader.load(CampusService.class, classLoader)) {
            services.add(service);
        }

        return services;
    }

    @Override
    public void close() {
        for (URLClassLoader classLoader : classLoaders) {
            try {
                classLoader.close();
            } catch (IOException ignored) {
                // 关闭阶段的失败不影响已加载的插件，也不应中断其他类加载器的关闭。
            }
        }

        classLoaders.clear();
    }

    private static String describe(Throwable error) {
        Throwable cause = error.getCause() == null ? error : error.getCause();
        String message = cause.getMessage();

        return message == null || message.isBlank()
                ? cause.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + "：" + message;
    }
}
