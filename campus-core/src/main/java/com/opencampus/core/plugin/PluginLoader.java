package com.opencampus.core.plugin;

import com.opencampus.api.CampusService;
import com.opencampus.api.ErrorCode;
import com.opencampus.api.ServiceMetadata;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class PluginLoader implements AutoCloseable {

    public record Report(List<CampusService> services, List<String> failures) {

        public Report {
            services = List.copyOf(services);
            failures = List.copyOf(failures);
        }
    }

    public record Diagnostic(Path jar, String serviceId, ErrorCode code, String message) {
    }

    private final List<URLClassLoader> classLoaders = new ArrayList<>();
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private boolean closed;

    public List<Diagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    public Report loadWithDiagnostics(Path directory) {
        List<CampusService> services = loadFromDirectory(directory);
        return new Report(services, diagnostics.stream()
                .map(diagnostic -> diagnostic.code() + " " + diagnostic.jar().getFileName()
                        + " " + diagnostic.serviceId() + ": " + diagnostic.message()).toList());
    }

    public List<CampusService> loadFromDirectory(Path pluginDirectory) {
        if (closed) {
            throw new IllegalStateException("加载器已关闭");
        }
        if (pluginDirectory == null) {
            throw new IllegalArgumentException("插件目录不能为空");
        }
        diagnostics.clear();
        List<CampusService> services = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        if (!Files.isDirectory(pluginDirectory)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(pluginDirectory)) {
            for (Path jar : paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jar")).sorted().toList()) {
                loadJar(jar, services, ids);
            }
        } catch (IOException exception) {
            report(pluginDirectory, null, exception.toString());
        }
        return List.copyOf(services);
    }

    private void loadJar(Path jar, List<CampusService> services, Set<String> ids) {
        try (JarFile archive = new JarFile(jar.toFile())) {
            if (archive.getJarEntry("META-INF/services/" + CampusService.class.getName()) == null) {
                throw new IOException("缺少 SPI 描述文件");
            }
            URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()},
                    CampusService.class.getClassLoader());
            classLoaders.add(loader);
            var providers = ServiceLoader.load(CampusService.class, loader).stream().iterator();
            while (providers.hasNext()) {
                String serviceId = null;
                try {
                    var provider = providers.next();
                    CampusService service = provider.get();
                    ServiceMetadata metadata = service.getMetadata();
                    serviceId = metadata == null ? null : metadata.serviceId();
                    if (serviceId == null || serviceId.isBlank() || metadata.name() == null
                            || metadata.name().isBlank() || metadata.description() == null
                            || metadata.parameters() == null || metadata.parameters().stream().anyMatch(parameter ->
                                    parameter == null || parameter.name() == null || parameter.name().isBlank())) {
                        throw new IllegalArgumentException("服务元信息不合法");
                    }
                    if (metadata.apiVersion() == null || !metadata.apiVersion().matches("1\\.[0-9]+")) {
                        throw new IllegalArgumentException("不兼容的 API 版本：" + metadata.apiVersion());
                    }
                    if (!ids.add(serviceId)) {
                        throw new IllegalArgumentException("服务 ID 重复：" + serviceId);
                    }
                    services.add(service);
                } catch (RuntimeException | ServiceConfigurationError | LinkageError exception) {
                    report(jar, serviceId, exception.toString());
                }
            }
        } catch (IOException | RuntimeException | ServiceConfigurationError | LinkageError exception) {
            report(jar, null, exception.toString());
        }
    }

    private void report(Path jar, String serviceId, String message) {
        diagnostics.add(new Diagnostic(jar, serviceId, ErrorCode.PLUGIN_LOAD_FAILED, message));
    }

    @Override
    public void close() {
        for (URLClassLoader loader : classLoaders) {
            try {
                loader.close();
            } catch (IOException exception) {
                report(null, null, "无法关闭插件类加载器：" + exception.getMessage());
            }
        }
        classLoaders.clear();
        closed = true;
    }
}
