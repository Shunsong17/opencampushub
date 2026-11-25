# 公告基础服务（成员 B）

公告服务位于 `com.opencampus.core.announcement`，是 `campus-core` 的内置基础能力，
实现 `campus-api` 的 `CampusService`。服务 ID 为 `announcement_query`，API 版本为 `1.0`。
当前已完成数据加载、查询和注册/调度集成测试，CLI 与配置框架已从 main 同步；
公告服务在 `Main` 中的注册入口仍由成员 D 接入。

## 查询规则

| 参数 | 是否必填 | 行为 |
|---|---|---|
| `keyword` | 否 | 标题或正文的包含匹配，英文忽略大小写 |
| `category` | 否 | 分类精确匹配，例如 `活动`、`服务通知` |

- 参数值去除首尾空白；值为 `null` 或空白时视为未提供。
- 两个条件都提供时取交集；都未提供时列出全部公告，保持 CSV 原始顺序。
- 空筛选应传 `Map.of()`；整个参数表为 `null`、请求为空、服务 ID 错误、未知参数或空参数名返回 `INVALID_PARAMETER`。
- 正常但无匹配返回 `success=true`、空 `items` 和“未找到匹配结果”，不使用 `NO_MATCH`。
- 数据文件缺失、读取失败或格式错误返回 `DATA_LOAD_FAILED`，不向调用方抛出 CSV 读取异常。
- 请求校验优先于数据可用性检查，非法请求不被数据错误掩盖。

输出 `ResultItem.title` 为公告标题，`fields` 包含 `id`、`category`、`date`、`content`。
例如 `keyword=图书馆` 返回 A002，标题“图书馆闭馆通知”、分类“服务通知”、日期 `2026-11-22`。

## 样例数据与格式

`src/main/resources/announcements.csv` 随核心 JAR 打包，从 classpath 读取，不依赖当前工作目录。
服务构造时读取一次，更新样例数据后需重新构建并创建服务实例。

- UTF-8 编码，支持文件开头 BOM 和空白行。
- 表头固定为 `id,title,category,date,content`；每条公告五列，所有字段必填，ID 唯一。
- 日期必须是有效的 `yyyy-MM-dd`，例如 `2026-11-22`；无效日历日期会被拒绝。
- 支持用双引号包裹含英文逗号的字段，字段内双引号写为 `""`；不支持字段内换行。
- 引号必须紧贴字段边界；字段值去除首尾空白。
- 任意坏行使整份数据加载失败，提示包含行号，不返回部分数据；仅有表头的文件是合法空数据集。
- 加载器负责关闭输入流。没有引入额外运行时依赖。

## 提供给 D 的接入方式

在应用初始化时注册一次，查询统一通过调度器调用：

```java
import com.opencampus.api.QueryRequest;
import com.opencampus.api.QueryResult;
import com.opencampus.core.announcement.AnnouncementService;
import com.opencampus.core.dispatcher.ServiceDispatcher;
import com.opencampus.core.registry.ServiceRegistry;
import java.util.Map;

ServiceRegistry registry = new ServiceRegistry();
registry.register(new AnnouncementService());
ServiceDispatcher dispatcher = new ServiceDispatcher(registry);
QueryResult result = dispatcher.dispatch(new QueryRequest(
        AnnouncementService.SERVICE_ID,
        Map.of("keyword", "图书馆", "category", "服务通知")));
```

目录通过 `registry.listMetadata()` 获取参数说明；CLI 展示统一结果，不直接调用公告实现。
公告无需 SPI 描述文件，也不依赖 `course-plugin`。启禁用由核心启动配置负责，公告服务本身不读取插件配置。

## 测试与本地验证

在仓库根目录执行：

```powershell
mvn -pl campus-core -am test
mvn -B clean verify
```

原始公告分支基线 `5b4aea7` 于 2026-11-24 实测，Maven 3.9.11、JDK 21.0.12.1，编译目标 `release 17`：

- `AnnouncementServiceTest`：35 项，覆盖元信息、标题/正文关键词、分类、交集、空条件、无匹配、错误请求、CSV 引号/日期/坏行、读取失败与校验优先级。
- `AnnouncementIntegrationTest`：3 项，经过真实注册中心和调度器验证成功、无匹配和数据加载失败结果。
- 核心模块与依赖测试共 72 项通过；全项目 `clean verify` 共 98 项通过。
- 在工程外临时目录，仅通过 API JAR 和核心 JAR 调用公告服务，成功查到 A002，空条件返回 5 条；复现命令见下。
- 尚未在 JDK 17 运行环境复测；本地测试成功不代表 CI 成功。

2026-11-25 同步 `main` @ `5d32020` 后，在 Maven 3.9.11、JDK 21.0.12.1、Windows 11 下重新执行完整
`clean verify`：5 个模块构建成功，核心 94 项、课程插件 25 项、插件 JAR 集成 1 项、跨模块集成 6 项，
合计 126 项全部通过，Checkstyle 0 违规。上述 98 项为原始基线数字，当前分支以本段复验结果为准。

工程外 JAR 验证：先在仓库根目录完成上面的 `mvn -B clean verify`，再从仓库根目录执行以下 PowerShell 命令。
它将临时 Java 源文件写到系统临时目录并切换到该目录运行，classpath 仅包含 API 和核心两个 JAR，
不包含源码或 `target/classes`。Java 源文件启动模式要求 JDK 17+，断言失败会以非零退出码报错。

```powershell
$repoPath = (Get-Location).Path
$apiJar = (Resolve-Path (Join-Path $repoPath 'campus-api/target/campus-api-0.1.0-SNAPSHOT.jar')).Path
$coreJar = (Resolve-Path (Join-Path $repoPath 'campus-core/target/campus-core-0.1.0-SNAPSHOT.jar')).Path
$smokeDir = Join-Path ([System.IO.Path]::GetTempPath()) ('opencampus-announcement-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $smokeDir -ErrorAction Stop | Out-Null
@'
import com.opencampus.api.QueryRequest;
import com.opencampus.core.announcement.AnnouncementService;
import java.util.Map;

class AnnouncementSmoke {
    public static void main(String[] args) {
        var service = new AnnouncementService();
        var match = service.query(new QueryRequest(AnnouncementService.SERVICE_ID,
                Map.of("keyword", "\u56fe\u4e66\u9986")));
        if (!match.success() || match.items().size() != 1
                || !"A002".equals(match.items().get(0).fields().get("id"))) {
            throw new AssertionError("Expected A002: " + match);
        }
        var all = service.query(new QueryRequest(AnnouncementService.SERVICE_ID, Map.of()));
        if (!all.success() || all.items().size() != 5) {
            throw new AssertionError("Expected 5 announcements: " + all);
        }
        System.out.println("A002 verified; all announcements: " + all.items().size());
    }
}
'@ | Set-Content -LiteralPath (Join-Path $smokeDir 'AnnouncementSmoke.java') -Encoding ASCII
Push-Location -LiteralPath $smokeDir
try {
    java --class-path "$apiJar;$coreJar" .\AnnouncementSmoke.java
    if ($LASTEXITCODE -ne 0) { throw 'Announcement JAR verification failed' }
} finally {
    Pop-Location
}
```

预期输出：`A002 verified; all announcements: 5`。源码中的 `\u56fe\u4e66\u9986` 为“图书馆”，
使用 Unicode 转义以避免 PowerShell 版本或控制台编码影响验证。两个断言同时验证了打包后的样例资源可读取。

故障注入已在临时工程副本执行：将 `AnnouncementService` 中
`announcement.category().equals(category)` 临时改为
`!announcement.category().equals(category)`，运行：

```powershell
mvn -pl campus-core -am "-Dtest=AnnouncementServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

35 项测试中 4 项断言失败、0 项执行错误，构建失败；恢复原条件后 35 项全部通过。
原仓库未保留故障版本。可用相同步骤在临时副本重复演示。

## 协作与核验状态

实现与测试由 AI 辅助完成，已通过上述本地验证。空条件列出全部公告的规则已写入元信息和断言，
仍需成员 B 本人核对需求、解释实现并确认采用。成员 A 或 D 的人工 Review、意见处理和复核尚待完成；
成员 B 审查他人改动的记录需按实际过程补充，不能用自动测试或 AI 说明替代。
