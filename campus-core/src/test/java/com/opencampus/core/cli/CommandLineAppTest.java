package com.opencampus.core.cli;

import com.opencampus.core.cli.fixture.SampleService;
import com.opencampus.core.config.PluginConfig;
import com.opencampus.core.dispatcher.ServiceDispatcher;
import com.opencampus.core.registry.ServiceRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLineAppTest {

    private static final String ENABLED_CONFIG = "sample_query.enabled=true\n";

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldExitOnZero() throws IOException {
        String output = runSession("0\n", ENABLED_CONFIG);

        assertTrue(output.contains("再见"), output);
    }

    @Test
    void shouldRejectUnknownOptionAndKeepRunning() throws IOException {
        String output = runSession("9\n0\n", ENABLED_CONFIG);

        assertTrue(output.contains("无效选项"), output);
        assertTrue(output.contains("再见"), output);
    }

    @Test
    void shouldExitWhenInputEnds() {
        assertDoesNotThrow(() -> runSession("", ENABLED_CONFIG));
    }

    @Test
    void shouldShowEnabledServiceInCatalog() throws IOException {
        String output = runSession("1\n0\n", ENABLED_CONFIG);

        assertTrue(output.contains("样例服务"), output);
        assertTrue(output.contains("sample_query"), output);
        assertTrue(output.contains("keyword（可选）：关键词"), output);
    }

    @Test
    void shouldHideDisabledServiceAndExplainWhy() throws IOException {
        String output = runSession("1\n0\n", "sample_query.enabled=false\n");

        assertTrue(output.contains("当前没有已启用的服务"), output);
        assertFalse(output.contains("sample_query"), output);
    }

    @Test
    void shouldQueryWithParameterAndRenderFields() throws IOException {
        String output = runSession("2\nsample_query\n软件\n0\n", ENABLED_CONFIG);

        assertTrue(output.contains("查询完成，共找到 1 条记录"), output);
        assertTrue(output.contains("[1] 软件工程"), output);
        assertTrue(output.contains("教师：张老师"), output);
        assertTrue(output.contains("时间：周三 08:00-09:35"), output);
    }

    @Test
    void shouldReturnToMenuWhenServiceIdIsBlank() throws IOException {
        String output = runSession("2\n\n0\n", ENABLED_CONFIG);

        assertFalse(output.contains("查询完成"), output);
        assertTrue(output.contains("再见"), output);
    }

    @Test
    void shouldReportUnknownServiceWithoutStackTrace() throws IOException {
        String output = runSession("2\nunknown_query\n0\n", ENABLED_CONFIG);

        assertTrue(output.contains("SERVICE_NOT_FOUND"), output);
        assertFalse(output.contains("Exception"), output);
    }

    @Test
    void shouldTreatBlankConditionAsMissingButStillCallService() throws IOException {
        String output = runSession("2\nsample_query\n\n0\n", ENABLED_CONFIG);

        // 空白输入视为"未提供"，CLI 不替插件拒绝请求，仍交给服务判断。
        assertTrue(output.contains("查询完成"), output);
    }

    private String runSession(String script, String configContent) throws IOException {
        Path configFile = temporaryDirectory.resolve(PluginConfig.FILE_NAME);
        Files.writeString(configFile, configContent, StandardCharsets.UTF_8);

        PluginConfig config = PluginConfig.load(configFile);
        ServiceRegistry registry = new ServiceRegistry();
        registry.register(new SampleService());

        ServiceDispatcher dispatcher = new ServiceDispatcher(registry, config::isEnabled);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

        new CommandLineApp(
                new ByteArrayInputStream(script.getBytes(StandardCharsets.UTF_8)),
                out,
                registry,
                dispatcher,
                config,
                List.of("配置：测试")
        ).run();

        return buffer.toString(StandardCharsets.UTF_8);
    }
}
