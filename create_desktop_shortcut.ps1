$jar = Join-Path $PSScriptRoot 'stocksim.jar'
$icon = Join-Path $PSScriptRoot 'icon.ico'
if (-not (Test-Path -LiteralPath $jar)) { throw "App JAR not found: $jar" }
if (-not (Test-Path -LiteralPath $icon)) { throw "App icon not found: $icon" }

# Look in JDK folders first. Some Windows Java PATH shims can exist without a working JDK.
$candidates = @()
if ($env:JAVA_HOME) { $candidates += Join-Path $env:JAVA_HOME 'bin\java.exe' }
$candidates += Get-ChildItem 'C:\Program Files\Java\jdk-*\bin\java.exe' -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | Select-Object -ExpandProperty FullName
$pathJava = (Get-Command java.exe -ErrorAction SilentlyContinue).Source
if ($pathJava) { $candidates += $pathJava }
$java = $null
foreach ($candidate in ($candidates | Select-Object -Unique)) {
    if (Test-Path -LiteralPath $candidate) {
        & $candidate -version *> $null
        if ($LASTEXITCODE -eq 0) { $java = $candidate; break }
    }
}
if (-not $java) { throw 'Java 17 or later was not found. Install a JDK, then run this script again.' }

$desktop = [Environment]::GetFolderPath('Desktop')
$batchPath = Join-Path $desktop 'StockTradingSimulator.bat'
$shortcutPath = Join-Path $desktop 'StockTradingSimulator.lnk'
$batchContent = @"
@echo off
setlocal
cd /d "$PSScriptRoot"
"$java" -jar "$jar"
if errorlevel 1 (
    echo The simulator could not start. Check that Java 17 or later is installed.
    pause
)
endlocal
"@
Set-Content -LiteralPath $batchPath -Value $batchContent -Encoding ASCII

# The shortcut carries the custom icon and opens the batch launcher on double-click.
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $batchPath
$shortcut.WorkingDirectory = $PSScriptRoot
$shortcut.IconLocation = $icon
$shortcut.Save()
Write-Host "Launcher created: $batchPath"
Write-Host "Desktop icon created: $shortcutPath"
