package com.opencampus.core.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * 读取 plugins.properties，回答两个问题：插件目录在哪、某个服务是否启用。
 *
 * <p>查找顺序：命令行显式指定的文件 → 工作目录下的 plugins.properties → 类路径内置默认值。
 * 相对路径的 plugins.directory 以配置文件所在目录为基准，这样发行包换到任意工作目录启动都能找到插件。
 *
 * <p>启禁用规则：只认 {@code <serviceId>.enabled=true}；未配置或值不是 true 一律视为禁用，
 * 且不会让程序崩溃，只在启动诊断里说明。{@code announcement_query} 额外接受实施方案示例中的
 * {@code announcement.enabled} 作为别名，避免配置键与服务 ID 不一致。
 */
public final class PluginConfig {

    /** 默认配置文件名。 */
    public static final String FILE_NAME = "plugins.properties";

    /** 插件目录配置键。 */
    public static final String PLUGINS_DIRECTORY_KEY = "plugins.directory";

    /** 服务启用标志的后缀，完整键形如 course_query.enabled。 */
    public static final String ENABLED_SUFFIX = ".enabled";

    private static final String DEFAULT_PLUGINS_DIRECTORY = "plugins";

    /** 实施方案 §6.4 的示例用 announcement.enabled，而 §7.1 的服务 ID 是 announcement_query，两者都接受。 */
    private static final Map<String, List<String>> ENABLED_ALIASES = Map.of(
            "announcement_query", List.of("announcement.enabled")
    );

    private final Path source;
    private final Path baseDirectory;
    private final Map<String, String> values;
    private final List<String> warnings;

    private PluginConfig(
            Path source,
            Path baseDirectory,
            Map<String, String> values,
            List<String> warnings
    ) {
        this.source = source;
        this.baseDirectory = baseDirectory;
        this.values = Map.copyOf(values);
        this.warnings = List.copyOf(warnings);
    }

    /**
     * 从指定文件加载配置。
     *
     * @throws IOException 文件不存在、不可读或不是合法的 properties 编码
     */
    public static PluginConfig load(Path file) throws IOException {
        Path absolute = file.toAbsolutePath().normalize();
        String content = Files.readString(absolute, StandardCharsets.UTF_8);

        // 记事本等编辑器保存 UTF-8 时可能带 BOM，不剥掉会变成第一个键名的一部分。
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        Properties properties = new Properties();
        properties.load(new StringReader(content));

        Path parent = absolute.getParent();
        Path base = parent == null ? Path.of("").toAbsolutePath() : parent;

        return fromProperties(absolute, base, properties);
    }

    /**
     * 工作目录有 plugins.properties 就用它，否则用 classpath 里的默认配置，都没有则返回空配置。
     */
    public static PluginConfig loadDefault() throws IOException {
        Path local = Path.of(FILE_NAME);

        if (Files.isRegularFile(local)) {
            return load(local);
        }

        try (InputStream stream = PluginConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (stream == null) {
                return defaults();
            }

            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));

            return fromProperties(null, Path.of("").toAbsolutePath(), properties);
        }
    }

    /** 不含任何配置项的实例：所有服务禁用，插件目录为工作目录下的 plugins。 */
    public static PluginConfig defaults() {
        return new PluginConfig(null, Path.of("").toAbsolutePath(), Map.of(), List.of());
    }

    private static PluginConfig fromProperties(
            Path source,
            Path baseDirectory,
            Properties properties
    ) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        for (String rawKey : properties.stringPropertyNames()) {
            String key = rawKey.trim();
            String value = properties.getProperty(rawKey).trim();
            values.put(key, value);

            if (key.endsWith(ENABLED_SUFFIX) && !isBooleanLiteral(value)) {
                warnings.add("配置项 " + key + " 的值不是 true/false：\"" + value
                        + "\"，按未启用处理");
            }

            // properties 会把 \t、\f 当转义字符，Windows 绝对路径写成 C:\temp 就会被悄悄改写。
            if (key.equals(PLUGINS_DIRECTORY_KEY) && (value.indexOf('\t') >= 0 || value.indexOf('\f') >= 0)) {
                warnings.add("配置项 " + key + " 含制表符等异常字符：Windows 路径请写成正斜杠，"
                        + "例如 C:/apps/plugins，或把反斜杠写成 \\\\");
            }
        }

        return new PluginConfig(source, baseDirectory, values, warnings);
    }

    /**
     * 服务是否启用。未配置、值非法、服务 ID 为空都返回 false，由调用方决定如何提示。
     */
    public boolean isEnabled(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return false;
        }

        Boolean configured = booleanValue(serviceId + ENABLED_SUFFIX);

        if (configured != null) {
            return configured;
        }

        for (String alias : ENABLED_ALIASES.getOrDefault(serviceId, List.of())) {
            Boolean fromAlias = booleanValue(alias);

            if (fromAlias != null) {
                return fromAlias;
            }
        }

        return false;
    }

    /** 解析后的插件目录绝对路径。 */
    public Path pluginsDirectory() {
        String configured = values.get(PLUGINS_DIRECTORY_KEY);

        if (configured == null || configured.isBlank()) {
            configured = DEFAULT_PLUGINS_DIRECTORY;
        }

        Path directory = Path.of(configured);

        return directory.isAbsolute()
                ? directory.normalize()
                : baseDirectory.resolve(directory).normalize();
    }

    /** 配置来源；内置默认值没有对应文件。 */
    public Optional<Path> source() {
        return Optional.ofNullable(source);
    }

    /** 加载阶段发现的可读问题，例如 true/fase 之外的布尔值。 */
    public List<String> warnings() {
        return warnings;
    }

    private Boolean booleanValue(String key) {
        String value = values.get(key);

        if (value == null) {
            return null;
        }

        if (isTrue(value)) {
            return Boolean.TRUE;
        }

        // "false" 与非法值都返回 FALSE，非法值已在 warnings 里说明。
        return Boolean.FALSE;
    }

    private static boolean isBooleanLiteral(String value) {
        return isTrue(value) || "false".equalsIgnoreCase(value);
    }

    private static boolean isTrue(String value) {
        return "true".equalsIgnoreCase(value);
    }
}
