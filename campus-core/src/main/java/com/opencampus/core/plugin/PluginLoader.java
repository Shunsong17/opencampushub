package com.opencampus.core.plugin;

import com.opencampus.api.CampusService;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

public class PluginLoader {

    private final List<URLClassLoader> classLoaders = new ArrayList<>();

    public List<CampusService> loadFromDirectory(Path pluginDirectory) {
        if (pluginDirectory == null) {
            throw new IllegalArgumentException("插件目录不能为空");
        }

        if (!Files.isDirectory(pluginDirectory)) {
            return List.of();
        }

        List<CampusService> services = new ArrayList<>();

        try (Stream<Path> paths = Files.list(pluginDirectory)) {
            List<Path> pluginJars = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jar"))
                    .sorted()
                    .toList();

            for (Path pluginJar : pluginJars) {
                services.addAll(loadFromJar(pluginJar));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "无法读取插件目录：" + pluginDirectory,
                    exception
            );
        }

        return List.copyOf(services);
    }

    private List<CampusService> loadFromJar(Path pluginJar) {
        try {
            URL pluginUrl = pluginJar.toUri().toURL();

            URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{pluginUrl},
                    CampusService.class.getClassLoader()
            );

            classLoaders.add(classLoader);

            ServiceLoader<CampusService> serviceLoader = ServiceLoader.load(
                    CampusService.class,
                    classLoader
            );

            List<CampusService> services = new ArrayList<>();

            for (CampusService service : serviceLoader) {
                services.add(service);
            }

            return services;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "无法加载插件：" + pluginJar,
                    exception
            );
        }
    }
}