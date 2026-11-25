package com.opencampus.integration;

import com.opencampus.api.*;
import com.opencampus.core.Main;
import com.opencampus.core.config.PluginConfig;
import com.opencampus.core.dispatcher.ServiceDispatcher;
import com.opencampus.core.plugin.PluginLoader;
import com.opencampus.core.registry.ServiceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;
import static org.junit.jupiter.api.Assertions.*;

class PluginsIT {
    @TempDir
    Path temporary;

    private Path install() throws Exception {
        Path directory = Files.createDirectory(temporary.resolve("plugins"));
        Path root = Path.of(System.getProperty("project.root"));
        for (String name : List.of("course", "library", "canteen", "activity")) {
            Files.copy(root.resolve(name + "-plugin/target/" + name + "-plugin-0.1.0-SNAPSHOT.jar"),
                    directory.resolve(name + ".jar"));
        }
        return directory;
    }

    private ServiceRegistry register(List<CampusService> services, Set<String> disabled) {
        ServiceRegistry registry = new ServiceRegistry();
        services.stream().filter(service -> !disabled.contains(service.getMetadata().serviceId()))
                .forEach(registry::register);
        return registry;
    }

    @Test
    void fourRealJarsQueryThroughCoreAndClose() throws Exception {
        Path directory = install();
        PluginLoader loader = new PluginLoader();
        try (loader) {
            List<CampusService> services = loader.loadFromDirectory(directory);
            assertEquals(4, services.size());
            assertTrue(loader.diagnostics().isEmpty());
            ServiceDispatcher dispatcher = new ServiceDispatcher(register(services, Set.of()));
            Map<String, Map<String, String>> queries = Map.of(
                    "course_query", Map.of("name", "软件"),
                    "library_query", Map.of("name", "Java"),
                    "canteen_query", Map.of("date", "2026-11-25", "meal", "午餐"),
                    "activity_query", Map.of("category", "讲座", "date", "2026-11-25"));
            Map<String, String> titles = Map.of("course_query", "软件工程", "library_query", "Java程序设计",
                    "canteen_query", "番茄炒蛋", "activity_query", "人工智能讲座");
            for (var entry : queries.entrySet()) {
                QueryResult result = dispatcher.dispatch(new QueryRequest(entry.getKey(), entry.getValue()));
                assertTrue(result.success(), result.message());
                assertEquals(titles.get(entry.getKey()), result.items().get(0).title());
            }
            for (CampusService service : services) {
                assertTrue(service.getClass().getProtectionDomain().getCodeSource().getLocation().toString().endsWith(".jar"));
            }
        }
        assertThrows(IllegalStateException.class, () -> loader.loadFromDirectory(directory));
        try (var paths = Files.list(directory)) {
            for (Path jar : paths.toList()) Files.delete(jar);
        }
    }

    @Test
    void disabledServiceHiddenAndRejectedEvenWithoutJar() throws Exception {
        Path directory = install();
        Path configFile = temporary.resolve("plugins.properties");
        Files.writeString(configFile, "plugins.directory=plugins\nlibrary_query.enabled=false\n");
        PluginConfig config = PluginConfig.load(configFile);
        assertEquals(directory, config.directory());
        try (PluginLoader loader = new PluginLoader()) {
            ServiceRegistry registry = register(loader.loadFromDirectory(config.directory()), config.disabledServices());
            assertEquals(3, registry.listMetadata().size());
            assertTrue(registry.find("library_query").isEmpty());
            ServiceDispatcher dispatcher = new ServiceDispatcher(registry, config.disabledServices());
            assertEquals(ErrorCode.SERVICE_DISABLED,
                    dispatcher.dispatch(new QueryRequest("library_query", Map.of("name", "Java"))).error().code());
            assertTrue(dispatcher.dispatch(new QueryRequest("course_query", Map.of("day", "周三"))).success());
        }
        Files.delete(directory.resolve("library.jar"));
        try (PluginLoader loader = new PluginLoader()) {
            ServiceDispatcher dispatcher = new ServiceDispatcher(
                    register(loader.loadFromDirectory(directory), config.disabledServices()), config.disabledServices());
            assertEquals(ErrorCode.SERVICE_DISABLED,
                    dispatcher.dispatch(new QueryRequest("library_query", Map.of("name", "Java"))).error().code());
        }
    }

    @Test
    void corruptMissingProviderAndDuplicateDoNotBlockHealthyPlugins() throws Exception {
        Path directory = install();
        Files.writeString(directory.resolve("00-corrupt.jar"), "not a jar");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(directory.resolve("01-bad-spi.jar")))) {
            jar.putNextEntry(new JarEntry("META-INF/services/com.opencampus.api.CampusService"));
            jar.write("missing.Provider\n".getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        Files.copy(directory.resolve("library.jar"), directory.resolve("zz-duplicate.jar"));
        try (PluginLoader loader = new PluginLoader()) {
            assertEquals(4, loader.loadFromDirectory(directory).size());
            assertEquals(3, loader.diagnostics().size());
            assertTrue(loader.diagnostics().stream().allMatch(diagnostic -> diagnostic.code() == ErrorCode.PLUGIN_LOAD_FAILED));
            assertTrue(loader.diagnostics().stream().anyMatch(diagnostic -> "library_query".equals(diagnostic.serviceId())));
        }
    }

    private void fixture(Path directory, String name, String version, String queryBody, String constructorBody) throws Exception {
        Path source = temporary.resolve(name + ".java");
        String code = "import com.opencampus.api.*; import java.util.*; public class " + name
                + " implements CampusService { public " + name + "() {" + constructorBody + "} "
                + "public ServiceMetadata getMetadata() { return new ServiceMetadata(\"" + name
                + "\", \"fixture\", \"test\", List.of(), \"" + version + "\"); } "
                + "public QueryResult query(QueryRequest request) { " + queryBody + " } }";
        Files.writeString(source, code);
        String apiPath = Path.of(CampusService.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "17",
                "-classpath", apiPath, "-d", temporary.toString(), source.toString()));
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(directory.resolve("00-" + name + ".jar")))) {
            jar.putNextEntry(new JarEntry(name + ".class"));
            Files.copy(temporary.resolve(name + ".class"), jar);
            jar.closeEntry();
            jar.putNextEntry(new JarEntry("META-INF/services/com.opencampus.api.CampusService"));
            jar.write((name + "\n").getBytes(StandardCharsets.UTF_8));
            jar.closeEntry();
        }
    }

    @Test
    void incompatibleAndInitializationFailureIsolatedExecutionFailureMapped() throws Exception {
        Path directory = install();
        fixture(directory, "FuturePlugin", "2.0", "return null;", "");
        fixture(directory, "BrokenInit", "1.0", "return null;", "throw new IllegalStateException(\"init\");");
        fixture(directory, "BrokenQuery", "1.0", "throw new IllegalStateException(\"query\");", "");
        try (PluginLoader loader = new PluginLoader()) {
            List<CampusService> services = loader.loadFromDirectory(directory);
            assertEquals(5, services.size());
            assertEquals(2, loader.diagnostics().size());
            assertTrue(loader.diagnostics().stream().anyMatch(diagnostic -> "FuturePlugin".equals(diagnostic.serviceId())));
            ServiceDispatcher dispatcher = new ServiceDispatcher(register(services, Set.of()));
            assertEquals(ErrorCode.PLUGIN_EXECUTION_FAILED,
                    dispatcher.dispatch(new QueryRequest("BrokenQuery", Map.of())).error().code());
            assertTrue(dispatcher.dispatch(new QueryRequest("course_query", Map.of("name", "软件"))).success());
        }
    }

    @Test
    void invalidConfigRejected() throws Exception {
        Path config = temporary.resolve("invalid.properties");
        Files.writeString(config, "library_query.enabled=maybe\n");
        assertThrows(java.io.IOException.class, () -> PluginConfig.load(config));
    }

    @Test
    void commandLineListsQueriesAndDisables() throws Exception {
        install();
        Path config = temporary.resolve("cli.properties");
        Files.writeString(config, "plugins.directory=plugins\nlibrary_query.enabled=false\n");
        PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setOut(output);
            Main.main(new String[]{config.toString(), "list"});
            assertTrue(captured.toString(StandardCharsets.UTF_8).contains("activity_query"));
            assertFalse(captured.toString(StandardCharsets.UTF_8).contains("library_query"));
            captured.reset();
            Main.main(new String[]{config.toString(), "query", "canteen_query", "date=2026-11-25", "meal=午餐"});
            assertTrue(captured.toString(StandardCharsets.UTF_8).contains("番茄炒蛋"));
            captured.reset();
            Main.main(new String[]{config.toString(), "query", "library_query", "name=Java"});
            assertTrue(captured.toString(StandardCharsets.UTF_8).contains("SERVICE_DISABLED"));
        } finally {
            System.setOut(previous);
        }
    }
}
