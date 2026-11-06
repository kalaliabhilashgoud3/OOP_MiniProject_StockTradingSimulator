@echo off
setlocal
set "MAVEN_CMD=mvn"
where mvn >nul 2>nul
if errorlevel 1 (
  if exist "work\apache-maven-3.10.0\bin\mvn.cmd" (
    set "MAVEN_CMD=work\apache-maven-3.10.0\bin\mvn.cmd"
  ) else (
    echo Maven is required. Install Maven or unpack it under work\apache-maven-3.10.0.
    exit /b 1
  )
)
set "JPACKAGE=jpackage"
where jpackage >nul 2>nul
if errorlevel 1 (
  for /d %%D in ("%ProgramFiles%\Java\jdk-*") do if exist "%%~fD\bin\jpackage.exe" set "JPACKAGE=%%~fD\bin\jpackage.exe"
  if "%JPACKAGE%"=="jpackage" (
    echo JDK 17+ with jpackage is required.
    exit /b 1
  )
)
call "%MAVEN_CMD%" clean package || exit /b 1
copy /Y target\stocksim.jar stocksim.jar >nul
if not exist dist mkdir dist
if not exist dist\input mkdir dist\input
copy /Y target\stocksim.jar dist\input\stocksim.jar >nul
if exist dist\StockTradingSimulator rmdir /S /Q dist\StockTradingSimulator
"%JPACKAGE%" --type app-image --name StockTradingSimulator --input dist\input --main-jar stocksim.jar --main-class com.stocksim.Main --icon icon.ico --dest dist
if errorlevel 1 exit /b 1
xcopy data dist\StockTradingSimulator\data /E /I /Y >nul
echo App image created at dist\StockTradingSimulator
endlocal
