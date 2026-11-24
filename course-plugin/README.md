# 课程查询插件（角色 C）

Java 17 / Maven 模块，仅依赖 campus-api，服务 ID 为 `course_query`，API 版本 `1.0`。

在仓库根目录执行：

```sh
mvn -pl course-plugin -am clean verify
mvn -pl course-plugin -am clean package
```

产物为 `course-plugin/target/course-plugin-0.1.0-SNAPSHOT.jar`。`verify` 运行单元测试、SPI 测试和打包后的独立 JAR 加载测试；`package` 不运行 JAR 集成测试。

## 查询规则

- `name`：课程名称包含匹配，英文忽略大小写。
- `day`：精确匹配周一至周日。
- `time`：严格的 24 小时制 `HH:mm`，匹配上课区间 `[开始, 结束)`，不支持跨午夜。
- 至少一个有效条件，多条件取交集；去除输入首尾空白，null 值或空字符串视为未提供。
- 空请求、错误服务 ID、未知参数、全部条件为空、非法星期或时间返回 `INVALID_PARAMETER`。
- 无匹配为成功响应，条目为空；数据加载失败返回 `DATA_LOAD_FAILED`。参数校验优先于数据错误。
- 输出标题为课程名称，字段为 `id`、`teacher`、`time`（星期和时间区间）、`location`，按 CSV 顺序返回。

例如 `name=软件, day=周三, time=08:30` 返回软件工程、张老师、周三 08:00-09:35、A201；`time=09:35` 不匹配该课程。

## 样例数据

`courses.csv` 从插件 JAR 的资源读取，不依赖当前工作目录。采用 UTF-8，允许 BOM 和空行；表头固定为 `id,name,teacher,day,time,location`。当前是简单六列格式，不支持字段内逗号、引号和换行。所有字段必填、ID 唯一、星期有效、结束时间晚于开始时间。任意坏行导致整份数据加载失败，错误包含行号，不返回部分数据。仅有表头的文件视为空数据集。

## 与核心接入

将插件 JAR 放入核心配置的 `plugins.directory` 目录，设置 `course_query.enabled=true` 并重启。禁用设为 false 并重启。插件 JAR 不包含 API 类，API 由核心提供。

SPI 文件 `META-INF/services/com.opencampus.api.CampusService` 声明 `com.opencampus.plugin.course.CourseService`，实现类具有公开无参构造方法。

配置启禁用、CLI 展示与核心异常隔离由角色 D 负责。当前仓库尚无完整 CLI/配置流程，上述为团队约定的接入步骤，不能视为已完成端到端验证。

## 测试与协作

`CourseServiceTest` 覆盖查询、交集、时间边界、错误参数及坏数据；`CourseSpiTest` 验证公开接口；`CourseJarIT` 使用隔离类加载器，仅提供 API 与真实插件 JAR，验证 SPI 发现、资源读取和查询，排除测试类路径造成的假成功。

故障演示方法：临时将 CourseService 的星期过滤改成始终 false，运行 `mvn -pl course-plugin -am test`，星期查询与交集断言应失败；恢复后重新验证。此段是操作说明，实际执行结果另行记录。

本模块由 AI 辅助实现和测试；成员 C 应核对查询语义和实际验证结果。A 已于 2026-11-24 完成插件契约与错误映射审查（审查意见待整理进根 README 贡献表），本文件按复核结果更新了下方验证数字，其余意见待 C 处理后登记结论；C 对 D 的真实审查需另行记录，不能以此说明代替审查记录。

## 本地验证记录

### 2026-11-24 复核（`main` @ `d7e5414`，A）

- 环境：Apache Maven 3.9.11，JDK 21.0.12.1（Oracle，`release 17` 编译目标）；仍未在 JDK 17 运行环境复测。
- 根目录执行 `mvn -pl course-plugin -am clean verify`：成功，25 项单元/SPI 测试（`CourseServiceTest` 24 + `CourseSpiTest` 1）和 1 项真实 JAR 集成测试（`CourseJarIT`）通过，产出 `course-plugin/target/course-plugin-0.1.0-SNAPSHOT.jar`。
- 根目录执行 `mvn -pl course-plugin -am clean package`：成功，25 项单元/SPI 测试通过，不执行 `CourseJarIT`（failsafe 只绑定到 `integration-test`/`verify` 阶段）。
- 根目录执行 `mvn -B clean verify`：成功，根 pom、`campus-api`、`campus-core`、`course-plugin` 四个 reactor 模块全绿，全项目 38 项测试通过（核心 12 + 插件单元 25 + JAR 集成 1）。
- 仍未验证：CI、Checkstyle、完整 CLI 启禁用、故障注入演示；仓库尚无 Checkstyle 配置与 CLI。

### 2026-11-17 首次验证（C）

- Maven 3.9.14，Maven 使用 JDK 21.0.8，编译目标 release 17；尚未在 JDK 17 运行环境复测。
- 根目录执行 `mvn -pl course-plugin -am clean verify`：成功，25 项单元/SPI 测试和 1 项真实 JAR 集成测试通过，同时生成插件 JAR。
- 根目录执行 `mvn -B clean verify`：成功，包含当时核心的 10 项测试，全项目共 36 项测试通过（合并 `014713a` 新增的 2 项插件加载测试后为 38 项）。
- 目前未验证 CI、Checkstyle、完整 CLI 启禁用和故障注入演示；仓库尚无 Checkstyle 配置。
