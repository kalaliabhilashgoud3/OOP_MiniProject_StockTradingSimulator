param([string]$AppPath = (Join-Path $PSScriptRoot 'dist\StockTradingSimulator'))
$exe = Join-Path $AppPath 'StockTradingSimulator.exe'
if (-not (Test-Path -LiteralPath $exe)) { throw "App executable not found: $exe. Run build_windows.bat first." }
$desktop = [Environment]::GetFolderPath('Desktop')
$shortcutPath = Join-Path $desktop 'StockTradingSimulator.lnk'
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $exe
$shortcut.WorkingDirectory = $AppPath
$shortcut.IconLocation = (Join-Path $PSScriptRoot 'icon.ico')
$shortcut.Save()
Write-Host "Desktop shortcut created: $shortcutPath"
