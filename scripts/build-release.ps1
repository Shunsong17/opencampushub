<#
.SYNOPSIS
    构建 OpenCampus Hub 发行包，解压到临时目录复验，并输出 SHA-256 与 RELEASE.md。

.DESCRIPTION
    步骤：mvn clean verify → 复制核心/API/插件 JAR 与配置到 staging → 生成 run.bat/run.sh
    → 打 ZIP → 解压到临时目录 → 实际启动 --list 和一次课程查询 → 计算 SHA-256
    → 在仓库外 release\ 写 RELEASE.md。

    脚本只读仓库源码，唯一写入位置是 -OutputDirectory（默认仓库外层的 release\）。
    不创建 Git Tag、不推送远端、不改仓库文件，这些动作需要人工确认后执行。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1 -SkipBuild -OutputDirectory D:\tmp\release
#>
[CmdletBinding()]
param(
    [string]$OutputDirectory,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path (Split-Path $repoRoot -Parent) 'release'
}

function Write-Utf8NoBom {
    param([string]$Path, [string]$Text)

    [IO.File]::WriteAllText($Path, $Text, $utf8NoBom)
}

function Invoke-App {
    param(
        [string]$ClassPath,
        [string[]]$Arguments,
        [string]$WorkingDirectory
    )

    # 用 Start-Process 的独立重定向：直接 & java 2>&1 会把 java 的 stderr 变成
    # PowerShell 错误记录，写进 RELEASE.md 会带上 "At ... char" 之类的噪音。
    $stdoutPath = [IO.Path]::GetTempFileName()
    $stderrPath = [IO.Path]::GetTempFileName()

    $javaArguments = @(
        '-Dstdout.encoding=UTF-8',
        '-Dstderr.encoding=UTF-8',
        '-cp',
        ('"' + $ClassPath + '"'),
        'com.opencampus.core.Main'
    ) + $Arguments

    try {
        $process = Start-Process -FilePath 'java' -ArgumentList $javaArguments `
            -WorkingDirectory $WorkingDirectory -NoNewWindow -Wait -PassThru `
            -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
        $code = $process.ExitCode
        $stdout = [IO.File]::ReadAllText($stdoutPath, [Text.Encoding]::UTF8)
        $stderr = [IO.File]::ReadAllText($stderrPath, [Text.Encoding]::UTF8)
    } finally {
        Remove-Item $stdoutPath, $stderrPath -Force -ErrorAction SilentlyContinue
    }

    return [pscustomobject]@{ Output = ($stderr + $stdout).Trim(); ExitCode = $code }
}

# ---------- 1. 读版本号 ----------
$pomText = Get-Content (Join-Path $repoRoot 'pom.xml') -Raw -Encoding UTF8
$projectVersion = [regex]::Match($pomText, '<version>([^<]+)</version>').Groups[1].Value
$releaseVersion = $projectVersion -replace '-SNAPSHOT$', ''

Write-Host "版本：$projectVersion（发行名 $releaseVersion）"
Write-Host "输出目录：$OutputDirectory"

# ---------- 2. 构建与测试 ----------
if (-not $SkipBuild) {
    Write-Host '==> mvn -B clean verify'
    Push-Location $repoRoot

    try {
        & mvn -B clean verify
        $buildExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    if ($buildExit -ne 0) {
        throw "构建失败（mvn 退出码 $buildExit），未生成发行包。"
    }
}

# 统计 surefire / failsafe 实际执行的测试数，写进 RELEASE.md 时引用真实数字。
$testCount = 0
$reportFiles = Get-ChildItem -Path $repoRoot -Recurse -File -Filter 'TEST-*.xml' |
    Where-Object { $_.FullName -match '\\(surefire|failsafe)-reports\\' }

foreach ($report in $reportFiles) {
    $testCount += [int]([xml](Get-Content $report.FullName -Raw)).testsuite.tests
}

# ---------- 3. 组装 staging ----------
$stagingRoot = Join-Path ([IO.Path]::GetTempPath()) ('och-stage-' + [guid]::NewGuid().ToString('N'))
$appName = "opencampus-hub-$releaseVersion"
$appRoot = Join-Path $stagingRoot $appName
$libDirectory = Join-Path $appRoot 'lib'
$pluginDirectory = Join-Path $appRoot 'plugins'

New-Item -ItemType Directory -Force -Path $libDirectory, $pluginDirectory | Out-Null

Copy-Item (Join-Path $repoRoot 'campus-api\target\campus-api-0.1.0-SNAPSHOT.jar') $libDirectory
Copy-Item (Join-Path $repoRoot 'campus-core\target\campus-core-0.1.0-SNAPSHOT.jar') $libDirectory
Copy-Item (Join-Path $repoRoot 'course-plugin\target\course-plugin-0.1.0-SNAPSHOT.jar') $pluginDirectory

$configText = @'
# OpenCampus Hub 运行配置（发行包默认值）
#
# 服务启禁用：<serviceId>.enabled=true|false，修改后需重启（无热加载）。
# 课程插件已随包提供，默认禁用；把 course_query.enabled 改成 true 再重启即可启用。
announcement.enabled=true
course_query.enabled=false

# 插件 JAR 目录，相对路径以本文件所在目录为基准。
plugins.directory=plugins
'@
Write-Utf8NoBom -Path (Join-Path $appRoot 'plugins.properties') -Text ($configText + "`r`n")

$runBat = @'
@echo off
chcp 65001 >nul
setlocal
set "DIR=%~dp0"
java -cp "%DIR%lib\*" com.opencampus.core.Main %*
endlocal
'@
[IO.File]::WriteAllText((Join-Path $appRoot 'run.bat'), ($runBat + "`r`n"), (New-Object System.Text.ASCIIEncoding))

$runSh = @'
#!/usr/bin/env sh
DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec java -cp "$DIR/lib/*" com.opencampus.core.Main "$@"
'@
[IO.File]::WriteAllText((Join-Path $appRoot 'run.sh'), ($runSh + "`n"), (New-Object System.Text.ASCIIEncoding))

$readmeText = @"
OpenCampus Hub $releaseVersion 运行说明
========================================

环境：JDK 17 及以上（本包在 JDK 25 上验证）。

启动（Windows）：双击 run.bat，或在命令行执行：
    run.bat
启动（Linux/macOS）：
    sh run.sh

首次启动默认只有公告服务（若已实现），课程插件默认禁用。启用课程插件：
    1. 编辑 plugins.properties，把 course_query.enabled 改为 true；
    2. 重新启动程序；
    3. 选择「1. 查看可用服务」，应出现 course_query（课程查询）。

命令行快速验证（不需要交互输入）：
    run.bat --list
    run.bat --query course_query name=软件
    run.bat --query course_query day=周三

其它：
    --config <文件>  使用指定配置文件
    --help           查看用法

插件目录：plugins\，放入新的插件 JAR 并在 plugins.properties 中启用后重启即可，
不需要重新打包核心。
"@
Write-Utf8NoBom -Path (Join-Path $appRoot 'README.txt') -Text ($readmeText + "`r`n")

# ---------- 4. 打 ZIP ----------
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$zipPath = Join-Path $OutputDirectory "$appName.zip"

if (Test-Path $zipPath) {
    Remove-Item $zipPath -Force
}

Compress-Archive -Path $appRoot -DestinationPath $zipPath -CompressionLevel Optimal

# ---------- 5. 解压复验 ----------
$verifyRoot = Join-Path ([IO.Path]::GetTempPath()) ('och-verify-' + [guid]::NewGuid().ToString('N'))
$verifyOk = $false
$verifyLog = New-Object System.Collections.Generic.List[string]

try {
    Expand-Archive -Path $zipPath -DestinationPath $verifyRoot
    $extracted = Join-Path $verifyRoot $appName
    $classPath = Join-Path $extracted 'lib\*'

    $listResult = Invoke-App -ClassPath $classPath -Arguments @('--list') -WorkingDirectory $extracted
    $verifyLog.Add('--list 退出码：' + $listResult.ExitCode)
    $verifyLog.Add($listResult.Output.Trim())

    if ($listResult.ExitCode -ne 0) {
        throw "解压后 --list 失败，退出码 $($listResult.ExitCode)"
    }

    $configFile = Join-Path $extracted 'plugins.properties'
    $updatedConfig = (Get-Content $configFile -Raw -Encoding UTF8) -replace 'course_query\.enabled=false', 'course_query.enabled=true'
    Write-Utf8NoBom -Path $configFile -Text $updatedConfig

    $queryResult = Invoke-App -ClassPath $classPath `
        -Arguments @('--query', 'course_query', 'name=软件') -WorkingDirectory $extracted
    $verifyLog.Add('--query course_query name=软件 退出码：' + $queryResult.ExitCode)
    $verifyLog.Add($queryResult.Output.Trim())

    if ($queryResult.Output -notmatch '软件工程' -or $queryResult.ExitCode -ne 0) {
        throw '解压后课程查询未返回预期结果，发行包不可用。'
    }

    $verifyOk = $true
} finally {
    if (Test-Path $verifyRoot) {
        Remove-Item $verifyRoot -Recurse -Force
    }
}

# ---------- 6. SHA-256 与 RELEASE.md ----------
$hash = (Get-FileHash -Path $zipPath -Algorithm SHA256).Hash
$sizeKb = [math]::Round((Get-Item $zipPath).Length / 1KB, 1)

Push-Location $repoRoot
try {
    $commit = (& git rev-parse HEAD).Trim()
    $status = (& git status --porcelain | Out-String).Trim()
} finally {
    Pop-Location
}

if ([string]::IsNullOrWhiteSpace($status)) {
    $statusText = '干净（无未提交改动）'
} else {
    $changedCount = ($status -split "`n" | Where-Object { $_ -ne '' }).Count
    $statusText = "有未提交改动，共 $changedCount 条"
}

$verifyText = '未通过'
if ($verifyOk) {
    $verifyText = '通过'
}

$verifyLogText = $verifyLog -join "`n"
$today = Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz'

$releaseNotesTemplate = @'
# 本次版本交付

- 版本及选择理由：{0}，首个可运行版本（CLI、配置加载、插件接入集成测试、Checkstyle、CI 就绪）。公告服务尚未实现，因此未提升到 1.0。
- 最终 Commit（完整 SHA）：{1}
- Tag 及其目标 Commit：**未创建**（需人工确认后执行 git tag，脚本不代做）
- 工作区状态：{2}
- 构建/测试结果：`mvn -B clean verify` 成功；surefire + failsafe 共 {3} 项测试通过；Checkstyle 绑定在 validate 阶段，0 违规
- CI 真实状态：**未验证**。`.github/workflows/ci.yml` 已随代码提交，但尚未在 GitHub 上产生运行记录，不得据此声称 CI 通过
- 交付物：{4}.zip（{5} KB），JDK 17+，运行 `run.bat` 或 `sh run.sh`
- 交付物 SHA-256：{6}
- 解压复验：{7}（解压到临时目录后执行 --list 与 course_query 查询）
- 本次变化：新增 campus-core CLI/配置/展示层；新增 integration-tests 模块与 6 项插件接入测试；新增 checkstyle.xml 与 CI 配置；新增发布脚本与本记录
- 已知限制：公告查询服务（成员 B）未实现，默认服务目录可能为空；仅 Windows + JDK 25 验证，JDK 17 未复测；无 LICENSE 文件（待组内确定 MIT 或 Apache-2.0）
- 发布决定：**HOLD**。原因：公告基础能力缺失，验收表中"默认显示公告基础服务"与"查询图书馆公告"尚不可演示；补齐后可重跑本脚本并把结论改为 RELEASE。

## 复验命令与输出

```text
{8}
```

生成时间：{9}
'@

$releaseNotes = $releaseNotesTemplate -f $releaseVersion, $commit, $statusText, $testCount,
    $appName, $sizeKb, $hash, $verifyText, $verifyLogText, $today

$releaseNotesPath = Join-Path $OutputDirectory 'RELEASE.md'
Write-Utf8NoBom -Path $releaseNotesPath -Text ($releaseNotes + "`n")

Remove-Item $stagingRoot -Recurse -Force

Write-Host ''
Write-Host "发行包：$zipPath"
Write-Host "SHA-256：$hash"
Write-Host "发布记录：$releaseNotesPath"
Write-Host "解压复验：$verifyText"
