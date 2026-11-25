# OpenCampus Hub

校园服务插件平台：统一命令行入口 + 通过 Java SPI 接入的校园服务插件，数据来自本地 CSV，不连接真实教务系统。

- 仓库：<https://github.com/Shunsong17/opencampushub>
- 实施方案：[OpenCampus-Hub-四人小组实施方案.md](OpenCampus-Hub-四人小组实施方案.md)
- 课程插件：[course-plugin/README.md](course-plugin/README.md)
- 契约补充：[docs/api-contract.md](docs/api-contract.md)
- 公告基础服务与 D 的接入说明：[campus-core/README.md](campus-core/README.md)

## 当前状态

2026-11-25 合并版本：保留 main 的交互式 CLI、配置、契约校验、公告源码、CI、Checkstyle 和发布脚本，接入角色 C 的课程、图书、食堂、活动四个插件。公告源码已经合入，但尚未在 Main 注册；下方旧验证数字与审查内容作为历史记录保留，本次多插件合并说明见下一节。

### 四插件运行与合并说明

2026-11-26 本地合并验证：`mvn -B clean verify` 成功，8 个 reactor 模块、162 项测试通过（核心 94、课程 26、新增插件 30、跨模块集成 12），Checkstyle 零违规。保留双方提交历史与已有审查记录；本次验证不代表 GitHub CI 已通过。

根目录执行 `mvn clean verify` 后：

```powershell
.\scripts\prepare-plugins.ps1
.\scripts\run.ps1 list
.\scripts\run.ps1 query library_query "name=Java"
.\scripts\run.ps1 query canteen_query "date=2026-11-25" "meal=午餐"
.\scripts\run.ps1 query activity_query "category=讲座" "date=2026-11-25"
```

如果本机策略阻止脚本，可在允许执行仓库脚本的前提下使用
`powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run.ps1 list`；
准备脚本同理，仅对单次进程放行。运行脚本保持 list/query 简写，内部转换为 main 的 --list/--query 和 --config。
直接调用 Java 时使用 main 的选项格式，不能使用旧的“配置文件 list/query”位置参数。

| 服务 ID | 文档 | 条件 |
|---|---|---|
| course_query | [课程](course-plugin/README.md) | name、day、time |
| library_query | [图书](library-plugin/README.md) | name、author、category |
| canteen_query | [食堂](canteen-plugin/README.md) | name、canteen、date、meal |
| activity_query | [活动](activity-plugin/README.md) | keyword、category、date |

根目录 plugins.properties 是四插件启用示例，目录按配置文件位置解析。开关沿用 main：未配置或非法值禁用，非法值产生警告。已发现的禁用服务查询返回 SERVICE_DISABLED；JAR 不存在则返回 SERVICE_NOT_FOUND。只读本地样例，日期不会自动滚动到当天。
准备脚本仅更新四个同名插件 JAR。各插件仅依赖 campus-api，仍是同 JVM 插件；独立构建示例：`mvn -pl library-plugin -am clean package`。

加载器兼容 main 的 Report/loadWithDiagnostics 接口，同时保留结构化诊断、重复 ID 检测和逐 JAR 故障隔离。
集成测试同时运行 D 的 PluginIntegrationTest 和 C 的 PluginsIT，业务插件从测试运行类路径排除，只加载真实 JAR。新增插件代码遵守 main 的 Checkstyle。
现有 build-release.ps1 仍只组装课程插件发行包；四插件本地运行使用 prepare-plugins.ps1，不将旧发布记录视为四插件发行验证。

| 已完成（main） | 待完成 |
|---|---|
| `campus-api` 契约、`ServiceRegistry`、`ServiceDispatcher`、`PluginLoader`、`course-plugin`、CLI/配置/展示层、`integration-tests`（6 项）、Checkstyle、CI 配置、发布脚本 | 公告服务接入（代码在 `5b4aea7`，已审查）、`docs/DESIGN.docx`、LICENSE、GitHub 上的真实 CI 运行记录、最终 Tag |

当前公告功能分支已同步 `main` @ `5d32020`，保留公告服务实现和 `5163f3f` 的评审修复。
下文保留 main 的状态、贡献和审查记录；公告服务的 `Main` 注册接入仍待完成，分支同步不代表 PR 已合并。

## 1. 用户、场景与范围

**用户**：在校学生与教职工，面向本地样例数据。

**流程**：查看服务目录 → 选择服务并输入条件 → 核心查找并调用服务/插件 → 统一结果或可读错误 → CLI 展示。

**做**：公告查询（核心内置，代码在 B 分支，待合并接入）、服务注册/发现/调度、一个可独立构建的课程查询插件、配置启禁用、CSV 样例数据与自动化测试、命令行交互。

**不做**：登录认证、真实教务系统/数据库/外部 API、Web 与移动端、插件市场、热加载。

## 2. 快速开始

环境：JDK 17+（编译目标 `release 17`；本机验证用 JDK 25.0.4.1，**JDK 17 运行环境未复测**）、Maven 3.9+（验证用 3.9.16）。仓库根目录执行：

```bash
mvn clean verify
```

预期：`BUILD SUCCESS`，5 个模块；当前公告功能分支为 126 项测试，main 基线为 88 项，Checkstyle 0 违规。

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
| `campus-core` | 注册中心、调度器、SPI 插件加载、内置公告查询、配置、CLI、展示 | `campus-api` |
| `course-plugin` | 课程查询插件，可独立构建 | 仅 `campus-api` |
| `integration-tests` | 跨模块插件接入测试（真实 JAR + 真实核心 + CLI） | `campus-api`、`campus-core`（test） |
| `scripts/` | 发行包构建与解压复验脚本 | — |

插件只依赖公开 API，不得引用 `campus-core` 内部类——`CourseJarIT` 用隔离类加载器断言 `com.opencampus.core.plugin.PluginLoader` 不可见，`PluginIntegrationTest` 还会扫描核心源码，确认没有引用插件包或插件类。

公告服务 `announcement_query` 支持关键词和分类交集筛选，空条件列出全部公告。
它通过统一注册中心与调度器调用，查询规则、数据格式及接入示例见 [campus-core/README.md](campus-core/README.md)。

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
| `AnnouncementServiceTest` | 35 | 关键词、分类、交集、空条件、无匹配、错误参数、CSV 与数据读取失败及错误行号 |
| `AnnouncementIntegrationTest` | 3 | 公告注册、调度及统一结果/错误契约 |
| `CommandLineAppTest` | 9 | 菜单、非法选项、EOF、目录只显示启用服务、参数输入、错误展示 |
| `PluginConfigTest` | 7 | 启禁用、相对/绝对目录、非法布尔值、别名、BOM/反斜杠防御 |
| `ResultPrinterTest` | 6 | 条目/字段标签、未知字段、错误码、null 字段容错、目录渲染 |
| `CourseServiceTest` | 24 | 查询、交集、时间边界、非法参数、坏数据 |
| `CourseSpiTest` | 1 | SPI 发现 |
| `CourseJarIT` | 1 | 真实 JAR + 隔离类加载器 |
| `PluginIntegrationTest` | 6 | SPI 发现、启用目录与字段核对、禁用行为、坏 JAR 隔离、异常隔离、核心无课程专用代码 |

当前公告功能分支合计 126 项（核心 94 + 插件 25 + 插件 JAR 集成 1 + 跨模块集成 6）。main 基线为 88 项（不含公告的 38 项测试）。

成员 B 的公告测试（`AnnouncementServiceTest` 35 + `AnnouncementIntegrationTest` 3）已包含在上表。D 审查时曾将公告分支与 main 临时合并实测：**126 项全部通过**（核心 94 + 插件 25 + 插件 JAR 1 + 跨模块集成 6），Checkstyle 0 违规。

main 基线实测（仓库根目录，2026-11-25，Maven 3.9.16 + JDK 25.0.4.1，Windows 11）：

| 命令 | 结果 |
|---|---|
| `mvn -B clean verify` | 成功，5 个模块，88 项测试，Checkstyle 0 违规 |
| `mvn -B -pl course-plugin -am test` | 成功，25 项（用于故障恢复后复验） |

当前公告功能分支同步 `main` @ `5d32020` 后，于 2026-11-25 在 Windows 11、Maven 3.9.11、
JDK 21.0.12.1 下完成完整 `clean verify`：5 个模块成功，126 项测试全部通过，Checkstyle 0 违规。
公告工程外 JAR 验证命令及原始基线记录见 [campus-core/README.md](campus-core/README.md)。

**故障注入演示**（执行并恢复，证据可复核）：

1. 把 `CourseService` 的星期过滤 `.filter(course -> day.isEmpty() || course.day().equals(day))` 改成 `.filter(course -> day.isEmpty())`。
2. 执行 `mvn -pl course-plugin -am test`，预期星期相关断言失败。实际 25 项中 2 项失败：
   - `CourseServiceTest.dayAndIntersection`：`expected: <2> but was: <0>`
   - `CourseSpiTest.discoversServiceThroughPublicContract`：`expected: <2> but was: <0>`
3. 执行 `git checkout -- course-plugin/src/main/java/com/opencampus/plugin/course/CourseService.java` 恢复，重跑同一命令：25 项全绿。

**Checkstyle**：`checkstyle.xml` 在根目录，绑定在 `validate` 阶段，`mvn clean verify` 与 `mvn checkstyle:check` 都会检查；范围为各模块 `src/main/java`，测试源码不参与。规则只收录误报少的项：行尾换行、行宽 120、Tab、行尾空白、通配导入/多余导入/未使用导入、必须写花括号、一行一条语句、空白与空行等。当前 0 违规。修复过程中给 `campus-api` 的 8 个文件补了缺失的文件末尾换行（纯空白改动，无语义变化）。

**CI**：`.github/workflows/ci.yml` 已提交，在 Ubuntu + Temurin JDK 17 上执行 `mvn -B clean verify` 并上传测试报告。**目前没有任何真实运行记录，不得声称 CI 已通过**；首次推送后用实际运行链接替换本句。

**发布脚本**：`scripts/build-release.ps1`（Windows PowerShell 5.1+）执行 `mvn clean verify` → 组装 `lib/`、`plugins/`、`plugins.properties`、`run.bat`、`run.sh` → 打 ZIP → 解压到临时目录实际启动 `--list` 与课程查询 → 计算 SHA-256 → 在**仓库外** `release/RELEASE.md` 记录版本、Commit、测试数、哈希与复验输出。当前结论为 `HOLD`：公告服务尚未合并接入，验收表中"默认显示公告基础服务""查询图书馆公告"两条还不能演示。

## 5. 协作

- 问题放 GitHub Issues；功能走 `feat/<主题>` 分支，修复走 `fix/<主题>`。
- 提交信息 `类型(范围): 简述`，一个提交一件事。
- 功能分支 → PR → 至少一名其他成员 review → 合并，合并前 `mvn clean verify` 必须通过。
- 平台不可用时可本地分支审查，但要在第 6 节记录被审查提交、意见、回应与结论，不虚构平台记录。

## 6. 成员贡献与 AI 核验

| 姓名/学号 | 实质改动（Commit/PR） | 审查他人改动 | AI 使用与核验 |
|---|---|---|---|
| A：胡城玮 | `campus-api` 8 个契约类型；`ServiceRegistry`、`ServiceDispatcher`、`PluginLoader`；核心 34 项测试；`docs/api-contract.md`。`363870c`…`014713a` 经本地合并 `d7e5414` 进入 `main`（未经 PR）；调度器加固经 PR #3（`afdb217`）合并 | 审查 C 的 `3e94c43`（插件契约与错误映射） | 本文件与审查意见由 AI 辅助整理，结论以实际构建/测试复核 |
| B：王湛阳 | `feat/role-b-announcement-service` @ `5b4aea7`：公告模型、CSV 加载、查询、5 条样例、35 项单元测试 + 3 项注册/调度集成测试、`campus-core/README.md` | 尚待 B 本人审查他人改动 | AI 辅助实现与测试；D 已复核其测试数字与故障注入（4 项失败）并完成代码审查，结论见下 |
| C：翁政杰 | 课程插件 `3e94c43`（PR #1）；图书、食堂、活动插件、多插件测试与运行脚本 `4af7351`；本次合并适配 main CLI/配置/Checkstyle | 待填（需本人审查 D 的集成测试与接入流程） | AI 辅助实现、冲突解决与测试；实际验证结果见本次合并记录 |
| D：吴诗祺 | CLI/配置/展示层（`Main`、`CommandLineApp`、`PluginConfig`、`ResultPrinter`）、`integration-tests` 6 项、Checkstyle 与 CI 配置、发布脚本、README；分支 `feat/d-cli-config-release` 的 4 个提交：`feat(cli)`（CLI/配置/展示）、`chore(build)`（Checkstyle/CI/集成测试）、`feat(release)`（发行脚本）、`docs(readme)` | 审查 B 的 `5b4aea7`（公告服务，9 条，有条件通过，见下） | 代码与文档由 AI 辅助实现；本人在 JDK 25 + Maven 3.9.16 下实际跑通 88 项测试、多组 CLI 场景（目录、按名称/星期/时间查询、无条件、未知服务、禁用、坏 JAR、交互式会话）、故障注入与发行包解压复验，结论以这些实测输出为准 |

**审查记录**

| 被审查对象 | 审查人 | 意见 | 处理/回应 | 更新提交 | 结论 |
|---|---|---|---|---|---|
| 翁政杰 `3e94c43` | 胡城玮 | 9 条，见下 | 待翁政杰回应 | — | 待定 |
| 王湛阳 `5b4aea7`（`feat/role-b-announcement-service`，公告服务） | 吴诗祺（D） | 9 条，见下 | 待王湛阳回应；接入 `Main` 由 D 在合并后完成 | — | 有条件通过 |

**A 审查 C（`3e94c43`）**

1. 插件 README 验证数字过期（36 → 38），已由胡城玮更新，翁政杰确认即可。
2. 拒绝未知参数在契约中无依据 —— 已在 `docs/api-contract.md` 明确"插件可拒绝或忽略，须在自身 README 声明"，翁政杰确认即可。
3. 插件自校验 `serviceId` 与 §6.2 核心职责重复 —— 核心已补校验（PR #3），翁政杰保留防御校验即可。
4. `NO_MATCH` 无使用场景 —— 已在 `docs/api-contract.md` 标注"暂不使用"，无需改动代码。
5. 资源 `/courses.csv` 位于 JAR 根，父加载器优先可能撞名，建议包内路径。
6. 只捕获 `IOException`，`RuntimeException` 会中断整批插件加载。
7. `invalid()` 回填 `SERVICE_ID`，应回填请求中的 serviceId。
8. 缺参数值为 null 的测试用例。
9. 参数校验优先于数据错误 —— 已在 `docs/api-contract.md` 写明该优先级，翁政杰确认即可。

**D 审查 B（`5b4aea7`）**

复核方式：把 `origin/feat/role-b-announcement-service` 检出到临时 worktree 只读审查；故障注入在临时副本中执行；仓库与 B 的分支未改动。实测：B 分支 98 项通过；合并 `main` 后 126 项通过、Checkstyle 0 违规；合并仅 `README.md` 冲突。

1. **P0**：`AnnouncementService` 未在 `Main.registerBuiltinServices` 注册，合并后 `--list` 输出"当前没有已启用的服务"，公告服务对用户不可见。这不是 B 的代码缺陷，需 D 在合并后补注册并加一条 CLI 级测试。
2. **P0**：B 基于 `756bb8e` 修改 `README.md`，与 `main` 唯一冲突即此文件；其版本会把 D 的工作写成"未开始"、把测试数写回 98。请 rebase 后只保留公告增量，状态/数字/贡献表由 D 维护。
3. **P1**：空条件列出全部公告依赖独立分支 `docs/announcement-contract` @ `4a830c2` 的裁定，该分支不是 B 分支的祖先。合并顺序须先合契约分支，或在 PR 中引用该条款，否则实现看起来与 §5.4 旧表述冲突。
4. **P1**：`campus-core/README.md` 的"98 项"是 `5b4aea7` 基线数字，合并后为 126 项，建议改成"基线 + 合并后"两段，避免过期。
5. **P2**：`getResourceAsStream("/announcements.csv")` 位于 JAR 根，与 C 的 `/courses.csv` 同类风险，建议改包内路径（与 A 的第 5 条一并处理）。
6. **P2**：`invalid()` 在 `request == null` 时回填 `null` serviceId，生产路径由调度器拦截、不会触发；建议在公告 README 注明。
7. **P2**：35 项测试中多条断言依赖样例数据具体内容（A001–A005），改样例会碎；格式类测试已用 `fromCsv` 注入，设计合理，建议 README 注明即可。
8. **P2**：可选补两条测试——`category` 大小写敏感性；未知参数与数据加载失败同时发生的优先级（已有单侧覆盖）。
9. **P2**：命名风格与 A 的 `shouldXxx` 不一致（团队未统一，不阻塞）；`AnnouncementIntegrationTest` 由 surefire 在 `campus-core` 执行，注意别与 failsafe 的 `*IntegrationTest` 规则混淆。

## 7. 许可与限制

- **LICENSE 未添加**，交付前必须选定。建议 MIT（简洁）或 Apache-2.0（含专利授权），组内确认后写入。
- 依赖：JUnit 5.11.4（EPL-2.0，测试作用域）、Maven 官方插件与 Checkstyle 10.21.4（Apache-2.0 / LGPL-2.1，构建期），均不随发行物分发。
- 限制：公告服务已审查但未合并接入；设计文档未完成；`ResultItem.fields` 只有英文字段名，CLI 用映射表显示中文标签，未知插件字段原样输出；仅 Windows + JDK 25 验证，JDK 17 与 Linux 未在本机复测（CI 首次运行后补充）；纯本地 CSV，无数据库、无并发与权限控制；发布脚本依赖 Windows PowerShell，CI 不构建发行包。
