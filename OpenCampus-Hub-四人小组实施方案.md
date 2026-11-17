# OpenCampus Hub 校园服务插件平台：四人小组实施方案

> 本文依据《大作业任务书》《项目交付模板与检查清单》《项目选题与参考案例》整理，作为项目实施、分工、验收和发布的统一计划。项目使用 Java 实现，面向本地样例数据，不依赖学校真实系统。

## 1. 项目定位

### 1.1 目标用户与核心场景

校园学生或教职工通过统一命令行入口查看可用校园服务、输入查询条件，并获得结构统一且可核对的校园信息结果或错误提示。

核心流程：

```text
用户查看服务目录
    → 选择服务并输入条件
    → Campus Core 查找并调用服务/插件
    → 返回统一结果或错误
    → CLI 展示结果
```

### 1.2 本次范围

- 基础公告查询。
- 服务目录、服务注册/发现、查询调度和统一错误处理。
- 一个独立课程查询插件，支持按课程名称、星期或时间查询。
- 使用配置启用或禁用课程插件，修改配置后重启生效。
- Java CLI、本地 CSV 样例数据、自动化测试和可运行发行物。

### 1.3 明确不做

- 登录、认证、权限、支付。
- 真实学校教务系统、数据库或外部 API。
- Web/移动端、插件市场、热加载、在线安装。
- 完整教务系统、课程冲突检测或其他额外校园业务。

## 2. 技术选型

| 项目 | 选择 |
|---|---|
| 语言 | Java 17 |
| 构建 | Maven 多模块项目 |
| 交互 | 命令行 CLI |
| 扩展发现 | Java SPI (`ServiceLoader`) 与插件 JAR 专用类加载器 |
| 数据 | 随项目提供的 CSV 样例数据 |
| 测试 | JUnit 5 |
| 基础质量检查 | Checkstyle |
| CI | GitHub Actions 或课程允许的平台 CI |

## 3. 总体架构

```text
                        ┌───────────────────────┐
                        │        CLI 用户        │
                        │ 查看目录 / 输入查询条件 │
                        └───────────┬───────────┘
                                    │
                        ┌───────────▼───────────┐
                        │      campus-core      │
                        │ CLI / 配置 / 调度器    │
                        │ 注册发现 / 结果展示    │
                        └───────┬─────────┬─────┘
                                │         │ Java SPI
                       内置基础能力       │
              ┌─────────────────▼──┐   ┌──▼──────────────────┐
              │ AnnouncementService │   │   course-plugin      │
              │ 公告查询基础能力     │   │ CourseService        │
              └─────────┬─────────┘   │ 独立课程查询扩展      │
                        │             └──────────┬───────────┘
              ┌─────────▼─────────┐              │
              │ announcements.csv │       ┌──────▼───────┐
              └───────────────────┘       │ courses.csv  │
                                          └──────────────┘

                       ┌─────────────────┐
                       │   campus-api    │
                       │ 公共接口与模型   │
                       └─────────────────┘
```

模块依赖方向：

```text
campus-core    → campus-api
course-plugin  → campus-api
```

`course-plugin` 不得依赖 `campus-core` 的内部类。扩展只依赖公开 API，并通过约定接入。

## 4. Maven 项目结构

```text
OpenCampusHub/
├─ pom.xml
├─ README.md
├─ LICENSE
├─ .gitignore
├─ checkstyle.xml
├─ .github/workflows/ci.yml
├─ docs/DESIGN.docx
├─ campus-api/
│  ├─ pom.xml
│  └─ src/main/java/com/opencampus/api/
│     ├─ CampusService.java
│     ├─ ServiceMetadata.java
│     ├─ QueryParameter.java
│     ├─ QueryRequest.java
│     ├─ QueryResult.java
│     ├─ ResultItem.java
│     ├─ ErrorInfo.java
│     └─ ErrorCode.java
├─ campus-core/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/opencampus/core/
│     │  ├─ Main.java
│     │  ├─ cli/CommandLineApp.java
│     │  ├─ config/PluginConfig.java
│     │  ├─ registry/ServiceRegistry.java
│     │  ├─ dispatcher/ServiceDispatcher.java
│     │  ├─ plugin/PluginLoader.java
│     │  ├─ announcement/Announcement.java
│     │  ├─ announcement/AnnouncementService.java
│     │  └─ display/ResultPrinter.java
│     ├─ main/resources/
│     │  ├─ announcements.csv
│     │  └─ plugins.properties
│     └─ test/java/com/opencampus/core/
│        ├─ ServiceRegistryTest.java
│        ├─ ServiceDispatcherTest.java
│        ├─ AnnouncementServiceTest.java
│        └─ CoreIntegrationTest.java
├─ course-plugin/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/opencampus/plugin/course/
│     │  ├─ Course.java
│     │  └─ CourseService.java
│     ├─ main/resources/
│     │  ├─ courses.csv
│     │  └─ META-INF/services/com.opencampus.api.CampusService
│     └─ test/java/com/opencampus/plugin/course/CourseServiceTest.java
└─ integration-tests/
   ├─ pom.xml
   └─ src/test/java/com/opencampus/integration/PluginIntegrationTest.java
```

最终打包目录另外包含 `release/`，其位置和内容见“发布与最终提交”。

## 5. 公共接口契约

### 5.1 服务接口

```java
package com.opencampus.api;

public interface CampusService {
    ServiceMetadata getMetadata();

    QueryResult query(QueryRequest request);
}
```

### 5.2 元信息与请求

```java
public record ServiceMetadata(
        String serviceId,
        String name,
        String description,
        List<QueryParameter> parameters,
        String apiVersion
) {}

public record QueryParameter(
        String name,
        String description,
        boolean required
) {}

public record QueryRequest(
        String serviceId,
        Map<String, String> parameters
) {}
```

课程服务元信息示例：

```text
serviceId: course_query
name: 课程查询
apiVersion: 1.0
parameters: name, day, time
```

### 5.3 统一结果与错误

```java
public record QueryResult(
        boolean success,
        String serviceId,
        String message,
        List<ResultItem> items,
        ErrorInfo error
) {}

public record ResultItem(String title, Map<String, String> fields) {}

public record ErrorInfo(ErrorCode code, String message) {}

public enum ErrorCode {
    SERVICE_NOT_FOUND,
    SERVICE_DISABLED,
    INVALID_PARAMETER,
    NO_MATCH,
    DATA_LOAD_FAILED,
    PLUGIN_LOAD_FAILED,
    PLUGIN_EXECUTION_FAILED,
    INTERNAL_ERROR
}
```

成功结果示例：

```json
{
  "success": true,
  "serviceId": "course_query",
  "message": "查询完成，共找到 1 条记录",
  "items": [
    {
      "title": "软件工程",
      "fields": {
        "teacher": "张老师",
        "time": "周三 08:00-09:35",
        "location": "A201"
      }
    }
  ],
  "error": null
}
```

错误结果示例：

```json
{
  "success": false,
  "serviceId": "course_query",
  "message": "查询失败",
  "items": [],
  "error": {
    "code": "INVALID_PARAMETER",
    "message": "课程查询至少需要提供 name、day 或 time 中的一项"
  }
}
```

### 5.4 错误语义

| 情况 | 结果约定 |
|---|---|
| 查询成功且有数据 | `success=true`，返回条目 |
| 查询成功但无匹配 | `success=true`、`items=[]`，message 提示未找到；不作为系统故障 |
| 服务 ID 不存在 | `SERVICE_NOT_FOUND` |
| 插件配置禁用 | `SERVICE_DISABLED` |
| 未提供有效查询条件 | `INVALID_PARAMETER` |
| 数据文件无法读取 | `DATA_LOAD_FAILED` |
| 插件执行抛出异常 | `PLUGIN_EXECUTION_FAILED` |
| 插件无法加载 | `PLUGIN_LOAD_FAILED`，记录服务 ID 与原因，不让整个基础公告功能退出 |

### 5.5 兼容策略

- 公共 API 初始版本为 `1.0`，插件元信息声明其 API 版本。
- 新增输出字段应为可选字段，消费者忽略未知字段。
- 不改变既有字段语义，不静默改变必填参数。
- 方法签名、必填字段或既有语义出现不兼容变更时提升主版本，例如 `2.0`，并更新接入说明。
- 本项目不提供多主版本插件兼容框架；运行时拒绝不兼容主版本并报告清晰错误。

## 6. 注册、发现与调度

### 6.1 注册中心职责

`ServiceRegistry` 提供以下能力：

```java
void register(CampusService service);
void unregister(String serviceId);
Optional<CampusService> find(String serviceId);
List<ServiceMetadata> listMetadata();
```

注册时检查服务 ID 非空、元信息合法和 ID 重复。重复 ID 不应静默覆盖，应报告注册失败并保留原服务。

### 6.2 调度器职责

`ServiceDispatcher`：

1. 根据请求的 `serviceId` 查找服务。
2. 检查该服务是否启用。
3. 校验请求服务 ID 与服务元信息匹配。
4. 调用插件 `query`。
5. 将插件异常转换为统一错误结果，避免异常堆栈直接暴露给 CLI 用户。

### 6.3 Java SPI 插件发现

课程插件 JAR 包含：

```text
META-INF/services/com.opencampus.api.CampusService
```

文件内容：

```text
com.opencampus.plugin.course.CourseService
```

插件不能只放入任意 `plugins/` 文件夹就期望默认类路径发现。核心的 `PluginLoader` 应从配置指定的插件目录读取 JAR，创建 `URLClassLoader`，并通过该类加载器调用 `ServiceLoader.load(CampusService.class, pluginClassLoader)`。每个插件加载或初始化失败应被隔离并转换为诊断信息；关闭应用时关闭相应类加载器。

### 6.4 启用和禁用配置

`plugins.properties`：

```properties
announcement.enabled=true
course_query.enabled=false
plugins.directory=plugins
```

启用课程插件的步骤：

1. 独立构建 `course-plugin`。
2. 将产出的插件 JAR 放到配置的 `plugins/` 目录。
3. 设置 `course_query.enabled=true`。
4. 重启应用。
5. 查看服务目录并查询课程。

禁用时设置为 `false` 并重启。目录中仍存在的插件 JAR 不会出现在可用服务目录，按约定返回 `SERVICE_DISABLED`。本项目不做热加载；不要求重打核心 JAR。

### 6.5 独立构建

从项目根目录执行：

```bash
mvn -pl course-plugin -am clean package
```

插件模块只依赖 `campus-api`，不得依赖 `campus-core`。接入插件只需构建插件 JAR、放入插件目录并修改配置，核心源代码不变。测试应通过实际 SPI 加载证明这一点。

## 7. 服务与样例数据

### 7.1 公告基础服务

服务 ID：`announcement_query`。

支持参数：

| 参数 | 示例 | 行为 |
|---|---|---|
| `keyword` | `图书馆` | 匹配标题或正文关键词 |
| `category` | `活动` | 按分类筛选 |

`announcements.csv` 示例：

```csv
id,title,category,date,content
A001,校园运动会通知,活动,2026-11-20,请各班按时参加运动会
A002,图书馆闭馆通知,服务通知,2026-11-22,图书馆设备维护期间暂停开放
A003,奖学金材料提交提醒,教务通知,2026-11-25,请在截止日期前提交申请材料
```

公告查询属于核心的基础能力，不要求把它包装为第二个独立插件。

### 7.2 课程独立插件

服务 ID：`course_query`。

支持参数：

| 参数 | 是否必填 | 示例 |
|---|---:|---|
| `name` | 条件之一 | `软件` |
| `day` | 条件之一 | `周三` |
| `time` | 条件之一 | `08:00` |

至少提供一个条件。多条件组合时取交集。匹配规则和大小写/空白处理应在 README 中说明。

`courses.csv` 示例：

```csv
id,name,teacher,day,time,location
C001,软件工程,张老师,周三,08:00-09:35,A201
C002,数据库原理,李老师,周四,10:00-11:35,B302
C003,Java程序设计,王老师,周三,10:00-11:35,C105
C004,计算机网络,赵老师,周五,14:00-15:35,A302
```

## 8. CLI 交互

主菜单：

```text
================================
       OpenCampus Hub
================================
1. 查看可用服务
2. 查询服务
0. 退出
请选择：
```

目录显示每个服务的 ID、名称、说明和查询参数。查询时按服务元信息提示输入参数；允许用户取消返回菜单。对空输入、未知服务 ID、非法选项和插件异常提供明确反馈，不显示原始 Java 堆栈给普通用户。

成功结果示例：

```text
查询完成，共找到 1 条记录

[1] 软件工程
教师：张老师
时间：周三 08:00-09:35
地点：A201
```

## 9. 四人分工与交叉审查

| 成员 | 主责 | 实质代码/测试产出 | 建议审查对象 |
|---|---|---|---|
| A | 公共 API、注册中心、调度器 | `campus-api` 模型和接口；注册/发现/调度；对应单元测试 | C 的插件契约和错误映射 |
| B | 公告基础服务 | 公告模型、CSV 加载、查询实现、单元测试 | A 的参数校验/错误处理 |
| C | 独立课程扩展 | 独立 Maven 模块、课程查询、SPI 描述文件、课程测试 | D 的插件集成测试与接入流程 |
| D | CLI、配置加载、集成/发布 | CLI、插件目录加载配置、集成测试、CI、Checkstyle、README 和发布脚本 | B 的数据边界和查询测试 |

C 负责独立扩展但不负责核心模块开发，符合扩展成员依据公开契约接入的要求。D 可以编写核心加载器，但应确保 C 的插件实现仅依赖 API，并由独立接入测试验证。

每人都必须：

- 有至少一项有实际内容的代码或测试修改。
- 至少审查一次其他成员的修改。
- Review 针对行为、边界、失败处理、测试或扩展契约提出具体意见。
- 对意见作出修改或有依据的回应，并由审查者核对后再合并。
- 能说明自己的贡献、解释一条完整运行流程，并理解至少一个他人模块/接口。

采用分支与 PR/MR；平台无法使用时用本地分支审查，并在 README 记录作者、实际审查人、被审查 Commit、具体意见、处理/回应、更新 Commit 和结论。不得虚构平台或 Review 记录。

## 10. 测试计划

### 10.1 核心与公告测试

- 注册、查找、列目录、注销。
- 重复服务 ID 不静默覆盖。
- 未知服务返回 `SERVICE_NOT_FOUND`。
- 禁用服务返回约定错误。
- 公告按关键词、分类和组合条件查询。
- 公告无匹配返回空条目与清晰提示。
- 空条件、坏数据行、缺少数据文件的行为有断言。

### 10.2 课程插件测试

- 按课程名称查询。
- 按星期查询。
- 按时间查询。
- 多条件交集查询。
- 无匹配结果。
- 未提供任何条件返回 `INVALID_PARAMETER`。
- 插件数据缺失/格式错误时行为符合文档。

### 10.3 插件集成/契约测试

至少一条集成测试通过真实公开接口和 SPI 加载验证：

1. 核心从独立构建的插件 JAR 发现课程服务。
2. 启用配置后服务目录出现 `course_query`。
3. 使用标准 `QueryRequest` 调用后得到字段可核对的课程结果。
4. 禁用后服务不可用，行为与 README 一致。
5. 插件异常时核心返回约定的 `PLUGIN_EXECUTION_FAILED`，公告基础能力仍可用。
6. 接入插件无需修改核心源码；测试可以对比/检查核心源代码无课程专用分支，并实际加载插件制品。

### 10.4 故意错误演示

选择一个有针对性的错误，例如将 `CourseService` 的星期匹配逻辑改为总是不匹配。执行测试应因课程星期查询断言失败；恢复实现后测试通过。README 记录错误位置、触发命令、预期失败和恢复方法。无需提交故障版本或额外截图。

### 10.5 命令

```bash
mvn clean verify
mvn -pl course-plugin -am clean package
mvn checkstyle:check
```

每条命令在 README 标明执行目录和实际结果。若 `verify` 已绑定 Checkstyle，仍可提供单独检查命令作为本地质量检查入口。

## 11. CI 与质量控制

`.github/workflows/ci.yml` 示例：

```yaml
name: Java CI

on:
  push:
  pull_request:

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"
          cache: maven
      - name: Build, test and style check
        run: mvn clean verify
```

CI 应至少保留一次真实成功记录，并在 README 链接运行记录或写出可核对的编号/状态。不得把本地成功写成 CI 成功。若托管平台不可用，如实记录限制和本地验证结果。

## 12. README 规划

仓库 `README.md` 至少覆盖以下栏目：

1. **用户、场景与范围**：用户是谁、完整输入到输出、基础与扩展能力、不做什么、仓库地址/托管说明。
2. **快速开始**：JDK/Maven 版本、构建与运行命令、执行目录、样例和预期输出。
3. **核心与扩展**：模块作用、设计文档链接、插件独立构建、接入/禁用、是否需重启/重打包、核心源码不变的核验方式。
4. **测试与检查**：正常、边界/失败、扩展测试覆盖；故障注入演示；Checkstyle 命令；CI 配置和真实状态。
5. **协作与贡献入口**：如何报告问题、分支、PR、Review 和合并；代表性审查意见及其处理结果。
6. **成员贡献与 AI 核验**：每人贡献、审查记录、关键 AI 建议及采用情况和验证结论；未使用 AI 写“未使用”。
7. **许可与已知限制**：项目许可证选择理由、关键依赖许可义务、功能/环境限制。

贡献表模板：

```markdown
| 姓名/学号 | 本人实质改动及 Commit/PR | 审查他人的改动及结果 | 关键决策与 AI 核验 |
|---|---|---|---|
| 成员 A | | | |
| 成员 B | | | |
| 成员 C | | | |
| 成员 D | | | |
```

## 13. 系统设计文档 `docs/DESIGN.docx`

必须将设计文档以 `project/docs/DESIGN.docx` 纳入 Git。可先用 Markdown 编写，再导出为 Word；最终检查 Word 内容与提交代码一致。建议目录：

1. 项目概述与需求：目标用户、场景、输入输出、范围与完成条件。
2. 总体架构：模块、依赖方向、外部依赖及选择理由；包含架构图。
3. 模块设计：职责、关键类/接口、数据流和对应代码目录。
4. 扩展机制与接口设计：契约、输入输出、错误、注册/发现、兼容策略和独立接入示例。
5. 关键业务流程：至少一张流程图或时序图，包含一个失败分支。
6. 数据与配置：CSV 结构、配置项、样例和敏感信息策略；未使用数据库可明确说明。
7. 测试与质量方案：正常、边界/失败、扩展接入、CI 和 Checkstyle，并指向实际测试。
8. 设计取舍、局限与后续计划：至少两项有实质意义的取舍，说明替代方案或代价。

避免大段通用概念或源码粘贴。若保留 Markdown 源稿，可与 DOCX 一同提交，但 DOCX 是指定交付格式，不能缺失。

## 14. 发布与最终提交

### 14.1 发布步骤

1. 完成所有代码、文档和版本说明提交。
2. 确认工作区干净。
3. 执行 `mvn clean verify`，确认本地构建、测试和基础检查通过。
4. 核对 CI 真实状态。
5. 构建发行包并按运行命令实际启动，演示公告和课程查询。
6. 生成版本 Tag，例如 `v0.1.0`，Tag 指向最终 Commit。
7. 计算制品 SHA-256，并写入仓库外 `release/RELEASE.md`。
8. 将整个提交目录压成 ZIP，包含 `.git`；解压到新目录后重新按 README 验证。

### 14.2 发布目录

```text
第X组_OpenCampusHub/
├─ project/
│  ├─ .git/
│  ├─ README.md
│  ├─ LICENSE
│  ├─ docs/DESIGN.docx
│  ├─ 源码、测试、样例数据
│  ├─ Maven 依赖声明
│  ├─ CI 与基础检查配置
│  └─ .gitignore
└─ release/
   ├─ opencampus-hub-0.1.0.zip
   └─ RELEASE.md
```

发行 ZIP 可包含核心、API、插件 JAR、配置、样例数据和运行脚本。不能依赖 IDE 启动。保留 Git 历史，不要用 `git archive` 替代完整工程压缩；排除缓存、IDE 目录、秘密和无关大文件。

### 14.3 `release/RELEASE.md` 内容

```markdown
# 本次版本交付

- 版本及选择理由：
- 最终 Commit（完整 SHA）：
- Tag 及其目标 Commit（完整 SHA）：
- 工作区状态、构建/测试结果及 CI 真实状态：
- 交付物名称、环境要求与运行命令：
- 交付物 SHA-256；原件/副本一致性：
- 核心场景及扩展操作的实际结果：
- 本次变化、已知限制/未验证范围：
- 发布决定：RELEASE / HOLD 及理由：
```

发布记录放在仓库外层 `release/`，最终 Commit、Tag 和 SHA 填入该文件，避免为记录最终摘要再次修改已发布仓库。

## 15. 演示流程与验收表

| 顺序 | 演示 | 预期结果 |
|---:|---|---|
| 1 | 展示仓库、分支、PR/Review 或本地审查记录 | 成员贡献及具体审查过程可追溯 |
| 2 | 执行 `mvn clean verify` | 构建、测试、Checkstyle 成功 |
| 3 | 启动应用并查看目录 | 默认显示公告基础服务 |
| 4 | 查询“图书馆”公告 | 返回样例数据中的公告及字段 |
| 5 | 构建课程插件，放入插件目录并启用配置 | 不改核心源码，重启后课程服务出现 |
| 6 | 按课程名称、星期或时间查询 | 返回不同课程业务结果 |
| 7 | 查询无匹配课程 | 成功响应、空条目和未找到提示 |
| 8 | 不输入条件或输入未知服务 | 返回统一、可读错误 |
| 9 | 禁用插件并重启 | 课程服务消失或按契约返回禁用错误 |
| 10 | 模拟插件失败 | 返回统一插件错误，基础公告服务仍可运行 |
| 11 | 展示故障注入测试 | 错误实现导致测试失败，恢复后通过 |
| 12 | 展示 Tag、制品、RELEASE.md 和 ZIP 解压运行 | 交付版本身份和可运行性可核验 |

## 16. 项目完成检查清单

- [ ] 一个核心场景可重复运行，公告能力可用。
- [ ] 存在一个独立课程扩展，增加实质查询能力。
- [ ] 插件仅依赖公开 API，可独立构建和通过 SPI 接入。
- [ ] 接入插件不修改核心源码；禁用和加载/执行失败行为有验证。
- [ ] 服务元信息、请求、结果、错误和兼容策略有文档。
- [ ] 测试有正常、边界/失败和插件接入断言。
- [ ] 演示过一个故意错误导致测试失败并恢复通过。
- [ ] 构建/测试命令清楚，CI 有真实成功记录或如实说明未执行限制。
- [ ] 执行过一种项目相关基础检查。
- [ ] 四人各有实质代码/测试贡献，且各自审查过他人改动。
- [ ] README 有成员贡献与 AI 核验、协作、许可证和限制说明。
- [ ] `docs/DESIGN.docx` 含架构图和关键流程图/时序图，并与实现一致。
- [ ] 有最终 Tag、已验证发行物和仓库外 `release/RELEASE.md`。
- [ ] 最终 ZIP 含隐藏 `.git`、源码、测试、设计文档、LICENSE、CI、样例和发行物。
- [ ] ZIP 已解压到新目录并按 README 验证运行。

若构建、运行或交付物一致性尚未完成，应在 `RELEASE.md` 标记 `HOLD` 并说明缺口，不得写成已验证发布。
