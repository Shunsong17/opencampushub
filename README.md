# opencampushub

角色 C 的独立课程查询插件已实现，构建、查询规则、SPI 接入及本地测试记录见 [course-plugin/README.md](course-plugin/README.md)。

## 多插件运行

目前支持四个同 JVM 的独立插件 JAR，插件只依赖 campus-api，核心没有业务专用分支：

| 服务 ID | 模块 | 条件 |
|---|---|---|
| course_query | course-plugin | name、day、time |
| library_query | library-plugin | name、author、category |
| canteen_query | canteen-plugin | name、canteen、date、meal |
| activity_query | activity-plugin | keyword、category、date |

需要完整 JDK 17 或更高版本、Maven。集成测试会使用 JDK 编译器构造故障插件。在仓库根目录的 PowerShell 执行：

```powershell
mvn clean verify
.\scripts\prepare-plugins.ps1
.\scripts\run.ps1 list
.\scripts\run.ps1 query course_query "name=软件"
.\scripts\run.ps1 query library_query "name=Java"
.\scripts\run.ps1 query canteen_query "canteen=第一食堂" "date=2026-11-25" "meal=午餐"
.\scripts\run.ps1 query activity_query "category=讲座" "date=2026-11-25"
```

预期分别返回软件工程、Java程序设计、两道午餐菜品、两场讲座。日期为固定样例日期，不随系统日期变化。
准备脚本复制四个已构建 JAR 到根目录 plugins，重跑时更新这四个同名制品，不清理其他插件。
若本机策略禁止 PowerShell 脚本，可手动复制这四个模块 target 下的插件 JAR 到 plugins，使用：

```powershell
[Console]::OutputEncoding = [Text.UTF8Encoding]::new()
java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp "campus-api/target/campus-api-0.1.0-SNAPSHOT.jar;campus-core/target/campus-core-0.1.0-SNAPSHOT.jar" com.opencampus.core.Main plugins.properties list
java "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -cp "campus-api/target/campus-api-0.1.0-SNAPSHOT.jar;campus-core/target/campus-core-0.1.0-SNAPSHOT.jar" com.opencampus.core.Main plugins.properties query library_query "name=Java"
```

Linux/macOS 类路径分隔符改为冒号。插件 JAR 不应加到核心启动类路径上，由加载器从插件目录发现。

也可在你允许执行这些仓库脚本时，仅对单次进程放行，不更改系统策略：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\prepare-plugins.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run.ps1 list
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\run.ps1 query library_query "name=Java"
```

## 配置与错误隔离

编辑根目录 plugins.properties；目录相对于配置文件所在位置解析，未指定开关的服务默认启用。
例如将 library_query.enabled 改为 false，下次启动时图书服务不出现在目录，查询返回 SERVICE_DISABLED。
无需重新构建核心；每条命令启动一次应用，不支持热加载。开关仅控制可用性，禁用插件仍可能在发现阶段被实例化，不是安全沙箱。

加载器按 JAR 文件名排序；同 ID 的首个有效服务保留，后续重复项报告 PLUGIN_LOAD_FAILED。
API 版本接受 1.x 数字版本，拒绝其他主版本。坏 JAR、错误 SPI、初始化失败和版本不兼容会记录文件、可获得的服务 ID 与原因，继续加载其他 JAR。
查询异常由调度器转换为 PLUGIN_EXECUTION_FAILED；应用退出关闭所有插件类加载器。
对 JVM 退出、内存耗尽或无限循环没有进程级隔离保障。

## 查询规则与数据

新增插件至少一个有效条件，多条件取交集，去除首尾空白；名称/作者/活动关键词采用包含匹配且英文忽略大小写，其余条件精确匹配。
非法参数返回 INVALID_PARAMETER，无匹配成功返回空列表；缺失或坏数据返回 DATA_LOAD_FAILED。
每个新增插件使用独立资源路径，数据格式和独立构建命令见：

- [图书插件](library-plugin/README.md)：库存数量非负整数，可借数量为零的馆藏仍可查询。
- [食堂插件](canteen-plugin/README.md)：LocalDate 日期、BigDecimal 金额（最多两位小数），餐次为早餐/午餐/晚餐。
- [活动插件](activity-plugin/README.md)：LocalDate 日期、LocalTime 时间，关键词匹配活动名称。

仅使用本地样例；没有借还书、下单支付、活动报名、数据库或真实学校系统。

## 验证

`mvn clean verify` 执行原有测试、新增插件单元/SPI 测试和 integration-tests 的真实多 JAR 集成测试。
集成测试从测试运行类路径排除全部业务插件，仅通过核心加载器访问实际打包 JAR，覆盖四服务查询、禁用、坏 JAR、错误 SPI、重复 ID、版本不兼容、初始化失败、执行失败、CLI 和关闭后资源释放。
单插件构建示例：`mvn -pl library-plugin -am clean package`；这不会执行多插件集成测试。

本次扩展包括通用命令行入口、配置和加载器的必要配套修改，需与角色 D 协调审查。当前分支没有公告实现，测试验证其他健康插件仍可使用，不声称已验证公告。
CI、Checkstyle 和完整发行流程未在本次实现中配置。代码由 AI 辅助实现，实际团队审查记录需另行填写。

### 本地验证记录（2026-11-25）

- 根目录执行 `mvn -B clean verify` 成功：原有 36 项测试、新增插件 30 项测试、多插件集成 6 项测试，共 72 项，失败/错误/跳过均为 0。
- 编译目标为 Java 17；此次成功不等同于已验证 JDK 17 运行时或 CI。
- 准备脚本成功复制四个插件 JAR；启动脚本的 list 显示四个服务。
- 启动脚本查询第一食堂、2026-11-25 午餐，实际输出番茄炒蛋 8.50 和红烧牛肉 18.00，UTF-8 中文显示正常。
- 本机脚本执行策略默认阻止直接运行，以上脚本通过获准的单次 PowerShell 进程执行，未修改系统策略。
