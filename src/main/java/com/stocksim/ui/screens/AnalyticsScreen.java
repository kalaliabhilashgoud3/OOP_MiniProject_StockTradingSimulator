package com.stocksim.ui.screens;

import com.stocksim.model.Trader;
import com.stocksim.service.AnalyticsService;
import com.stocksim.service.TradingPlatform;
import com.stocksim.ui.components.Charts;
import com.stocksim.ui.components.Formatters;
import com.stocksim.ui.components.RoundedPanel;
import com.stocksim.ui.components.Theme;
import com.stocksim.ui.components.Ui;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.function.Supplier;

public class AnalyticsScreen extends JPanel implements Refreshable {
    private final TradingPlatform platform;
    private final Supplier<Trader> traderSupplier;
    private final JPanel cards = new JPanel(new GridLayout(2, 4, 12, 12));
    private final JPanel pnlChart = new JPanel(new BorderLayout());

    public AnalyticsScreen(TradingPlatform platform, Supplier<Trader> traderSupplier) {
        this.platform = platform; this.traderSupplier = traderSupplier;
        setLayout(new BorderLayout());
        JPanel page = Ui.page("Analytics", "Performance is calculated from simulator trades, account positions and virtual ledger records.");
        JPanel body = new JPanel(new BorderLayout(0, 14)); body.setOpaque(false);
        cards.setOpaque(false);
        body.add(cards, BorderLayout.NORTH);
        RoundedPanel chart = Ui.card("Daily, weekly and monthly realized P/L");
        chart.add(pnlChart, BorderLayout.CENTER);
        body.add(chart, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        add(page, BorderLayout.CENTER);
        refreshScreen();
    }

    @Override public void refreshScreen() {
        Trader trader = traderSupplier.get();
        if (trader == null) return;
        var metrics = new AnalyticsService(platform.getTradeHistory().getAll(),
                platform.getTransactionLedger().getAll(), platform.getStocks()).forTrader(trader);
        cards.removeAll();
        cards.add(metric("Trades", Integer.toString(metrics.totalTrades())));
        cards.add(metric("Winning / losing", metrics.winningTrades() + " / " + metrics.losingTrades()));
        cards.add(metric("Win rate", Formatters.percent(metrics.winRate())));
        cards.add(metric("Average profit", Formatters.money(metrics.averageProfit())));
        cards.add(metric("Average loss", Formatters.money(metrics.averageLoss())));
        cards.add(metric("Best realized stock", metrics.bestStock()));
        cards.add(metric("Worst realized stock", metrics.worstStock()));
        double total = metrics.realizedProfitLoss() + metrics.unrealizedProfitLoss();
        cards.add(metric("Portfolio return", Formatters.money(total) + " · "
                + Formatters.percent(trader.getInitialBalance() == 0 ? 0 : total * 100 / trader.getInitialBalance())));
        cards.revalidate(); cards.repaint();
        pnlChart.removeAll();
        pnlChart.add(Charts.periodPnl(metrics.dailyProfitLoss(), metrics.weeklyProfitLoss(), metrics.monthlyProfitLoss()), BorderLayout.CENTER);
        pnlChart.revalidate(); pnlChart.repaint();
    }

    private RoundedPanel metric(String title, String value) {
        RoundedPanel panel = Ui.card(title);
        JLabel label = new JLabel(value);
        label.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 17));
        label.setForeground(Theme.NAVY);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }
}
