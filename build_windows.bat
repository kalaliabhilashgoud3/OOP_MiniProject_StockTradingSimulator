@echo off
setlocal
where mvn >nul 2>nul || (echo Maven is required.& exit /b 1)
where jpackage >nul 2>nul || (echo JDK 17+ with jpackage is required.& exit /b 1)
call mvn clean package || exit /b 1
copy /Y target\stocksim.jar stocksim.jar >nul
if not exist dist mkdir dist
if not exist dist\input mkdir dist\input
copy /Y target\stocksim.jar dist\input\stocksim.jar >nul
jpackage --type app-image --name StockTradingSimulator --input dist\input --main-jar stocksim.jar --main-class com.stocksim.Main --icon icon.ico --dest dist
if errorlevel 1 exit /b 1
xcopy data dist\StockTradingSimulator\data /E /I /Y >nul
echo App image created at dist\StockTradingSimulator
endlocal
