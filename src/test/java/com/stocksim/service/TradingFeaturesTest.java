package com.stocksim.service;

import com.stocksim.model.BuyOrder;
import com.stocksim.model.OrderStatus;
import com.stocksim.model.OrderType;
import com.stocksim.model.Stock;
import com.stocksim.model.Trader;
import com.stocksim.model.SellOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class TradingFeaturesTest {
    @TempDir Path temp;

    @Test void marketBuyExecutesAgainstLabeledSimulatorLiquidity() throws Exception {
        var history = new TradeHistory();
        var stock = new Stock("XYZ", "Example", 25);
        var trader = new Trader("T", "Trader", 1_000);
        var order = new BuyOrder(trader, stock, OrderType.MARKET, 4, 0, 0);
        new OrderBook(history).submit(order);
        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertEquals(4, trader.getPortfolio().getQuantity("XYZ"));
        assertEquals("SIMULATED-LIQUIDITY", history.getAll().get(0).seller());
        assertEquals(899.95, trader.getWallet(), 0.01);
    }

    @Test void stopLossWaitsForTriggerThenExecutes() throws Exception {
        var history = new TradeHistory();
        var ledger = new TransactionLedger();
        var book = new OrderBook(history, ledger);
        var stock = new Stock("XYZ", "Example", 25);
        var trader = new Trader("T", "Trader", 1_000);
        var order = new BuyOrder(trader, stock, OrderType.STOP_LOSS, 2, 30, 30);
        book.submit(order);
        assertEquals(OrderStatus.TRIGGER_PENDING, order.getStatus());
        stock.applyMarketTick(31, 10, java.time.Instant.now());
        book.onPriceTick(stock);
        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertEquals(2, trader.getPortfolio().getQuantity("XYZ"));
        assertEquals(1, history.getAll().size());
    }

    @Test void rejectsSellOrderWhenSharesAreUnavailable() {
        var book = new OrderBook(new TradeHistory());
        var stock = new Stock("XYZ", "Example", 25);
        var trader = new Trader("T", "Trader", 1_000);
        assertThrows(IllegalArgumentException.class, () -> book.placeSell(new SellOrder(trader, stock, 25, 1)));
    }

    @Test void marketEngineAddsPriceHistoryAndStopsWhenClosed() {
        var stock = new Stock("XYZ", "Example", 25);
        var engine = new MarketEngine(java.util.List.of(stock));
        engine.setMarketOpen(false);
        long before = engine.getTicks();
        engine.tickOnce();
        assertEquals(before, engine.getTicks());
        engine.setMarketOpen(true);
        engine.tickOnce();
        assertEquals(before + 1, engine.getTicks());
        assertTrue(stock.getPriceHistory().size() >= 2);
        engine.close();
    }

    @Test void platformPersistsAndReloadsAccountPortfolioAndHistory() throws Exception {
        var platform = TradingPlatform.open(temp);
        var trader = platform.getTraderList().get(0);
        double original = trader.getWallet();
        platform.getAccounts().deposit(trader, 123.45);
        platform.getMarket().tickOnce();
        platform.getMarket().setTickMillis(750);
        platform.getMarket().setMarketOpen(false);
        platform.setDefaultTraderId(trader.getId());
        platform.setNotificationsEnabled(false);
        platform.setDarkTheme(true);
        platform.setUseCurrencyCode(true);
        platform.save();
        platform.getPriceUpdater().stop();
        platform.getMarket().close();

        var loaded = TradingPlatform.open(temp);
        assertEquals(original + 123.45, loaded.getTrader(trader.getId()).getWallet(), 0.01);
        assertEquals(loaded.getStocks().size(), loaded.getStockList().size());
        assertFalse(loaded.getTransactionLedger().getAll().isEmpty());
        assertEquals(750, loaded.getMarket().getTickMillis());
        assertFalse(loaded.getMarket().isMarketOpen());
        assertEquals(trader.getId(), loaded.getDefaultTraderId());
        assertFalse(loaded.isNotificationsEnabled());
        assertTrue(loaded.isDarkTheme());
        assertTrue(loaded.isUseCurrencyCode());
        loaded.close();
    }

    @Test void csvStoreRecoversFromMalformedFileUsingBackup() throws Exception {
        var csv = temp.resolve("sample.csv");
        com.stocksim.util.CsvStore.writeRows(csv, java.util.List.of(java.util.List.of("value"), java.util.List.of("safe")));
        com.stocksim.util.CsvStore.writeRows(csv, java.util.List.of(java.util.List.of("value"), java.util.List.of("new")));
        java.nio.file.Files.writeString(csv, "\"broken\n");
        assertEquals("safe", com.stocksim.util.CsvStore.readRows(csv).get(1).get(0));
    }
}
