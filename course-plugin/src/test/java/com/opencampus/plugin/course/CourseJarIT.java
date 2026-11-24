package com.opencampus.plugin.course;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.*;

class CourseJarIT {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void loadsOnlyApiAndPackagedPlugin() throws Exception {
        URL api = com.opencampus.api.CampusService.class.getProtectionDomain()
                .getCodeSource().getLocation();
        URL jar = Path.of(System.getProperty("course.jar")).toUri().toURL();
        // Platform parent excludes Maven's test and main output directories.
        try (URLClassLoader apiLoader = new URLClassLoader(new URL[]{api},
                ClassLoader.getPlatformClassLoader());
             URLClassLoader pluginLoader = new URLClassLoader(new URL[]{jar}, apiLoader)) {
            Class<?> contract = apiLoader.loadClass("com.opencampus.api.CampusService");
            Object service = ServiceLoader.load((Class) contract, pluginLoader).findFirst().orElseThrow();
            assertEquals(jar, service.getClass().getProtectionDomain().getCodeSource().getLocation());
            assertEquals(pluginLoader, service.getClass().getClassLoader());
            Class<?> requestType = apiLoader.loadClass("com.opencampus.api.QueryRequest");
            Object request = requestType.getConstructor(String.class, Map.class)
                    .newInstance("course_query", Map.of("name", "软件"));
            Object result = contract.getMethod("query", requestType).invoke(service, request);
            assertEquals(true, result.getClass().getMethod("success").invoke(result));
            List<?> items = (List<?>) result.getClass().getMethod("items").invoke(result);
            assertEquals(1, items.size());
            assertEquals("软件工程", items.get(0).getClass().getMethod("title").invoke(items.get(0)));
            assertThrows(ClassNotFoundException.class,
                    () -> pluginLoader.loadClass("com.opencampus.core.plugin.PluginLoader"));
        }
    }
}
