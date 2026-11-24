# OpenCampus Hub

校园服务插件平台：统一命令行入口 + 通过 Java SPI 接入的校园服务插件，数据来自本地 CSV，不连接真实教务系统。

- 仓库：<https://github.com/Shunsong17/opencampushub>
- 实施方案：[OpenCampus-Hub-四人小组实施方案.md](OpenCampus-Hub-四人小组实施方案.md)
- 课程插件：[course-plugin/README.md](course-plugin/README.md)

## 当前状态

`main` @ `d7e5414`（2026-11-24）。**项目进行中，还不能端到端运行。**

| 已完成 | 未开始 |
|---|---|
| `campus-api` 契约（8 个类型）、`ServiceRegistry`、`ServiceDispatcher`、`PluginLoader`、`course-plugin` 课程插件与测试 | 公告服务、CLI、配置、集成测试、Checkstyle、CI、设计文档、LICENSE、发行包 |

插件加载已做到：按目录扫描 JAR、单个插件失败只记诊断不影响其他插件、`apiVersion` 主版本校验、`close()` 关闭类加载器。调度器已实现启用判断、服务 ID 一致性校验和结果契约校验。

## 1. 用户、场景与范围

**用户**：在校学生与教职工，面向本地样例数据。

**流程**：查看服务目录 → 输入查询条件 → 核心查找并调用服务/插件 → 统一结果或可读错误 → CLI 展示。

**做**：公告查询（核心内置）、服务注册/发现/调度、一个可独立构建的课程查询插件、配置启禁用、CSV 样例数据与自动化测试。

**不做**：登录认证、真实教务系统/数据库/外部 API、Web 与移动端、插件市场、热加载。

## 2. 快速开始

环境：JDK 17+（编译目标 `release 17`，验证用 JDK 21.0.12.1，**JDK 17 未复测**）、Maven 3.9+（验证用 3.9.11）。

在仓库根目录：

```bash
mvn clean verify
```

预期：`BUILD SUCCESS`，60 项测试（核心 34 + 插件单元 25 + JAR 集成 1）。

**运行：尚未实现。** 没有 CLI 入口，应用无法启动，现有能力只能用测试验证。CLI 落地后补启动命令与样例输出。

Windows 中文控制台建议先 `chcp 65001`，否则 Maven 输出的中文路径是乱码。

## 3. 核心与扩展

| 模块 | 作用 | 依赖 |
|---|---|---|
| `campus-api` | 公共契约：服务接口、元信息、请求、结果、错误码 | — |
| `campus-core` | 注册中心、调度器、SPI 插件加载 | `campus-api` |
| `course-plugin` | 课程查询插件，可独立构建 | 仅 `campus-api` |
| `integration-tests` | 跨模块接入测试（计划中，未创建） | — |

插件只依赖公开 API，不得引用 `campus-core` 内部类——`CourseJarIT` 用隔离类加载器断言 `com.opencampus.core.plugin.PluginLoader` 不可见。

契约细节（错误码分工、未知参数、校验优先级、结果契约、版本兼容）见 [docs/api-contract.md](docs/api-contract.md)。

独立构建：`mvn -pl course-plugin -am clean package`，产物 `course-plugin/target/course-plugin-0.1.0-SNAPSHOT.jar`。

接入约定：JAR 放入 `plugins.directory`，`plugins.properties` 中 `course_query.enabled=true`，重启生效（无热加载）。**该流程尚未实现，无法验证**：配置与 CLI 都还没有。设计文档 `docs/DESIGN.docx` 未创建。

## 4. 测试与检查

| 测试类 | 数量 | 覆盖 |
|---|---:|---|
| `ServiceRegistryTest` | 13 | 注册、查找、注销、重复 ID、元信息校验、版本兼容 |
| `ServiceDispatcherTest` | 15 | 未知服务、禁用、元信息不一致、插件异常、结果契约 |
| `PluginLoaderTest` | 6 | 空目录、真实 JAR 发现、坏插件隔离、损坏 JAR、重复关闭 |
| `CourseServiceTest` | 24 | 查询、交集、时间边界、非法参数、坏数据 |
| `CourseSpiTest` | 1 | SPI 发现 |
| `CourseJarIT` | 1 | 真实 JAR + 隔离类加载器 |

实测（仓库根目录，2026-11-24，Maven 3.9.11 + JDK 21.0.12.1）：

| 命令 | 结果 |
|---|---|
| `mvn -B clean verify` | 成功，4 个模块，60 项测试 |
| `mvn -pl course-plugin -am clean verify` | 成功，25 + 1 项 |
| `mvn -pl course-plugin -am clean package` | 成功，25 项，不跑 `CourseJarIT` |

**故障注入：未执行。** 方法见方案 §10.4（把星期过滤改成恒不匹配，`day` 查询应失败，恢复后通过），交付前需实测并记录。

**Checkstyle：未配置。CI：未配置，无任何运行记录。**

## 5. 协作

- 问题放 GitHub Issues；功能走 `feat/<主题>` 分支，修复走 `fix/<主题>`。
- 提交信息 `类型(范围): 简述`，一个提交一件事。
- 功能分支 → PR → 至少一名其他成员 review → 合并，合并前 `mvn clean verify` 必须通过。
- 平台不可用时可本地分支审查，但要在第 6 节记录被审查提交、意见、回应与结论，不虚构平台记录。

## 6. 成员贡献与 AI 核验

| 姓名/学号 | 实质改动（Commit/PR） | 审查他人改动 | AI 使用与核验 |
|---|---|---|---|
| A：胡城玮 | `campus-api` 8 个契约类型；`ServiceRegistry`、`ServiceDispatcher`、`PluginLoader`；核心 34 项测试；`docs/api-contract.md`。`363870c`…`014713a` 经本地合并 `d7e5414` 进入 `main`（未经 PR）；调度器加固在分支 `feat/core-dispatcher-hardening`，PR 待开 | 审查 C 的 `3e94c43`（插件契约与错误映射） | 本文件与审查意见由 AI 辅助整理，结论以实际构建/测试复核 |
| B：王湛阳 | 公告服务：未开始 | 待填 | 待填 |
| C：翁政杰 | 课程插件模块全部代码与测试，`3e94c43`（PR #1） | 待填（需审查 D 的集成测试与接入流程） | 插件 README 注明模块由 AI 辅助实现与测试，需本人核对语义 |
| D：吴诗祺 | CLI、配置、集成测试、CI、发行包：未开始 | 待填 | 待填 |

**审查记录**

| 被审查对象 | 审查人 | 意见 | 处理/回应 | 更新提交 | 结论 |
|---|---|---|---|---|---|
| 翁政杰 | 胡城玮 | 9 条，见下 | 待翁政杰回应 | — | 待定 |

1. 插件 README 验证数字过期（36 → 38），已由胡城玮更新，翁政杰确认即可。
2. 拒绝未知参数在契约中无依据 —— 已在 `docs/api-contract.md` 明确"插件可拒绝或忽略，须在自身 README 声明"，翁政杰确认即可。
3. 插件自校验 `serviceId` 与 §6.2 核心职责重复 —— 核心已补校验（本次 `feat/core-dispatcher-hardening`），翁政杰保留防御校验即可。
4. `NO_MATCH` 无使用场景 —— 已在 `docs/api-contract.md` 标注"暂不使用"，无需改动代码。
5. 资源 `/courses.csv` 位于 JAR 根，父加载器优先可能撞名，建议包内路径。
6. 只捕获 `IOException`，`RuntimeException` 会中断整批插件加载。
7. `invalid()` 回填 `SERVICE_ID`，应回填请求中的 serviceId。
8. 缺参数值为 null 的测试用例。
9. 参数校验优先于数据错误 —— 已在 `docs/api-contract.md` 写明该优先级，翁政杰确认即可。

## 7. 许可与限制

- **LICENSE 未添加**，交付前必须选定。建议 MIT（简洁）或 Apache-2.0（含专利授权），组内确认后写入。
- 依赖：JUnit 5.11.4（EPL-2.0，测试作用域）、Maven 官方插件（Apache-2.0，构建期），均不随发行物分发。
- 限制：无 CLI 不能运行；无公告服务；无配置启禁用；`ResultItem.fields` 无中文标签，CLI 无法生成"教师：…"；仅 Windows + JDK 21 验证；纯本地 CSV，无数据库与并发；不做认证与权限。
