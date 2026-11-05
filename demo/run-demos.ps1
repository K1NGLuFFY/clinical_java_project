<#
    Runs the three scripted demo sessions of the clinic system against one data folder so the
    restart-with-data-intact requirement is demonstrated as well. Requires Maven on PATH.
    Usage: powershell -ExecutionPolicy Bypass -File demo/run-demos.ps1 [-DataDir <folder>]
#>
param(
    [string]$DataDir = (Join-Path (Split-Path -Parent $PSScriptRoot) "demo-data")
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root "target\clinic-app.jar"
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin\java.exe" } else { "java" }

Push-Location $root
try {
    Write-Host "Building the clinic application..." -ForegroundColor Cyan
    & mvn -q -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "Maven build failed." }

    if (Test-Path $DataDir) { Remove-Item -Recurse -Force $DataDir }
    $scripts = Get-ChildItem (Join-Path $PSScriptRoot "*.txt") | Sort-Object Name
    foreach ($script in $scripts) {
        Write-Host ""
        Write-Host ("=== " + $script.Name + " ===") -ForegroundColor Cyan
        Get-Content $script.FullName | & $java -jar $jar "--data=$DataDir" "--now=2026-11-02T08:00"
        if ($LASTEXITCODE -ne 0) { throw "Demo $($script.Name) failed with exit code $LASTEXITCODE" }
    }
    Write-Host ""
    Write-Host "Data files after the demo (survived two restarts):" -ForegroundColor Cyan
    Get-ChildItem $DataDir | Select-Object Name, Length | Format-Table -AutoSize
    Write-Host "Run the self-test with: java -cp target/clinic-app.jar com.clinic.SelfTest"
}
finally {
    Pop-Location
}