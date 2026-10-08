param(
    [ValidateSet('validate', 'test', 'package', 'verify')]
    [string]$Goal = 'verify'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$sdkPath = Join-Path $projectRoot 'assessment-server/lib/DoorAccessIO.jar'
if (-not (Test-Path -LiteralPath $sdkPath -PathType Leaf)) { throw '缺少 lib/DoorAccessIO.jar。' }
Push-Location -LiteralPath $projectRoot
try {
    # Maven 使用 JAVA_HOME，其 JDK 可能与 PATH 中的 java 不同。
    $versionText = (& mvn -v | Out-String)
    if ($LASTEXITCODE -ne 0 -or $versionText -notmatch 'Java version:\s*21(?:[.,]|\s)') {
        throw '请让 Maven 使用 JDK 21（检查 JAVA_HOME 和 mvn -v）。'
    }
    & mvn -B -ntp install:install-file "-Dfile=$sdkPath" '-DgroupId=com.hnkjzyxy' '-DartifactId=door-access-io' '-Dversion=1.0' '-Dpackaging=jar'
    if ($LASTEXITCODE -ne 0) { throw '门锁 SDK 安装失败。' }
    & mvn -B -ntp $Goal
    if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败。' }
} finally {
    Pop-Location
}
