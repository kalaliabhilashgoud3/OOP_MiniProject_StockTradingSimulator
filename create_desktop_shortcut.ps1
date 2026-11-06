param([string]$AppPath = (Join-Path $PSScriptRoot 'dist\StockTradingSimulator'))
$exe = Join-Path $AppPath 'StockTradingSimulator.exe'
$desktop = [Environment]::GetFolderPath('Desktop')
$shortcutPath = Join-Path $desktop 'StockTradingSimulator.lnk'
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
if (Test-Path -LiteralPath $exe) {
    $shortcut.TargetPath = $exe
    $shortcut.WorkingDirectory = $AppPath
} else {
    # A JAR shortcut also works before building the optional jpackage app image.
    $jar = Join-Path $PSScriptRoot 'stocksim.jar'
    $javaw = (Get-Command javaw.exe -ErrorAction SilentlyContinue).Source
    if (-not (Test-Path -LiteralPath $jar)) { throw "App JAR not found: $jar" }
    if (-not $javaw) { throw "javaw.exe was not found. Install JDK 17+ and add its bin folder to PATH." }
    $shortcut.TargetPath = $javaw
    $shortcut.Arguments = "-jar `"$jar`""
    $shortcut.WorkingDirectory = $PSScriptRoot
}
$shortcut.IconLocation = (Join-Path $PSScriptRoot 'icon.ico')
$shortcut.Save()
Write-Host "Desktop shortcut created: $shortcutPath"
