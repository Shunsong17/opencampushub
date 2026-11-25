package com.opencampus.integration;

import com.opencampus.api.CampusService;
import com.opencampus.api.ErrorCode;
import com.opencampus.api.QueryParameter;
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.api.ResultItem;
import com.opencampus.api.ServiceMetadata;
import com.opencampus.core.cli.CommandLineApp;
import com.opencampus.core.config.PluginConfig;
import com.opencampus.core.dispatcher.ServiceDispatcher;
import com.opencampus.core.plugin.PluginLoader;
import com.opencampus.core.registry.ServiceRegistry;
import com.opencampus.integration.fixture.ThrowingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 插件接入集成测试（成员 D）。
 *
 * <p>与 course-plugin 内部的 CourseJarIT 不同，这里不直接引用插件的任何类，
 * 而是从"已经打包好的插件 JAR"出发，走完真实链路：
 * 目录扫描 → SPI 发现 → 注册中心 → 配置启禁用 → 调度器 → CLI 文本输出。
 *
 * <p>因此本模块不需要依赖 course-plugin 的编译产物，只依赖它的 JAR 文件，
 * 这也正是"插件只依赖公开 API、核心源码不变"的验证方式。
 */
class PluginIntegrationTest {

    private static final String SERVICE_ID = "course_query";

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldDiscoverCourseServiceFromPackagedJarThroughSpi() throws IOException {
        Path plugins = preparePluginDirectory();

        try (PluginLoader loader = new PluginLoader()) {
            PluginLoader.Report report = loader.loadWithDiagnostics(plugins);

            assertTrue(report.failures().isEmpty(), () -> report.failures().toString());
            assertEquals(1, report.services().size());

            ServiceMetadata metadata = report.services().get(0).getMetadata();

            assertEquals(SERVICE_ID, metadata.serviceId());
            assertEquals("课程查询", metadata.name());
            assertEquals("1.0", metadata.apiVersion());
            assertEquals(
                    List.of("name", "day", "time"),
                    metadata.parameters().stream().map(QueryParameter::name).toList()
            );
        }
    }

    @Test
    void shouldListEnabledServiceAndReturnVerifiableFields() throws IOException {
        preparePluginDirectory();
        PluginConfig config = PluginConfig.load(writeConfig(true));

        try (PluginLoader loader = new PluginLoader()) {
            ServiceRegistry registry = loadIntoRegistry(loader, config);
            ServiceDispatcher dispatcher = new ServiceDispatcher(registry, config::isEnabled);

            String output = runCli("1\n2\n" + SERVICE_ID + "\n软件\n\n\n0\n", registry, dispatcher, config);

            assertTrue(output.contains("课程查询"), output);
            assertTrue(output.contains("软件工程"), output);
            assertTrue(output.contains("教师：张老师"), output);
            assertTrue(output.contains("时间：周三 08:00-09:35"), output);
            assertTrue(output.contains("地点：A201"), output);

            QueryResult result = dispatcher.dispatch(
                    new QueryRequest(SERVICE_ID, Map.of("day", "周三"))
            );

            assertTrue(result.success());
            assertEquals(
                    List.of("软件工程", "Java程序设计"),
                    result.items().stream().map(ResultItem::title).toList()
            );
        }
    }

    @Test
    void shouldHideDisabledServiceAndReturnServiceDisabled() throws IOException {
        preparePluginDirectory();
        PluginConfig config = PluginConfig.load(writeConfig(false));

        try (PluginLoader loader = new PluginLoader()) {
            ServiceRegistry registry = loadIntoRegistry(loader, config);
            ServiceDispatcher dispatcher = new ServiceDispatcher(registry, config::isEnabled);

            String output = runCli("1\n0\n", registry, dispatcher, config);

            assertFalse(output.contains(SERVICE_ID), output);
            assertTrue(output.contains("当前没有已启用的服务"), output);

            QueryResult result = dispatcher.dispatch(
                    new QueryRequest(SERVICE_ID, Map.of("name", "软件"))
            );

            assertFalse(result.success());
            assertEquals(ErrorCode.SERVICE_DISABLED, result.error().code());
        }
    }

    @Test
    void shouldIsolateCorruptJarAndKeepValidPluginQueryable() throws IOException {
        Path plugins = preparePluginDirectory();
        Files.writeString(
                plugins.resolve("broken.jar"),
                "not a jar",
                StandardCharsets.UTF_8
        );

        try (PluginLoader loader = new PluginLoader()) {
            PluginLoader.Report report = loader.loadWithDiagnostics(plugins);

            assertEquals(1, report.services().size());
            assertEquals(1, report.failures().size());
            assertTrue(report.failures().get(0).contains("broken.jar"), report.failures().toString());
            assertEquals(SERVICE_ID, report.services().get(0).getMetadata().serviceId());
        }
    }

    @Test
    void shouldConvertPluginExceptionAndKeepOtherServicesUsable() throws IOException {
        ServiceRegistry registry = new ServiceRegistry();
        registry.register(new ThrowingService());

        Path plugins = preparePluginDirectory();

        try (PluginLoader loader = new PluginLoader()) {
            registry.register(loader.loadFromDirectory(plugins).get(0));
        }

        ServiceDispatcher dispatcher = new ServiceDispatcher(registry, serviceId -> true);

        QueryResult failure = dispatcher.dispatch(new QueryRequest(ThrowingService.SERVICE_ID, Map.of()));

        assertFalse(failure.success());
        assertEquals(ErrorCode.PLUGIN_EXECUTION_FAILED, failure.error().code());

        QueryResult stillWorking = dispatcher.dispatch(
                new QueryRequest(SERVICE_ID, Map.of("time", "08:30"))
        );

        assertTrue(stillWorking.success(), stillWorking.message());
        assertEquals(1, stillWorking.items().size());
        assertEquals("软件工程", stillWorking.items().get(0).title());
    }

    /**
     * 架构断言：核心源码不得引用插件包或插件实现类。
     *
     * <p>只查"包名 / 类名"这类真正的编译期耦合；服务 ID（course_query）作为配置数据
     * 出现在 CLI 用法示例和配置注释里是允许的，不算核心为课程插件写分支。
     */
    @Test
    void shouldKeepCoreSourceFreeOfCourseSpecificBranches() throws IOException {
        Path coreSource = Path.of(System.getProperty("core.src", ""));

        assertTrue(Files.isDirectory(coreSource), "找不到核心源码目录：" + coreSource);

        List<String> offenders = new ArrayList<>();
        List<String> markers = List.of("courseservice", "opencampus.plugin.course");

        try (Stream<Path> sources = Files.walk(coreSource)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String code = stripComments(Files.readString(source, StandardCharsets.UTF_8))
                        .toLowerCase(Locale.ROOT);

                for (String marker : markers) {
                    if (code.contains(marker)) {
                        offenders.add(coreSource.relativize(source) + " → " + marker);
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(), () -> "核心源码引用了课程插件，接入插件不应改核心：" + offenders);
    }

    /** 去掉行注释与块注释后再检查，避免注释里的说明文字造成误报。 */
    private static String stripComments(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", " ")
                .replaceAll("//[^\\n]*", " ");
    }

    /** 把真实的课程插件 JAR 复制到临时 plugins 目录。 */
    private Path preparePluginDirectory() throws IOException {
        Path jar = Path.of(System.getProperty("course.jar", ""));

        assertTrue(
                Files.isRegularFile(jar),
                "找不到课程插件 JAR：" + jar
                        + "。集成测试依赖 course-plugin 的打包产物，请在仓库根目录执行 mvn clean verify"
        );

        Path plugins = temporaryDirectory.resolve("plugins");
        Files.createDirectories(plugins);
        Files.copy(jar, plugins.resolve(jar.getFileName()));

        return plugins;
    }

    /** 写一份真实配置文件，插件目录用相对路径，顺带验证相对路径解析。 */
    private Path writeConfig(boolean courseEnabled) throws IOException {
        Path configFile = temporaryDirectory.resolve(PluginConfig.FILE_NAME);

        Files.writeString(
                configFile,
                "plugins.directory=plugins\n"
                        + SERVICE_ID + ".enabled=" + courseEnabled + "\n",
                StandardCharsets.UTF_8
        );

        return configFile;
    }

    private static ServiceRegistry loadIntoRegistry(PluginLoader loader, PluginConfig config) {
        ServiceRegistry registry = new ServiceRegistry();

        for (CampusService service : loader.loadFromDirectory(config.pluginsDirectory())) {
            registry.register(service);
        }

        return registry;
    }

    private static String runCli(
            String script,
            ServiceRegistry registry,
            ServiceDispatcher dispatcher,
            PluginConfig config
    ) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

        new CommandLineApp(
                new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
                out,
                registry,
                dispatcher,
                config,
                List.of()
        ).run();

        return buffer.toString(StandardCharsets.UTF_8);
    }
}
