param(
    [ValidateSet('dev', 'test', 'prod', 'prd')]
    [string]$Profile = 'dev',
    [string]$ConfigDirectory,
    [string[]]$JvmOptions = @(),
    [string[]]$AppArguments = @()
)
$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$jar = Join-Path $projectRoot 'assessment-server/target/assessment-server-1.0.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw '请先运行 scripts/build.ps1 构建可执行包。' }
$runtimeInfo = (& java --version | Out-String)
if ($LASTEXITCODE -ne 0 -or $runtimeInfo -notmatch '(?m)^(?:openjdk|java)\s+21(?:[.+-]|\s)') {
    throw '运行环境必须使用 JDK 21，请检查 PATH 中的 java。'
}
$appOptions = @("--spring.profiles.active=$Profile")
if ($ConfigDirectory) {
    $configRoot = [System.IO.Path]::GetFullPath($ConfigDirectory)
    if (-not (Test-Path -LiteralPath $configRoot -PathType Container)) { throw '外部配置目录不存在。' }
    $configUri = [Uri]::new($configRoot.TrimEnd('\', '/') + [System.IO.Path]::DirectorySeparatorChar)
    $appOptions += "--spring.config.additional-location=$($configUri.AbsoluteUri)"
}
Push-Location -LiteralPath $projectRoot
try {
    & java @JvmOptions -jar $jar @appOptions @AppArguments
    if ($LASTEXITCODE -ne 0) { throw '应用启动或运行失败，请检查日志。' }
} finally {
    Pop-Location
}
