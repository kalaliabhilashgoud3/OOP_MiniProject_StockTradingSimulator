# Stock Trading Platform Simulator

**MNR University · OOP Java · II-Year I-Semester**

## Problem definition

Buy and sell orders can arrive at different prices and times. A simulator needs to select the best available prices fairly, update account balances and holdings, and keep a record of each completed trade.

## Proposed solution

This Java 17 Swing project uses a price-time priority order book. Buy orders sort by highest price first and then earliest arrival; sell orders sort by lowest price and then earliest arrival. Orders cross when the best bid is at least the best ask. The project demonstrates encapsulation, inheritance, polymorphism, interfaces, collections, checked exceptions, CSV files, and a background price thread.

## Team

| Team member (name, roll number) |
|---|
| 25rbaim0028 — K.Abhilash Goud |
| 25rbaim0026 — K.chandhan |
| 25rbaim0027 — Narsing |
| 25rbaim0029 — K.thanish |
| 25rbaim0030 — saikiran |

## Module contributions

| Module | Contribution | Main files |
|---|---|---|
| 1 | Stocks, trader accounts, wallets, portfolios | `model/Stock.java`, `Trader.java`, `Portfolio.java` |
| 2 | Buy and sell order types and order entry | `model/Order.java`, `BuyOrder.java`, `SellOrder.java`, `ui/MainWindow.java` |
| 3 | Price-time priority and matching (owner module) | `service/OrderBook.java` |
| 4 | Concurrent market price updates | `service/PriceUpdater.java` |
| 5 | Trade history and portfolio calculations | `model/Trade.java`, `service/TradeHistory.java`, `model/Portfolio.java` |

## Run

Install JDK 17 or later and Maven. From the project folder run:

```powershell
mvn test
mvn package
java -jar stocksim.jar
```

The main window has Market, Place Order, Order Book, Portfolio, and Trade History tabs. A sample account list is included. Orders in this teaching simulator are in-memory; on exit stocks, traders, and completed trades are written to `data/` as CSV.

## Desktop app build

Run `build_windows.bat` from a Windows terminal with JDK 17+ installed. It builds the runnable JAR, creates a Windows app image with `jpackage`, and includes `icon.ico`. The generated app is in `dist/StockTradingSimulator`. Run `create_desktop_shortcut.ps1` from the project folder (or pass `-AppPath` to the app folder) to put a shortcut on the Desktop. Double-click **StockTradingSimulator** on the Desktop to start the simulator. `jpackage --type app-image` does not need WiX; EXE/MSI packaging can be added optionally when WiX is installed.

`icon.png` and `icon.ico` are the stock-chart app icon used by the Swing window and Windows packaging.

## Extra feature

Completed trades are exported to `data/trades.csv` when the window closes. Sample CSV files contain at least five Stock, Trader, Order, and Trade records.

## Tests

JUnit 5 cases cover matching, negative prices, insufficient wallet funds, no match, and best-bid priority.

## Screenshots

Add screenshots of the Market, Order Book, and Portfolio tabs here after launching the app.

## Project map

```text
src/main/java/com/stocksim/  Main, model, service, exception, util, ui
src/test/java/               OrderBook matching tests
data/                         Sample CSV records and saved data
docs/                         PlantUML class diagram
```
