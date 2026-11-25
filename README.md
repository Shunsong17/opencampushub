# OpenCampus Hub

校园服务插件平台：统一命令行入口 + 通过 Java SPI 接入的校园服务插件，数据来自本地 CSV，不连接真实教务系统。

- 仓库：<https://github.com/Shunsong17/opencampushub>
- 实施方案：[OpenCampus-Hub-四人小组实施方案.md](OpenCampus-Hub-四人小组实施方案.md)
- 课程插件：[course-plugin/README.md](course-plugin/README.md)
- 契约补充：[docs/api-contract.md](docs/api-contract.md)

## 当前状态

`main` @ `756bb8e`（2026-11-25）。**CLI、配置、插件接入、集成测试、Checkstyle、CI 配置、发布脚本已就绪；公告基础服务（成员 B）尚未实现。**

| 已完成 | 未开始 |
|---|---|
| `campus-api` 契约、`ServiceRegistry`、`ServiceDispatcher`、`PluginLoader`、`course-plugin`、CLI/配置/展示层、`integration-tests`（6 项）、Checkstyle、CI 配置、发布脚本 | 公告服务、`docs/DESIGN.docx`、LICENSE、GitHub 上的真实 CI 运行记录、最终 Tag |

## 1. 用户、场景与范围

**用户**：在校学生与教职工，面向本地样例数据。

**流程**：查看服务目录 → 选择服务并输入条件 → 核心查找并调用服务/插件 → 统一结果或可读错误 → CLI 展示。

**做**：公告查询（核心内置，待实现）、服务注册/发现/调度、一个可独立构建的课程查询插件、配置启禁用、CSV 样例数据与自动化测试、命令行交互。

**不做**：登录认证、真实教务系统/数据库/外部 API、Web 与移动端、插件市场、热加载。

## 2. 快速开始

环境：JDK 17+（编译目标 `release 17`；本机验证用 JDK 25.0.4.1，**JDK 17 运行环境未复测**）、Maven 3.9+（验证用 3.9.16）。仓库根目录执行：

```bash
mvn clean verify
```

预期：`BUILD SUCCESS`，5 个模块，88 项测试通过，Checkstyle 0 违规。

构建后可直接运行（不需要 IDE）。先准备插件目录与本地配置（两者都不入库）：

```bash
mkdir plugins
copy course-plugin\target\course-plugin-0.1.0-SNAPSHOT.jar plugins\      # Windows
# cp course-plugin/target/course-plugin-0.1.0-SNAPSHOT.jar plugins/    # macOS/Linux
copy campus-core\src\main\resources\plugins.properties .\               # 再手动把 course_query.enabled 改成 true
```

`plugins.properties` 需要和 `plugins/` 位于同一目录；相对路径以配置文件所在目录为基准，因此从任意工作目录启动都能找到插件。

启动：

```bash
java -cp "campus-core/target/campus-core-0.1.0-SNAPSHOT.jar;campus-api/target/campus-api-0.1.0-SNAPSHOT.jar" com.opencampus.core.Main
```

macOS/Linux 把 classpath 分隔符 `;` 换成 `:`。实际输出：

```text
配置：D:\...\opencampushub\plugins.properties
插件目录：D:\...\opencampushub\plugins

================================
       OpenCampus Hub
================================
1. 查看可用服务
2. 查询服务
0. 退出
请选择：
```

非交互方式（适合脚本与验收演示）：

```bash
java -cp "..." com.opencampus.core.Main --list
java -cp "..." com.opencampus.core.Main --query course_query name=软件
```

`--query course_query day=周三` 的实际输出：

```text
查询完成，共找到 2 条记录

[1] 软件工程
编号：C001
教师：张老师
时间：周三 08:00-09:35
地点：A201

[2] Java程序设计
编号：C003
教师：王老师
时间：周三 10:00-11:35
地点：C105
```

### 命令行选项

| 选项 | 作用 |
|---|---|
| 无选项 | 启动交互式菜单 |
| `--list` | 列出已启用服务后退出 |
| `--query <服务ID> [参数=值 ...]` | 执行一次查询后退出，例如 `--query course_query name=软件` |
| `--config <文件>` | 指定配置文件；默认取工作目录 `plugins.properties`，没有则用程序内置默认值 |
| `-h` / `--help` | 用法说明 |

退出码：`0` 正常；`1` 配置无法加载；`2` 参数用法错误。查询结果本身失败（参数错误、服务禁用等）不算进程失败，退出码仍是 `0`，错误体现在输出文本里。

### Windows 中文控制台

JDK 19+ 会按控制台代码页输出。若看到乱码，先执行 `chcp 65001` 再运行；发行包的 `run.bat` 已内置这一步。用管道重定向输出时，JVM 可能改用系统本地编码（GBK），需要 UTF-8 文本时加 `-Dstdout.encoding=UTF-8`。

## 3. 核心与扩展

| 模块 | 作用 | 依赖 |
|---|---|---|
| `campus-api` | 公共契约：服务接口、元信息、请求、结果、错误码 | — |
| `campus-core` | 注册中心、调度器、SPI 插件加载、配置、CLI、展示 | `campus-api` |
| `course-plugin` | 课程查询插件，可独立构建 | 仅 `campus-api` |
| `integration-tests` | 跨模块插件接入测试（真实 JAR + 真实核心 + CLI） | `campus-api`、`campus-core`（test） |
| `scripts/` | 发行包构建与解压复验脚本 | — |

插件只依赖公开 API，不得引用 `campus-core` 内部类——`CourseJarIT` 用隔离类加载器断言 `com.opencampus.core.plugin.PluginLoader` 不可见，`PluginIntegrationTest` 还会扫描核心源码，确认没有引用插件包或插件类。

### 配置项 `plugins.properties`

| 键 | 默认 | 说明 |
|---|---|---|
| `announcement.enabled` | `true` | 公告服务开关（成员 B 接入后生效；也可写成 `announcement_query.enabled`） |
| `course_query.enabled` | `false` | 课程插件开关 |
| `plugins.directory` | `plugins` | 插件 JAR 目录，相对路径以配置文件所在目录为基准 |

- 只认 `<serviceId>.enabled=true`；未配置、拼错或值不是 `true/false` 都视为禁用，并在启动诊断中提示，不会导致启动失败。
- 修改配置后必须重启，本项目不做热加载。
- Windows 绝对路径请写成正斜杠（`C:/apps/plugins`），因为 properties 会把反斜杠当转义符；脚本检测到异常字符会给出警告。

### 插件接入（不改核心源码）

1. 独立构建插件：`mvn -pl course-plugin -am clean package` → `course-plugin/target/course-plugin-0.1.0-SNAPSHOT.jar`。
2. 把 JAR 放进 `plugins.directory`。
3. 配置 `course_query.enabled=true`。
4. 重启，`--list` 应出现 `course_query（课程查询）`。

原理：核心启动时扫描目录 → `URLClassLoader` 加载 JAR → `ServiceLoader.load(CampusService.class, pluginClassLoader)` 读到 `META-INF/services/com.opencampus.api.CampusService` 声明的实现类 → 注册到 `ServiceRegistry` → `ServiceDispatcher` 按 `serviceId` 调用。整个过程只认公开 API，核心没有 `if (course)` 之类的分支；启用状态由配置注入调度器，插件自身不读配置。

插件加载失败、注册失败（例如 ID 重复、主版本不兼容）只记录一条诊断，其他服务照常可用。禁用时服务不出现在目录中，按 ID 调用返回 `SERVICE_DISABLED`。

## 4. 测试与检查

| 测试类 | 数量 | 覆盖 |
|---|---:|---|
| `ServiceRegistryTest` | 13 | 注册、查找、注销、重复 ID、元信息校验、版本兼容 |
| `ServiceDispatcherTest` | 15 | 未知服务、禁用、元信息不一致、插件异常、结果契约 |
| `PluginLoaderTest` | 6 | 空目录、真实 JAR 发现、坏插件隔离、损坏 JAR、重复关闭 |
| `CommandLineAppTest` | 9 | 菜单、非法选项、EOF、目录只显示启用服务、参数输入、错误展示 |
| `PluginConfigTest` | 7 | 启禁用、相对/绝对目录、非法布尔值、别名、BOM/反斜杠防御 |
| `ResultPrinterTest` | 6 | 条目/字段标签、未知字段、错误码、null 字段容错、目录渲染 |
| `CourseServiceTest` | 24 | 查询、交集、时间边界、非法参数、坏数据 |
| `CourseSpiTest` | 1 | SPI 发现 |
| `CourseJarIT` | 1 | 真实 JAR + 隔离类加载器 |
| `PluginIntegrationTest` | 6 | SPI 发现、启用目录与字段核对、禁用行为、坏 JAR 隔离、异常隔离、核心无课程专用代码 |

合计 88 项（核心 56 + 插件 25 + 插件 JAR 集成 1 + 跨模块集成 6）。

实测（仓库根目录，2026-11-25，Maven 3.9.16 + JDK 25.0.4.1，Windows 11）：

| 命令 | 结果 |
|---|---|
| `mvn -B clean verify` | 成功，5 个模块，88 项测试，Checkstyle 0 违规 |
| `mvn -B -pl course-plugin -am test` | 成功，25 项（用于故障恢复后复验） |

**故障注入演示**（执行并恢复，证据可复核）：

1. 把 `CourseService` 的星期过滤 `.filter(course -> day.isEmpty() || course.day().equals(day))` 改成 `.filter(course -> day.isEmpty())`。
2. 执行 `mvn -pl course-plugin -am test`，预期星期相关断言失败。实际 25 项中 2 项失败：
   - `CourseServiceTest.dayAndIntersection`：`expected: <2> but was: <0>`
   - `CourseSpiTest.discoversServiceThroughPublicContract`：`expected: <2> but was: <0>`
3. 执行 `git checkout -- course-plugin/src/main/java/com/opencampus/plugin/course/CourseService.java` 恢复，重跑同一命令：25 项全绿。

**Checkstyle**：`checkstyle.xml` 在根目录，绑定在 `validate` 阶段，`mvn clean verify` 与 `mvn checkstyle:check` 都会检查；范围为各模块 `src/main/java`，测试源码不参与。规则只收录误报少的项：行尾换行、行宽 120、Tab、行尾空白、通配导入/多余导入/未使用导入、必须写花括号、一行一条语句、空白与空行等。当前 0 违规。修复过程中给 `campus-api` 的 8 个文件补了缺失的文件末尾换行（纯空白改动，无语义变化）。

**CI**：`.github/workflows/ci.yml` 已提交，在 Ubuntu + Temurin JDK 17 上执行 `mvn -B clean verify` 并上传测试报告。**目前没有任何真实运行记录，不得声称 CI 已通过**；首次推送后用实际运行链接替换本句。

**发布脚本**：`scripts/build-release.ps1`（Windows PowerShell 5.1+）执行 `mvn clean verify` → 组装 `lib/`、`plugins/`、`plugins.properties`、`run.bat`、`run.sh` → 打 ZIP → 解压到临时目录实际启动 `--list` 与课程查询 → 计算 SHA-256 → 在**仓库外** `release/RELEASE.md` 记录版本、Commit、测试数、哈希与复验输出。当前结论为 `HOLD`：公告服务缺失，验收表中"默认显示公告基础服务""查询图书馆公告"两条还不能演示。

## 5. 协作

- 问题放 GitHub Issues；功能走 `feat/<主题>` 分支，修复走 `fix/<主题>`。
- 提交信息 `类型(范围): 简述`，一个提交一件事。
- 功能分支 → PR → 至少一名其他成员 review → 合并，合并前 `mvn clean verify` 必须通过。
- 平台不可用时可本地分支审查，但要在第 6 节记录被审查提交、意见、回应与结论，不虚构平台记录。

## 6. 成员贡献与 AI 核验

| 姓名/学号 | 实质改动（Commit/PR） | 审查他人改动 | AI 使用与核验 |
|---|---|---|---|
| A：胡城玮 | `campus-api` 8 个契约类型；`ServiceRegistry`、`ServiceDispatcher`、`PluginLoader`；核心 34 项测试；`docs/api-contract.md`。`363870c`…`014713a` 经本地合并 `d7e5414` 进入 `main`（未经 PR）；调度器加固经 PR #3（`afdb217`）合并 | 审查 C 的 `3e94c43`（插件契约与错误映射） | 本文件与审查意见由 AI 辅助整理，结论以实际构建/测试复核 |
| B：王湛阳 | 公告服务：未开始 | 待填 | 待填 |
| C：翁政杰 | 课程插件模块全部代码与测试，`3e94c43`（PR #1） | 待填（需审查 D 的集成测试与接入流程） | 插件 README 注明模块由 AI 辅助实现与测试，需本人核对语义 |
| D：吴诗祺 | CLI/配置/展示层（`Main`、`CommandLineApp`、`PluginConfig`、`ResultPrinter`）、`integration-tests` 6 项、Checkstyle 与 CI 配置、发布脚本、README；分支 `feat/d-cli-config-release` 的 4 个提交：`feat(cli)`（CLI/配置/展示）、`chore(build)`（Checkstyle/CI/集成测试）、`feat(release)`（发行脚本）、`docs(readme)` | 待做：审查 B 的数据边界与查询测试（下一步） | 代码与文档由 AI 辅助实现；本人在 JDK 25 + Maven 3.9.16 下实际跑通 88 项测试、多组 CLI 场景（目录、按名称/星期/时间查询、无条件、未知服务、禁用、坏 JAR、交互式会话）、故障注入与发行包解压复验，结论以这些实测输出为准 |

**审查记录**

| 被审查对象 | 审查人 | 意见 | 处理/回应 | 更新提交 | 结论 |
|---|---|---|---|---|---|
| 翁政杰 | 胡城玮 | 9 条，见下 | 待翁政杰回应 | — | 待定 |
| 成员 B 的公告数据边界与查询测试 | 吴诗祺 | 待开展 | — | — | 未开始 |

1. 插件 README 验证数字过期（36 → 38），已由胡城玮更新，翁政杰确认即可。
2. 拒绝未知参数在契约中无依据 —— 已在 `docs/api-contract.md` 明确"插件可拒绝或忽略，须在自身 README 声明"，翁政杰确认即可。
3. 插件自校验 `serviceId` 与 §6.2 核心职责重复 —— 核心已补校验（PR #3），翁政杰保留防御校验即可。
4. `NO_MATCH` 无使用场景 —— 已在 `docs/api-contract.md` 标注"暂不使用"，无需改动代码。
5. 资源 `/courses.csv` 位于 JAR 根，父加载器优先可能撞名，建议包内路径。
6. 只捕获 `IOException`，`RuntimeException` 会中断整批插件加载。
7. `invalid()` 回填 `SERVICE_ID`，应回填请求中的 serviceId。
8. 缺参数值为 null 的测试用例。
9. 参数校验优先于数据错误 —— 已在 `docs/api-contract.md` 写明该优先级，翁政杰确认即可。

## 7. 许可与限制

- **LICENSE 未添加**，交付前必须选定。建议 MIT（简洁）或 Apache-2.0（含专利授权），组内确认后写入。
- 依赖：JUnit 5.11.4（EPL-2.0，测试作用域）、Maven 官方插件与 Checkstyle 10.21.4（Apache-2.0 / LGPL-2.1，构建期），均不随发行物分发。
- 限制：公告基础服务与设计文档未完成；`ResultItem.fields` 只有英文字段名，CLI 用映射表显示中文标签，未知插件字段原样输出；仅 Windows + JDK 25 验证，JDK 17 与 Linux 未在本机复测（CI 首次运行后补充）；纯本地 CSV，无数据库、无并发与权限控制；发布脚本依赖 Windows PowerShell，CI 不构建发行包。
